package com.weiver.auth.validator;

import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthUserValidator {

    private final ApplicantRepository applicantRepository;

    public void validateExist(Long userId, UserRole userRole) {
        switch (userRole) {
            case APPLICANT -> {
                if(!applicantRepository.existsById(userId)) {
                    throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                }
            }
            // 추후 COMPANY 추가
        }
    }
}
