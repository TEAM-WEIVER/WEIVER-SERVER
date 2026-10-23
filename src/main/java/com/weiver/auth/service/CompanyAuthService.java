package com.weiver.auth.service;

import com.weiver.auth.dto.request.CompanyLoginRequestDTO;
import com.weiver.auth.repository.LoginAttemptRepository;
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

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CompanyAuthService {

    // 로그인 시도 제한: 15분 안에 5회까지 비밀번호를 검사하고, 6번째 시도부터 15분 동안 차단(성공하면 카운터 삭제)
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOGIN_BLOCK_DURATION = Duration.ofMinutes(15);

    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenVersionRepository tokenVersionRepository;
    private final LoginAttemptRepository loginAttemptRepository;

    @Transactional
    public CompanyLoginResult login(CompanyLoginRequestDTO request) {
        String loginId = request.loginId();

        // 계정 조회·비밀번호 비교 전에 시도를 원자적으로 기록하고 허용 여부를 확인한다.
        // 한도를 넘었거나 차단 중이면 비밀번호가 맞아도 거절한다.
        if (!loginAttemptRepository.tryAcquire(loginId, UserRole.COMPANY, MAX_LOGIN_ATTEMPTS, LOGIN_ATTEMPT_WINDOW, LOGIN_BLOCK_DURATION)) {
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }

        // 로그인 경로에서는 계정 미존재와 비밀번호 불일치를 동일한 오류(INVALID_PASSWORD)로 통일해 아이디 열거를 차단한다.
        // 계정이 없는 경우도 시도로 센다.
        Company company = companyRepository.findByLoginIdAndDeletedFalse(loginId).orElse(null);

        // 시도는 tryAcquire에서 이미 셌으므로 실패 시 추가 Redis 호출은 없다.
        if (company == null || !passwordEncoder.matches(request.password(), company.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        loginAttemptRepository.clearAttempts(loginId, UserRole.COMPANY);

        String publicId = company.getPublicId();
        UserRole role = company.getRole();

        long tokenVersion = tokenVersionRepository.getCurrentVersion(publicId, role);

        String accessToken = jwtTokenProvider.createAccessToken(publicId, role, tokenVersion);
        String refreshToken = jwtTokenProvider.createRefreshToken(publicId, role, tokenVersion);

        refreshTokenRepository.save(publicId, role, refreshToken, jwtTokenProvider.getRefreshTokenExpirationMillis());

        return new CompanyLoginResult(accessToken, refreshToken, role);
    }
}
