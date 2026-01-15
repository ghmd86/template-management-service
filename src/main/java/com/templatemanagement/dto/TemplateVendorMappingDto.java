package com.templatemanagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for Template Vendor Mapping
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateVendorMappingDto {

    private UUID templateVendorId;
    private UUID masterTemplateId;
    private Integer templateVersion;
    private String vendor;
    private String vendorTemplateKey;
    private String vendorTemplateName;
    private String referenceKeyType;
    private UUID consumerId;
    private Long startDate;
    private Long endDate;
    private Integer vendorMappingVersion;
    private Boolean primaryFlag;
    private Boolean activeFlag;
    private String templateStatus;
    private Map<String, Object> schemaInfo;
    private Map<String, Object> templateFields;
    private Map<String, Object> vendorConfig;
    private Map<String, Object> apiConfig;
    private String createdBy;
    private LocalDateTime createdTimestamp;
    private String updatedBy;
    private LocalDateTime updatedTimestamp;
    private String recordStatus;
    private String vendorType;
    private Integer priorityOrder;
    private List<String> supportedRegions;
    private String vendorStatus;
    private Integer rateLimitPerMinute;
    private Integer rateLimitPerDay;
    private Integer timeoutMs;
    private Integer maxRetryAttempts;
    private Integer retryBackoffMs;
    private BigDecimal costPerUnit;
    private String costUnit;
    private List<String> supportedFormats;
    private LocalDateTime lastHealthCheck;
    private String lastHealthStatus;
    private String healthCheckEndpoint;
}
