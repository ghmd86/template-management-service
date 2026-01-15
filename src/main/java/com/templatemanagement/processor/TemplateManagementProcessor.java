package com.templatemanagement.processor;

import com.templatemanagement.dto.request.TemplateCreateRequest;
import com.templatemanagement.dto.request.TemplateUpdateRequest;
import com.templatemanagement.dto.request.TemplateVendorCreateRequest;
import com.templatemanagement.dto.request.TemplateVendorUpdateRequest;
import com.templatemanagement.dto.response.TemplatePageResponse;
import com.templatemanagement.dto.response.TemplateResponse;
import com.templatemanagement.dto.response.TemplateVendorPageResponse;
import com.templatemanagement.dto.response.TemplateVendorResponse;
import com.templatemanagement.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Processor layer for Template Management operations.
 * Acts as intermediary between controllers and services.
 * Handles request/response transformation and orchestration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateManagementProcessor {

    private final TemplateService templateService;

    // ========================================================================
    // Template Operations
    // ========================================================================

    /**
     * Process create template request
     */
    public Mono<TemplateResponse> processCreateTemplate(
            TemplateCreateRequest request,
            String correlationId,
            String userId) {
        log.info("Processing create template: correlationId={}, type={}", correlationId, request.getTemplateType());

        return templateService.createTemplate(request, userId)
                .doOnSuccess(response -> log.info("Template created: correlationId={}, templateId={}",
                        correlationId, response.getTemplate().getMasterTemplateId()))
                .doOnError(error -> log.error("Failed to create template: correlationId={}, error={}",
                        correlationId, error.getMessage()));
    }

    /**
     * Process get template by ID
     */
    public Mono<TemplatePageResponse> processGetTemplateById(UUID masterTemplateId, String correlationId) {
        log.debug("Processing get template by ID: correlationId={}, templateId={}", correlationId, masterTemplateId);

        return templateService.getTemplateById(masterTemplateId)
                .doOnSuccess(response -> log.debug("Template retrieved: correlationId={}, versions={}",
                        correlationId, response.getTemplates().size()))
                .doOnError(error -> log.error("Failed to get template: correlationId={}, error={}",
                        correlationId, error.getMessage()));
    }

    /**
     * Process get template by ID and version
     */
    public Mono<TemplateResponse> processGetTemplateByIdAndVersion(
            UUID masterTemplateId,
            Integer templateVersion,
            boolean includeVendors,
            String correlationId) {
        log.debug("Processing get template version: correlationId={}, templateId={}, version={}",
                correlationId, masterTemplateId, templateVersion);

        return templateService.getTemplateByIdAndVersion(masterTemplateId, templateVersion, includeVendors)
                .doOnSuccess(response -> log.debug("Template version retrieved: correlationId={}", correlationId))
                .doOnError(error -> log.error("Failed to get template version: correlationId={}, error={}",
                        correlationId, error.getMessage()));
    }

    /**
     * Process list templates with filters
     */
    public Mono<TemplatePageResponse> processListTemplates(
            String lineOfBusiness,
            String templateType,
            Boolean activeFlag,
            String communicationType,
            int page,
            int size,
            String correlationId) {
        log.debug("Processing list templates: correlationId={}, page={}, size={}", correlationId, page, size);

        return templateService.listTemplates(lineOfBusiness, templateType, activeFlag, communicationType, page, size)
                .doOnSuccess(response -> log.debug("Templates listed: correlationId={}, count={}, total={}",
                        correlationId, response.getTemplates().size(), response.getPagination().getTotalElements()))
                .doOnError(error -> log.error("Failed to list templates: correlationId={}, error={}",
                        correlationId, error.getMessage()));
    }

    /**
     * Process update template
     */
    public Mono<TemplateResponse> processUpdateTemplate(
            UUID masterTemplateId,
            Integer templateVersion,
            TemplateUpdateRequest request,
            boolean createNewVersion,
            String correlationId,
            String userId) {
        log.info("Processing update template: correlationId={}, templateId={}, version={}, createNewVersion={}",
                correlationId, masterTemplateId, templateVersion, createNewVersion);

        return templateService.updateTemplate(masterTemplateId, templateVersion, request, userId, createNewVersion)
                .doOnSuccess(response -> log.info("Template updated: correlationId={}, newVersion={}",
                        correlationId, response.getTemplate().getTemplateVersion()))
                .doOnError(error -> log.error("Failed to update template: correlationId={}, error={}",
                        correlationId, error.getMessage()));
    }

    /**
     * Process delete template
     */
    public Mono<Void> processDeleteTemplate(
            UUID masterTemplateId,
            Integer templateVersion,
            String correlationId,
            String userId) {
        log.info("Processing delete template: correlationId={}, templateId={}, version={}",
                correlationId, masterTemplateId, templateVersion);

        return templateService.deleteTemplate(masterTemplateId, templateVersion, userId)
                .doOnSuccess(v -> log.info("Template deleted: correlationId={}", correlationId))
                .doOnError(error -> log.error("Failed to delete template: correlationId={}, error={}",
                        correlationId, error.getMessage()));
    }

    // ========================================================================
    // Vendor Mapping Operations
    // ========================================================================

    /**
     * Process create vendor mapping
     */
    public Mono<TemplateVendorResponse> processCreateVendorMapping(
            TemplateVendorCreateRequest request,
            String correlationId,
            String userId) {
        log.info("Processing create vendor mapping: correlationId={}, templateId={}, vendor={}",
                correlationId, request.getMasterTemplateId(), request.getVendor());

        return templateService.createVendorMapping(request, userId)
                .doOnSuccess(response -> log.info("Vendor mapping created: correlationId={}, vendorId={}",
                        correlationId, response.getVendorMapping().getTemplateVendorId()))
                .doOnError(error -> log.error("Failed to create vendor mapping: correlationId={}, error={}",
                        correlationId, error.getMessage()));
    }

    /**
     * Process get vendor mapping by ID
     */
    public Mono<TemplateVendorResponse> processGetVendorMappingById(
            UUID vendorId,
            boolean includeTemplateDetails,
            String correlationId) {
        log.debug("Processing get vendor mapping: correlationId={}, vendorId={}", correlationId, vendorId);

        return templateService.getVendorMappingById(vendorId, includeTemplateDetails)
                .doOnSuccess(response -> log.debug("Vendor mapping retrieved: correlationId={}", correlationId))
                .doOnError(error -> log.error("Failed to get vendor mapping: correlationId={}, error={}",
                        correlationId, error.getMessage()));
    }

    /**
     * Process list vendor mappings with filters
     */
    public Mono<TemplateVendorPageResponse> processListVendorMappings(
            UUID masterTemplateId,
            String vendorType,
            String vendor,
            Boolean activeFlag,
            int page,
            int size,
            String correlationId) {
        log.debug("Processing list vendor mappings: correlationId={}, templateId={}, page={}, size={}",
                correlationId, masterTemplateId, page, size);

        return templateService.listVendorMappings(masterTemplateId, vendorType, vendor, activeFlag, page, size)
                .doOnSuccess(response -> log.debug("Vendor mappings listed: correlationId={}, count={}, total={}",
                        correlationId, response.getVendorMappings().size(), response.getPagination().getTotalElements()))
                .doOnError(error -> log.error("Failed to list vendor mappings: correlationId={}, error={}",
                        correlationId, error.getMessage()));
    }

    /**
     * Process update vendor mapping
     */
    public Mono<TemplateVendorResponse> processUpdateVendorMapping(
            UUID vendorId,
            TemplateVendorUpdateRequest request,
            String correlationId,
            String userId) {
        log.info("Processing update vendor mapping: correlationId={}, vendorId={}", correlationId, vendorId);

        return templateService.updateVendorMapping(vendorId, request, userId)
                .doOnSuccess(response -> log.info("Vendor mapping updated: correlationId={}", correlationId))
                .doOnError(error -> log.error("Failed to update vendor mapping: correlationId={}, error={}",
                        correlationId, error.getMessage()));
    }

    /**
     * Process delete vendor mapping
     */
    public Mono<Void> processDeleteVendorMapping(
            UUID vendorId,
            String correlationId,
            String userId) {
        log.info("Processing delete vendor mapping: correlationId={}, vendorId={}", correlationId, vendorId);

        return templateService.deleteVendorMapping(vendorId, userId)
                .doOnSuccess(v -> log.info("Vendor mapping deleted: correlationId={}", correlationId))
                .doOnError(error -> log.error("Failed to delete vendor mapping: correlationId={}, error={}",
                        correlationId, error.getMessage()));
    }

    /**
     * Process get vendors for routing
     */
    public Mono<TemplateVendorPageResponse> processGetVendorsForRouting(
            UUID masterTemplateId,
            Integer templateVersion,
            String vendorType,
            String correlationId) {
        log.debug("Processing get vendors for routing: correlationId={}, templateId={}, version={}, type={}",
                correlationId, masterTemplateId, templateVersion, vendorType);

        return templateService.getVendorsForRouting(masterTemplateId, templateVersion, vendorType)
                .doOnSuccess(response -> log.debug("Vendors for routing retrieved: correlationId={}, count={}",
                        correlationId, response.getVendorMappings().size()))
                .doOnError(error -> log.error("Failed to get vendors for routing: correlationId={}, error={}",
                        correlationId, error.getMessage()));
    }
}
