package com.weiver.dashboard.service;

import com.weiver.company.domain.Company;
import com.weiver.company.repository.CompanyRepository;
import com.weiver.dashboard.dto.response.CompanyDashboardResponseDTO;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    private final CompanyRepository companyRepository;
    private final S3Service s3Service;

    /**
     *  기업 대시보드 - 기업 정보 카드 부분 조회
     * */
    public CompanyDashboardResponseDTO getCompanyInfo(String publicId){
        Company company = getCompany(publicId);
        String presignedUrl = s3Service.getPresignedUrl(company.getCompanyLogoUrl());
        return CompanyDashboardResponseDTO.from(company, presignedUrl);
    }

    private Company getCompany(String publicId) {
        Company company = companyRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));
        return company;
    }
}
