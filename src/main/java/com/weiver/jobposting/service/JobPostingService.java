package com.weiver.jobposting.service;

import com.weiver.company.domain.Company;
import com.weiver.company.repository.CompanyRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import com.weiver.jobposting.domain.EmailTemplate;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.dto.request.JobPostingRequestDTO;
import com.weiver.jobposting.repository.EmailTemplateRepository;
import com.weiver.jobposting.repository.JobPostingRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
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

}
