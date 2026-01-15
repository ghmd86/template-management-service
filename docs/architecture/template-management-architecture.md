# Template Management Service - Architecture Diagrams

## Overview

This document provides comprehensive architecture diagrams for the Template Management Service, showing its internal structure, external integrations, and data flows.

---

## 1. High-Level System Context

Shows how Template Management Service fits within the broader Document Hub ecosystem.

```mermaid
C4Context
    title System Context - Template Management Service

    Person(admin, "Admin User", "Manages templates and vendor configurations")
    Person(developer, "Developer", "Integrates via API")

    System(tms, "Template Management Service", "Manages document templates, versions, and vendor mappings")

    System_Ext(docHub, "Document Hub API", "Document storage and retrieval")
    System_Ext(letterApi, "Letter API Service", "Document generation (SmartComm/Assentis)")
    System_Ext(printPartner, "Print Partner Service", "Physical mail delivery (LPS)")
    System_Ext(emailService, "Email Service", "Email delivery (Salesforce)")

    SystemDb(postgres, "PostgreSQL", "Template definitions and vendor mappings")

    Rel(admin, tms, "Creates/manages templates", "HTTPS")
    Rel(developer, tms, "Queries templates", "HTTPS")
    Rel(docHub, tms, "Gets template config", "HTTPS")
    Rel(letterApi, tms, "Validates templates", "HTTPS")
    Rel(tms, postgres, "Reads/Writes", "R2DBC")
    Rel(tms, printPartner, "Validates accounts", "HTTPS")
    Rel(emailService, tms, "Gets email templates", "HTTPS")
```

---

## 2. Service Architecture (Layered)

Shows the internal layered architecture of the Template Management Service.

```mermaid
flowchart TB
    subgraph Clients["API Consumers"]
        AdminPortal[Admin Portal]
        DocHubAPI[Document Hub API]
        EmailSvc[Email Service]
        OtherSvc[Other Services]
    end

    subgraph TMS["Template Management Service :8081"]
        subgraph Controllers["Controller Layer"]
            TC[TemplateController]
            TVC[TemplateVendorController]
        end

        subgraph Processors["Processor Layer"]
            TMP[TemplateManagementProcessor]
        end

        subgraph Services["Service Layer"]
            TS[TemplateService]
        end

        subgraph DAOs["DAO Layer + Cache"]
            MTD[MasterTemplateDao]
            TVD[TemplateVendorMappingDao]
            Cache[(Caffeine Cache)]
        end

        subgraph Repositories["Repository Layer"]
            MTR[MasterTemplateRepository]
            TVR[TemplateVendorMappingRepository]
        end

        subgraph CrossCutting["Cross-Cutting"]
            Filter[CorrelationIdFilter]
            ExHandler[GlobalExceptionHandler]
        end
    end

    subgraph Database["PostgreSQL"]
        MTT[(master_template_definition)]
        TVT[(template_vendor_mapping)]
    end

    AdminPortal --> TC
    DocHubAPI --> TC
    EmailSvc --> TVC
    OtherSvc --> TC

    TC --> TMP
    TVC --> TMP
    TMP --> TS
    TS --> MTD
    TS --> TVD
    MTD --> Cache
    TVD --> Cache
    MTD --> MTR
    TVD --> TVR
    MTR --> MTT
    TVR --> TVT

    Filter -.-> TC
    Filter -.-> TVC
    ExHandler -.-> TC
    ExHandler -.-> TVC

    style TMS fill:#e1f5fe
    style Controllers fill:#bbdefb
    style Processors fill:#c8e6c9
    style Services fill:#fff9c4
    style DAOs fill:#ffe0b2
    style Repositories fill:#f8bbd9
    style Cache fill:#ff8a65
```

---

## 3. Component Diagram

Detailed view of components and their responsibilities.

```mermaid
flowchart LR
    subgraph API["REST API Layer"]
        direction TB
        TC["TemplateController<br/>─────────────<br/>POST /templates<br/>GET /templates<br/>GET /templates/{id}<br/>PATCH /templates/{id}/versions/{v}<br/>DELETE /templates/{id}/versions/{v}"]
        TVC["TemplateVendorController<br/>─────────────<br/>POST /templates/vendors<br/>GET /templates/vendors<br/>GET /templates/vendors/{id}<br/>PATCH /templates/vendors/{id}<br/>DELETE /templates/vendors/{id}<br/>GET /templates/vendors/routing"]
    end

    subgraph Processing["Processing Layer"]
        TMP["TemplateManagementProcessor<br/>─────────────<br/>• Request orchestration<br/>• Logging & metrics<br/>• Correlation tracking"]
    end

    subgraph Business["Business Logic"]
        TS["TemplateService<br/>─────────────<br/>• Create/Update/Delete templates<br/>• Version management<br/>• Vendor mapping CRUD<br/>• Conflict detection<br/>• Routing logic"]
    end

    subgraph Data["Data Access"]
        MTD["MasterTemplateDao<br/>─────────────<br/>• Template caching<br/>• Entity ↔ DTO conversion<br/>• Cache invalidation"]
        TVD["TemplateVendorMappingDao<br/>─────────────<br/>• Vendor caching<br/>• Entity ↔ DTO conversion<br/>• Status updates"]
    end

    subgraph Persistence["Persistence"]
        MTR["MasterTemplateRepository<br/>─────────────<br/>• Custom R2DBC queries<br/>• Pagination support<br/>• Soft delete"]
        TVR["TemplateVendorMappingRepository<br/>─────────────<br/>• Priority-based queries<br/>• Health status updates<br/>• Routing queries"]
    end

    TC --> TMP
    TVC --> TMP
    TMP --> TS
    TS --> MTD
    TS --> TVD
    MTD --> MTR
    TVD --> TVR

    style API fill:#4fc3f7
    style Processing fill:#81c784
    style Business fill:#fff176
    style Data fill:#ffb74d
    style Persistence fill:#f48fb1
```

---

## 4. Data Flow - Template Creation

```mermaid
sequenceDiagram
    autonumber
    participant Client as Admin Portal
    participant Filter as CorrelationIdFilter
    participant TC as TemplateController
    participant TMP as Processor
    participant TS as TemplateService
    participant DAO as MasterTemplateDao
    participant Repo as Repository
    participant DB as PostgreSQL

    Client->>+Filter: POST /templates
    Note over Filter: Generate/validate<br/>correlation ID

    Filter->>+TC: Forward request
    TC->>TC: Validate request body

    TC->>+TMP: processCreateTemplate()
    TMP->>TMP: Log request start

    TMP->>+TS: createTemplate()

    TS->>+DAO: existsByTemplateType()
    DAO->>+Repo: Query
    Repo->>+DB: SELECT COUNT(*)
    DB-->>-Repo: Result
    Repo-->>-DAO: Boolean
    DAO-->>-TS: false (not exists)

    TS->>TS: Build entity with UUID

    TS->>+DAO: save(entity)
    DAO->>+Repo: save()
    Repo->>+DB: INSERT
    DB-->>-Repo: Saved entity
    Repo-->>-DAO: Entity
    DAO->>DAO: Update cache
    DAO-->>-TS: TemplateDto

    TS-->>-TMP: TemplateResponse

    TMP->>TMP: Log success
    TMP-->>-TC: Response

    TC-->>-Filter: 201 Created
    Filter-->>-Client: Response + X-Correlation-Id
```

---

## 5. Data Flow - Vendor Routing (Failover)

```mermaid
sequenceDiagram
    autonumber
    participant DocHub as Document Hub
    participant TMS as Template Management
    participant Cache as Caffeine Cache
    participant DB as PostgreSQL
    participant Primary as Primary Vendor<br/>(SmartComm)
    participant Backup as Backup Vendor<br/>(Assentis)

    DocHub->>+TMS: GET /templates/vendors/routing<br/>?templateId=X&vendorType=GENERATION

    TMS->>+Cache: Check cache
    alt Cache Hit
        Cache-->>TMS: Cached vendors list
    else Cache Miss
        TMS->>+DB: SELECT * FROM template_vendor_mapping<br/>WHERE vendor_type='GENERATION'<br/>AND active_flag=true<br/>ORDER BY priority_order
        DB-->>-TMS: Vendor list
        TMS->>Cache: Store in cache
    end
    Cache-->>-TMS: Vendors

    TMS-->>-DocHub: [SmartComm(priority=1), Assentis(priority=2)]

    DocHub->>+Primary: POST /generate
    Primary-->>-DocHub: 503 Service Unavailable

    Note over DocHub: Failover to backup

    DocHub->>+Backup: POST /generate
    Backup-->>-DocHub: 200 OK (PDF)

    DocHub->>TMS: Update vendor status<br/>(SmartComm = DEGRADED)
    TMS->>Cache: Invalidate cache
```

---

## 6. Database Schema

```mermaid
erDiagram
    master_template_definition {
        uuid master_template_id PK
        int template_version PK
        string template_type
        string line_of_business
        string display_name
        string template_name
        string template_description
        string template_category
        string language_code
        string owning_dept
        boolean notification_needed
        boolean regulatory_flag
        boolean message_center_doc_flag
        boolean active_flag
        boolean shared_document_flag
        string sharing_scope
        string communication_type
        string workflow
        boolean single_document_flag
        json template_variables
        json data_extraction_config
        json eligibility_criteria
        json access_control
        json required_fields
        json template_config
        bigint start_date
        bigint end_date
        string created_by
        timestamp created_timestamp
        string updated_by
        timestamp updated_timestamp
        boolean archive_indicator
        timestamp archive_timestamp
        string record_status
    }

    template_vendor_mapping {
        uuid template_vendor_id PK
        uuid master_template_id FK
        int template_version FK
        string vendor
        string vendor_type
        string vendor_template_key
        string vendor_template_name
        int vendor_mapping_version
        int priority_order
        boolean primary_flag
        boolean active_flag
        string vendor_status
        string template_status
        json vendor_config
        json api_config
        json schema_info
        json template_fields
        int rate_limit_per_minute
        int rate_limit_per_day
        int timeout_ms
        int max_retry_attempts
        decimal cost_per_unit
        string cost_unit
        string[] supported_regions
        string[] supported_formats
        string health_check_endpoint
        timestamp last_health_check
        string last_health_status
        bigint start_date
        bigint end_date
        string created_by
        timestamp created_timestamp
        boolean archive_indicator
        string record_status
    }

    master_template_definition ||--o{ template_vendor_mapping : "has vendors"
```

---

## 7. Caching Strategy

```mermaid
flowchart TB
    subgraph Request["Incoming Request"]
        R1[GET /templates/{id}/versions/{v}]
    end

    subgraph CacheLayer["Caffeine Cache Layer"]
        direction TB
        TC["Template Cache<br/>─────────<br/>Key: {templateId}:{version}<br/>TTL: 30 minutes<br/>Max: 1000 entries"]
        VC["Vendor Cache<br/>─────────<br/>Key: {vendorId}<br/>TTL: 30 minutes<br/>Max: 500 entries"]
    end

    subgraph Logic["Cache Logic"]
        Check{Cache Hit?}
        Return[Return Cached]
        Query[Query Database]
        Store[Store in Cache]
    end

    subgraph DB["PostgreSQL"]
        Data[(Template Data)]
    end

    R1 --> Check
    Check -->|Yes| Return
    Check -->|No| Query
    Query --> Data
    Data --> Store
    Store --> TC
    Store --> Return

    subgraph Invalidation["Cache Invalidation Triggers"]
        I1[Template Update]
        I2[Template Delete]
        I3[Vendor Status Change]
    end

    I1 --> TC
    I2 --> TC
    I3 --> VC

    style CacheLayer fill:#ffcc80
    style TC fill:#ff8a65
    style VC fill:#ff8a65
```

---

## 8. Deployment Architecture

```mermaid
flowchart TB
    subgraph Internet["Internet"]
        Client[API Clients]
    end

    subgraph LB["Load Balancer"]
        ALB[Application Load Balancer]
    end

    subgraph K8s["Kubernetes Cluster"]
        subgraph TMS_Pods["Template Management Service"]
            TMS1[TMS Pod 1<br/>:8081]
            TMS2[TMS Pod 2<br/>:8081]
            TMS3[TMS Pod 3<br/>:8081]
        end

        subgraph DocHub_Pods["Document Hub API"]
            DH1[DocHub Pod 1<br/>:8080]
            DH2[DocHub Pod 2<br/>:8080]
        end

        subgraph Config["Configuration"]
            CM[ConfigMap]
            Secret[Secrets]
        end
    end

    subgraph Data["Data Layer"]
        subgraph PG["PostgreSQL HA"]
            Primary[(Primary)]
            Replica[(Read Replica)]
        end
        Redis[(Redis<br/>Session/Distributed Cache)]
    end

    subgraph Monitoring["Observability"]
        Prometheus[Prometheus]
        Grafana[Grafana]
        Jaeger[Jaeger]
    end

    Client --> ALB
    ALB --> TMS1
    ALB --> TMS2
    ALB --> TMS3
    ALB --> DH1
    ALB --> DH2

    TMS1 --> Primary
    TMS2 --> Primary
    TMS3 --> Replica

    DH1 --> TMS1
    DH2 --> TMS2

    CM -.-> TMS1
    CM -.-> TMS2
    CM -.-> TMS3
    Secret -.-> TMS1
    Secret -.-> TMS2
    Secret -.-> TMS3

    TMS1 -.-> Prometheus
    TMS2 -.-> Prometheus
    TMS3 -.-> Prometheus
    Prometheus --> Grafana

    style K8s fill:#e3f2fd
    style Data fill:#e8f5e9
    style Monitoring fill:#fff3e0
```

---

## 9. Integration Points

```mermaid
flowchart LR
    subgraph Consumers["API Consumers"]
        DocHub[Document Hub API]
        EmailSvc[Email Service]
        AdminUI[Admin Portal]
        BatchJob[Batch Jobs]
    end

    subgraph TMS["Template Management Service"]
        API[REST API<br/>:8081/api/v1]
    end

    subgraph Vendors["External Vendors"]
        SmartComm[SmartComm API]
        Assentis[Assentis API]
        LPS[LPS Print API]
        Salesforce[Salesforce Email]
    end

    subgraph Storage["Data Storage"]
        PG[(PostgreSQL)]
    end

    DocHub -->|Get template config| API
    EmailSvc -->|Get email templates| API
    AdminUI -->|CRUD operations| API
    BatchJob -->|Bulk updates| API

    API -->|Validate template| SmartComm
    API -->|Validate template| Assentis
    API -->|Validate account| LPS
    API -->|Read/Write| PG

    style TMS fill:#4fc3f7
    style Consumers fill:#c8e6c9
    style Vendors fill:#ffe0b2
    style Storage fill:#f8bbd9
```

---

## 10. Security Architecture

```mermaid
flowchart TB
    subgraph External["External"]
        Client[API Client]
    end

    subgraph Security["Security Layer"]
        TLS[TLS 1.3]
        Auth[JWT Authentication]
        RBAC[Role-Based Access]
    end

    subgraph API["API Layer"]
        Filter[CorrelationIdFilter]
        RateLimit[Rate Limiting]
        Validation[Input Validation]
        Controller[Controllers]
    end

    subgraph Business["Business Layer"]
        AuthZ[Authorization Check]
        Service[Service Layer]
    end

    subgraph Data["Data Layer"]
        Encrypt[Field Encryption]
        Audit[Audit Logging]
        DB[(PostgreSQL)]
    end

    Client -->|HTTPS| TLS
    TLS --> Auth
    Auth -->|Bearer Token| RBAC
    RBAC --> Filter
    Filter --> RateLimit
    RateLimit --> Validation
    Validation --> Controller
    Controller --> AuthZ
    AuthZ --> Service
    Service --> Encrypt
    Service --> Audit
    Encrypt --> DB
    Audit --> DB

    style Security fill:#ffcdd2
    style API fill:#bbdefb
    style Business fill:#c8e6c9
    style Data fill:#fff9c4
```

---

## View These Diagrams

1. **GitHub/GitLab**: Mermaid diagrams render automatically
2. **VS Code**: Install "Markdown Preview Mermaid Support" extension
3. **Online**: Use [Mermaid Live Editor](https://mermaid.live)

---

## Summary

| Diagram | Purpose |
|---------|---------|
| System Context | Shows external systems and actors |
| Service Architecture | Internal layered structure |
| Component Diagram | Detailed component responsibilities |
| Template Creation Flow | Step-by-step data flow |
| Vendor Routing Flow | Failover mechanism |
| Database Schema | Entity relationships |
| Caching Strategy | Cache architecture and invalidation |
| Deployment Architecture | Production deployment view |
| Integration Points | All external connections |
| Security Architecture | Security controls and layers |
