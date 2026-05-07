package com.weiver.company.service;

import com.weiver.company.domain.Company;
import com.weiver.company.dto.response.CompanyInfoResponseDTO;
import com.weiver.company.repository.CompanyRepository;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public CompanyInfoResponseDTO getMyCompanyInfo(String publicId, UserRole role) {
        if (role != UserRole.COMPANY) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "기업 회원만 접근할 수 있습니다.");
        }

        Company company = companyRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        return CompanyInfoResponseDTO.from(company);
    }
}
