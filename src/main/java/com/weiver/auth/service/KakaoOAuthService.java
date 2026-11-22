package com.weiver.auth.service;

import com.weiver.auth.dto.KakaoProfile;
import com.weiver.auth.dto.request.ApplicantPasswordSetupRequestDTO;
import com.weiver.auth.dto.request.KakaoSignupCompleteRequestDTO;
import com.weiver.auth.repository.KakaoOAuthTicketRepository;
import com.weiver.auth.service.dto.ApplicantLoginResult;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.util.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KakaoOAuthService {

    public static final Duration BROWSER_TTL = Duration.ofMinutes(30);
    public static final Duration RESULT_TTL = Duration.ofMinutes(2);
    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private static final Duration SIGNUP_TTL = Duration.ofMinutes(10);
    private static final Duration PASSWORD_SETUP_TTL = Duration.ofMinutes(5);

    private static final String STATE = "state";
    private static final String SIGNUP = "signup";
    private static final String PASSWORD_SETUP = "password-setup";

    private final KakaoOAuthClient client;
    private final KakaoOAuthProperties properties;
    private final KakaoOAuthTicketRepository tickets;
    private final KakaoApplicantAccountService accounts;

    public AuthorizationStart startLogin(String browserId) {
        return start(browserId, Purpose.LOGIN, null);
    }

    public AuthorizationStart startLink(String browserId, String publicId, String accessToken, String currentPassword) {
        properties.requireLoginConfiguration();
        String session = sessionBinding(publicId, accessToken);
        accounts.verifyLinkPassword(publicId, currentPassword);
        return start(browserId, Purpose.LINK, session);
    }

    public AuthorizationStart startReauth(String browserId, String publicId, String accessToken) {
        properties.requireLoginConfiguration();
        String session = sessionBinding(publicId, accessToken);
        if (!accounts.getLoginMethods(publicId).kakaoLinked()) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_REAUTH_TOKEN);
        }
        return start(browserId, Purpose.REAUTH, session);
    }

    private AuthorizationStart start(String browserId, Purpose purpose, String session) {
        String browser = isOpaqueToken(browserId) ? browserId : newToken();
        String state = newToken();
        String flowId = newToken();
        URI authorizationUri = client.authorizationUri(state, purpose != Purpose.LOGIN);
        tickets.save(STATE, browser, state, new State(purpose, session, flowId), STATE_TTL);
        return new AuthorizationStart(authorizationUri, browser, flowId);
    }

    public CallbackReturn callback(String browserId, String state, String code, String providerError) {
        State stored = consume(STATE, browserId, state, State.class, ErrorCode.INVALID_KAKAO_OAUTH_TICKET);
        AuthorizationOutcome result = authorize(code, providerError);
        String resultToken = newToken();
        tickets.save(stored.purpose().resultKey, binding(browserId, resultBinding(stored.session(), stored.flowId())),
                resultToken, result, RESULT_TTL);
        return new CallbackReturn(properties.frontendReturnUri(), stored.flowId(), resultToken);
    }

    private AuthorizationOutcome authorize(String code, String providerError) {
        if ("access_denied".equals(providerError)) {
            return new AuthorizationOutcome(null, ErrorCode.KAKAO_OAUTH_CANCELLED);
        }
        if (StringUtils.hasText(providerError) || !StringUtils.hasText(code) || code.length() > 4096) {
            return new AuthorizationOutcome(null, ErrorCode.KAKAO_PROVIDER_ERROR);
        }
        try {
            return new AuthorizationOutcome(client.fetchUser(code), null);
        } catch (BusinessException e) {
            if (e.getCode() != ErrorCode.KAKAO_PROVIDER_ERROR) {
                throw e;
            }
            return new AuthorizationOutcome(null, ErrorCode.KAKAO_PROVIDER_ERROR);
        }
    }

    public LoginExchange exchange(String browserId, String flowId, String resultToken) {
        KakaoProfile profile = consumeResult(Purpose.LOGIN, browserId, null, flowId, resultToken);
        return accounts.loginIfLinked(profile.kakaoUserId())
                .map(login -> new LoginExchange(ExchangeStatus.LOGGED_IN, null, null, login))
                .orElseGet(() -> signupExchange(browserId, profile));
    }

    private LoginExchange signupExchange(String browserId, KakaoProfile profile) {
        return switch (accounts.classifySignup(profile.verifiedEmail())) {
            case EXISTING_ACCOUNT -> new LoginExchange(ExchangeStatus.EXISTING_ACCOUNT, null, profile.verifiedEmail(), null);
            case PENDING_ACCOUNT -> new LoginExchange(ExchangeStatus.PENDING_ACCOUNT, null, profile.verifiedEmail(), null);
            case SIGNUP_REQUIRED -> {
                String signupToken = newToken();
                tickets.save(SIGNUP, browserId, signupToken, profile, SIGNUP_TTL);
                yield new LoginExchange(ExchangeStatus.SIGNUP_REQUIRED, signupToken, profile.verifiedEmail(), null);
            }
        };
    }

    public ApplicantLoginResult completeSignup(String browserId, KakaoSignupCompleteRequestDTO request) {
        KakaoProfile profile = consume(SIGNUP, browserId, request.signupToken(), KakaoProfile.class, ErrorCode.INVALID_SIGNUP_TOKEN);
        String publicId = accounts.completeSignup(profile, request);
        return accounts.loginAfterSignup(profile.kakaoUserId(), publicId);
    }

    public void completeLink(String browserId, String publicId, String accessToken, String flowId, String resultToken) {
        KakaoProfile profile = consumeResult(Purpose.LINK, browserId, sessionBinding(publicId, accessToken), flowId, resultToken);
        accounts.completeLink(publicId, profile.kakaoUserId());
    }

    public String completeReauth(String browserId, String publicId, String accessToken, String flowId, String resultToken) {
        String session = sessionBinding(publicId, accessToken);
        KakaoProfile profile = consumeResult(Purpose.REAUTH, browserId, session, flowId, resultToken);
        if (accounts.findLinkedApplicantPublicId(profile.kakaoUserId()).filter(publicId::equals).isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_REAUTH_TOKEN);
        }
        String reauthToken = newToken();
        tickets.save(PASSWORD_SETUP, binding(browserId, session), reauthToken, profile, PASSWORD_SETUP_TTL);
        return reauthToken;
    }

    public void setupPassword(String browserId, String publicId, String accessToken, ApplicantPasswordSetupRequestDTO request) {
        String session = sessionBinding(publicId, accessToken);
        KakaoProfile profile = consume(PASSWORD_SETUP, browserId, session, request.reauthToken(),
                KakaoProfile.class, ErrorCode.INVALID_PASSWORD_REAUTH_TOKEN);
        accounts.setupPassword(publicId, profile, request);
    }

    private KakaoProfile consumeResult(Purpose purpose, String browserId, String session, String flowId, String token) {
        AuthorizationOutcome result = consume(purpose.resultKey, browserId, resultBinding(session, flowId), token,
                AuthorizationOutcome.class, ErrorCode.INVALID_KAKAO_OAUTH_TICKET);
        if (result.error() != null) {
            throw new BusinessException(result.error());
        }
        return result.profile();
    }

    private <T> T consume(String purpose, String browserId, String token, Class<T> type, ErrorCode error) {
        return consume(purpose, browserId, null, token, type, error);
    }

    private <T> T consume(String purpose, String browserId, String session, String token, Class<T> type, ErrorCode error) {
        if (!isOpaqueToken(browserId) || !isOpaqueToken(token)) {
            throw new BusinessException(error);
        }
        return tickets.consume(purpose, binding(browserId, session), token, type)
                .orElseThrow(() -> new BusinessException(error));
    }

    private static String resultBinding(String session, String flowId) {
        return session == null ? flowId : session + ":" + flowId;
    }

    private static String binding(String browserId, String session) {
        return session == null ? browserId : browserId + ":" + session;
    }

    private static String sessionBinding(String publicId, String accessToken) {
        if (!StringUtils.hasText(publicId) || !StringUtils.hasText(accessToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return publicId + ":" + TokenHashUtil.sha256(accessToken);
    }

    private static boolean isOpaqueToken(String token) {
        return token != null && token.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    }

    private static String newToken() {
        return UUID.randomUUID().toString();
    }

    private enum Purpose {
        LOGIN("result-login"),
        LINK("result-link"),
        REAUTH("result-reauth");

        private final String resultKey;

        Purpose(String resultKey) {
            this.resultKey = resultKey;
        }
    }

    private record State(Purpose purpose, String session, String flowId) {
    }

    private record AuthorizationOutcome(KakaoProfile profile, ErrorCode error) {
    }

    public record AuthorizationStart(URI authorizationUri, String browserId, String flowId) {
    }

    public record CallbackReturn(URI returnUri, String flowId, String resultToken) {
    }

    public enum ExchangeStatus {
        LOGGED_IN, SIGNUP_REQUIRED, EXISTING_ACCOUNT, PENDING_ACCOUNT
    }

    public record LoginExchange(ExchangeStatus status, String signupToken, String verifiedEmail, ApplicantLoginResult login) {
    }
}
