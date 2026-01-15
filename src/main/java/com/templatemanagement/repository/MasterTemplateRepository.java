package com.templatemanagement.repository;

import com.templatemanagement.entity.MasterTemplateDefinitionEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Master Template Definition
 */
@Repository
public interface MasterTemplateRepository extends R2dbcRepository<MasterTemplateDefinitionEntity, UUID> {

    /**
     * Find template by master template ID and version
     */
    @Query("SELECT * FROM document_hub.master_template_definition " +
           "WHERE master_template_id = :masterTemplateId " +
           "AND template_version = :templateVersion " +
           "AND archive_indicator = false")
    Mono<MasterTemplateDefinitionEntity> findByMasterTemplateIdAndVersion(
        UUID masterTemplateId,
        Integer templateVersion
    );

    /**
     * Find all versions of a template by master template ID
     */
    @Query("SELECT * FROM document_hub.master_template_definition " +
           "WHERE master_template_id = :masterTemplateId " +
           "AND archive_indicator = false " +
           "ORDER BY template_version DESC")
    Flux<MasterTemplateDefinitionEntity> findAllVersionsByMasterTemplateId(UUID masterTemplateId);

    /**
     * Find all active templates within date range
     */
    @Query("SELECT * FROM document_hub.master_template_definition " +
           "WHERE active_flag = true " +
           "AND archive_indicator = false " +
           "AND (start_date IS NULL OR start_date <= :currentDate) " +
           "AND (end_date IS NULL OR end_date >= :currentDate)")
    Flux<MasterTemplateDefinitionEntity> findActiveTemplates(Long currentDate);

    /**
     * Find templates by type (non-archived)
     */
    @Query("SELECT * FROM document_hub.master_template_definition " +
           "WHERE template_type = :templateType " +
           "AND archive_indicator = false")
    Flux<MasterTemplateDefinitionEntity> findByTemplateType(String templateType);

    /**
     * Find the latest active template by type
     */
    @Query("SELECT * FROM document_hub.master_template_definition " +
           "WHERE template_type = :templateType " +
           "AND active_flag = true " +
           "AND archive_indicator = false " +
           "AND (start_date IS NULL OR start_date <= :currentDate) " +
           "AND (end_date IS NULL OR end_date >= :currentDate) " +
           "ORDER BY template_version DESC " +
           "LIMIT 1")
    Mono<MasterTemplateDefinitionEntity> findLatestActiveTemplateByType(
        String templateType,
        Long currentDate
    );

    /**
     * Find template by type and version
     */
    @Query("SELECT * FROM document_hub.master_template_definition " +
           "WHERE template_type = :templateType " +
           "AND template_version = :templateVersion " +
           "AND archive_indicator = false")
    Mono<MasterTemplateDefinitionEntity> findByTemplateTypeAndVersion(
        String templateType,
        Integer templateVersion
    );

    /**
     * Find active templates by line of business
     */
    @Query("SELECT * FROM document_hub.master_template_definition " +
           "WHERE active_flag = true " +
           "AND archive_indicator = false " +
           "AND (line_of_business = :lineOfBusiness OR line_of_business = 'ENTERPRISE') " +
           "AND (start_date IS NULL OR start_date <= :currentDate) " +
           "AND (end_date IS NULL OR end_date >= :currentDate)")
    Flux<MasterTemplateDefinitionEntity> findActiveTemplatesByLineOfBusiness(
        String lineOfBusiness,
        Long currentDate
    );

    /**
     * Find templates with pagination support
     */
    @Query("SELECT * FROM document_hub.master_template_definition " +
           "WHERE archive_indicator = false " +
           "AND (:lineOfBusiness IS NULL OR line_of_business = :lineOfBusiness OR line_of_business = 'ENTERPRISE') " +
           "AND (:templateType IS NULL OR template_type = :templateType) " +
           "AND (:activeFlag IS NULL OR active_flag = :activeFlag) " +
           "AND (:communicationType IS NULL OR communication_type = :communicationType) " +
           "ORDER BY created_timestamp DESC " +
           "LIMIT :limit OFFSET :offset")
    Flux<MasterTemplateDefinitionEntity> findWithFilters(
        String lineOfBusiness,
        String templateType,
        Boolean activeFlag,
        String communicationType,
        int limit,
        long offset
    );

    /**
     * Count templates with filters for pagination
     */
    @Query("SELECT COUNT(*) FROM document_hub.master_template_definition " +
           "WHERE archive_indicator = false " +
           "AND (:lineOfBusiness IS NULL OR line_of_business = :lineOfBusiness OR line_of_business = 'ENTERPRISE') " +
           "AND (:templateType IS NULL OR template_type = :templateType) " +
           "AND (:activeFlag IS NULL OR active_flag = :activeFlag) " +
           "AND (:communicationType IS NULL OR communication_type = :communicationType)")
    Mono<Long> countWithFilters(
        String lineOfBusiness,
        String templateType,
        Boolean activeFlag,
        String communicationType
    );

    /**
     * Get the next version number for a template
     */
    @Query("SELECT COALESCE(MAX(template_version), 0) + 1 " +
           "FROM document_hub.master_template_definition " +
           "WHERE master_template_id = :masterTemplateId")
    Mono<Integer> getNextVersionNumber(UUID masterTemplateId);

    /**
     * Soft delete (archive) a template version
     */
    @Query("UPDATE document_hub.master_template_definition " +
           "SET archive_indicator = true, " +
           "archive_timestamp = NOW(), " +
           "updated_by = :updatedBy, " +
           "updated_timestamp = NOW() " +
           "WHERE master_template_id = :masterTemplateId " +
           "AND template_version = :templateVersion")
    Mono<Integer> archiveTemplateVersion(
        UUID masterTemplateId,
        Integer templateVersion,
        String updatedBy
    );

    /**
     * Check if template exists by type (for duplicate prevention)
     */
    @Query("SELECT COUNT(*) > 0 FROM document_hub.master_template_definition " +
           "WHERE template_type = :templateType " +
           "AND archive_indicator = false")
    Mono<Boolean> existsByTemplateType(String templateType);
}
