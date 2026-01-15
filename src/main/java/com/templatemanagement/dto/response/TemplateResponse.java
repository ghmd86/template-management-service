package com.templatemanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.templatemanagement.dto.MasterTemplateDto;
import com.templatemanagement.dto.TemplateVendorMappingDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for template operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateResponse {

    private MasterTemplateDto template;
    private List<TemplateVendorMappingDto> vendorMappings;

    public static TemplateResponse of(MasterTemplateDto template) {
        return TemplateResponse.builder()
                .template(template)
                .build();
    }

    public static TemplateResponse of(MasterTemplateDto template, List<TemplateVendorMappingDto> vendorMappings) {
        return TemplateResponse.builder()
                .template(template)
                .vendorMappings(vendorMappings)
                .build();
    }
}
