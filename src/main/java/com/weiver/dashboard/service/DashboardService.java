package com.weiver.dashboard.service;

import com.weiver.company.domain.Company;
import com.weiver.company.repository.CompanyRepository;
import com.weiver.dashboard.dto.response.CompanyDashboardResponseDTO;
import com.weiver.dashboard.dto.response.DashboardNotificationListResponseDTO;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.jobposting.dto.response.JobPostingPageResponseDTO;
import com.weiver.jobposting.service.JobPostingService;
import com.weiver.jobposting.type.JobPostingStatus;
import com.weiver.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    private final CompanyRepository companyRepository;
    private final JobPostingService jobPostingService;
    private final NotificationService notificationService;

    /**
     *  기업 대시보드 - 기업 정보 카드 부분 조회
     * */
    public CompanyDashboardResponseDTO getCompanyInfo(String publicId){
        Company company = getCompany(publicId);
        return CompanyDashboardResponseDTO.from(company, company.getCompanyLogoUrl());
    }

    /**
     *  기업 대시보드 - 공고 리스트 조회 (페이징)
     * */
    public JobPostingPageResponseDTO getJobPostingsList(String publicId, JobPostingStatus status, int page, int size) {
        return jobPostingService.searchJobPostingsList(publicId, status, page, size);
    }

    /**
     *  기업 대시보드 - 알림 목록 조회
     * */
    public DashboardNotificationListResponseDTO getNotifications(String publicId, int page, int size) {
        return DashboardNotificationListResponseDTO.from(notificationService.getCompanyNotifications(publicId, page, size));
    }

    /**
     *  기업 대시보드 - 알림 단건 읽음 처리
     * */
    @Transactional
    public void readNotification(Long notificationId, String publicId) {
        notificationService.markAsRead(notificationId, publicId);
    }

    /**
     *  기업 대시보드 - 알림 전체 읽음 처리
     * */
    @Transactional
    public void readAllNotifications(String publicId) {
        notificationService.markAllAsRead(publicId);
    }

    private Company getCompany(String publicId) {
        Company company = companyRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));
        return company;
    }
}
