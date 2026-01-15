package com.templatemanagement.repository;

import com.templatemanagement.entity.TemplateVendorMappingEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repository for Template Vendor Mapping
 */
@Repository
public interface TemplateVendorMappingRepository extends R2dbcRepository<TemplateVendorMappingEntity, UUID> {

    /**
     * Find vendor mapping by ID (non-archived)
     */
    @Query("SELECT * FROM document_hub.template_vendor_mapping " +
           "WHERE template_vendor_id = :vendorId " +
           "AND archive_indicator = false")
    Mono<TemplateVendorMappingEntity> findByVendorId(UUID vendorId);

    /**
     * Find all vendor mappings for a template
     */
    @Query("SELECT * FROM document_hub.template_vendor_mapping " +
           "WHERE master_template_id = :masterTemplateId " +
           "AND archive_indicator = false " +
           "ORDER BY priority_order ASC")
    Flux<TemplateVendorMappingEntity> findByMasterTemplateId(UUID masterTemplateId);

    /**
     * Find vendor mappings for a specific template version
     */
    @Query("SELECT * FROM document_hub.template_vendor_mapping " +
           "WHERE master_template_id = :masterTemplateId " +
           "AND template_version = :templateVersion " +
           "AND archive_indicator = false " +
           "ORDER BY priority_order ASC")
    Flux<TemplateVendorMappingEntity> findByMasterTemplateIdAndVersion(
        UUID masterTemplateId,
        Integer templateVersion
    );

    /**
     * Find vendor mappings by vendor type
     */
    @Query("SELECT * FROM document_hub.template_vendor_mapping " +
           "WHERE master_template_id = :masterTemplateId " +
           "AND vendor_type = :vendorType " +
           "AND archive_indicator = false " +
           "ORDER BY priority_order ASC")
    Flux<TemplateVendorMappingEntity> findByMasterTemplateIdAndVendorType(
        UUID masterTemplateId,
        String vendorType
    );

    /**
     * Find primary vendor mapping for a template and vendor type
     */
    @Query("SELECT * FROM document_hub.template_vendor_mapping " +
           "WHERE master_template_id = :masterTemplateId " +
           "AND template_version = :templateVersion " +
           "AND vendor_type = :vendorType " +
           "AND primary_flag = true " +
           "AND active_flag = true " +
           "AND archive_indicator = false")
    Mono<TemplateVendorMappingEntity> findPrimaryVendorMapping(
        UUID masterTemplateId,
        Integer templateVersion,
        String vendorType
    );

    /**
     * Find active vendor mappings for routing with priority order
     */
    @Query("SELECT * FROM document_hub.template_vendor_mapping " +
           "WHERE master_template_id = :masterTemplateId " +
           "AND template_version = :templateVersion " +
           "AND vendor_type = :vendorType " +
           "AND active_flag = true " +
           "AND archive_indicator = false " +
           "AND (vendor_status IS NULL OR vendor_status IN ('ACTIVE', 'DEGRADED')) " +
           "ORDER BY priority_order ASC")
    Flux<TemplateVendorMappingEntity> findActiveVendorsForRouting(
        UUID masterTemplateId,
        Integer templateVersion,
        String vendorType
    );

    /**
     * Find vendor mappings with pagination
     */
    @Query("SELECT * FROM document_hub.template_vendor_mapping " +
           "WHERE archive_indicator = false " +
           "AND (:masterTemplateId IS NULL OR master_template_id = :masterTemplateId) " +
           "AND (:vendorType IS NULL OR vendor_type = :vendorType) " +
           "AND (:vendor IS NULL OR vendor = :vendor) " +
           "AND (:activeFlag IS NULL OR active_flag = :activeFlag) " +
           "ORDER BY created_timestamp DESC " +
           "LIMIT :limit OFFSET :offset")
    Flux<TemplateVendorMappingEntity> findWithFilters(
        UUID masterTemplateId,
        String vendorType,
        String vendor,
        Boolean activeFlag,
        int limit,
        long offset
    );

    /**
     * Count vendor mappings with filters
     */
    @Query("SELECT COUNT(*) FROM document_hub.template_vendor_mapping " +
           "WHERE archive_indicator = false " +
           "AND (:masterTemplateId IS NULL OR master_template_id = :masterTemplateId) " +
           "AND (:vendorType IS NULL OR vendor_type = :vendorType) " +
           "AND (:vendor IS NULL OR vendor = :vendor) " +
           "AND (:activeFlag IS NULL OR active_flag = :activeFlag)")
    Mono<Long> countWithFilters(
        UUID masterTemplateId,
        String vendorType,
        String vendor,
        Boolean activeFlag
    );

    /**
     * Soft delete (archive) a vendor mapping
     */
    @Query("UPDATE document_hub.template_vendor_mapping " +
           "SET archive_indicator = true, " +
           "archive_timestamp = NOW(), " +
           "updated_by = :updatedBy, " +
           "updated_timestamp = NOW() " +
           "WHERE template_vendor_id = :vendorId")
    Mono<Integer> archiveVendorMapping(UUID vendorId, String updatedBy);

    /**
     * Update vendor status (for health monitoring)
     */
    @Query("UPDATE document_hub.template_vendor_mapping " +
           "SET vendor_status = :vendorStatus, " +
           "last_health_check = NOW(), " +
           "last_health_status = :healthStatus, " +
           "updated_timestamp = NOW() " +
           "WHERE template_vendor_id = :vendorId")
    Mono<Integer> updateVendorStatus(
        UUID vendorId,
        String vendorStatus,
        String healthStatus
    );

    /**
     * Check for duplicate vendor mapping
     */
    @Query("SELECT COUNT(*) > 0 FROM document_hub.template_vendor_mapping " +
           "WHERE master_template_id = :masterTemplateId " +
           "AND template_version = :templateVersion " +
           "AND vendor = :vendor " +
           "AND vendor_type = :vendorType " +
           "AND archive_indicator = false")
    Mono<Boolean> existsDuplicateMapping(
        UUID masterTemplateId,
        Integer templateVersion,
        String vendor,
        String vendorType
    );
}
