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
import com.weiver.jobposting.dto.response.JobPostingPageResponseDTO;
import com.weiver.jobposting.dto.response.JobPostingResponseDTO;
import com.weiver.jobposting.dto.response.JobPostingsDetails;
import com.weiver.jobposting.repository.EmailTemplateRepository;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.type.JobPostingStatus;
import com.weiver.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class JobPostingService {

    private final EmailTemplateRepository emailTemplateRepository;
    private final NotificationRepository notificationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CompanyRepository companyRepository;
    private final S3Service s3Service;

    /**
     * 기업 공고 통합 생성 API
     * */
    public void saveJobPosting(Boolean isTemp, String publicId, JobPostingRequestDTO requestDTO,
                               MultipartFile bannerImage){

        String bannerImageUrl = null;
        if (bannerImage != null && !bannerImage.isEmpty()) {
            bannerImageUrl = s3Service.publicUpload(bannerImage, "email-banners");
        }

        Company company = companyRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        JobPostingStatus status = JobPostingStatus.ACTIVE;
        if(isTemp){
            status = JobPostingStatus.DRAFT;
        }
        JobPosting jobPosting = requestDTO.toJobPosting(company, status);

        JobPosting savedJobPosting = jobPostingRepository.save(jobPosting);

        EmailTemplate emailTemplate = requestDTO.toEmailTemplate(savedJobPosting, bannerImageUrl);
        emailTemplateRepository.save(emailTemplate);
    }

    /**
     * 기업 공고 통합 수정 API
     * */
    public void updateJobPosting(Long jdId, String publicId, JobPostingUpdateDTO updateDTO,
                                 MultipartFile bannerImage){
        EmailTemplate emailTemplate = emailTemplateRepository.findWithJobPostingByJdId(jdId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_FOUND));

        JobPosting jobPosting = emailTemplate.getJobPosting();

        if (!jobPosting.getCompany().getPublicId().equals(publicId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "공고 수정 권한이 없습니다.");
        }


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
    
    /**
     * 기업 공고 조회 API
     * */
    @Transactional(readOnly = true)
    public JobPostingPageResponseDTO searchJobPostingsList(String publicId, JobPostingStatus status, int page, int size){
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<JobPosting> jobPostingPage;
        if (status != null) {
            jobPostingPage = jobPostingRepository.findByCompany_PublicIdAndStatus(publicId, status, pageable);
        } else {
            jobPostingPage = jobPostingRepository.findByCompany_PublicId(publicId, pageable);
        }

        List<Long> jdIds = jobPostingPage.getContent().stream()
                .map(JobPosting::getJdId)
                .toList();

        Map<Long, Long> newApplicantCountMap;

        if (!jdIds.isEmpty()) {
            List<Object[]> counts = notificationRepository.countNewApplicantsByJdIds(jdIds);
            newApplicantCountMap = counts.stream()
                    .collect(Collectors.toMap(
                            row -> ((Number) row[0]).longValue(),
                            row -> ((Number) row[1]).longValue()
                    ));
        } else {
            newApplicantCountMap = Map.of();
        }

        List<JobPostingsDetails> detailsList = jobPostingPage.getContent().stream()
                .map(jobPosting -> {
                    long newApplicantCount = newApplicantCountMap.getOrDefault(jobPosting.getJdId(), 0L); // 새로운 지원자가 없으면 0
                    return JobPostingsDetails.of(jobPosting, newApplicantCount);
                })
                .toList();

        return JobPostingPageResponseDTO.of(jobPostingPage, detailsList);
    }

    @Transactional(readOnly = true)
    public JobPostingResponseDTO searchJobPosting(String publicId, Long jdId){
        EmailTemplate emailTemplate = emailTemplateRepository.findWithJobPostingByJdId(jdId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_FOUND));

        JobPosting jobPosting = emailTemplate.getJobPosting();

        if (!jobPosting.getCompany().getPublicId().equals(publicId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 공고를 열람할 권한이 없습니다.");
        }

        return JobPostingResponseDTO.of(jobPosting, emailTemplate);
    }

}
