package com.weiver.jobposting.service;

import com.weiver.company.domain.Company;
import com.weiver.company.repository.CompanyRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.s3.service.S3Service;
import com.weiver.jobposting.domain.EmailTemplate;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.dto.request.JobPostingRequestDTO;
import com.weiver.jobposting.dto.request.JobPostingUpdateDTO;
import com.weiver.jobposting.repository.EmailTemplateRepository;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.type.JobPostingStatus;
import com.weiver.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobPostingServiceTest {

    @InjectMocks
    private JobPostingService jobPostingService;

    @Mock private EmailTemplateRepository emailTemplateRepository;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private S3Service s3Service;

    @Test
    @DisplayName("공고 생성 성공: 임시저장이면 상태가 DRAFT이고 이미지가 없으면 S3를 호출하지 않는다")
    void saveJobPosting_Draft_NoImage() {
        // given
        Long companyId = 1L;
        JobPostingRequestDTO requestDTO = mock(JobPostingRequestDTO.class);
        Company company = mock(Company.class);
        JobPosting jobPosting = mock(JobPosting.class);
        EmailTemplate emailTemplate = mock(EmailTemplate.class);

        given(companyRepository.findById(companyId)).willReturn(Optional.of(company));
        given(requestDTO.toJobPosting(company, JobPostingStatus.DRAFT)).willReturn(jobPosting);
        given(jobPostingRepository.save(jobPosting)).willReturn(jobPosting);
        given(requestDTO.toEmailTemplate(jobPosting, null)).willReturn(emailTemplate);

        // when
        jobPostingService.saveJobPosting(true, companyId, requestDTO, null);

        // then
        verify(s3Service, never()).publicUpload(any(), any()); // 이미지가 없으므로 S3 업로드 미호출
        verify(jobPostingRepository, times(1)).save(jobPosting);
        verify(emailTemplateRepository, times(1)).save(emailTemplate);
    }

    @Test
    @DisplayName("공고 생성 실패: 존재하지 않는 회사 ID인 경우 예외 발생")
    void saveJobPosting_CompanyNotFound() {
        // given
        Long companyId = 999L;
        JobPostingRequestDTO requestDTO = mock(JobPostingRequestDTO.class);
        given(companyRepository.findById(companyId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> jobPostingService.saveJobPosting(false, companyId, requestDTO, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("공고 수정 실패: 다른 회사의 공고를 수정하려고 하면 권한 없음 예외 발생")
    void updateJobPosting_Forbidden() {
        // given
        Long reqCompanyId = 1L;
        Long targetJdId = 100L;
        Long ownerCompanyId = 2L;

        JobPostingUpdateDTO updateDTO = mock(JobPostingUpdateDTO.class);
        EmailTemplate emailTemplate = mock(EmailTemplate.class);
        JobPosting jobPosting = mock(JobPosting.class);
        Company ownerCompany = mock(Company.class);

        given(emailTemplateRepository.findWithJobPostingByJdId(targetJdId)).willReturn(Optional.of(emailTemplate));
        given(emailTemplate.getJobPosting()).willReturn(jobPosting);
        given(jobPosting.getCompany()).willReturn(ownerCompany);
        given(ownerCompany.getCompanyId()).willReturn(ownerCompanyId);

        // when & then
        assertThatThrownBy(() -> jobPostingService.updateJobPosting(targetJdId, reqCompanyId, updateDTO, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("공고 수정 권한이 없습니다");
    }

    @Test
    @DisplayName("공고 수정 성공: 기존 이미지가 있고 삭제 플래그가 true면 S3에서 삭제되어야 한다")
    void updateJobPosting_DeleteExistingImage() {
        // given
        Long companyId = 1L;
        Long jdId = 100L;
        String existingImageUrl = "https://s3.url/old-image.png";

        JobPostingUpdateDTO updateDTO = mock(JobPostingUpdateDTO.class);
        given(updateDTO.isEmailBannerDeleted()).willReturn(true);

        EmailTemplate emailTemplate = mock(EmailTemplate.class);
        JobPosting jobPosting = mock(JobPosting.class);
        Company company = mock(Company.class);

        given(emailTemplateRepository.findWithJobPostingByJdId(jdId)).willReturn(Optional.of(emailTemplate));
        given(emailTemplate.getJobPosting()).willReturn(jobPosting);
        given(jobPosting.getCompany()).willReturn(company);
        given(company.getCompanyId()).willReturn(companyId);
        given(emailTemplate.getEmailBannerUrl()).willReturn(existingImageUrl);

        // when
        jobPostingService.updateJobPosting(jdId, companyId, updateDTO, null);

        // then
        verify(s3Service, times(1)).deleteFile(existingImageUrl);
        verify(jobPosting, times(1)).updateJobPosting(updateDTO);
        verify(emailTemplate, times(1)).updateEmailTemplate(updateDTO, null);
    }
}