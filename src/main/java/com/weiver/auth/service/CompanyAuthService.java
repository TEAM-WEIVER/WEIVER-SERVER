package com.weiver.auth.service;

import com.weiver.auth.dto.request.CompanyLoginRequestDTO;
import com.weiver.auth.service.dto.CompanyLoginResult;
import com.weiver.company.domain.Company;
import com.weiver.company.repository.CompanyRepository;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.RefreshTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyAuthService {

    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenVersionRepository tokenVersionRepository;

    @Transactional
    public CompanyLoginResult login(CompanyLoginRequestDTO request) {
        // 로그인 경로에서는 계정 미존재와 비밀번호 불일치를 동일한 오류(INVALID_PASSWORD)로 통일해 아이디 열거를 차단한다.
        Company company = companyRepository.findByLoginIdAndDeletedFalse(request.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD));

        if(!passwordEncoder.matches(request.password(), company.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        String publicId = company.getPublicId();
        UserRole role = company.getRole();

        long tokenVersion = tokenVersionRepository.getCurrentVersion(publicId, role);

        String accessToken = jwtTokenProvider.createAccessToken(publicId, role, tokenVersion);
        String refreshToken = jwtTokenProvider.createRefreshToken(publicId, role, tokenVersion);

        refreshTokenRepository.save(publicId, role, refreshToken, jwtTokenProvider.getRefreshTokenExpirationMillis());

        return new CompanyLoginResult(accessToken, refreshToken, role);
    }
}
