# Template Admin Portal - Integration Architecture

## Overview

This document illustrates how the Template Admin Portal interacts with the Template Management Service for creating, updating, and disabling templates and their vendor mappings.

**Approval Workflow**: The template approval process is managed by **Pega CaseWiz**, which provides case management, workflow orchestration, and audit trail capabilities.

**Approval Impact**:
- **Template**: Approval sets `record_status=Approved` and `activeFlag=true`
- **Vendor Mappings**: Approval sets `vendor_mapping_status=Approved` and `activeFlag=true`

**Status Values**: `Draft`, `Pending`, `Approved`, `Archived`

---

## 0. Glossary

| Term | Definition |
|------|------------|
| **Resource** | Specification that provides metadata about a resource, points to or holds a data structure, that allows us to manage them efficiently |
| **Document** | Refers to a file that will be stored on DCMS; there is no significance on the fact that it happens to be a "Template" |
| **Resource Approver** | Refers to a CTB employee or contractor that has the ability to approve a new template. (We can have a different type of role for each template if needed) |

---

## 1. System Context - Admin Portal Integration

```mermaid
C4Context
    title Template Admin Portal - System Context

    Person(admin, "Resource Creator", "Creates and manages document templates")
    Person(approver, "Resource Approver", "CTB employee who reviews and approves templates")

    System(portal, "Template Admin Portal (UI)", "Web application for template management")
    System(bff, "BFF", "Backend for Frontend - API gateway")

    System(tms, "Template Management Service", "REST API for template CRUD operations")
    System(pega, "Pega CaseWiz", "Workflow orchestration and approval management")
    System(docHub, "Document Hub", "Resource metadata storage")
    System(dcms, "DCMS", "Document Content Management - file storage")

    System_Ext(emailSvc, "Email Service", "Approval notifications")
    System_Ext(mychannel, "Mychannel", "Channel integration")

    SystemDb(chaseNetDb, "ChaseNet DB", "Enterprise data")
    SystemDb(docHubDb, "Document Hub DB", "Resource metadata")

    Rel(admin, portal, "Creates/updates templates", "Browser")
    Rel(approver, pega, "Reviews/approves", "Browser")
    Rel(portal, bff, "API calls", "HTTPS/REST")
    Rel(bff, tms, "Template operations", "HTTPS/REST")
    Rel(bff, pega, "Creates approval case", "HTTPS/REST")
    Rel(pega, tms, "Updates status on approval", "HTTPS/REST")
    Rel(pega, emailSvc, "Sends notifications", "HTTPS")
    Rel(tms, docHub, "Stores resource metadata", "HTTPS")
    Rel(docHub, dcms, "Stores template files", "HTTPS")
    Rel(docHub, docHubDb, "Persists", "R2DBC")
```

---

## 2. Admin Portal Architecture

```mermaid
flowchart TB
    subgraph AdminPortal["Template Admin Portal (UI)"]
        subgraph UI["User Interface"]
            Dashboard[Dashboard]
            TemplateList[Template List]
            TemplateForm[Template Form]
            VendorConfig[Vendor Configuration]
        end
    end

    subgraph BFFLayer["BFF (Backend for Frontend)"]
        BFF[BFF API Gateway]
        AuthService[Auth Service]
    end

    subgraph PegaCaseWiz["Pega CaseWiz"]
        CaseManager[Case Manager]
        ApprovalQueue[Approval Queue]
        WorkflowEngine[Workflow Engine]
        AuditLog[Audit Trail]
    end

    subgraph Backend["Backend Services"]
        subgraph TMS["Template Management Service :8081"]
            TC[Template Controller]
            TVC[Vendor Controller]
        end

        subgraph DocHub["Document Hub"]
            ResourceAPI[Resource API]
            DocHubDB[(Document Hub DB)]
        end

        subgraph DCMS["DCMS"]
            FileStorage[(File Storage)]
        end
    end

    subgraph External["External Services"]
        EmailSvc[Email Service]
        Mychannel[Mychannel]
        ChaseNetDB[(ChaseNet DB)]
    end

    UI --> BFF
    BFF --> TC
    BFF --> TVC
    BFF --> CaseManager
    AuthService --> ChaseNetDB

    CaseManager --> WorkflowEngine
    WorkflowEngine --> ApprovalQueue
    WorkflowEngine --> AuditLog
    WorkflowEngine --> TC
    WorkflowEngine --> EmailSvc

    TC --> ResourceAPI
    ResourceAPI --> DocHubDB
    ResourceAPI --> FileStorage

    style AdminPortal fill:#e3f2fd
    style BFFLayer fill:#fff3e0
    style PegaCaseWiz fill:#fff9c4
    style Backend fill:#c8e6c9
    style External fill:#f8bbd9
```

---

## 2a. Data Ownership

```mermaid
flowchart LR
    subgraph CaseWiz["CaseWiz DB"]
        CaseInfo["CASE INFO<br/>─────────────<br/>• Case ID<br/>• Case Status<br/>• Approver Info<br/>• Approval Timestamp<br/>─────────────<br/>We do not need to keep<br/>the whole approval info.<br/>Query CaseWiz to discover<br/>who approved it and when."]
    end

    subgraph DocHubDB["Document Hub DB"]
        Resources["RESOURCES Table<br/>─────────────<br/>• Resource ID<br/>• Category<br/>• Line of Business<br/>• Communication Type<br/>• DCMS Folder Reference<br/>• Metadata JSON<br/>─────────────<br/>Contains DCMS 'folder'<br/>where templates are stored"]
    end

    subgraph DCMSStorage["DCMS"]
        Files["FILE STORAGE<br/>─────────────<br/>Organized by:<br/>category/<br/>  line_of_business/<br/>    communication_type/<br/>      doc/<br/>─────────────<br/>Example:<br/>STATEMENTS/<br/>  CREDIT_CARD/<br/>    MONTHLY_STATEMENT/<br/>      PROD-2025.01.01/<br/>        EP001-approved.pdf"]
    end

    subgraph TMS["Template Management Service"]
        Templates["TEMPLATE METADATA<br/>─────────────<br/>• Template Definition<br/>• Vendor Mappings<br/>• Template Variables<br/>• Eligibility Criteria<br/>─────────────<br/>Note: Template file<br/>reference points to DCMS"]
    end

    CaseWiz -.->|"Case Status"| TMS
    TMS -->|"Resource Metadata"| DocHubDB
    DocHubDB -->|"File Reference"| DCMSStorage

    style CaseWiz fill:#ffecb3
    style DocHubDB fill:#c8e6c9
    style DCMSStorage fill:#bbdefb
    style TMS fill:#f8bbd9
```

### Data Ownership Summary

| System | Data Owned | Purpose |
|--------|-----------|---------|
| **CaseWiz** | Case ID, Status, Approver, Timestamps | Authentic record of approval workflow; query to get approval details |
| **Document Hub DB** | Resource metadata, DCMS folder reference | JSON record in RESOURCES table with folder location |
| **DCMS** | Actual template files | Physical file storage organized by `category/lob/comm_type/doc` |
| **Template Management** | Template definitions, vendor mappings | Business configuration and routing rules |

---

## 2b. Template Wizard - 6 Step Configuration Process

The Template Wizard provides a guided 6-step process for creating document templates without requiring technical knowledge.

```mermaid
flowchart LR
    S1[Step 1<br/>Basic Info] --> S2[Step 2<br/>Ownership]
    S2 --> S3[Step 3<br/>Fields]
    S3 --> S4[Step 4<br/>Source APIs]
    S4 --> S5[Step 5<br/>Access Rules]
    S5 --> S6[Step 6<br/>Review]

    style S1 fill:#e8f5e9
    style S2 fill:#e3f2fd
    style S3 fill:#fff3e0
    style S4 fill:#fce4ec
    style S5 fill:#f3e5f5
    style S6 fill:#e0f2f1
```

### Step 1: Basic Information
| Field | Description | Options |
|-------|-------------|---------|
| Template Name | Display name for the template | Free text |
| Description | Template purpose and usage | Free text |
| Document Category | Type of document | Statement, Legal, Tax, Regulatory, Notice |
| Line of Business | Business area | CREDIT_CARD, SAVINGS, MORTGAGE, etc. |

### Step 2: Document Ownership
| Type | Description | Use Case |
|------|-------------|----------|
| Account-specific | Document belongs to a specific account | Statements, notices |
| Customer-wide | Document accessible to all accounts for a customer | Disclosures |
| Shared/Public | Document available to multiple customers | Marketing materials |
| Conditional | Access based on eligibility rules | Premium-tier documents |

### Step 3: Extractable Fields
- Pre-populated fields based on document category
- Add custom fields as needed
- Automatic field type detection (DATE, STRING, NUMBER, etc.)
- Required/optional field configuration

### Step 4: Source APIs
Three ways to configure source APIs for eligibility checks:

| Option | Description | When to Use |
|--------|-------------|-------------|
| **A: Existing API** | Select from pre-defined APIs (Credit Info, Account Info, Arrangements) | Common use cases |
| **B: Upload API Spec** | Upload OpenAPI 3.0/Swagger 2.0 JSON specification | New integrations |
| **C: Custom API** | Manually register new API with endpoint and fields | One-off integrations |

### Step 5: Access Rules
- Visual rule builder with dropdown selections
- Plain language description option
- Automatic field population from selected API
- Example: `membershipTier IN ["PLATINUM", "GOLD"]`

### Step 6: Review & Generate
- Summary of all configurations
- Generated JSON configuration
- Downloadable SQL INSERT statement for `master_template_definition`

---

## 2c. Template Onboarding Process Flow

The end-to-end onboarding process from business request to production deployment.

```mermaid
flowchart TB
    subgraph Phase1["Phase 1: Request"]
        A([Start]) --> B[Submit Template Request]
        B --> C[Define Requirements]
        C --> D[Provide Sample Document]
    end

    subgraph Phase2["Phase 2: Configuration"]
        D --> E[Use Template Wizard]
        E --> F[Define Access Rules]
        F --> G[Generate Config/SQL]
        G --> H{IT Review}
        H -->|Issues| E
        H -->|Approved| I[Insert to DEV DB]
    end

    subgraph Phase3["Phase 3: Technical Setup & UAT"]
        I --> J[Configure Vendor Mapping]
        J --> K[Run Integration Tests]
        K --> L{Tests Pass?}
        L -->|No| K
        L -->|Yes| M[Load Test Data]
        M --> N[Business UAT]
        N --> O{UAT Approved?}
        O -->|No| E
        O -->|Yes| P[Create Migration Script]
    end

    subgraph Phase4["Phase 4: Deployment"]
        P --> Q[Deploy to PROD]
        Q --> R[Smoke Test]
        R --> S[Go-Live Confirmation]
        S --> T([End])
    end

    style Phase1 fill:#e8f5e9,stroke:#4caf50
    style Phase2 fill:#e3f2fd,stroke:#2196f3
    style Phase3 fill:#fff3e0,stroke:#ff9800
    style Phase4 fill:#f3e5f5,stroke:#9c27b0
```

### Onboarding Timeline

| Phase | Activities | Duration | Owner |
|-------|------------|----------|-------|
| **Phase 1: Request** | Submit request, define requirements, provide sample | 1-2 days | Business |
| **Phase 2: Configuration** | Use wizard, IT review, insert to DEV | 1-2 days | Business + IT |
| **Phase 3: Setup & UAT** | Vendor mapping, integration tests, UAT | 2-3 days | IT + Business |
| **Phase 4: Deployment** | Migration script, PROD deploy, smoke test | 1 day | IT |

**Total: ~6 days typical**

### RACI Matrix

| Activity | Business | IT | Management |
|----------|----------|-----|------------|
| Submit Request | R/A | I | I |
| Define Requirements | R/A | C | I |
| Use Template Wizard | R | C | I |
| Review Configuration | C | R/A | I |
| Deploy to DEV | I | R/A | I |
| Run Tests | I | R/A | I |
| UAT Testing | R/A | C | I |
| Deploy to PROD | I | R/A | A |
| Go-Live Confirmation | R | I | A |

*R=Responsible, A=Accountable, C=Consulted, I=Informed*

---

## 3. Template Lifecycle States

```mermaid
stateDiagram-v2
    [*] --> Draft: Create Template

    Draft --> Pending: Submit for Review
    Draft --> Draft: Save Draft

    Pending --> Approved: Approver Accepts
    Pending --> Draft: Approver Rejects / Withdraw

    Approved --> Approved: Update (creates new version)
    Approved --> Archived: Archive

    Archived --> [*]

    note right of Draft
        Template can be edited freely
        Vendor mappings can be added
        activeFlag = false
    end note

    note right of Pending
        Awaiting approval in Pega CaseWiz
        Cannot be edited
    end note

    note right of Approved
        Template is live and in use
        activeFlag = true
        Updates create new versions
    end note

    note right of Archived
        Template is decommissioned
        activeFlag = false
        Cannot be reactivated
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
        TMS->>+DB: INSERT master_template_definition<br/>(record_status=Draft, version=1)
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
        Portal->>+TMS: PATCH /templates/{id}/versions/1<br/>{recordStatus: "Pending"}
        TMS->>+DB: UPDATE record_status='Pending'
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

    alt Update In-Place (Draft status)
        rect rgb(240, 255, 240)
            Note over Portal,DB: Option A: Update Existing Version
            Portal->>+TMS: PATCH /templates/{id}/versions/{v}<br/>{displayName, templateConfig, ...}
            TMS->>+DB: UPDATE master_template_definition
            DB-->>-TMS: Updated
            TMS-->>-Portal: 200 OK (same version)
        end
    else Create New Version (Approved status)
        rect rgb(255, 240, 255)
            Note over Portal,DB: Option B: Create New Version
            Portal->>+TMS: PATCH /templates/{id}/versions/{v}<br/>?createNewVersion=true<br/>{displayName, templateConfig, ...}
            TMS->>TMS: Get next version number
            TMS->>+DB: INSERT new version (v+1)<br/>with record_status=Draft
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
        Note over Portal,DB: Step 3: Archive Template
        Portal->>+TMS: PATCH /templates/{id}/versions/{v}<br/>{activeFlag: false, recordStatus: "Archived"}
        TMS->>+DB: UPDATE master_template_definition<br/>SET active_flag=false, record_status='Archived'
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

This sequence follows the architecture: **UI → BFF → CaseWiz → PEGA → Template Management → Document Hub → DCMS**

```mermaid
sequenceDiagram
    autonumber
    participant UI as UI
    participant BFF as BFF
    participant CaseWiz as CaseWiz
    participant ChaseNetDB as ChaseNet DB
    participant PEGA as PEGA
    participant Mychannel as Mychannel
    participant CCMS as CCMS
    participant EmailSvc as Email Service
    participant DocHub as Document Hub
    participant DocHubDB as Document Hub DB
    participant DCMS as DCMS

    rect rgb(240, 248, 255)
        Note over UI,BFF: Step 1: Resource Creator Submits for Approval
        UI->>+BFF: POST /resources/approval<br/>{resourceId, version}
        BFF->>+CaseWiz: Create Case<br/>{HTML/CSS/JS_FILE_ASSET}
        CaseWiz->>CaseWiz: Validate request
        CaseWiz->>+ChaseNetDB: Store case details (DOCUMENT ID)
        ChaseNetDB-->>-CaseWiz: OK
        CaseWiz-->>-BFF: {caseId, status: "Pending"}
        BFF-->>-UI: Case created
    end

    rect rgb(255, 248, 240)
        Note over CaseWiz,PEGA: Step 2: PEGA Workflow Initiated
        CaseWiz->>+PEGA: POST /assignments<br/>{caseId, assignmentRules}
        PEGA->>PEGA: Evaluate assignment rules
        PEGA->>PEGA: GET RESOURCE APPROVED RULE<br/>GET RESOURCE RULES
        PEGA-->>-CaseWiz: Assignment created
    end

    rect rgb(240, 255, 240)
        Note over PEGA,EmailSvc: Step 3: Notify Approvers
        PEGA->>+EmailSvc: POST /notifications<br/>{to: approvers, template: "approval_request"}
        EmailSvc-->>-PEGA: Sent
    end

    rect rgb(255, 240, 255)
        Note over PEGA,DocHub: Step 4: Approver Reviews & Approves
        Note right of PEGA: Resource Approver opens worklist
        PEGA->>PEGA: Approver reviews case
        PEGA->>+DocHub: GET /resources/{id}<br/>Retrieve template for review
        DocHub-->>-PEGA: Resource details

        Note right of PEGA: Approver clicks "Approve"
        PEGA->>PEGA: SET APPROVED RULE<br/>Update case status
    end

    rect rgb(240, 248, 255)
        Note over PEGA,DCMS: Step 5: Store Approved Template
        PEGA->>+DocHub: POST /resources/{id}/approve<br/>{approvedBy, caseId}
        DocHub->>+DocHubDB: UPDATE resource<br/>SET status='Approved', active=true
        DocHubDB-->>-DocHub: Updated

        DocHub->>+DCMS: POST /documents<br/>Store approved template file
        Note right of DCMS: Path: category/lob/comm_type/doc
        DCMS-->>-DocHub: {documentId, path}

        DocHub->>+DocHubDB: UPDATE resource<br/>SET dcms_folder_ref=path
        DocHubDB-->>-DocHub: Updated
        DocHub-->>-PEGA: 200 OK
    end

    rect rgb(255, 255, 224)
        Note over PEGA,UI: Step 6: Case Resolution & Notification
        PEGA->>+EmailSvc: POST /notifications<br/>{to: requestor, template: "approved"}
        EmailSvc-->>-PEGA: Sent
        PEGA->>+CaseWiz: Update case status<br/>{status: "Resolved-Approved"}
        CaseWiz->>+ChaseNetDB: Store resolution
        ChaseNetDB-->>-CaseWiz: OK
        CaseWiz-->>-PEGA: Case closed
    end
```

---

## 9a. Pega CaseWiz - Rejection Flow

```mermaid
sequenceDiagram
    autonumber
    participant PEGA as PEGA
    participant CaseWiz as CaseWiz
    participant ChaseNetDB as ChaseNet DB
    participant DocHub as Document Hub
    participant DocHubDB as Document Hub DB
    participant EmailSvc as Email Service
    participant UI as UI

    Note right of PEGA: Resource Approver opens worklist
    PEGA->>+DocHub: GET /resources/{id}<br/>Retrieve template for review
    DocHub-->>-PEGA: Resource details

    rect rgb(255, 240, 240)
        Note over PEGA,PEGA: Rejection with Comments
        Note right of PEGA: Approver enters rejection reason
        PEGA->>PEGA: SET REJECTED RULE<br/>"Missing regulatory fields"
    end

    rect rgb(255, 248, 240)
        Note over PEGA,DocHubDB: Revert Resource to Draft
        PEGA->>+DocHub: POST /resources/{id}/reject<br/>{rejectedBy, reason, caseId}
        DocHub->>+DocHubDB: UPDATE resource<br/>SET status='Draft', active=false
        DocHubDB-->>-DocHub: Updated
        DocHub-->>-PEGA: 200 OK
    end

    rect rgb(240, 255, 240)
        Note over PEGA,EmailSvc: Notify Creator of Rejection
        PEGA->>+EmailSvc: POST /notifications<br/>{to: requestor, template: "rejected",<br/>reason: "Missing regulatory fields"}
        EmailSvc-->>-PEGA: Sent
    end

    rect rgb(240, 248, 255)
        Note over PEGA,ChaseNetDB: Case Resolution
        PEGA->>+CaseWiz: Update case status<br/>{status: "Resolved-Rejected", reason}
        CaseWiz->>+ChaseNetDB: Store resolution & rejection reason
        ChaseNetDB-->>-CaseWiz: OK
        CaseWiz-->>-PEGA: Case closed
    end

    rect rgb(255, 255, 224)
        Note over UI,DocHub: Creator Revises & Resubmits
        UI->>UI: View rejection feedback
        UI->>DocHub: Update resource to address issues
        Note over UI,PEGA: New case created for resubmission
    end
```

---

## 9b. Pega CaseWiz Integration Details

### Case Data Model (Stored in CaseWiz/ChaseNet DB)

| Field | Type | Description |
|-------|------|-------------|
| `caseId` | String | Pega-generated case ID (e.g., CASE-12345) |
| `caseType` | String | "ResourceApproval" |
| `documentId` | String | Reference to resource/document being approved |
| `resourceType` | String | Type of resource (e.g., TEMPLATE, STATEMENT) |
| `requestor` | String | Resource creator who submitted for approval |
| `assignedTo` | String | Current approver (from pool based on rules) |
| `status` | String | Pending, In Review, Approved, Rejected, Resolved |
| `slaDeadline` | DateTime | Auto-escalation deadline |
| `comments` | List | Approval/rejection comments |

**Note**: CaseWiz contains the authentic record of approval. We do not need to keep the whole approval information in Document Hub - query CaseWiz to discover who approved it and when.

### Resource Data Model (Stored in Document Hub DB)

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Resource identifier |
| `category` | String | e.g., STATEMENTS, DISCLOSURES |
| `resourceType` | String | e.g., MONTHLY_STATEMENT |
| `lineOfBusiness` | String | e.g., CREDIT_CARD |
| `description` | String | Resource description |
| `docType` | String | e.g., PDF |
| `languageCode` | String | e.g., EN_US |
| `createdTimestamp` | DateTime | Creation time |
| `department` | String | Owning department |
| `effectiveStart` | Date | Effective start date |
| `effectiveEnd` | Date | Effective end date |
| `status` | String | Draft, Pending, Approved, Archived |
| `caseId` | String | Reference to CaseWiz case |
| `dcmsFolderRef` | String | DCMS folder path reference |

### DCMS File Storage Structure

```
category/
  line_of_business/
    communication_type/
      doc/
        PROD-{date}/
          {resource_id}-approved.{ext}

Example:
STATEMENTS/
  CREDIT_CARD/
    MONTHLY_STATEMENT/
      PROD-2025.01.01/
        EP001-approved.pdf
        EP001-approved-v2.pdf
```

### Approval Impact on Records

| Decision | Document Hub Effect | DCMS Effect |
|----------|---------------------|-------------|
| **Approve** | `status=Approved`, `activeFlag=true`, `dcmsFolderRef` populated | File stored in approved folder |
| **Reject** | `status=Draft`, `activeFlag=false` | No file stored |
| **Archive** | `status=Archived`, `activeFlag=false` | File remains but marked archived |

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
  "status": "Resolved-Approved",
  "templateId": "550e8400-e29b-41d4-a716-446655440000",
  "templateVersion": 1,
  "decision": "Approved",
  "approvedBy": "approver@company.com",
  "approvedAt": "2024-01-15T14:30:00Z",
  "templateUpdate": {
    "activeFlag": true,
    "recordStatus": "Approved"
  },
  "vendorMappingsUpdated": [
    {
      "vendorId": "660e8400-e29b-41d4-a716-446655440001",
      "vendor": "SmartComm",
      "vendorMappingStatus": "Approved",
      "activeFlag": true
    },
    {
      "vendorId": "660e8400-e29b-41d4-a716-446655440002",
      "vendor": "LPS",
      "vendorMappingStatus": "Approved",
      "activeFlag": true
    }
  ]
}
```

### Workflow Configuration in Pega

```
┌─────────────────────────────────────────────────────────────────┐
│                  PEGA CASEWIZ WORKFLOW                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────┐     ┌──────────┐     ┌───────────┐               │
│   │  START  │────▶│ Pending  │────▶│ In Review │               │
│   └─────────┘     └──────────┘     └───────────┘               │
│                        │                 │                      │
│                        │            ┌────┴────┐                 │
│                        │            ▼         ▼                 │
│                   [SLA Breach]  ┌────────┐ ┌──────────┐         │
│                        │        │Approved│ │ Rejected │         │
│                        ▼        └────────┘ └──────────┘         │
│                   ┌─────────┐       │           │               │
│                   │Escalate │       │     (→ Draft)             │
│                   └─────────┘       ▼           ▼               │
│                        │        ┌───────────────────┐           │
│                        └───────▶│   Resolved        │           │
│                                 └───────────────────┘           │
│                                                                 │
│  Status Flow:                                                   │
│  • Approved → Template: record_status=Approved, activeFlag=true │
│  • Rejected → Template: record_status=Draft, activeFlag=false   │
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
    "activeFlag": false,
    "recordStatus": "Draft",
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

### Archive Template

```http
PATCH /api/v1/templates/550e8400-e29b-41d4-a716-446655440000/versions/1
Content-Type: application/json
X-Correlation-Id: admin-archive-001
X-User-Id: admin@company.com

{
  "activeFlag": false,
  "recordStatus": "Archived"
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
│  │ Type              │ LOB         │ Status    │ Version │ Actions   │   │
│  ├───────────────────┼─────────────┼───────────┼─────────┼───────────┤   │
│  │ MONTHLY_STATEMENT │ CREDIT_CARD │ 🟢 Approved│ v3     │ [Edit][▼] │   │
│  │ WELCOME_LETTER    │ CREDIT_CARD │ 🟢 Approved│ v1     │ [Edit][▼] │   │
│  │ RATE_CHANGE       │ CREDIT_CARD │ 🟡 Draft   │ v2     │ [Edit][▼] │   │
│  │ CLOSURE_NOTICE    │ SAVINGS     │ 🔵 Pending │ v1     │ [Edit][▼] │   │
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
│  │  Status:           🟢 Approved                                    │   │
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
