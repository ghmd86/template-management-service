package com.templatemanagement.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.templatemanagement.dto.TemplateVendorMappingDto;
import com.templatemanagement.entity.TemplateVendorMappingEntity;
import com.templatemanagement.repository.TemplateVendorMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Access Object for TemplateVendorMapping operations.
 * Provides a layer of abstraction over the repository with built-in caching.
 */
@Slf4j
@Component
public class TemplateVendorMappingDao {

    private final TemplateVendorMappingRepository repository;
    private final ObjectMapper objectMapper;
    private final Cache<UUID, TemplateVendorMappingDto> vendorCache;
    private final Cache<String, List<TemplateVendorMappingDto>> vendorListCache;

    public TemplateVendorMappingDao(
            TemplateVendorMappingRepository repository,
            ObjectMapper objectMapper,
            @Value("${cache.vendor.ttl-minutes:30}") long ttlMinutes,
            @Value("${cache.vendor.max-size:500}") long maxSize) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.vendorCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .recordStats()
                .build();
        this.vendorListCache = Caffeine.newBuilder()
                .maximumSize(maxSize / 2)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .recordStats()
                .build();
    }

    /**
     * Find vendor mapping by ID
     */
    public Mono<TemplateVendorMappingDto> findById(UUID vendorId) {
        log.debug("Finding vendor mapping by id: {}", vendorId);

        TemplateVendorMappingDto cached = vendorCache.getIfPresent(vendorId);
        if (cached != null) {
            log.debug("Vendor cache hit: id={}", vendorId);
            return Mono.just(cached);
        }

        return repository.findByVendorId(vendorId)
                .map(this::toDto)
                .doOnNext(dto -> {
                    vendorCache.put(vendorId, dto);
                    log.debug("Vendor cached: id={}", vendorId);
                });
    }

    /**
     * Find vendor mappings for a template
     */
    public Flux<TemplateVendorMappingDto> findByMasterTemplateId(UUID masterTemplateId) {
        log.debug("Finding vendor mappings for template: {}", masterTemplateId);
        return repository.findByMasterTemplateId(masterTemplateId)
                .map(this::toDto);
    }

    /**
     * Find vendor mappings for a specific template version
     */
    public Flux<TemplateVendorMappingDto> findByMasterTemplateIdAndVersion(UUID masterTemplateId, Integer templateVersion) {
        log.debug("Finding vendor mappings for template version: id={}, version={}", masterTemplateId, templateVersion);
        return repository.findByMasterTemplateIdAndVersion(masterTemplateId, templateVersion)
                .map(this::toDto);
    }

    /**
     * Find vendor mappings by vendor type
     */
    public Flux<TemplateVendorMappingDto> findByMasterTemplateIdAndVendorType(UUID masterTemplateId, String vendorType) {
        log.debug("Finding vendor mappings by type: templateId={}, type={}", masterTemplateId, vendorType);
        return repository.findByMasterTemplateIdAndVendorType(masterTemplateId, vendorType)
                .map(this::toDto);
    }

    /**
     * Find primary vendor mapping
     */
    public Mono<TemplateVendorMappingDto> findPrimaryVendorMapping(UUID masterTemplateId, Integer templateVersion, String vendorType) {
        log.debug("Finding primary vendor: templateId={}, version={}, type={}", masterTemplateId, templateVersion, vendorType);
        return repository.findPrimaryVendorMapping(masterTemplateId, templateVersion, vendorType)
                .map(this::toDto);
    }

    /**
     * Find active vendors for routing with failover support
     */
    public Flux<TemplateVendorMappingDto> findActiveVendorsForRouting(UUID masterTemplateId, Integer templateVersion, String vendorType) {
        log.debug("Finding active vendors for routing: templateId={}, version={}, type={}", masterTemplateId, templateVersion, vendorType);
        return repository.findActiveVendorsForRouting(masterTemplateId, templateVersion, vendorType)
                .map(this::toDto);
    }

    /**
     * Find vendor mappings with pagination and filters
     */
    public Flux<TemplateVendorMappingDto> findWithFilters(
            UUID masterTemplateId,
            String vendorType,
            String vendor,
            Boolean activeFlag,
            int page,
            int size) {
        log.debug("Finding vendor mappings with filters: templateId={}, type={}, vendor={}, active={}",
                masterTemplateId, vendorType, vendor, activeFlag);
        long offset = (long) page * size;
        return repository.findWithFilters(masterTemplateId, vendorType, vendor, activeFlag, size, offset)
                .map(this::toDto);
    }

    /**
     * Count vendor mappings with filters
     */
    public Mono<Long> countWithFilters(UUID masterTemplateId, String vendorType, String vendor, Boolean activeFlag) {
        return repository.countWithFilters(masterTemplateId, vendorType, vendor, activeFlag);
    }

    /**
     * Save a new vendor mapping
     */
    public Mono<TemplateVendorMappingDto> save(TemplateVendorMappingEntity entity) {
        log.debug("Saving vendor mapping: templateId={}, vendor={}", entity.getMasterTemplateId(), entity.getVendor());
        return repository.save(entity)
                .map(this::toDto)
                .doOnNext(dto -> {
                    vendorCache.put(dto.getTemplateVendorId(), dto);
                    invalidateListCache(dto.getMasterTemplateId());
                });
    }

    /**
     * Update an existing vendor mapping
     */
    public Mono<TemplateVendorMappingDto> update(TemplateVendorMappingEntity entity) {
        log.debug("Updating vendor mapping: id={}", entity.getTemplateVendorId());
        return repository.save(entity)
                .map(this::toDto)
                .doOnNext(dto -> {
                    vendorCache.put(dto.getTemplateVendorId(), dto);
                    invalidateListCache(dto.getMasterTemplateId());
                });
    }

    /**
     * Archive (soft delete) a vendor mapping
     */
    public Mono<Integer> archiveVendorMapping(UUID vendorId, String updatedBy) {
        log.debug("Archiving vendor mapping: id={}", vendorId);
        return repository.findByVendorId(vendorId)
                .flatMap(entity -> repository.archiveVendorMapping(vendorId, updatedBy)
                        .doOnNext(count -> {
                            if (count > 0) {
                                vendorCache.invalidate(vendorId);
                                invalidateListCache(entity.getMasterTemplateId());
                            }
                        }))
                .switchIfEmpty(Mono.just(0));
    }

    /**
     * Update vendor health status
     */
    public Mono<Integer> updateVendorStatus(UUID vendorId, String vendorStatus, String healthStatus) {
        log.debug("Updating vendor status: id={}, status={}, health={}", vendorId, vendorStatus, healthStatus);
        return repository.updateVendorStatus(vendorId, vendorStatus, healthStatus)
                .doOnNext(count -> {
                    if (count > 0) {
                        vendorCache.invalidate(vendorId);
                    }
                });
    }

    /**
     * Check for duplicate vendor mapping
     */
    public Mono<Boolean> existsDuplicateMapping(UUID masterTemplateId, Integer templateVersion, String vendor, String vendorType) {
        return repository.existsDuplicateMapping(masterTemplateId, templateVersion, vendor, vendorType);
    }

    /**
     * Invalidate cache for a specific vendor
     */
    public void invalidateCache(UUID vendorId) {
        vendorCache.invalidate(vendorId);
        log.info("Vendor cache invalidated: id={}", vendorId);
    }

    /**
     * Invalidate list cache for a template
     */
    public void invalidateListCache(UUID masterTemplateId) {
        vendorListCache.asMap().keySet().removeIf(key -> key.startsWith(masterTemplateId.toString()));
        log.info("Vendor list cache invalidated for template: {}", masterTemplateId);
    }

    /**
     * Invalidate all cache
     */
    public void invalidateAllCache() {
        vendorCache.invalidateAll();
        vendorListCache.invalidateAll();
        log.info("All vendor cache invalidated");
    }

    /**
     * Get cache statistics
     */
    public CacheStats getCacheStats() {
        var stats = vendorCache.stats();
        return new CacheStats(
                vendorCache.estimatedSize(),
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate()
        );
    }

    public record CacheStats(long size, long hitCount, long missCount, double hitRate) {}

    private TemplateVendorMappingDto toDto(TemplateVendorMappingEntity entity) {
        return TemplateVendorMappingDto.builder()
                .templateVendorId(entity.getTemplateVendorId())
                .masterTemplateId(entity.getMasterTemplateId())
                .templateVersion(entity.getTemplateVersion())
                .vendor(entity.getVendor())
                .vendorTemplateKey(entity.getVendorTemplateKey())
                .vendorTemplateName(entity.getVendorTemplateName())
                .referenceKeyType(entity.getReferenceKeyType())
                .consumerId(entity.getConsumerId())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .vendorMappingVersion(entity.getVendorMappingVersion())
                .primaryFlag(entity.getPrimaryFlag())
                .activeFlag(entity.getActiveFlag())
                .templateStatus(entity.getTemplateStatus())
                .schemaInfo(jsonNodeToMap(entity.getSchemaInfo()))
                .templateFields(jsonNodeToMap(entity.getTemplateFields()))
                .vendorConfig(jsonNodeToMap(entity.getVendorConfig()))
                .apiConfig(jsonNodeToMap(entity.getApiConfig()))
                .createdBy(entity.getCreatedBy())
                .createdTimestamp(entity.getCreatedTimestamp())
                .updatedBy(entity.getUpdatedBy())
                .updatedTimestamp(entity.getUpdatedTimestamp())
                .recordStatus(entity.getRecordStatus())
                .vendorType(entity.getVendorType())
                .priorityOrder(entity.getPriorityOrder())
                .supportedRegions(entity.getSupportedRegions() != null ? Arrays.asList(entity.getSupportedRegions()) : null)
                .vendorStatus(entity.getVendorStatus())
                .rateLimitPerMinute(entity.getRateLimitPerMinute())
                .rateLimitPerDay(entity.getRateLimitPerDay())
                .timeoutMs(entity.getTimeoutMs())
                .maxRetryAttempts(entity.getMaxRetryAttempts())
                .retryBackoffMs(entity.getRetryBackoffMs())
                .costPerUnit(entity.getCostPerUnit())
                .costUnit(entity.getCostUnit())
                .supportedFormats(entity.getSupportedFormats() != null ? Arrays.asList(entity.getSupportedFormats()) : null)
                .lastHealthCheck(entity.getLastHealthCheck())
                .lastHealthStatus(entity.getLastHealthStatus())
                .healthCheckEndpoint(entity.getHealthCheckEndpoint())
                .build();
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to convert JsonNode to map: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Convert DTO back to entity for persistence
     */
    public TemplateVendorMappingEntity toEntity(TemplateVendorMappingDto dto) {
        return TemplateVendorMappingEntity.builder()
                .templateVendorId(dto.getTemplateVendorId())
                .masterTemplateId(dto.getMasterTemplateId())
                .templateVersion(dto.getTemplateVersion())
                .vendor(dto.getVendor())
                .vendorTemplateKey(dto.getVendorTemplateKey())
                .vendorTemplateName(dto.getVendorTemplateName())
                .referenceKeyType(dto.getReferenceKeyType())
                .consumerId(dto.getConsumerId())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .vendorMappingVersion(dto.getVendorMappingVersion())
                .primaryFlag(dto.getPrimaryFlag())
                .activeFlag(dto.getActiveFlag())
                .templateStatus(dto.getTemplateStatus())
                .schemaInfo(mapToJsonNode(dto.getSchemaInfo()))
                .templateFields(mapToJsonNode(dto.getTemplateFields()))
                .vendorConfig(mapToJsonNode(dto.getVendorConfig()))
                .apiConfig(mapToJsonNode(dto.getApiConfig()))
                .createdBy(dto.getCreatedBy())
                .createdTimestamp(dto.getCreatedTimestamp() != null ? dto.getCreatedTimestamp() : LocalDateTime.now())
                .updatedBy(dto.getUpdatedBy())
                .updatedTimestamp(LocalDateTime.now())
                .recordStatus(dto.getRecordStatus())
                .vendorType(dto.getVendorType())
                .priorityOrder(dto.getPriorityOrder())
                .supportedRegions(dto.getSupportedRegions() != null ? dto.getSupportedRegions().toArray(new String[0]) : null)
                .vendorStatus(dto.getVendorStatus())
                .rateLimitPerMinute(dto.getRateLimitPerMinute())
                .rateLimitPerDay(dto.getRateLimitPerDay())
                .timeoutMs(dto.getTimeoutMs())
                .maxRetryAttempts(dto.getMaxRetryAttempts())
                .retryBackoffMs(dto.getRetryBackoffMs())
                .costPerUnit(dto.getCostPerUnit())
                .costUnit(dto.getCostUnit())
                .supportedFormats(dto.getSupportedFormats() != null ? dto.getSupportedFormats().toArray(new String[0]) : null)
                .lastHealthCheck(dto.getLastHealthCheck())
                .lastHealthStatus(dto.getLastHealthStatus())
                .healthCheckEndpoint(dto.getHealthCheckEndpoint())
                .archiveIndicator(false)
                .build();
    }

    private JsonNode mapToJsonNode(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        try {
            return objectMapper.valueToTree(map);
        } catch (Exception e) {
            log.warn("Failed to convert map to JsonNode: {}", e.getMessage());
            return null;
        }
    }
}
