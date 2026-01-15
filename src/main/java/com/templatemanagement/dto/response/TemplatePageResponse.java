package com.templatemanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.templatemanagement.dto.MasterTemplateDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated response for template list operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplatePageResponse {

    private List<MasterTemplateDto> templates;
    private PaginationResponse pagination;

    public static TemplatePageResponse of(List<MasterTemplateDto> templates, PaginationResponse pagination) {
        return TemplatePageResponse.builder()
                .templates(templates)
                .pagination(pagination)
                .build();
    }
}
