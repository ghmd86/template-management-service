# Template Admin Portal - Integration Architecture

## Overview

This document illustrates how the Template Admin Portal interacts with the Template Management Service for creating, updating, and disabling templates and their vendor mappings.

**Approval Workflow**: The template approval process is managed by **Pega CaseWiz**, which provides case management, workflow orchestration, and audit trail capabilities.

---

## 1. System Context - Admin Portal Integration

```mermaid
C4Context
    title Template Admin Portal - System Context

    Person(admin, "Template Admin", "Creates and manages document templates")
    Person(approver, "Template Approver", "Reviews and approves template changes")

    System(portal, "Template Admin Portal", "Web application for template management")

    System(tms, "Template Management Service", "REST API for template CRUD operations")
    System(pega, "Pega CaseWiz", "Workflow orchestration and approval management")

    System_Ext(letterApi, "Letter API Service", "Validates templates with vendors")
    System_Ext(printPartner, "Print Partner Service", "Validates print configurations")

    SystemDb(postgres, "PostgreSQL", "Template storage")

    Rel(admin, portal, "Creates/updates templates", "Browser")
    Rel(approver, pega, "Reviews/approves", "Browser")
    Rel(portal, tms, "API calls", "HTTPS/REST")
    Rel(portal, pega, "Creates approval case", "HTTPS/REST")
    Rel(pega, tms, "Updates status on approval", "HTTPS/REST")
    Rel(tms, letterApi, "Validates", "HTTPS")
    Rel(tms, printPartner, "Validates", "HTTPS")
    Rel(tms, postgres, "Persists", "R2DBC")
```

---

## 2. Admin Portal Architecture

```mermaid
flowchart TB
    subgraph AdminPortal["Template Admin Portal (Web App)"]
        subgraph UI["User Interface"]
            Dashboard[Dashboard]
            TemplateList[Template List]
            TemplateForm[Template Form]
            VendorConfig[Vendor Configuration]
        end

        subgraph Services["Frontend Services"]
            AuthService[Auth Service]
            TemplateService[Template Service]
            VendorService[Vendor Service]
            WorkflowService[Workflow Service]
        end

        subgraph State["State Management"]
            Store[Redux/State Store]
            Cache[Local Cache]
        end
    end

    subgraph PegaCaseWiz["Pega CaseWiz"]
        CaseManager[Case Manager]
        ApprovalQueue[Approval Queue]
        WorkflowEngine[Workflow Engine]
        AuditLog[Audit Trail]
    end

    subgraph API["Template Management Service :8081"]
        TC[Template Controller]
        TVC[Vendor Controller]
    end

    subgraph Auth["Identity Provider"]
        IDP[OAuth2/OIDC]
    end

    Dashboard --> TemplateList
    TemplateList --> TemplateForm
    TemplateForm --> VendorConfig

    TemplateService --> TC
    VendorService --> TVC
    WorkflowService --> CaseManager
    AuthService --> IDP

    UI --> Services
    Services --> Store
    Store --> Cache

    CaseManager --> WorkflowEngine
    WorkflowEngine --> ApprovalQueue
    WorkflowEngine --> AuditLog
    WorkflowEngine --> TC

    style AdminPortal fill:#e3f2fd
    style PegaCaseWiz fill:#fff9c4
    style API fill:#c8e6c9
    style Auth fill:#fff3e0
```

---

## 3. Template Lifecycle States

```mermaid
stateDiagram-v2
    [*] --> DRAFT: Create Template

    DRAFT --> PENDING_APPROVAL: Submit for Review
    DRAFT --> DRAFT: Save Draft

    PENDING_APPROVAL --> APPROVED: Approver Accepts
    PENDING_APPROVAL --> REJECTED: Approver Rejects
    PENDING_APPROVAL --> DRAFT: Withdraw

    REJECTED --> DRAFT: Revise

    APPROVED --> ACTIVE: Activate
    APPROVED --> APPROVED: Schedule Activation

    ACTIVE --> INACTIVE: Disable
    ACTIVE --> ACTIVE: Update (creates new version)

    INACTIVE --> ACTIVE: Re-enable
    INACTIVE --> ARCHIVED: Archive

    ARCHIVED --> [*]

    note right of DRAFT
        Template can be edited freely
        Vendor mappings can be added
    end note

    note right of ACTIVE
        Template is live and in use
        Updates create new versions
    end note
```

---

## 4. Create Template Flow

```mermaid
sequenceDiagram
    autonumber
    participant Admin as Template Admin
    participant Portal as Admin Portal
    participant TMS as Template Management<br/>Service
    participant LetterAPI as Letter API<br/>(SmartComm)
    participant DB as PostgreSQL

    rect rgb(240, 248, 255)
        Note over Admin,Portal: Step 1: Create Template Definition
        Admin->>Portal: Fill template form<br/>(type, LOB, display name, etc.)
        Portal->>Portal: Validate form fields
        Admin->>Portal: Click "Save Draft"
    end

    rect rgb(255, 248, 240)
        Note over Portal,TMS: Step 2: Save Template
        Portal->>+TMS: POST /templates<br/>{templateType, lineOfBusiness, displayName, ...}
        TMS->>TMS: Validate request
        TMS->>TMS: Check templateType uniqueness
        TMS->>+DB: INSERT master_template_definition<br/>(status=DRAFT, version=1)
        DB-->>-TMS: Template created
        TMS-->>-Portal: 201 Created<br/>{masterTemplateId, templateVersion: 1}
        Portal->>Portal: Update UI with template ID
        Portal->>Admin: Show success message
    end

    rect rgb(240, 255, 240)
        Note over Admin,LetterAPI: Step 3: Add Generation Vendor
        Admin->>Portal: Click "Add Vendor"
        Admin->>Portal: Select vendor: SmartComm<br/>Enter vendorTemplateKey
        Portal->>+TMS: POST /templates/vendors<br/>{masterTemplateId, vendor: "SmartComm",<br/>vendorType: "GENERATION", vendorTemplateKey}

        TMS->>+LetterAPI: POST /templates/validate<br/>{vendorTemplateKey}
        LetterAPI-->>-TMS: 200 OK (template valid)

        TMS->>+DB: INSERT template_vendor_mapping
        DB-->>-TMS: Vendor mapping created
        TMS-->>-Portal: 201 Created<br/>{templateVendorId}
        Portal->>Admin: Show vendor added
    end

    rect rgb(255, 240, 255)
        Note over Admin,DB: Step 4: Add Print Vendor (Optional)
        Admin->>Portal: Add Print Vendor: LPS
        Portal->>+TMS: POST /templates/vendors<br/>{masterTemplateId, vendor: "LPS",<br/>vendorType: "PRINT"}
        TMS->>+DB: INSERT template_vendor_mapping
        DB-->>-TMS: Print vendor created
        TMS-->>-Portal: 201 Created
    end

    rect rgb(255, 255, 224)
        Note over Admin,TMS: Step 5: Submit for Approval
        Admin->>Portal: Click "Submit for Approval"
        Portal->>+TMS: PATCH /templates/{id}/versions/1<br/>{recordStatus: "PENDING_APPROVAL"}
        TMS->>+DB: UPDATE record_status
        DB-->>-TMS: Updated
        TMS-->>-Portal: 200 OK
        Portal->>Admin: Template submitted for approval
    end
```

---

## 5. Update Template Flow

```mermaid
sequenceDiagram
    autonumber
    participant Admin as Template Admin
    participant Portal as Admin Portal
    participant TMS as Template Management<br/>Service
    participant DB as PostgreSQL

    rect rgb(240, 248, 255)
        Note over Admin,TMS: Step 1: Load Existing Template
        Admin->>Portal: Select template from list
        Portal->>+TMS: GET /templates/{id}/versions/{v}?includeVendors=true
        TMS->>+DB: SELECT template + vendors
        DB-->>-TMS: Template data
        TMS-->>-Portal: TemplateResponse
        Portal->>Admin: Display template form
    end

    rect rgb(255, 248, 240)
        Note over Admin,Portal: Step 2: Make Changes
        Admin->>Portal: Modify fields<br/>(displayName, config, etc.)
        Portal->>Portal: Track changes locally
    end

    alt Update In-Place (DRAFT status)
        rect rgb(240, 255, 240)
            Note over Portal,DB: Option A: Update Existing Version
            Portal->>+TMS: PATCH /templates/{id}/versions/{v}<br/>{displayName, templateConfig, ...}
            TMS->>+DB: UPDATE master_template_definition
            DB-->>-TMS: Updated
            TMS-->>-Portal: 200 OK (same version)
        end
    else Create New Version (ACTIVE status)
        rect rgb(255, 240, 255)
            Note over Portal,DB: Option B: Create New Version
            Portal->>+TMS: PATCH /templates/{id}/versions/{v}<br/>?createNewVersion=true<br/>{displayName, templateConfig, ...}
            TMS->>TMS: Get next version number
            TMS->>+DB: INSERT new version (v+1)<br/>with status=DRAFT
            DB-->>-TMS: New version created
            TMS-->>-Portal: 200 OK (new version number)
        end
    end

    rect rgb(255, 255, 224)
        Note over Admin,Portal: Step 3: Update Confirmation
        Portal->>Admin: Show update success<br/>Display version number
    end
```

---

## 6. Update Vendor Mapping Flow

```mermaid
sequenceDiagram
    autonumber
    participant Admin as Template Admin
    participant Portal as Admin Portal
    participant TMS as Template Management<br/>Service
    participant Vendor as Vendor API
    participant DB as PostgreSQL

    Admin->>Portal: Select vendor mapping to edit
    Portal->>+TMS: GET /templates/vendors/{vendorId}?includeTemplateDetails=true
    TMS-->>-Portal: VendorMappingResponse

    Admin->>Portal: Modify vendor settings<br/>(priority, timeout, rate limits)

    Portal->>+TMS: PATCH /templates/vendors/{vendorId}<br/>{priorityOrder: 2, timeoutMs: 45000,<br/>rateLimitPerMinute: 100}

    alt Vendor Template Key Changed
        TMS->>+Vendor: POST /templates/validate<br/>{newVendorTemplateKey}
        Vendor-->>-TMS: Validation result
    end

    TMS->>+DB: UPDATE template_vendor_mapping
    DB-->>-TMS: Updated
    TMS-->>-Portal: 200 OK

    Portal->>Admin: Vendor updated successfully
```

---

## 7. Disable Template Flow

```mermaid
sequenceDiagram
    autonumber
    participant Admin as Template Admin
    participant Approver as Template Approver
    participant Portal as Admin Portal
    participant TMS as Template Management<br/>Service
    participant DB as PostgreSQL
    participant DocHub as Document Hub

    rect rgb(255, 240, 240)
        Note over Admin,Portal: Step 1: Initiate Disable
        Admin->>Portal: Select active template
        Admin->>Portal: Click "Disable Template"
        Portal->>Portal: Show confirmation dialog<br/>"This will prevent new documents<br/>from using this template"
        Admin->>Portal: Confirm disable
    end

    rect rgb(255, 248, 240)
        Note over Portal,TMS: Step 2: Check Dependencies
        Portal->>+TMS: GET /templates/{id}/versions/{v}
        TMS-->>-Portal: Template details

        Portal->>+DocHub: GET /documents/count?templateId={id}&status=PENDING
        DocHub-->>-Portal: {count: 5}

        alt Has Pending Documents
            Portal->>Admin: Warning: 5 documents pending<br/>Disable anyway?
            Admin->>Portal: Confirm
        end
    end

    rect rgb(240, 255, 240)
        Note over Portal,DB: Step 3: Disable Template
        Portal->>+TMS: PATCH /templates/{id}/versions/{v}<br/>{activeFlag: false, recordStatus: "INACTIVE"}
        TMS->>+DB: UPDATE master_template_definition<br/>SET active_flag=false
        DB-->>-TMS: Updated
        TMS->>TMS: Invalidate cache
        TMS-->>-Portal: 200 OK
    end

    rect rgb(240, 248, 255)
        Note over Portal,DB: Step 4: Disable Vendor Mappings
        Portal->>+TMS: GET /templates/vendors?templateId={id}
        TMS-->>-Portal: List of vendors

        loop For each vendor
            Portal->>+TMS: PATCH /templates/vendors/{vendorId}<br/>{activeFlag: false}
            TMS->>+DB: UPDATE template_vendor_mapping
            DB-->>-TMS: Updated
            TMS-->>-Portal: 200 OK
        end
    end

    Portal->>Admin: Template disabled successfully
```

---

## 8. Disable Vendor Mapping Flow

```mermaid
sequenceDiagram
    autonumber
    participant Admin as Template Admin
    participant Portal as Admin Portal
    participant TMS as Template Management<br/>Service
    participant DB as PostgreSQL

    Admin->>Portal: Select vendor to disable
    Portal->>Portal: Show confirmation<br/>"Disable SmartComm for this template?"

    Admin->>Portal: Confirm disable

    Portal->>+TMS: PATCH /templates/vendors/{vendorId}<br/>{activeFlag: false, vendorStatus: "DISABLED"}

    TMS->>+DB: UPDATE template_vendor_mapping<br/>SET active_flag=false,<br/>vendor_status='DISABLED'
    DB-->>-TMS: Updated

    TMS->>TMS: Invalidate vendor cache
    TMS-->>-Portal: 200 OK

    Portal->>Portal: Check remaining active vendors

    alt No Active Vendors Remaining
        Portal->>Admin: Warning: No active vendors!<br/>Template cannot generate documents.
    else Has Backup Vendors
        Portal->>Admin: Vendor disabled.<br/>Failover to: Assentis (priority 2)
    end
```

---

## 9. Approval Workflow with Pega CaseWiz

```mermaid
sequenceDiagram
    autonumber
    participant Admin as Template Admin
    participant Portal as Admin Portal
    participant TMS as Template Management<br/>Service
    participant Pega as Pega CaseWiz
    participant Approver as Template Approver
    participant DB as PostgreSQL

    rect rgb(240, 248, 255)
        Note over Admin,Pega: Step 1: Submit for Approval (Create Case)
        Admin->>Portal: Click "Submit for Approval"
        Portal->>+TMS: PATCH /templates/{id}/versions/{v}<br/>{recordStatus: "PENDING_APPROVAL"}
        TMS->>+DB: UPDATE record_status
        DB-->>-TMS: Updated
        TMS-->>-Portal: 200 OK

        Portal->>+Pega: POST /cases<br/>{caseType: "TemplateApproval",<br/>templateId, version, requestor}
        Pega->>Pega: Create case & assign to approver pool
        Pega->>Pega: Start SLA timer
        Pega-->>-Portal: {caseId: "CASE-12345", status: "PENDING"}
    end

    rect rgb(255, 248, 240)
        Note over Pega,Approver: Step 2: Pega Notifies Approvers
        Pega->>Pega: Evaluate assignment rules
        Pega->>Approver: Email/In-app notification<br/>"Template pending approval"
    end

    rect rgb(240, 255, 240)
        Note over Approver,Pega: Step 3: Review in Pega CaseWiz
        Approver->>Pega: Open worklist
        Pega->>Pega: Display case details
        Approver->>Pega: Click "View Template"

        Pega->>+TMS: GET /templates/{id}/versions/{v}?includeVendors=true
        TMS-->>-Pega: Full template details
        Pega->>Approver: Display template for review

        Approver->>Pega: Add comments (optional)
        Approver->>Pega: Click "Approve"
    end

    rect rgb(255, 240, 255)
        Note over Pega,DB: Step 4: Pega Updates Template Status
        Pega->>+TMS: PATCH /templates/{id}/versions/{v}<br/>{recordStatus: "APPROVED", activeFlag: true,<br/>approvedBy: "approver@company.com"}
        TMS->>+DB: UPDATE status, active_flag, approved_by
        DB-->>-TMS: Updated
        TMS->>TMS: Invalidate cache
        TMS-->>-Pega: 200 OK
    end

    rect rgb(240, 248, 255)
        Note over Pega,Admin: Step 5: Case Resolution & Notification
        Pega->>Pega: Mark case as RESOLVED-APPROVED
        Pega->>Pega: Record audit trail
        Pega->>Admin: Notification: "Template approved"
        Pega->>Portal: Webhook: Case completed
        Portal->>Portal: Refresh template status
    end
```

---

## 9a. Pega CaseWiz - Rejection Flow

```mermaid
sequenceDiagram
    autonumber
    participant Approver as Template Approver
    participant Pega as Pega CaseWiz
    participant TMS as Template Management<br/>Service
    participant Admin as Template Admin
    participant DB as PostgreSQL

    Approver->>Pega: Open case from worklist
    Approver->>Pega: Review template details

    rect rgb(255, 240, 240)
        Note over Approver,Pega: Rejection with Comments
        Approver->>Pega: Enter rejection reason<br/>"Missing regulatory fields"
        Approver->>Pega: Click "Reject"
    end

    rect rgb(255, 248, 240)
        Note over Pega,DB: Update Template Status
        Pega->>+TMS: PATCH /templates/{id}/versions/{v}<br/>{recordStatus: "REJECTED",<br/>rejectionReason: "Missing regulatory fields"}
        TMS->>+DB: UPDATE record_status
        DB-->>-TMS: Updated
        TMS-->>-Pega: 200 OK
    end

    rect rgb(240, 255, 240)
        Note over Pega,Admin: Notify Admin of Rejection
        Pega->>Pega: Mark case as RESOLVED-REJECTED
        Pega->>Pega: Store rejection reason in audit
        Pega->>Admin: Notification with rejection reason
    end

    rect rgb(240, 248, 255)
        Note over Admin,TMS: Admin Revises Template
        Admin->>TMS: View rejection feedback
        Admin->>TMS: Update template to address issues
        Admin->>TMS: Resubmit for approval
        Note over Admin,Pega: New case created in Pega
    end
```

---

## 9b. Pega CaseWiz Integration Details

### Case Data Model

| Field | Type | Description |
|-------|------|-------------|
| `caseId` | String | Pega-generated case ID (e.g., CASE-12345) |
| `caseType` | String | "TemplateApproval" |
| `templateId` | UUID | Reference to master_template_id |
| `templateVersion` | Integer | Template version being approved |
| `templateType` | String | Type of template (e.g., MONTHLY_STATEMENT) |
| `requestor` | String | Admin who submitted for approval |
| `assignedTo` | String | Current approver (from pool) |
| `status` | String | PENDING, IN_REVIEW, APPROVED, REJECTED |
| `slaDeadline` | DateTime | Auto-escalation deadline |
| `comments` | List | Approval/rejection comments |
| `auditTrail` | List | All actions with timestamps |

### Pega API Endpoints

```http
# Create approval case
POST /prweb/api/v1/cases
Content-Type: application/json
Authorization: Bearer {pega-token}

{
  "caseTypeID": "DOCMGMT-TemplateApproval",
  "content": {
    "templateId": "550e8400-e29b-41d4-a716-446655440000",
    "templateVersion": 1,
    "templateType": "MONTHLY_STATEMENT",
    "lineOfBusiness": "CREDIT_CARD",
    "displayName": "Monthly Credit Card Statement",
    "requestor": "admin@company.com",
    "urgency": "NORMAL"
  }
}

# Get case status
GET /prweb/api/v1/cases/{caseId}

# Webhook callback (Pega → Portal)
POST /api/v1/webhooks/pega/case-update
{
  "caseId": "CASE-12345",
  "status": "RESOLVED-APPROVED",
  "templateId": "550e8400-e29b-41d4-a716-446655440000",
  "approvedBy": "approver@company.com",
  "approvedAt": "2024-01-15T14:30:00Z"
}
```

### Workflow Configuration in Pega

```
┌─────────────────────────────────────────────────────────────────┐
│                  PEGA CASEWIZ WORKFLOW                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────┐     ┌──────────┐     ┌───────────┐               │
│   │  START  │────▶│ PENDING  │────▶│ IN_REVIEW │               │
│   └─────────┘     └──────────┘     └───────────┘               │
│                        │                 │                      │
│                        │            ┌────┴────┐                 │
│                        │            ▼         ▼                 │
│                   [SLA Breach]  ┌────────┐ ┌──────────┐         │
│                        │        │APPROVED│ │ REJECTED │         │
│                        ▼        └────────┘ └──────────┘         │
│                   ┌─────────┐       │           │               │
│                   │ESCALATE │       │           │               │
│                   └─────────┘       ▼           ▼               │
│                        │        ┌───────────────────┐           │
│                        └───────▶│   RESOLVED        │           │
│                                 └───────────────────┘           │
│                                                                 │
│  Assignment Rules:                                              │
│  • Credit Card templates → CC Approval Team                     │
│  • Regulatory templates → Compliance Team                       │
│  • High-value templates → Senior Approvers                      │
│                                                                 │
│  SLA Configuration:                                             │
│  • Normal: 48 hours                                             │
│  • Urgent: 4 hours                                              │
│  • Escalation: Manager notification                             │
└─────────────────────────────────────────────────────────────────┘
```

---

## 10. Complete Admin Operations Matrix

```mermaid
flowchart TB
    subgraph Templates["Template Operations"]
        direction TB
        T1[Create Template]
        T2[Update Template]
        T3[Disable Template]
        T4[Enable Template]
        T5[Archive Template]
        T6[Clone Template]
    end

    subgraph Vendors["Vendor Operations"]
        direction TB
        V1[Add Vendor Mapping]
        V2[Update Vendor Config]
        V3[Disable Vendor]
        V4[Enable Vendor]
        V5[Set Primary Vendor]
        V6[Update Priority Order]
    end

    subgraph Approval["Approval Operations (via Pega CaseWiz)"]
        direction TB
        A1[Submit for Approval]
        A2[Approve Template]
        A3[Reject Template]
        A4[Withdraw Submission]
    end

    subgraph API["Template Management Service API"]
        POST_T[POST /templates]
        PATCH_T[PATCH /templates/{id}/versions/{v}]
        DELETE_T[DELETE /templates/{id}/versions/{v}]
        POST_V[POST /templates/vendors]
        PATCH_V[PATCH /templates/vendors/{id}]
        DELETE_V[DELETE /templates/vendors/{id}]
    end

    subgraph Pega["Pega CaseWiz API"]
        POST_CASE[POST /prweb/api/v1/cases]
        GET_CASE[GET /prweb/api/v1/cases/{id}]
        PATCH_CASE[PATCH /prweb/api/v1/cases/{id}]
    end

    T1 --> POST_T
    T2 --> PATCH_T
    T3 --> PATCH_T
    T4 --> PATCH_T
    T5 --> DELETE_T
    T6 --> POST_T

    V1 --> POST_V
    V2 --> PATCH_V
    V3 --> PATCH_V
    V4 --> PATCH_V
    V5 --> PATCH_V
    V6 --> PATCH_V

    A1 --> PATCH_T
    A1 --> POST_CASE
    A2 --> PATCH_CASE
    A2 -.-> PATCH_T
    A3 --> PATCH_CASE
    A3 -.-> PATCH_T
    A4 --> PATCH_CASE
    A4 -.-> PATCH_T

    style Templates fill:#bbdefb
    style Vendors fill:#c8e6c9
    style Approval fill:#fff9c4
    style API fill:#f8bbd9
    style Pega fill:#ffe0b2
```

---

## 11. API Request/Response Examples

### Create Template

```http
POST /api/v1/templates
Content-Type: application/json
X-Correlation-Id: admin-create-001
X-User-Id: admin@company.com

{
  "templateType": "MONTHLY_STATEMENT",
  "lineOfBusiness": "CREDIT_CARD",
  "displayName": "Monthly Credit Card Statement",
  "templateDescription": "Monthly statement for credit card accounts",
  "templateCategory": "STATEMENT",
  "languageCode": "en",
  "owningDept": "CARD_SERVICES",
  "communicationType": "LETTER",
  "workflow": "4_EYES",
  "singleDocumentFlag": false,
  "startDate": 1704067200000,
  "templateConfig": {
    "pageSize": "LETTER",
    "orientation": "PORTRAIT"
  }
}
```

**Response:**
```json
{
  "template": {
    "masterTemplateId": "550e8400-e29b-41d4-a716-446655440000",
    "templateVersion": 1,
    "templateType": "MONTHLY_STATEMENT",
    "lineOfBusiness": "CREDIT_CARD",
    "displayName": "Monthly Credit Card Statement",
    "activeFlag": true,
    "recordStatus": "DRAFT",
    "createdBy": "admin@company.com",
    "createdTimestamp": "2024-01-15T10:30:00"
  }
}
```

### Add Vendor Mapping

```http
POST /api/v1/templates/vendors
Content-Type: application/json
X-Correlation-Id: admin-vendor-001
X-User-Id: admin@company.com

{
  "masterTemplateId": "550e8400-e29b-41d4-a716-446655440000",
  "templateVersion": 1,
  "vendor": "SmartComm",
  "vendorType": "GENERATION",
  "vendorTemplateKey": "CC_STMT_V1",
  "vendorTemplateName": "Credit Card Statement Template",
  "primaryFlag": true,
  "priorityOrder": 1,
  "timeoutMs": 30000,
  "maxRetryAttempts": 3
}
```

### Disable Template

```http
PATCH /api/v1/templates/550e8400-e29b-41d4-a716-446655440000/versions/1
Content-Type: application/json
X-Correlation-Id: admin-disable-001
X-User-Id: admin@company.com

{
  "activeFlag": false,
  "recordStatus": "INACTIVE"
}
```

---

## 12. Admin Portal UI Wireframes

### Template List View

```
┌─────────────────────────────────────────────────────────────────────────┐
│  TEMPLATE ADMIN PORTAL                            [Admin User ▼] [⚙️]   │
├─────────────────────────────────────────────────────────────────────────┤
│  Templates  │  Vendors  │  Approvals (3)  │  Reports                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  [+ Create Template]    [Filter ▼]  [Search... 🔍]                      │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ Type              │ LOB         │ Status  │ Version │ Actions   │   │
│  ├───────────────────┼─────────────┼─────────┼─────────┼───────────┤   │
│  │ MONTHLY_STATEMENT │ CREDIT_CARD │ 🟢 ACTIVE │ v3    │ [Edit][▼] │   │
│  │ WELCOME_LETTER    │ CREDIT_CARD │ 🟢 ACTIVE │ v1    │ [Edit][▼] │   │
│  │ RATE_CHANGE       │ CREDIT_CARD │ 🟡 DRAFT  │ v2    │ [Edit][▼] │   │
│  │ CLOSURE_NOTICE    │ SAVINGS     │ 🔴 INACTIVE│ v1   │ [Edit][▼] │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  Showing 1-4 of 4 templates                        [< 1 2 3 ... 10 >]   │
└─────────────────────────────────────────────────────────────────────────┘
```

### Template Edit View

```
┌─────────────────────────────────────────────────────────────────────────┐
│  ← Back to Templates    MONTHLY_STATEMENT (v3)           [Save Draft]   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─ Basic Information ─────────────────────────────────────────────┐   │
│  │  Template Type:    MONTHLY_STATEMENT                             │   │
│  │  Display Name:     [Monthly Credit Card Statement          ]     │   │
│  │  Line of Business: [CREDIT_CARD ▼]                               │   │
│  │  Category:         [STATEMENT ▼]                                 │   │
│  │  Status:           🟢 ACTIVE                                     │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─ Vendor Mappings ───────────────────────────────────────────────┐   │
│  │  [+ Add Vendor]                                                  │   │
│  │                                                                  │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │ 🟢 SmartComm (GENERATION)  Priority: 1  ⭐ Primary       │    │   │
│  │  │    Template Key: CC_STMT_V1                    [Edit][❌]│    │   │
│  │  └─────────────────────────────────────────────────────────┘    │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │ 🟢 LPS (PRINT)            Priority: 1                   │    │   │
│  │  │    Account: LPS-PROD-001                       [Edit][❌]│    │   │
│  │  └─────────────────────────────────────────────────────────┘    │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  [Disable Template]  [Create New Version]  [Submit for Approval]        │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## View These Diagrams

1. **GitHub/GitLab**: Mermaid diagrams render automatically
2. **VS Code**: Install "Markdown Preview Mermaid Support" extension
3. **Online**: Use [Mermaid Live Editor](https://mermaid.live)
