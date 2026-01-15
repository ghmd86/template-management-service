package com.templatemanagement.controller;

import com.templatemanagement.dto.request.TemplateCreateRequest;
import com.templatemanagement.dto.request.TemplateUpdateRequest;
import com.templatemanagement.dto.response.TemplatePageResponse;
import com.templatemanagement.dto.response.TemplateResponse;
import com.templatemanagement.processor.TemplateManagementProcessor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.util.UUID;

/**
 * REST Controller for Template Management operations.
 */
@Slf4j
@RestController
@RequestMapping("/templates")
@RequiredArgsConstructor
@Validated
@Tag(name = "Template Management", description = "APIs for managing document templates")
public class TemplateController {

    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String DEFAULT_USER = "system";

    private final TemplateManagementProcessor processor;

    @PostMapping
    @Operation(summary = "Create a new template", description = "Creates a new master template definition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Template created successfully",
                    content = @Content(schema = @Schema(implementation = TemplateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Template already exists")
    })
    public Mono<ResponseEntity<TemplateResponse>> createTemplate(
            @Valid @RequestBody TemplateCreateRequest request,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = HEADER_USER_ID, required = false, defaultValue = DEFAULT_USER) String userId) {

        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("POST /templates - correlationId={}, type={}", corrId, request.getTemplateType());

        return processor.processCreateTemplate(request, corrId, userId)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping
    @Operation(summary = "List templates", description = "List templates with optional filters and pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Templates retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TemplatePageResponse.class)))
    })
    public Mono<ResponseEntity<TemplatePageResponse>> listTemplates(
            @Parameter(description = "Filter by line of business")
            @RequestParam(required = false) String lineOfBusiness,
            @Parameter(description = "Filter by template type")
            @RequestParam(required = false) String templateType,
            @Parameter(description = "Filter by active status")
            @RequestParam(required = false) Boolean activeFlag,
            @Parameter(description = "Filter by communication type")
            @RequestParam(required = false) String communicationType,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId) {

        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("GET /templates - correlationId={}, page={}, size={}", corrId, page, size);

        return processor.processListTemplates(lineOfBusiness, templateType, activeFlag, communicationType, page, size, corrId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{templateId}")
    @Operation(summary = "Get template by ID", description = "Get all versions of a template by master template ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Template retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TemplatePageResponse.class))),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    public Mono<ResponseEntity<TemplatePageResponse>> getTemplateById(
            @Parameter(description = "Master template ID")
            @PathVariable UUID templateId,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId) {

        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("GET /templates/{} - correlationId={}", templateId, corrId);

        return processor.processGetTemplateById(templateId, corrId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{templateId}/versions/{templateVersion}")
    @Operation(summary = "Get template version", description = "Get a specific version of a template")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Template version retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TemplateResponse.class))),
            @ApiResponse(responseCode = "404", description = "Template version not found")
    })
    public Mono<ResponseEntity<TemplateResponse>> getTemplateByIdAndVersion(
            @Parameter(description = "Master template ID")
            @PathVariable UUID templateId,
            @Parameter(description = "Template version")
            @PathVariable Integer templateVersion,
            @Parameter(description = "Include vendor mappings in response")
            @RequestParam(defaultValue = "false") boolean includeVendors,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId) {

        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("GET /templates/{}/versions/{} - correlationId={}", templateId, templateVersion, corrId);

        return processor.processGetTemplateByIdAndVersion(templateId, templateVersion, includeVendors, corrId)
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/{templateId}/versions/{templateVersion}")
    @Operation(summary = "Update template", description = "Update a template version or create a new version")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Template updated successfully",
                    content = @Content(schema = @Schema(implementation = TemplateResponse.class))),
            @ApiResponse(responseCode = "404", description = "Template version not found")
    })
    public Mono<ResponseEntity<TemplateResponse>> updateTemplate(
            @Parameter(description = "Master template ID")
            @PathVariable UUID templateId,
            @Parameter(description = "Template version")
            @PathVariable Integer templateVersion,
            @Valid @RequestBody TemplateUpdateRequest request,
            @Parameter(description = "Create a new version instead of updating existing")
            @RequestParam(defaultValue = "false") boolean createNewVersion,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = HEADER_USER_ID, required = false, defaultValue = DEFAULT_USER) String userId) {

        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("PATCH /templates/{}/versions/{} - correlationId={}, createNewVersion={}",
                templateId, templateVersion, corrId, createNewVersion);

        return processor.processUpdateTemplate(templateId, templateVersion, request, createNewVersion, corrId, userId)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{templateId}/versions/{templateVersion}")
    @Operation(summary = "Delete template", description = "Soft delete a template version")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Template deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Template version not found")
    })
    public Mono<ResponseEntity<Void>> deleteTemplate(
            @Parameter(description = "Master template ID")
            @PathVariable UUID templateId,
            @Parameter(description = "Template version")
            @PathVariable Integer templateVersion,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = HEADER_USER_ID, required = false, defaultValue = DEFAULT_USER) String userId) {

        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("DELETE /templates/{}/versions/{} - correlationId={}", templateId, templateVersion, corrId);

        return processor.processDeleteTemplate(templateId, templateVersion, corrId, userId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}
