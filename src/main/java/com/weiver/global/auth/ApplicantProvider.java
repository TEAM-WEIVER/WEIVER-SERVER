package com.weiver.global.auth;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ApplicantProvider {

    private final ApplicantRepository applicantRepository;

    @Transactional(readOnly = true)
    public Applicant findByPublicId(String publicId) {
        return applicantRepository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
    }
}
