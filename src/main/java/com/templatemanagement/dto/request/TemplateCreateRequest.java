package com.templatemanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request DTO for creating a new template
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateCreateRequest {

    @NotBlank(message = "Template type is required")
    private String templateType;

    @NotBlank(message = "Line of business is required")
    private String lineOfBusiness;

    @NotBlank(message = "Display name is required")
    private String displayName;

    private String templateName;
    private String templateDescription;
    private String templateCategory;
    private String languageCode;
    private String owningDept;
    private Boolean notificationNeeded;
    private Boolean regulatoryFlag;
    private Boolean messageCenterDocFlag;
    private Boolean sharedDocumentFlag;
    private String sharingScope;
    private Map<String, Object> templateVariables;
    private Map<String, Object> dataExtractionConfig;
    private Map<String, Object> documentMatchingConfig;
    private Map<String, Object> eligibilityCriteria;
    private Map<String, Object> accessControl;
    private Map<String, Object> requiredFields;
    private Map<String, Object> templateConfig;

    @NotNull(message = "Start date is required")
    private Long startDate;

    private Long endDate;
    private String communicationType;
    private String workflow;
    private Boolean singleDocumentFlag;
    private String legacyTemplateId;
    private String legacyTemplateName;
}
