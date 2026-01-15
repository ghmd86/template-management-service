package com.templatemanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for updating a template
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateUpdateRequest {

    private String templateName;
    private String templateDescription;
    private String displayName;
    private String templateCategory;
    private String languageCode;
    private String owningDept;
    private Boolean notificationNeeded;
    private Boolean regulatoryFlag;
    private Boolean messageCenterDocFlag;
    private Boolean activeFlag;
    private Boolean sharedDocumentFlag;
    private String sharingScope;
    private Map<String, Object> templateVariables;
    private Map<String, Object> dataExtractionConfig;
    private Map<String, Object> documentMatchingConfig;
    private Map<String, Object> eligibilityCriteria;
    private Map<String, Object> accessControl;
    private Map<String, Object> requiredFields;
    private Map<String, Object> templateConfig;
    private Long startDate;
    private Long endDate;
    private String communicationType;
    private String workflow;
    private Boolean singleDocumentFlag;
    private String recordStatus;
}
