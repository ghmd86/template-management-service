package com.templatemanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for creating a vendor mapping
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateVendorCreateRequest {

    @NotNull(message = "Master template ID is required")
    private UUID masterTemplateId;

    @NotNull(message = "Template version is required")
    private Integer templateVersion;

    @NotBlank(message = "Vendor is required")
    private String vendor;

    @NotBlank(message = "Vendor type is required")
    private String vendorType;

    @NotBlank(message = "Vendor template key is required")
    private String vendorTemplateKey;

    private String vendorTemplateName;
    private String referenceKeyType;
    private UUID consumerId;
    private Long startDate;
    private Long endDate;
    private Boolean primaryFlag;
    private Integer priorityOrder;
    private Map<String, Object> schemaInfo;
    private Map<String, Object> templateFields;
    private Map<String, Object> vendorConfig;
    private Map<String, Object> apiConfig;
    private List<String> supportedRegions;
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
