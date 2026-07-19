package com.weiver.jobposting.service;

import com.weiver.company.domain.Company;
import com.weiver.company.repository.CompanyRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.s3.service.S3Service;
import com.weiver.jobposting.domain.EmailTemplate;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.dto.request.JobPostingRequestDTO;
import com.weiver.jobposting.dto.request.JobPostingUpdateDTO;
import com.weiver.jobposting.dto.response.JobPostingPageResponseDTO;
import com.weiver.jobposting.event.JobPostingEventService;
import com.weiver.jobposting.repository.EmailTemplateRepository;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.type.JobPostingStatus;
import com.weiver.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobPostingServiceTest {

    @InjectMocks
    private JobPostingService jobPostingService;

    @Mock private EmailTemplateRepository emailTemplateRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private S3Service s3Service;
    @Mock private JobPostingEventService jobPostingEventService;

    @Test
    @DisplayName("공고 생성 성공: 임시저장이면 상태가 DRAFT이고 이미지가 없으면 S3를 호출하지 않는다")
    void saveJobPosting_Draft_NoImage() {
        // given
        String publicId = "2222";
        JobPostingRequestDTO requestDTO = mock(JobPostingRequestDTO.class);
        Company company = mock(Company.class);
        JobPosting jobPosting = mock(JobPosting.class);
        EmailTemplate emailTemplate = mock(EmailTemplate.class);

        given(companyRepository.findByPublicId(publicId)).willReturn(Optional.of(company));
        given(requestDTO.toJobPosting(company, JobPostingStatus.DRAFT)).willReturn(jobPosting);
        given(jobPostingRepository.save(jobPosting)).willReturn(jobPosting);
        given(requestDTO.toEmailTemplate(jobPosting, null)).willReturn(emailTemplate);

        // when
        jobPostingService.saveJobPosting(true, publicId, requestDTO, null);

        // then
        verify(s3Service, never()).publicUpload(any(), any()); // 이미지가 없으므로 S3 업로드 미호출
        verify(jobPostingRepository, times(1)).save(jobPosting);
        verify(emailTemplateRepository, times(1)).save(emailTemplate);
    }

    @Test
    @DisplayName("공고 생성 성공: isTemp가 null이면 ACTIVE로 저장하고 JD 분석 요청을 발행한다")
    void saveJobPosting_NullIsTemp_RequestsJdAnalysis() {
        // given
        String publicId = "2222";
        JobPostingRequestDTO requestDTO = mock(JobPostingRequestDTO.class);
        Company company = mock(Company.class);
        JobPosting jobPosting = mock(JobPosting.class);
        EmailTemplate emailTemplate = mock(EmailTemplate.class);

        given(companyRepository.findByPublicId(publicId)).willReturn(Optional.of(company));
        given(requestDTO.toJobPosting(company, JobPostingStatus.ACTIVE)).willReturn(jobPosting);
        given(jobPostingRepository.save(jobPosting)).willReturn(jobPosting);
        given(requestDTO.toEmailTemplate(jobPosting, null)).willReturn(emailTemplate);

        // when
        jobPostingService.saveJobPosting(null, publicId, requestDTO, null);

        // then
        verify(jobPostingRepository).save(jobPosting);
        verify(emailTemplateRepository).save(emailTemplate);
        verify(jobPostingEventService).publishJdAnalysisRequested(jobPosting);
    }

    @Test
    @DisplayName("공고 생성 실패: 존재하지 않는 회사 ID인 경우 예외 발생")
    void saveJobPosting_CompanyNotFound() {
        // given
        String publicId = "2222";
        JobPostingRequestDTO requestDTO = mock(JobPostingRequestDTO.class);
        given(companyRepository.findByPublicId(publicId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> jobPostingService.saveJobPosting(false, publicId, requestDTO, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("공고 수정 실패: 다른 회사의 공고를 수정하려고 하면 권한 없음 예외 발생")
    void updateJobPosting_Forbidden() {
        // given
        Long targetJdId = 100L;

        String requesterPublicId = "2222";
        String ownerPublicId = "1111";

        JobPostingUpdateDTO updateDTO = mock(JobPostingUpdateDTO.class);
        EmailTemplate emailTemplate = mock(EmailTemplate.class);
        JobPosting jobPosting = mock(JobPosting.class);
        Company ownerCompany = mock(Company.class);

        given(emailTemplateRepository.findWithJobPostingByJdId(targetJdId))
                .willReturn(Optional.of(emailTemplate));

        given(emailTemplate.getJobPosting())
                .willReturn(jobPosting);

        given(jobPosting.getCompany())
                .willReturn(ownerCompany);

        given(ownerCompany.getPublicId())
                .willReturn(ownerPublicId);

        // when & then
        assertThatThrownBy(() ->
                jobPostingService.updateJobPosting(
                        targetJdId,
                        requesterPublicId,
                        updateDTO,
                        null
                )
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("공고 수정 권한이 없습니다");
    }

    @Test
    @DisplayName("공고 수정 성공: 기존 이미지가 있고 삭제 플래그가 true면 S3에서 삭제되어야 한다")
    void updateJobPosting_DeleteExistingImage() {
        // given
        Long jdId = 100L;
        String publicId = "2222";
        String existingImageUrl = "https://s3.url/old-image.png";

        JobPostingUpdateDTO updateDTO = mock(JobPostingUpdateDTO.class);
        given(updateDTO.isEmailBannerDeleted()).willReturn(true);

        EmailTemplate emailTemplate = mock(EmailTemplate.class);
        JobPosting jobPosting = mock(JobPosting.class);
        Company company = mock(Company.class);

        given(emailTemplateRepository.findWithJobPostingByJdId(jdId)).willReturn(Optional.of(emailTemplate));
        given(emailTemplate.getJobPosting()).willReturn(jobPosting);
        given(jobPosting.getCompany()).willReturn(company);
        given(company.getPublicId()).willReturn(publicId);
        given(emailTemplate.getEmailBannerUrl()).willReturn(existingImageUrl);

        // when
        jobPostingService.updateJobPosting(jdId, publicId, updateDTO, null);

        // then
        verify(s3Service, times(1)).deleteFile(existingImageUrl);
        verify(jobPosting, times(1)).updateJobPosting(updateDTO);
        verify(emailTemplate, times(1)).updateEmailTemplate(updateDTO, null);
    }

    @Test
    @DisplayName("공고 목록 조회 성공: BaseTimeEntity의 createTime 필드 기준 내림차순으로 정렬한다")
    void searchJobPostingsList_SortsByCreateTimeDesc() {
        // given
        String publicId = "2222";
        int page = 0;
        int size = 10;

        JobPosting jobPosting = mock(JobPosting.class);
        given(jobPosting.getJdId()).willReturn(1L);
        given(jobPosting.getStatus()).willReturn(JobPostingStatus.ACTIVE);

        Page<JobPosting> jobPostingPage = new PageImpl<>(List.of(jobPosting));
        given(jobPostingRepository.findByCompany_PublicId(eq(publicId), any(Pageable.class)))
                .willReturn(jobPostingPage);
        given(notificationRepository.countNewApplicantsByJdIds(anyList())).willReturn(List.of());

        // when
        JobPostingPageResponseDTO result = jobPostingService.searchJobPostingsList(publicId, null, page, size);

        // then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(jobPostingRepository).findByCompany_PublicId(eq(publicId), pageableCaptor.capture());

        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("createTime");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(result.content()).hasSize(1);
    }
}
