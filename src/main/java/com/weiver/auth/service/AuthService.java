package com.weiver.auth.service;

import com.weiver.auth.service.dto.TokenReissueResult;

public interface AuthService {
    void logout(String accessToken);
    TokenReissueResult reissueToken(String refreshToken);
}
