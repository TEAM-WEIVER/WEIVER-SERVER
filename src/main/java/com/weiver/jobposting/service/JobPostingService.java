package com.weiver.jobposting.service;

import com.weiver.company.domain.Company;
import com.weiver.company.repository.CompanyRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import com.weiver.jobposting.domain.EmailTemplate;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.dto.request.JobPostingRequestDTO;
import com.weiver.jobposting.dto.request.JobPostingUpdateDTO;
import com.weiver.jobposting.repository.EmailTemplateRepository;
import com.weiver.jobposting.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final CompanyRepository companyRepository;
    private final S3Service s3Service;

    /**
     * 기업 공고 통합 생성 API
     * */
    public void saveJobPosting(Long companyId, JobPostingRequestDTO requestDTO,
                               MultipartFile bannerImage){

        String bannerImageUrl = null;
        if (bannerImage != null && !bannerImage.isEmpty()) {
            bannerImageUrl = s3Service.publicUpload(bannerImage, "email-banners");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        JobPosting jobPosting = requestDTO.toJobPosting(company);

        JobPosting savedJobPosting = jobPostingRepository.save(jobPosting);

        EmailTemplate emailTemplate = requestDTO.toEmailTemplate(savedJobPosting, bannerImageUrl);
        emailTemplateRepository.save(emailTemplate);
    }

    /**
     * 기업 공고 통합 수정 API
     * */
    public void updateJobPosting(Long jdId, Long companyId, JobPostingUpdateDTO updateDTO,
                                 MultipartFile bannerImage){
        JobPosting jobPosting = jobPostingRepository.findById(jdId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_POSTING_NOT_FOUND));

        if (!jobPosting.getCompany().getCompanyId().equals(companyId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "공고 수정 권한이 없습니다.");
        }

        EmailTemplate emailTemplate = emailTemplateRepository.findByJobPosting(jobPosting)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_FOUND));

        String finalBannerUrl = emailTemplate.getEmailBannerUrl();

        if(Boolean.TRUE.equals(updateDTO.isEmailBannerDeleted())){
            if(finalBannerUrl != null) {
                try {
                    s3Service.deleteFile(finalBannerUrl);
                } catch (Exception e) {
                    log.warn("S3 기존 배너 이미지 삭제 실패, 수동 정리 필요: {}", finalBannerUrl, e);
                }
            }
            finalBannerUrl = null;
        } else if (bannerImage != null && !bannerImage.isEmpty()) {
            if(finalBannerUrl != null) s3Service.deleteFile(finalBannerUrl);
            finalBannerUrl = s3Service.publicUpload(bannerImage, "email-banners");
        }

        jobPosting.updateJobPosting(updateDTO);
        emailTemplate.updateEmailTemplate(updateDTO, finalBannerUrl);
    }

}
