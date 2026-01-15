package com.templatemanagement.service;

import com.templatemanagement.dao.MasterTemplateDao;
import com.templatemanagement.dao.TemplateVendorMappingDao;
import com.templatemanagement.dto.MasterTemplateDto;
import com.templatemanagement.dto.TemplateVendorMappingDto;
import com.templatemanagement.dto.request.TemplateCreateRequest;
import com.templatemanagement.dto.request.TemplateUpdateRequest;
import com.templatemanagement.dto.request.TemplateVendorCreateRequest;
import com.templatemanagement.dto.response.TemplatePageResponse;
import com.templatemanagement.dto.response.TemplateResponse;
import com.templatemanagement.dto.response.TemplateVendorPageResponse;
import com.templatemanagement.dto.response.TemplateVendorResponse;
import com.templatemanagement.entity.MasterTemplateDefinitionEntity;
import com.templatemanagement.entity.TemplateVendorMappingEntity;
import com.templatemanagement.exception.ConflictException;
import com.templatemanagement.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateService Tests")
class TemplateServiceTest {

    @Mock
    private MasterTemplateDao templateDao;

    @Mock
    private TemplateVendorMappingDao vendorDao;

    @InjectMocks
    private TemplateService templateService;

    private UUID templateId;
    private UUID vendorId;
    private MasterTemplateDto sampleTemplate;
    private TemplateVendorMappingDto sampleVendor;

    @BeforeEach
    void setUp() {
        templateId = UUID.randomUUID();
        vendorId = UUID.randomUUID();

        sampleTemplate = MasterTemplateDto.builder()
                .masterTemplateId(templateId)
                .templateVersion(1)
                .templateType("STATEMENT")
                .lineOfBusiness("CREDIT_CARD")
                .displayName("Monthly Statement")
                .activeFlag(true)
                .createdBy("test-user")
                .createdTimestamp(LocalDateTime.now())
                .build();

        sampleVendor = TemplateVendorMappingDto.builder()
                .templateVendorId(vendorId)
                .masterTemplateId(templateId)
                .templateVersion(1)
                .vendor("SmartComm")
                .vendorType("GENERATION")
                .vendorTemplateKey("SC-001")
                .activeFlag(true)
                .createdBy("test-user")
                .createdTimestamp(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Template Operations")
    class TemplateOperations {

        @Test
        @DisplayName("Should create template successfully")
        void createTemplate_Success() {
            TemplateCreateRequest request = TemplateCreateRequest.builder()
                    .templateType("STATEMENT")
                    .lineOfBusiness("CREDIT_CARD")
                    .displayName("Monthly Statement")
                    .startDate(System.currentTimeMillis())
                    .build();

            when(templateDao.existsByTemplateType(anyString())).thenReturn(Mono.just(false));
            when(templateDao.save(any(MasterTemplateDefinitionEntity.class))).thenReturn(Mono.just(sampleTemplate));

            StepVerifier.create(templateService.createTemplate(request, "test-user"))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.getTemplate()).isNotNull();
                        assertThat(response.getTemplate().getTemplateType()).isEqualTo("STATEMENT");
                    })
                    .verifyComplete();

            verify(templateDao).existsByTemplateType("STATEMENT");
            verify(templateDao).save(any(MasterTemplateDefinitionEntity.class));
        }

        @Test
        @DisplayName("Should throw ConflictException when template type already exists")
        void createTemplate_Conflict() {
            TemplateCreateRequest request = TemplateCreateRequest.builder()
                    .templateType("STATEMENT")
                    .lineOfBusiness("CREDIT_CARD")
                    .displayName("Monthly Statement")
                    .startDate(System.currentTimeMillis())
                    .build();

            when(templateDao.existsByTemplateType(anyString())).thenReturn(Mono.just(true));

            StepVerifier.create(templateService.createTemplate(request, "test-user"))
                    .expectError(ConflictException.class)
                    .verify();

            verify(templateDao).existsByTemplateType("STATEMENT");
            verify(templateDao, never()).save(any());
        }

        @Test
        @DisplayName("Should get template by ID and version successfully")
        void getTemplateByIdAndVersion_Success() {
            when(templateDao.findByIdAndVersion(templateId, 1)).thenReturn(Mono.just(sampleTemplate));

            StepVerifier.create(templateService.getTemplateByIdAndVersion(templateId, 1, false))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.getTemplate().getMasterTemplateId()).isEqualTo(templateId);
                        assertThat(response.getTemplate().getTemplateVersion()).isEqualTo(1);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when template not found")
        void getTemplateByIdAndVersion_NotFound() {
            when(templateDao.findByIdAndVersion(templateId, 1)).thenReturn(Mono.empty());

            StepVerifier.create(templateService.getTemplateByIdAndVersion(templateId, 1, false))
                    .expectError(ResourceNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should list templates with pagination")
        void listTemplates_Success() {
            when(templateDao.countWithFilters(any(), any(), any(), any())).thenReturn(Mono.just(1L));
            when(templateDao.findWithFilters(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Flux.just(sampleTemplate));

            StepVerifier.create(templateService.listTemplates("CREDIT_CARD", null, null, null, 0, 20))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.getTemplates()).hasSize(1);
                        assertThat(response.getPagination().getTotalElements()).isEqualTo(1);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should update template successfully")
        void updateTemplate_Success() {
            TemplateUpdateRequest request = TemplateUpdateRequest.builder()
                    .displayName("Updated Statement")
                    .build();

            MasterTemplateDto updatedTemplate = MasterTemplateDto.builder()
                    .masterTemplateId(templateId)
                    .templateVersion(1)
                    .templateType("STATEMENT")
                    .displayName("Updated Statement")
                    .build();

            when(templateDao.findByIdAndVersion(templateId, 1)).thenReturn(Mono.just(sampleTemplate));
            when(templateDao.update(any(MasterTemplateDefinitionEntity.class))).thenReturn(Mono.just(updatedTemplate));

            StepVerifier.create(templateService.updateTemplate(templateId, 1, request, "test-user", false))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.getTemplate().getDisplayName()).isEqualTo("Updated Statement");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should delete template successfully")
        void deleteTemplate_Success() {
            when(templateDao.findByIdAndVersion(templateId, 1)).thenReturn(Mono.just(sampleTemplate));
            when(templateDao.archiveTemplate(templateId, 1, "test-user")).thenReturn(Mono.just(1));

            StepVerifier.create(templateService.deleteTemplate(templateId, 1, "test-user"))
                    .verifyComplete();

            verify(templateDao).archiveTemplate(templateId, 1, "test-user");
        }
    }

    @Nested
    @DisplayName("Vendor Mapping Operations")
    class VendorMappingOperations {

        @Test
        @DisplayName("Should create vendor mapping successfully")
        void createVendorMapping_Success() {
            TemplateVendorCreateRequest request = TemplateVendorCreateRequest.builder()
                    .masterTemplateId(templateId)
                    .templateVersion(1)
                    .vendor("SmartComm")
                    .vendorType("GENERATION")
                    .vendorTemplateKey("SC-001")
                    .build();

            when(templateDao.findByIdAndVersion(templateId, 1)).thenReturn(Mono.just(sampleTemplate));
            when(vendorDao.existsDuplicateMapping(templateId, 1, "SmartComm", "GENERATION"))
                    .thenReturn(Mono.just(false));
            when(vendorDao.save(any(TemplateVendorMappingEntity.class))).thenReturn(Mono.just(sampleVendor));

            StepVerifier.create(templateService.createVendorMapping(request, "test-user"))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.getVendorMapping().getVendor()).isEqualTo("SmartComm");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should throw ConflictException when vendor mapping already exists")
        void createVendorMapping_Conflict() {
            TemplateVendorCreateRequest request = TemplateVendorCreateRequest.builder()
                    .masterTemplateId(templateId)
                    .templateVersion(1)
                    .vendor("SmartComm")
                    .vendorType("GENERATION")
                    .vendorTemplateKey("SC-001")
                    .build();

            when(templateDao.findByIdAndVersion(templateId, 1)).thenReturn(Mono.just(sampleTemplate));
            when(vendorDao.existsDuplicateMapping(templateId, 1, "SmartComm", "GENERATION"))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(templateService.createVendorMapping(request, "test-user"))
                    .expectError(ConflictException.class)
                    .verify();

            verify(vendorDao, never()).save(any());
        }

        @Test
        @DisplayName("Should get vendor mapping by ID successfully")
        void getVendorMappingById_Success() {
            when(vendorDao.findById(vendorId)).thenReturn(Mono.just(sampleVendor));

            StepVerifier.create(templateService.getVendorMappingById(vendorId, false))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.getVendorMapping().getTemplateVendorId()).isEqualTo(vendorId);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should list vendor mappings with pagination")
        void listVendorMappings_Success() {
            when(vendorDao.countWithFilters(any(), any(), any(), any())).thenReturn(Mono.just(1L));
            when(vendorDao.findWithFilters(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Flux.just(sampleVendor));

            StepVerifier.create(templateService.listVendorMappings(templateId, null, null, null, 0, 20))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.getVendorMappings()).hasSize(1);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should get vendors for routing")
        void getVendorsForRouting_Success() {
            when(vendorDao.findActiveVendorsForRouting(templateId, 1, "GENERATION"))
                    .thenReturn(Flux.just(sampleVendor));

            StepVerifier.create(templateService.getVendorsForRouting(templateId, 1, "GENERATION"))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.getVendorMappings()).hasSize(1);
                        assertThat(response.getVendorMappings().get(0).getVendor()).isEqualTo("SmartComm");
                    })
                    .verifyComplete();
        }
    }
}
