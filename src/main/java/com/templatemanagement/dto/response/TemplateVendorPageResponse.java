package com.templatemanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.templatemanagement.dto.TemplateVendorMappingDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated response for vendor mapping list operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateVendorPageResponse {

    private List<TemplateVendorMappingDto> vendorMappings;
    private PaginationResponse pagination;

    public static TemplateVendorPageResponse of(List<TemplateVendorMappingDto> vendorMappings, PaginationResponse pagination) {
        return TemplateVendorPageResponse.builder()
                .vendorMappings(vendorMappings)
                .pagination(pagination)
                .build();
    }
}
