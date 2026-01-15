package com.templatemanagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for Master Template Definition
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MasterTemplateDto {

    private UUID masterTemplateId;
    private Integer templateVersion;
    private String legacyTemplateId;
    private String legacyTemplateName;
    private String templateName;
    private String templateDescription;
    private String lineOfBusiness;
    private String templateCategory;
    private String templateType;
    private String languageCode;
    private String owningDept;
    private Boolean notificationNeeded;
    private Boolean regulatoryFlag;
    private Boolean messageCenterDocFlag;
    private String displayName;
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
    private String createdBy;
    private LocalDateTime createdTimestamp;
    private String updatedBy;
    private LocalDateTime updatedTimestamp;
    private String recordStatus;
    private String communicationType;
    private String workflow;
    private Boolean singleDocumentFlag;
}
