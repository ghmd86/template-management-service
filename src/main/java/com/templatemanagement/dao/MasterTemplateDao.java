package com.templatemanagement.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.templatemanagement.dto.MasterTemplateDto;
import com.templatemanagement.entity.MasterTemplateDefinitionEntity;
import com.templatemanagement.repository.MasterTemplateRepository;
import io.r2dbc.postgresql.codec.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Data Access Object for MasterTemplateDefinition operations.
 * Provides a layer of abstraction over the repository with built-in caching.
 */
@Slf4j
@Component
public class MasterTemplateDao {

    private final MasterTemplateRepository repository;
    private final ObjectMapper objectMapper;
    private final Cache<String, MasterTemplateDto> templateCache;
    private final Cache<UUID, MasterTemplateDto> templateByIdCache;

    public MasterTemplateDao(
            MasterTemplateRepository repository,
            ObjectMapper objectMapper,
            @Value("${cache.template.ttl-minutes:30}") long ttlMinutes,
            @Value("${cache.template.max-size:1000}") long maxSize) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.templateCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .recordStats()
                .build();
        this.templateByIdCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .recordStats()
                .build();
    }

    /**
     * Find template by master template ID and version
     */
    public Mono<MasterTemplateDto> findByIdAndVersion(UUID masterTemplateId, Integer templateVersion) {
        log.debug("Finding template by id and version: id={}, version={}", masterTemplateId, templateVersion);
        String cacheKey = buildCacheKey(masterTemplateId, templateVersion);

        MasterTemplateDto cached = templateCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Template cache hit: key={}", cacheKey);
            return Mono.just(cached);
        }

        return repository.findByMasterTemplateIdAndVersion(masterTemplateId, templateVersion)
                .map(this::toDto)
                .doOnNext(dto -> {
                    templateCache.put(cacheKey, dto);
                    log.debug("Template cached: key={}", cacheKey);
                });
    }

    /**
     * Find all versions of a template
     */
    public Flux<MasterTemplateDto> findAllVersionsById(UUID masterTemplateId) {
        log.debug("Finding all versions for template: {}", masterTemplateId);
        return repository.findAllVersionsByMasterTemplateId(masterTemplateId)
                .map(this::toDto);
    }

    /**
     * Find template by type and version
     */
    public Mono<MasterTemplateDto> findByTypeAndVersion(String templateType, Integer templateVersion) {
        log.debug("Finding template by type and version: type={}, version={}", templateType, templateVersion);
        return repository.findByTemplateTypeAndVersion(templateType, templateVersion)
                .map(this::toDto);
    }

    /**
     * Find templates with pagination and filters
     */
    public Flux<MasterTemplateDto> findWithFilters(
            String lineOfBusiness,
            String templateType,
            Boolean activeFlag,
            String communicationType,
            int page,
            int size) {
        log.debug("Finding templates with filters: lob={}, type={}, active={}, comm={}",
                lineOfBusiness, templateType, activeFlag, communicationType);
        long offset = (long) page * size;
        return repository.findWithFilters(lineOfBusiness, templateType, activeFlag, communicationType, size, offset)
                .map(this::toDto);
    }

    /**
     * Count templates with filters
     */
    public Mono<Long> countWithFilters(
            String lineOfBusiness,
            String templateType,
            Boolean activeFlag,
            String communicationType) {
        return repository.countWithFilters(lineOfBusiness, templateType, activeFlag, communicationType);
    }

    /**
     * Save a new template
     */
    public Mono<MasterTemplateDto> save(MasterTemplateDefinitionEntity entity) {
        log.debug("Saving template: type={}", entity.getTemplateType());
        return repository.save(entity)
                .map(this::toDto)
                .doOnNext(dto -> {
                    String cacheKey = buildCacheKey(dto.getMasterTemplateId(), dto.getTemplateVersion());
                    templateCache.put(cacheKey, dto);
                    templateByIdCache.put(dto.getMasterTemplateId(), dto);
                });
    }

    /**
     * Update an existing template
     */
    public Mono<MasterTemplateDto> update(MasterTemplateDefinitionEntity entity) {
        log.debug("Updating template: id={}, version={}", entity.getMasterTemplateId(), entity.getTemplateVersion());
        return repository.save(entity)
                .map(this::toDto)
                .doOnNext(dto -> {
                    String cacheKey = buildCacheKey(dto.getMasterTemplateId(), dto.getTemplateVersion());
                    templateCache.put(cacheKey, dto);
                    templateByIdCache.put(dto.getMasterTemplateId(), dto);
                });
    }

    /**
     * Archive (soft delete) a template version
     */
    public Mono<Integer> archiveTemplate(UUID masterTemplateId, Integer templateVersion, String updatedBy) {
        log.debug("Archiving template: id={}, version={}", masterTemplateId, templateVersion);
        return repository.archiveTemplateVersion(masterTemplateId, templateVersion, updatedBy)
                .doOnNext(count -> {
                    if (count > 0) {
                        invalidateCache(masterTemplateId, templateVersion);
                    }
                });
    }

    /**
     * Get next version number for a template
     */
    public Mono<Integer> getNextVersionNumber(UUID masterTemplateId) {
        return repository.getNextVersionNumber(masterTemplateId);
    }

    /**
     * Check if template type already exists
     */
    public Mono<Boolean> existsByTemplateType(String templateType) {
        return repository.existsByTemplateType(templateType);
    }

    /**
     * Find active templates by line of business
     */
    public Flux<MasterTemplateDto> findActiveTemplatesByLineOfBusiness(String lineOfBusiness, Long currentDate) {
        log.debug("Finding active templates by LOB: {}", lineOfBusiness);
        return repository.findActiveTemplatesByLineOfBusiness(lineOfBusiness, currentDate)
                .map(this::toDto);
    }

    /**
     * Invalidate cache for a specific template
     */
    public void invalidateCache(UUID masterTemplateId, Integer templateVersion) {
        String cacheKey = buildCacheKey(masterTemplateId, templateVersion);
        templateCache.invalidate(cacheKey);
        templateByIdCache.invalidate(masterTemplateId);
        log.info("Template cache invalidated: key={}", cacheKey);
    }

    /**
     * Invalidate all cache
     */
    public void invalidateAllCache() {
        templateCache.invalidateAll();
        templateByIdCache.invalidateAll();
        log.info("All template cache invalidated");
    }

    /**
     * Get cache statistics
     */
    public CacheStats getCacheStats() {
        var stats = templateCache.stats();
        return new CacheStats(
                templateCache.estimatedSize(),
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate()
        );
    }

    public record CacheStats(long size, long hitCount, long missCount, double hitRate) {}

    private String buildCacheKey(UUID masterTemplateId, Integer templateVersion) {
        return masterTemplateId.toString() + ":" + templateVersion;
    }

    private MasterTemplateDto toDto(MasterTemplateDefinitionEntity entity) {
        return MasterTemplateDto.builder()
                .masterTemplateId(entity.getMasterTemplateId())
                .templateVersion(entity.getTemplateVersion())
                .legacyTemplateId(entity.getLegacyTemplateId())
                .legacyTemplateName(entity.getLegacyTemplateName())
                .templateName(entity.getTemplateName())
                .templateDescription(entity.getTemplateDescription())
                .lineOfBusiness(entity.getLineOfBusiness())
                .templateCategory(entity.getTemplateCategory())
                .templateType(entity.getTemplateType())
                .languageCode(entity.getLanguageCode())
                .owningDept(entity.getOwningDept())
                .notificationNeeded(entity.getNotificationNeeded())
                .regulatoryFlag(entity.getRegulatoryFlag())
                .messageCenterDocFlag(entity.getMessageCenterDocFlag())
                .displayName(entity.getDisplayName())
                .activeFlag(entity.getActiveFlag())
                .sharedDocumentFlag(entity.getSharedDocumentFlag())
                .sharingScope(entity.getSharingScope())
                .templateVariables(jsonToMap(entity.getTemplateVariables()))
                .dataExtractionConfig(jsonToMap(entity.getDataExtractionConfig()))
                .documentMatchingConfig(jsonToMap(entity.getDocumentMatchingConfig()))
                .eligibilityCriteria(jsonToMap(entity.getEligibilityCriteria()))
                .accessControl(jsonToMap(entity.getAccessControl()))
                .requiredFields(jsonToMap(entity.getRequiredFields()))
                .templateConfig(jsonToMap(entity.getTemplateConfig()))
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .createdBy(entity.getCreatedBy())
                .createdTimestamp(entity.getCreatedTimestamp())
                .updatedBy(entity.getUpdatedBy())
                .updatedTimestamp(entity.getUpdatedTimestamp())
                .recordStatus(entity.getRecordStatus())
                .communicationType(entity.getCommunicationType())
                .workflow(entity.getWorkflow())
                .singleDocumentFlag(entity.getSingleDocumentFlag())
                .build();
    }

    private Map<String, Object> jsonToMap(Json json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json.asString(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Convert DTO back to entity for persistence
     */
    public MasterTemplateDefinitionEntity toEntity(MasterTemplateDto dto) {
        return MasterTemplateDefinitionEntity.builder()
                .masterTemplateId(dto.getMasterTemplateId())
                .templateVersion(dto.getTemplateVersion())
                .legacyTemplateId(dto.getLegacyTemplateId())
                .legacyTemplateName(dto.getLegacyTemplateName())
                .templateName(dto.getTemplateName())
                .templateDescription(dto.getTemplateDescription())
                .lineOfBusiness(dto.getLineOfBusiness())
                .templateCategory(dto.getTemplateCategory())
                .templateType(dto.getTemplateType())
                .languageCode(dto.getLanguageCode())
                .owningDept(dto.getOwningDept())
                .notificationNeeded(dto.getNotificationNeeded())
                .regulatoryFlag(dto.getRegulatoryFlag())
                .messageCenterDocFlag(dto.getMessageCenterDocFlag())
                .displayName(dto.getDisplayName())
                .activeFlag(dto.getActiveFlag())
                .sharedDocumentFlag(dto.getSharedDocumentFlag())
                .sharingScope(dto.getSharingScope())
                .templateVariables(mapToJson(dto.getTemplateVariables()))
                .dataExtractionConfig(mapToJson(dto.getDataExtractionConfig()))
                .documentMatchingConfig(mapToJson(dto.getDocumentMatchingConfig()))
                .eligibilityCriteria(mapToJson(dto.getEligibilityCriteria()))
                .accessControl(mapToJson(dto.getAccessControl()))
                .requiredFields(mapToJson(dto.getRequiredFields()))
                .templateConfig(mapToJson(dto.getTemplateConfig()))
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .createdBy(dto.getCreatedBy())
                .createdTimestamp(dto.getCreatedTimestamp() != null ? dto.getCreatedTimestamp() : LocalDateTime.now())
                .updatedBy(dto.getUpdatedBy())
                .updatedTimestamp(LocalDateTime.now())
                .recordStatus(dto.getRecordStatus())
                .communicationType(dto.getCommunicationType())
                .workflow(dto.getWorkflow())
                .singleDocumentFlag(dto.getSingleDocumentFlag())
                .archiveIndicator(false)
                .build();
    }

    private Json mapToJson(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        try {
            return Json.of(objectMapper.writeValueAsString(map));
        } catch (Exception e) {
            log.warn("Failed to convert map to JSON: {}", e.getMessage());
            return null;
        }
    }
}
