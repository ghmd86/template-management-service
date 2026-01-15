package com.templatemanagement.service;

import com.templatemanagement.dao.MasterTemplateDao;
import com.templatemanagement.dao.TemplateVendorMappingDao;
import com.templatemanagement.dto.MasterTemplateDto;
import com.templatemanagement.dto.TemplateVendorMappingDto;
import com.templatemanagement.dto.request.TemplateCreateRequest;
import com.templatemanagement.dto.request.TemplateUpdateRequest;
import com.templatemanagement.dto.request.TemplateVendorCreateRequest;
import com.templatemanagement.dto.request.TemplateVendorUpdateRequest;
import com.templatemanagement.dto.response.*;
import com.templatemanagement.entity.MasterTemplateDefinitionEntity;
import com.templatemanagement.entity.TemplateVendorMappingEntity;
import com.templatemanagement.exception.ConflictException;
import com.templatemanagement.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service layer for Template Management operations.
 * Contains business logic for template and vendor mapping CRUD operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final MasterTemplateDao templateDao;
    private final TemplateVendorMappingDao vendorDao;

    // ========================================================================
    // Template Operations
    // ========================================================================

    /**
     * Create a new template
     */
    @Transactional
    public Mono<TemplateResponse> createTemplate(TemplateCreateRequest request, String createdBy) {
        log.info("Creating template: type={}, lob={}", request.getTemplateType(), request.getLineOfBusiness());

        return templateDao.existsByTemplateType(request.getTemplateType())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ConflictException(
                                "Template with type '" + request.getTemplateType() + "' already exists"));
                    }

                    UUID masterTemplateId = UUID.randomUUID();
                    MasterTemplateDefinitionEntity entity = buildTemplateEntity(request, masterTemplateId, 1, createdBy);

                    return templateDao.save(entity)
                            .map(TemplateResponse::of);
                });
    }

    /**
     * Get template by ID (all versions)
     */
    public Mono<TemplatePageResponse> getTemplateById(UUID masterTemplateId) {
        log.debug("Getting template by ID: {}", masterTemplateId);

        return templateDao.findAllVersionsById(masterTemplateId)
                .collectList()
                .flatMap(templates -> {
                    if (templates.isEmpty()) {
                        return Mono.error(new ResourceNotFoundException(
                                "Template not found with ID: " + masterTemplateId));
                    }
                    PaginationResponse pagination = PaginationResponse.of(0, templates.size(), templates.size());
                    return Mono.just(TemplatePageResponse.of(templates, pagination));
                });
    }

    /**
     * Get template by ID and version
     */
    public Mono<TemplateResponse> getTemplateByIdAndVersion(UUID masterTemplateId, Integer templateVersion, boolean includeVendors) {
        log.debug("Getting template: id={}, version={}, includeVendors={}", masterTemplateId, templateVersion, includeVendors);

        return templateDao.findByIdAndVersion(masterTemplateId, templateVersion)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Template not found: id=" + masterTemplateId + ", version=" + templateVersion)))
                .flatMap(template -> {
                    if (includeVendors) {
                        return vendorDao.findByMasterTemplateIdAndVersion(masterTemplateId, templateVersion)
                                .collectList()
                                .map(vendors -> TemplateResponse.of(template, vendors));
                    }
                    return Mono.just(TemplateResponse.of(template));
                });
    }

    /**
     * List templates with filters and pagination
     */
    public Mono<TemplatePageResponse> listTemplates(
            String lineOfBusiness,
            String templateType,
            Boolean activeFlag,
            String communicationType,
            int page,
            int size) {
        log.debug("Listing templates: lob={}, type={}, active={}, comm={}, page={}, size={}",
                lineOfBusiness, templateType, activeFlag, communicationType, page, size);

        return templateDao.countWithFilters(lineOfBusiness, templateType, activeFlag, communicationType)
                .flatMap(total -> templateDao.findWithFilters(lineOfBusiness, templateType, activeFlag, communicationType, page, size)
                        .collectList()
                        .map(templates -> {
                            PaginationResponse pagination = PaginationResponse.of(page, size, total);
                            return TemplatePageResponse.of(templates, pagination);
                        }));
    }

    /**
     * Update template (creates a new version or updates existing)
     */
    @Transactional
    public Mono<TemplateResponse> updateTemplate(
            UUID masterTemplateId,
            Integer templateVersion,
            TemplateUpdateRequest request,
            String updatedBy,
            boolean createNewVersion) {
        log.info("Updating template: id={}, version={}, createNewVersion={}", masterTemplateId, templateVersion, createNewVersion);

        return templateDao.findByIdAndVersion(masterTemplateId, templateVersion)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Template not found: id=" + masterTemplateId + ", version=" + templateVersion)))
                .flatMap(existing -> {
                    if (createNewVersion) {
                        return templateDao.getNextVersionNumber(masterTemplateId)
                                .flatMap(newVersion -> {
                                    MasterTemplateDefinitionEntity newEntity = buildUpdatedEntity(existing, request, newVersion, updatedBy);
                                    return templateDao.save(newEntity).map(TemplateResponse::of);
                                });
                    } else {
                        MasterTemplateDefinitionEntity updatedEntity = buildUpdatedEntity(existing, request, templateVersion, updatedBy);
                        updatedEntity.setMasterTemplateId(masterTemplateId);
                        return templateDao.update(updatedEntity).map(TemplateResponse::of);
                    }
                });
    }

    /**
     * Delete (archive) template
     */
    @Transactional
    public Mono<Void> deleteTemplate(UUID masterTemplateId, Integer templateVersion, String deletedBy) {
        log.info("Deleting template: id={}, version={}", masterTemplateId, templateVersion);

        return templateDao.findByIdAndVersion(masterTemplateId, templateVersion)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Template not found: id=" + masterTemplateId + ", version=" + templateVersion)))
                .flatMap(template -> templateDao.archiveTemplate(masterTemplateId, templateVersion, deletedBy))
                .then();
    }

    // ========================================================================
    // Vendor Mapping Operations
    // ========================================================================

    /**
     * Create vendor mapping
     */
    @Transactional
    public Mono<TemplateVendorResponse> createVendorMapping(TemplateVendorCreateRequest request, String createdBy) {
        log.info("Creating vendor mapping: templateId={}, vendor={}, type={}",
                request.getMasterTemplateId(), request.getVendor(), request.getVendorType());

        // Verify template exists
        return templateDao.findByIdAndVersion(request.getMasterTemplateId(), request.getTemplateVersion())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Template not found: id=" + request.getMasterTemplateId() + ", version=" + request.getTemplateVersion())))
                .flatMap(template -> vendorDao.existsDuplicateMapping(
                                request.getMasterTemplateId(),
                                request.getTemplateVersion(),
                                request.getVendor(),
                                request.getVendorType())
                        .flatMap(exists -> {
                            if (exists) {
                                return Mono.error(new ConflictException(
                                        "Vendor mapping already exists for template=" + request.getMasterTemplateId() +
                                                ", version=" + request.getTemplateVersion() +
                                                ", vendor=" + request.getVendor() +
                                                ", type=" + request.getVendorType()));
                            }

                            TemplateVendorMappingEntity entity = buildVendorEntity(request, createdBy);
                            return vendorDao.save(entity)
                                    .map(vendor -> TemplateVendorResponse.of(vendor, template));
                        }));
    }

    /**
     * Get vendor mapping by ID
     */
    public Mono<TemplateVendorResponse> getVendorMappingById(UUID vendorId, boolean includeTemplateDetails) {
        log.debug("Getting vendor mapping: id={}, includeTemplate={}", vendorId, includeTemplateDetails);

        return vendorDao.findById(vendorId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Vendor mapping not found: id=" + vendorId)))
                .flatMap(vendor -> {
                    if (includeTemplateDetails) {
                        return templateDao.findByIdAndVersion(vendor.getMasterTemplateId(), vendor.getTemplateVersion())
                                .map(template -> TemplateVendorResponse.of(vendor, template))
                                .switchIfEmpty(Mono.just(TemplateVendorResponse.of(vendor)));
                    }
                    return Mono.just(TemplateVendorResponse.of(vendor));
                });
    }

    /**
     * List vendor mappings with filters and pagination
     */
    public Mono<TemplateVendorPageResponse> listVendorMappings(
            UUID masterTemplateId,
            String vendorType,
            String vendor,
            Boolean activeFlag,
            int page,
            int size) {
        log.debug("Listing vendor mappings: templateId={}, type={}, vendor={}, active={}, page={}, size={}",
                masterTemplateId, vendorType, vendor, activeFlag, page, size);

        return vendorDao.countWithFilters(masterTemplateId, vendorType, vendor, activeFlag)
                .flatMap(total -> vendorDao.findWithFilters(masterTemplateId, vendorType, vendor, activeFlag, page, size)
                        .collectList()
                        .map(vendors -> {
                            PaginationResponse pagination = PaginationResponse.of(page, size, total);
                            return TemplateVendorPageResponse.of(vendors, pagination);
                        }));
    }

    /**
     * Update vendor mapping
     */
    @Transactional
    public Mono<TemplateVendorResponse> updateVendorMapping(
            UUID vendorId,
            TemplateVendorUpdateRequest request,
            String updatedBy) {
        log.info("Updating vendor mapping: id={}", vendorId);

        return vendorDao.findById(vendorId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Vendor mapping not found: id=" + vendorId)))
                .flatMap(existing -> {
                    TemplateVendorMappingEntity updatedEntity = buildUpdatedVendorEntity(existing, request, updatedBy);
                    return vendorDao.update(updatedEntity)
                            .map(TemplateVendorResponse::of);
                });
    }

    /**
     * Delete (archive) vendor mapping
     */
    @Transactional
    public Mono<Void> deleteVendorMapping(UUID vendorId, String deletedBy) {
        log.info("Deleting vendor mapping: id={}", vendorId);

        return vendorDao.findById(vendorId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Vendor mapping not found: id=" + vendorId)))
                .flatMap(vendor -> vendorDao.archiveVendorMapping(vendorId, deletedBy))
                .then();
    }

    /**
     * Get vendors for routing (for document generation)
     */
    public Mono<TemplateVendorPageResponse> getVendorsForRouting(
            UUID masterTemplateId,
            Integer templateVersion,
            String vendorType) {
        log.debug("Getting vendors for routing: templateId={}, version={}, type={}",
                masterTemplateId, templateVersion, vendorType);

        return vendorDao.findActiveVendorsForRouting(masterTemplateId, templateVersion, vendorType)
                .collectList()
                .map(vendors -> {
                    PaginationResponse pagination = PaginationResponse.of(0, vendors.size(), vendors.size());
                    return TemplateVendorPageResponse.of(vendors, pagination);
                });
    }

    // ========================================================================
    // Private Helper Methods
    // ========================================================================

    private MasterTemplateDefinitionEntity buildTemplateEntity(
            TemplateCreateRequest request,
            UUID masterTemplateId,
            Integer version,
            String createdBy) {
        return MasterTemplateDefinitionEntity.builder()
                .masterTemplateId(masterTemplateId)
                .templateVersion(version)
                .templateType(request.getTemplateType())
                .lineOfBusiness(request.getLineOfBusiness())
                .displayName(request.getDisplayName())
                .templateName(request.getTemplateName())
                .templateDescription(request.getTemplateDescription())
                .templateCategory(request.getTemplateCategory())
                .languageCode(request.getLanguageCode() != null ? request.getLanguageCode() : "en")
                .owningDept(request.getOwningDept())
                .notificationNeeded(request.getNotificationNeeded() != null ? request.getNotificationNeeded() : false)
                .regulatoryFlag(request.getRegulatoryFlag() != null ? request.getRegulatoryFlag() : false)
                .messageCenterDocFlag(request.getMessageCenterDocFlag() != null ? request.getMessageCenterDocFlag() : false)
                .activeFlag(true)
                .sharedDocumentFlag(request.getSharedDocumentFlag() != null ? request.getSharedDocumentFlag() : false)
                .sharingScope(request.getSharingScope())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .communicationType(request.getCommunicationType() != null ? request.getCommunicationType() : "LETTER")
                .workflow(request.getWorkflow() != null ? request.getWorkflow() : "2_EYES")
                .singleDocumentFlag(request.getSingleDocumentFlag() != null ? request.getSingleDocumentFlag() : true)
                .legacyTemplateId(request.getLegacyTemplateId())
                .legacyTemplateName(request.getLegacyTemplateName())
                .createdBy(createdBy)
                .createdTimestamp(LocalDateTime.now())
                .updatedBy(createdBy)
                .updatedTimestamp(LocalDateTime.now())
                .recordStatus("DRAFT")
                .archiveIndicator(false)
                .versionNumber(1L)
                .build();
    }

    private MasterTemplateDefinitionEntity buildUpdatedEntity(
            MasterTemplateDto existing,
            TemplateUpdateRequest request,
            Integer version,
            String updatedBy) {
        return MasterTemplateDefinitionEntity.builder()
                .masterTemplateId(existing.getMasterTemplateId())
                .templateVersion(version)
                .templateType(existing.getTemplateType())
                .lineOfBusiness(existing.getLineOfBusiness())
                .displayName(request.getDisplayName() != null ? request.getDisplayName() : existing.getDisplayName())
                .templateName(request.getTemplateName() != null ? request.getTemplateName() : existing.getTemplateName())
                .templateDescription(request.getTemplateDescription() != null ? request.getTemplateDescription() : existing.getTemplateDescription())
                .templateCategory(request.getTemplateCategory() != null ? request.getTemplateCategory() : existing.getTemplateCategory())
                .languageCode(request.getLanguageCode() != null ? request.getLanguageCode() : existing.getLanguageCode())
                .owningDept(request.getOwningDept() != null ? request.getOwningDept() : existing.getOwningDept())
                .notificationNeeded(request.getNotificationNeeded() != null ? request.getNotificationNeeded() : existing.getNotificationNeeded())
                .regulatoryFlag(request.getRegulatoryFlag() != null ? request.getRegulatoryFlag() : existing.getRegulatoryFlag())
                .messageCenterDocFlag(request.getMessageCenterDocFlag() != null ? request.getMessageCenterDocFlag() : existing.getMessageCenterDocFlag())
                .activeFlag(request.getActiveFlag() != null ? request.getActiveFlag() : existing.getActiveFlag())
                .sharedDocumentFlag(request.getSharedDocumentFlag() != null ? request.getSharedDocumentFlag() : existing.getSharedDocumentFlag())
                .sharingScope(request.getSharingScope() != null ? request.getSharingScope() : existing.getSharingScope())
                .startDate(request.getStartDate() != null ? request.getStartDate() : existing.getStartDate())
                .endDate(request.getEndDate() != null ? request.getEndDate() : existing.getEndDate())
                .communicationType(request.getCommunicationType() != null ? request.getCommunicationType() : existing.getCommunicationType())
                .workflow(request.getWorkflow() != null ? request.getWorkflow() : existing.getWorkflow())
                .singleDocumentFlag(request.getSingleDocumentFlag() != null ? request.getSingleDocumentFlag() : existing.getSingleDocumentFlag())
                .legacyTemplateId(existing.getLegacyTemplateId())
                .legacyTemplateName(existing.getLegacyTemplateName())
                .createdBy(existing.getCreatedBy())
                .createdTimestamp(existing.getCreatedTimestamp())
                .updatedBy(updatedBy)
                .updatedTimestamp(LocalDateTime.now())
                .recordStatus(request.getRecordStatus() != null ? request.getRecordStatus() : existing.getRecordStatus())
                .archiveIndicator(false)
                .versionNumber((existing.getTemplateVersion() != null ? existing.getTemplateVersion() : 0) + 1L)
                .build();
    }

    private TemplateVendorMappingEntity buildVendorEntity(TemplateVendorCreateRequest request, String createdBy) {
        return TemplateVendorMappingEntity.builder()
                .templateVendorId(UUID.randomUUID())
                .masterTemplateId(request.getMasterTemplateId())
                .templateVersion(request.getTemplateVersion())
                .vendor(request.getVendor())
                .vendorType(request.getVendorType())
                .vendorTemplateKey(request.getVendorTemplateKey())
                .vendorTemplateName(request.getVendorTemplateName())
                .referenceKeyType(request.getReferenceKeyType())
                .consumerId(request.getConsumerId())
                .startDate(request.getStartDate() != null ? request.getStartDate() : System.currentTimeMillis())
                .endDate(request.getEndDate())
                .vendorMappingVersion(1)
                .primaryFlag(request.getPrimaryFlag() != null ? request.getPrimaryFlag() : false)
                .activeFlag(true)
                .templateStatus("DRAFT")
                .priorityOrder(request.getPriorityOrder() != null ? request.getPriorityOrder() : 1)
                .rateLimitPerMinute(request.getRateLimitPerMinute())
                .rateLimitPerDay(request.getRateLimitPerDay())
                .timeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : 30000)
                .maxRetryAttempts(request.getMaxRetryAttempts() != null ? request.getMaxRetryAttempts() : 3)
                .retryBackoffMs(request.getRetryBackoffMs() != null ? request.getRetryBackoffMs() : 1000)
                .costPerUnit(request.getCostPerUnit())
                .costUnit(request.getCostUnit())
                .healthCheckEndpoint(request.getHealthCheckEndpoint())
                .vendorStatus("ACTIVE")
                .createdBy(createdBy)
                .createdTimestamp(LocalDateTime.now())
                .updatedBy(createdBy)
                .updatedTimestamp(LocalDateTime.now())
                .recordStatus("DRAFT")
                .archiveIndicator(false)
                .versionNumber(1L)
                .build();
    }

    private TemplateVendorMappingEntity buildUpdatedVendorEntity(
            TemplateVendorMappingDto existing,
            TemplateVendorUpdateRequest request,
            String updatedBy) {
        return TemplateVendorMappingEntity.builder()
                .templateVendorId(existing.getTemplateVendorId())
                .masterTemplateId(existing.getMasterTemplateId())
                .templateVersion(existing.getTemplateVersion())
                .vendor(existing.getVendor())
                .vendorType(existing.getVendorType())
                .vendorTemplateKey(request.getVendorTemplateKey() != null ? request.getVendorTemplateKey() : existing.getVendorTemplateKey())
                .vendorTemplateName(request.getVendorTemplateName() != null ? request.getVendorTemplateName() : existing.getVendorTemplateName())
                .referenceKeyType(request.getReferenceKeyType() != null ? request.getReferenceKeyType() : existing.getReferenceKeyType())
                .consumerId(request.getConsumerId() != null ? request.getConsumerId() : existing.getConsumerId())
                .startDate(request.getStartDate() != null ? request.getStartDate() : existing.getStartDate())
                .endDate(request.getEndDate() != null ? request.getEndDate() : existing.getEndDate())
                .vendorMappingVersion(existing.getVendorMappingVersion())
                .primaryFlag(request.getPrimaryFlag() != null ? request.getPrimaryFlag() : existing.getPrimaryFlag())
                .activeFlag(request.getActiveFlag() != null ? request.getActiveFlag() : existing.getActiveFlag())
                .templateStatus(request.getTemplateStatus() != null ? request.getTemplateStatus() : existing.getTemplateStatus())
                .priorityOrder(request.getPriorityOrder() != null ? request.getPriorityOrder() : existing.getPriorityOrder())
                .vendorStatus(request.getVendorStatus() != null ? request.getVendorStatus() : existing.getVendorStatus())
                .rateLimitPerMinute(request.getRateLimitPerMinute() != null ? request.getRateLimitPerMinute() : existing.getRateLimitPerMinute())
                .rateLimitPerDay(request.getRateLimitPerDay() != null ? request.getRateLimitPerDay() : existing.getRateLimitPerDay())
                .timeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : existing.getTimeoutMs())
                .maxRetryAttempts(request.getMaxRetryAttempts() != null ? request.getMaxRetryAttempts() : existing.getMaxRetryAttempts())
                .retryBackoffMs(request.getRetryBackoffMs() != null ? request.getRetryBackoffMs() : existing.getRetryBackoffMs())
                .costPerUnit(request.getCostPerUnit() != null ? request.getCostPerUnit() : existing.getCostPerUnit())
                .costUnit(request.getCostUnit() != null ? request.getCostUnit() : existing.getCostUnit())
                .healthCheckEndpoint(request.getHealthCheckEndpoint() != null ? request.getHealthCheckEndpoint() : existing.getHealthCheckEndpoint())
                .createdBy(existing.getCreatedBy())
                .createdTimestamp(existing.getCreatedTimestamp())
                .updatedBy(updatedBy)
                .updatedTimestamp(LocalDateTime.now())
                .recordStatus(existing.getRecordStatus())
                .archiveIndicator(false)
                .versionNumber(existing.getVendorMappingVersion() != null ? existing.getVendorMappingVersion() + 1L : 1L)
                .build();
    }
}
