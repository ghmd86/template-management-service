# Template Management Service

A Spring Boot microservice for managing document templates and vendor mappings.

## Overview

This service provides REST APIs for:
- Creating and managing document templates
- Configuring vendor mappings (SmartComm, Assentis, LPS, etc.)
- Supporting multi-channel document delivery (Print, Email, Digital)
- Vendor routing with failover support

## Technology Stack

- **Java 17**
- **Spring Boot 2.7.18**
- **Spring WebFlux** (Reactive)
- **Spring Data R2DBC** (Reactive PostgreSQL)
- **Caffeine Cache**
- **SpringDoc OpenAPI** (Swagger UI)
- **Lombok**
- **JUnit 5** + Reactor Test

## Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 14+ (with `document_hub` database)

## Quick Start

```bash
# Clone/navigate to project
cd template-management-service

# Build
mvn clean compile

# Run tests
mvn test

# Start service
mvn spring-boot:run
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8081 | Service port |
| `spring.r2dbc.url` | `r2dbc:postgresql://localhost:5432/document_hub` | Database URL |
| `cache.template.ttl-minutes` | 30 | Template cache TTL |
| `cache.vendor.ttl-minutes` | 30 | Vendor cache TTL |

## API Endpoints

### Templates

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/templates` | Create template |
| GET | `/api/v1/templates` | List templates |
| GET | `/api/v1/templates/{id}` | Get template (all versions) |
| GET | `/api/v1/templates/{id}/versions/{v}` | Get specific version |
| PATCH | `/api/v1/templates/{id}/versions/{v}` | Update template |
| DELETE | `/api/v1/templates/{id}/versions/{v}` | Delete template |

### Vendor Mappings

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/templates/vendors` | Create vendor mapping |
| GET | `/api/v1/templates/vendors` | List vendor mappings |
| GET | `/api/v1/templates/vendors/{id}` | Get vendor mapping |
| PATCH | `/api/v1/templates/vendors/{id}` | Update vendor mapping |
| DELETE | `/api/v1/templates/vendors/{id}` | Delete vendor mapping |
| GET | `/api/v1/templates/vendors/routing` | Get vendors for routing |

## API Documentation

Once the service is running:
- Swagger UI: http://localhost:8081/api/v1/swagger-ui.html
- OpenAPI JSON: http://localhost:8081/api/v1/api-docs

## Project Structure

```
src/main/java/com/templatemanagement/
├── config/         # Configuration classes
├── controller/     # REST controllers
├── dao/            # Data access with caching
├── dto/            # Data transfer objects
├── entity/         # Database entities
├── exception/      # Exception handling
├── filter/         # Web filters
├── processor/      # Request orchestration
├── repository/     # R2DBC repositories
└── service/        # Business logic
```

## Headers

| Header | Required | Description |
|--------|----------|-------------|
| `X-Correlation-Id` | No | Request tracking ID (auto-generated if missing) |
| `X-User-Id` | No | User identifier for audit |

## Related Services

- **Document Hub API** (port 8080) - Document storage and retrieval
- **Letter API Service** - Document generation (SmartComm/Assentis)
- **Print Partner Service** - Physical mail delivery (LPS)

## Status

See [PROJECT_STATUS.md](PROJECT_STATUS.md) for detailed implementation status and session notes.
