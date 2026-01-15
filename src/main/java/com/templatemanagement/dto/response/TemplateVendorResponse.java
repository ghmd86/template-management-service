package com.templatemanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.templatemanagement.dto.MasterTemplateDto;
import com.templatemanagement.dto.TemplateVendorMappingDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for vendor mapping operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateVendorResponse {

    private TemplateVendorMappingDto vendorMapping;
    private MasterTemplateDto templateDetails;

    public static TemplateVendorResponse of(TemplateVendorMappingDto vendorMapping) {
        return TemplateVendorResponse.builder()
                .vendorMapping(vendorMapping)
                .build();
    }

    public static TemplateVendorResponse of(TemplateVendorMappingDto vendorMapping, MasterTemplateDto templateDetails) {
        return TemplateVendorResponse.builder()
                .vendorMapping(vendorMapping)
                .templateDetails(templateDetails)
                .build();
    }
}
