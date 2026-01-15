package com.templatemanagement.controller;

import com.templatemanagement.dto.request.TemplateVendorCreateRequest;
import com.templatemanagement.dto.request.TemplateVendorUpdateRequest;
import com.templatemanagement.dto.response.TemplateVendorPageResponse;
import com.templatemanagement.dto.response.TemplateVendorResponse;
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
 * REST Controller for Template Vendor Mapping operations.
 */
@Slf4j
@RestController
@RequestMapping("/templates/vendors")
@RequiredArgsConstructor
@Validated
@Tag(name = "Template Vendor Mapping", description = "APIs for managing template vendor mappings")
public class TemplateVendorController {

    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String DEFAULT_USER = "system";

    private final TemplateManagementProcessor processor;

    @PostMapping
    @Operation(summary = "Create vendor mapping", description = "Create a new vendor mapping for a template")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Vendor mapping created successfully",
                    content = @Content(schema = @Schema(implementation = TemplateVendorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Template not found"),
            @ApiResponse(responseCode = "409", description = "Vendor mapping already exists")
    })
    public Mono<ResponseEntity<TemplateVendorResponse>> createVendorMapping(
            @Valid @RequestBody TemplateVendorCreateRequest request,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = HEADER_USER_ID, required = false, defaultValue = DEFAULT_USER) String userId) {

        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("POST /templates/vendors - correlationId={}, templateId={}, vendor={}",
                corrId, request.getMasterTemplateId(), request.getVendor());

        return processor.processCreateVendorMapping(request, corrId, userId)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @GetMapping
    @Operation(summary = "List vendor mappings", description = "List vendor mappings with optional filters and pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendor mappings retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TemplateVendorPageResponse.class)))
    })
    public Mono<ResponseEntity<TemplateVendorPageResponse>> listVendorMappings(
            @Parameter(description = "Filter by master template ID")
            @RequestParam(required = false) UUID templateId,
            @Parameter(description = "Filter by vendor type (GENERATION, PRINT, EMAIL, etc.)")
            @RequestParam(required = false) String vendorType,
            @Parameter(description = "Filter by vendor name")
            @RequestParam(required = false) String vendor,
            @Parameter(description = "Filter by active status")
            @RequestParam(required = false) Boolean activeFlag,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId) {

        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("GET /templates/vendors - correlationId={}, templateId={}, page={}, size={}",
                corrId, templateId, page, size);

        return processor.processListVendorMappings(templateId, vendorType, vendor, activeFlag, page, size, corrId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{vendorId}")
    @Operation(summary = "Get vendor mapping by ID", description = "Get a specific vendor mapping by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendor mapping retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TemplateVendorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vendor mapping not found")
    })
    public Mono<ResponseEntity<TemplateVendorResponse>> getVendorMappingById(
            @Parameter(description = "Vendor mapping ID")
            @PathVariable UUID vendorId,
            @Parameter(description = "Include template details in response")
            @RequestParam(defaultValue = "false") boolean includeTemplateDetails,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId) {

        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("GET /templates/vendors/{} - correlationId={}", vendorId, corrId);

        return processor.processGetVendorMappingById(vendorId, includeTemplateDetails, corrId)
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/{vendorId}")
    @Operation(summary = "Update vendor mapping", description = "Update an existing vendor mapping")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendor mapping updated successfully",
                    content = @Content(schema = @Schema(implementation = TemplateVendorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vendor mapping not found")
    })
    public Mono<ResponseEntity<TemplateVendorResponse>> updateVendorMapping(
            @Parameter(description = "Vendor mapping ID")
            @PathVariable UUID vendorId,
            @Valid @RequestBody TemplateVendorUpdateRequest request,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = HEADER_USER_ID, required = false, defaultValue = DEFAULT_USER) String userId) {

        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("PATCH /templates/vendors/{} - correlationId={}", vendorId, corrId);

        return processor.processUpdateVendorMapping(vendorId, request, corrId, userId)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{vendorId}")
    @Operation(summary = "Delete vendor mapping", description = "Soft delete a vendor mapping")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Vendor mapping deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Vendor mapping not found")
    })
    public Mono<ResponseEntity<Void>> deleteVendorMapping(
            @Parameter(description = "Vendor mapping ID")
            @PathVariable UUID vendorId,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = HEADER_USER_ID, required = false, defaultValue = DEFAULT_USER) String userId) {

        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("DELETE /templates/vendors/{} - correlationId={}", vendorId, corrId);

        return processor.processDeleteVendorMapping(vendorId, corrId, userId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @GetMapping("/routing")
    @Operation(summary = "Get vendors for routing", description = "Get active vendors for document generation routing with failover support")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendors for routing retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TemplateVendorPageResponse.class)))
    })
    public Mono<ResponseEntity<TemplateVendorPageResponse>> getVendorsForRouting(
            @Parameter(description = "Master template ID", required = true)
            @RequestParam UUID templateId,
            @Parameter(description = "Template version", required = true)
            @RequestParam Integer templateVersion,
            @Parameter(description = "Vendor type (GENERATION, PRINT, EMAIL, etc.)", required = true)
            @RequestParam String vendorType,
            @RequestHeader(value = HEADER_CORRELATION_ID, required = false) String correlationId) {

        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        log.info("GET /templates/vendors/routing - correlationId={}, templateId={}, version={}, type={}",
                corrId, templateId, templateVersion, vendorType);

        return processor.processGetVendorsForRouting(templateId, templateVersion, vendorType, corrId)
                .map(ResponseEntity::ok);
    }
}
