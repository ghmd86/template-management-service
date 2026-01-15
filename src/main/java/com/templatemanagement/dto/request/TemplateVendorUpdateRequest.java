package com.templatemanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for updating a vendor mapping
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateVendorUpdateRequest {

    private String vendorTemplateKey;
    private String vendorTemplateName;
    private String referenceKeyType;
    private UUID consumerId;
    private Long startDate;
    private Long endDate;
    private Boolean primaryFlag;
    private Boolean activeFlag;
    private String templateStatus;
    private Integer priorityOrder;
    private Map<String, Object> schemaInfo;
    private Map<String, Object> templateFields;
    private Map<String, Object> vendorConfig;
    private Map<String, Object> apiConfig;
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
    private String healthCheckEndpoint;
}
