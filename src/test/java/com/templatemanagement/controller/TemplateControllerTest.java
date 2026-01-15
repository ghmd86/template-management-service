package com.templatemanagement.controller;

import com.templatemanagement.dto.MasterTemplateDto;
import com.templatemanagement.dto.request.TemplateCreateRequest;
import com.templatemanagement.dto.request.TemplateUpdateRequest;
import com.templatemanagement.dto.response.PaginationResponse;
import com.templatemanagement.dto.response.TemplatePageResponse;
import com.templatemanagement.dto.response.TemplateResponse;
import com.templatemanagement.exception.ConflictException;
import com.templatemanagement.exception.GlobalExceptionHandler;
import com.templatemanagement.exception.ResourceNotFoundException;
import com.templatemanagement.processor.TemplateManagementProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateController Tests")
class TemplateControllerTest {

    private WebTestClient webTestClient;

    @Mock
    private TemplateManagementProcessor processor;

    private UUID templateId;
    private MasterTemplateDto sampleTemplate;

    @BeforeEach
    void setUp() {
        TemplateController controller = new TemplateController(processor);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .build();

        templateId = UUID.randomUUID();
        sampleTemplate = MasterTemplateDto.builder()
                .masterTemplateId(templateId)
                .templateVersion(1)
                .templateType("STATEMENT")
                .lineOfBusiness("CREDIT_CARD")
                .displayName("Monthly Statement")
                .activeFlag(true)
                .createdBy("test-user")
                .createdTimestamp(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("POST /templates")
    class CreateTemplate {

        @Test
        @DisplayName("Should create template and return 201")
        void createTemplate_Success() {
            TemplateCreateRequest request = TemplateCreateRequest.builder()
                    .templateType("STATEMENT")
                    .lineOfBusiness("CREDIT_CARD")
                    .displayName("Monthly Statement")
                    .startDate(System.currentTimeMillis())
                    .build();

            TemplateResponse response = TemplateResponse.of(sampleTemplate);

            when(processor.processCreateTemplate(any(), anyString(), anyString()))
                    .thenReturn(Mono.just(response));

            webTestClient.post()
                    .uri("/templates")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .header("X-Correlation-Id", "test-123")
                    .header("X-User-Id", "test-user")
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.template.templateType").isEqualTo("STATEMENT")
                    .jsonPath("$.template.lineOfBusiness").isEqualTo("CREDIT_CARD");
        }

        @Test
        @DisplayName("Should return 409 when template already exists")
        void createTemplate_Conflict() {
            TemplateCreateRequest request = TemplateCreateRequest.builder()
                    .templateType("STATEMENT")
                    .lineOfBusiness("CREDIT_CARD")
                    .displayName("Monthly Statement")
                    .startDate(System.currentTimeMillis())
                    .build();

            when(processor.processCreateTemplate(any(), anyString(), anyString()))
                    .thenReturn(Mono.error(new ConflictException("Template already exists")));

            webTestClient.post()
                    .uri("/templates")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .header("X-Correlation-Id", "test-123")
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(409)
                    .jsonPath("$.error").isEqualTo("Conflict");
        }
    }

    @Nested
    @DisplayName("GET /templates")
    class ListTemplates {

        @Test
        @DisplayName("Should list templates with pagination")
        void listTemplates_Success() {
            PaginationResponse pagination = PaginationResponse.of(0, 20, 1);
            TemplatePageResponse response = TemplatePageResponse.of(List.of(sampleTemplate), pagination);

            when(processor.processListTemplates(any(), any(), any(), any(), anyInt(), anyInt(), anyString()))
                    .thenReturn(Mono.just(response));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/templates")
                            .queryParam("page", 0)
                            .queryParam("size", 20)
                            .build())
                    .header("X-Correlation-Id", "test-123")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.templates").isArray()
                    .jsonPath("$.templates.length()").isEqualTo(1)
                    .jsonPath("$.pagination.totalElements").isEqualTo(1);
        }

        @Test
        @DisplayName("Should filter templates by line of business")
        void listTemplates_WithFilter() {
            PaginationResponse pagination = PaginationResponse.of(0, 20, 1);
            TemplatePageResponse response = TemplatePageResponse.of(List.of(sampleTemplate), pagination);

            when(processor.processListTemplates(eq("CREDIT_CARD"), any(), any(), any(), anyInt(), anyInt(), anyString()))
                    .thenReturn(Mono.just(response));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/templates")
                            .queryParam("lineOfBusiness", "CREDIT_CARD")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.templates[0].lineOfBusiness").isEqualTo("CREDIT_CARD");
        }
    }

    @Nested
    @DisplayName("GET /templates/{templateId}/versions/{version}")
    class GetTemplateVersion {

        @Test
        @DisplayName("Should get template version successfully")
        void getTemplateVersion_Success() {
            TemplateResponse response = TemplateResponse.of(sampleTemplate);

            when(processor.processGetTemplateByIdAndVersion(eq(templateId), eq(1), anyBoolean(), anyString()))
                    .thenReturn(Mono.just(response));

            webTestClient.get()
                    .uri("/templates/{id}/versions/{version}", templateId, 1)
                    .header("X-Correlation-Id", "test-123")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.template.masterTemplateId").isEqualTo(templateId.toString())
                    .jsonPath("$.template.templateVersion").isEqualTo(1);
        }

        @Test
        @DisplayName("Should return 404 when template version not found")
        void getTemplateVersion_NotFound() {
            when(processor.processGetTemplateByIdAndVersion(eq(templateId), eq(1), anyBoolean(), anyString()))
                    .thenReturn(Mono.error(new ResourceNotFoundException("Template not found")));

            webTestClient.get()
                    .uri("/templates/{id}/versions/{version}", templateId, 1)
                    .header("X-Correlation-Id", "test-123")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(404)
                    .jsonPath("$.error").isEqualTo("Not Found");
        }
    }

    @Nested
    @DisplayName("PATCH /templates/{templateId}/versions/{version}")
    class UpdateTemplate {

        @Test
        @DisplayName("Should update template successfully")
        void updateTemplate_Success() {
            TemplateUpdateRequest request = TemplateUpdateRequest.builder()
                    .displayName("Updated Statement")
                    .build();

            MasterTemplateDto updatedTemplate = MasterTemplateDto.builder()
                    .masterTemplateId(templateId)
                    .templateVersion(1)
                    .templateType("STATEMENT")
                    .displayName("Updated Statement")
                    .build();

            TemplateResponse response = TemplateResponse.of(updatedTemplate);

            when(processor.processUpdateTemplate(eq(templateId), eq(1), any(), anyBoolean(), anyString(), anyString()))
                    .thenReturn(Mono.just(response));

            webTestClient.patch()
                    .uri("/templates/{id}/versions/{version}", templateId, 1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .header("X-Correlation-Id", "test-123")
                    .header("X-User-Id", "test-user")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.template.displayName").isEqualTo("Updated Statement");
        }
    }

    @Nested
    @DisplayName("DELETE /templates/{templateId}/versions/{version}")
    class DeleteTemplate {

        @Test
        @DisplayName("Should delete template and return 204")
        void deleteTemplate_Success() {
            when(processor.processDeleteTemplate(eq(templateId), eq(1), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            webTestClient.delete()
                    .uri("/templates/{id}/versions/{version}", templateId, 1)
                    .header("X-Correlation-Id", "test-123")
                    .header("X-User-Id", "test-user")
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        @DisplayName("Should return 404 when template not found for deletion")
        void deleteTemplate_NotFound() {
            when(processor.processDeleteTemplate(eq(templateId), eq(1), anyString(), anyString()))
                    .thenReturn(Mono.error(new ResourceNotFoundException("Template not found")));

            webTestClient.delete()
                    .uri("/templates/{id}/versions/{version}", templateId, 1)
                    .header("X-Correlation-Id", "test-123")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }
}
