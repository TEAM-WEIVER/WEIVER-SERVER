package com.weiver.auth.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.ApplicantStatus;
import com.weiver.auth.domain.ApplicantKakaoAccount;
import com.weiver.auth.dto.KakaoProfile;
import com.weiver.auth.dto.request.ApplicantAgreementRequestDTO;
import com.weiver.auth.dto.request.ApplicantPasswordSetupRequestDTO;
import com.weiver.auth.dto.request.KakaoSignupCompleteRequestDTO;
import com.weiver.auth.dto.response.ApplicantLoginMethodsResponseDTO;
import com.weiver.auth.repository.ApplicantEmailVerificationRepository;
import com.weiver.auth.repository.ApplicantKakaoAccountRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KakaoApplicantAccountServiceTest {

    private static final String PUBLIC_ID = "applicant-id";
    private static final String EMAIL = "applicant@example.com";
    private static final long KAKAO_USER_ID = 123L;
    private static final ApplicantAgreementRequestDTO AGREEMENTS =
            new ApplicantAgreementRequestDTO(true, true, true, true, true, false);
    private static final ApplicantPasswordSetupRequestDTO PASSWORD_SETUP =
            new ApplicantPasswordSetupRequestDTO("reauth-token", "123456", "Secret!25", "Secret!25");

    @Mock private ApplicantRepository applicantRepository;
    @Mock private ApplicantKakaoAccountRepository kakaoAccountRepository;
    @Mock private ApplicantEmailVerificationRepository emailVerificationRepository;
    @Mock private ApplicantAuthService applicantAuthService;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private KakaoApplicantAccountService accountService;

    @Test
    void completeSignup_usesVerifiedKakaoEmailAndCreatesPasswordlessAccount() {
        when(applicantRepository.saveAndFlush(any(Applicant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String publicId = accountService.completeSignup(
                new KakaoProfile(KAKAO_USER_ID, EMAIL),
                new KakaoSignupCompleteRequestDTO("signup-token", null, null, AGREEMENTS));

        ArgumentCaptor<ApplicantKakaoAccount> link = ArgumentCaptor.forClass(ApplicantKakaoAccount.class);
        verify(kakaoAccountRepository).saveAndFlush(link.capture());
        Applicant applicant = link.getValue().getApplicant();
        assertThat(applicant.getPublicId()).isEqualTo(publicId);
        assertThat(applicant.getEmail()).isEqualTo(EMAIL);
        assertThat(applicant.hasLocalPassword()).isFalse();
        assertThat(link.getValue().getKakaoUserId()).isEqualTo(KAKAO_USER_ID);
        verifyNoInteractions(emailVerificationRepository, passwordEncoder);
    }

    @Test
    void completeSignup_usesEmailVerifiedByWeiverWhenChosenEmailDiffers() {
        when(emailVerificationRepository.findAndDeleteVerifiedToken("email-token")).thenReturn(Optional.of(EMAIL));
        when(applicantRepository.saveAndFlush(any(Applicant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.completeSignup(
                new KakaoProfile(KAKAO_USER_ID, "other@example.com"),
                new KakaoSignupCompleteRequestDTO("signup-token", EMAIL, "email-token", AGREEMENTS));

        ArgumentCaptor<Applicant> applicant = ArgumentCaptor.forClass(Applicant.class);
        verify(applicantRepository).saveAndFlush(applicant.capture());
        assertThat(applicant.getValue().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void completeSignup_rejectsEmailProofForAnotherAddress() {
        when(emailVerificationRepository.findAndDeleteVerifiedToken("email-token"))
                .thenReturn(Optional.of("other@example.com"));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> accountService.completeSignup(new KakaoProfile(KAKAO_USER_ID, null),
                        new KakaoSignupCompleteRequestDTO("signup-token", EMAIL, "email-token", AGREEMENTS)))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
        verifyNoInteractions(applicantRepository);
    }

    @Test
    void completeSignup_rejectsExistingActiveEmailWithoutMerging() {
        when(applicantRepository.findByEmailAndDeletedFalse(EMAIL)).thenReturn(Optional.of(applicant("old-hash")));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> accountService.completeSignup(new KakaoProfile(KAKAO_USER_ID, EMAIL),
                        new KakaoSignupCompleteRequestDTO("signup-token", null, null, AGREEMENTS)))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
        verify(applicantRepository, never()).saveAndFlush(any());
        verify(kakaoAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void completeSignup_requiresPendingEmailSignupToFinish() {
        when(applicantRepository.findByEmailAndDeletedFalse(EMAIL))
                .thenReturn(Optional.of(Applicant.builder().email(EMAIL).password("old-hash").build()));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> accountService.completeSignup(new KakaoProfile(KAKAO_USER_ID, EMAIL),
                        new KakaoSignupCompleteRequestDTO("signup-token", null, null, AGREEMENTS)))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.SIGNUP_NOT_COMPLETED));
        verify(kakaoAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void completeLink_rejectsKakaoIdBelongingToAnotherApplicant() {
        when(applicantRepository.findByPublicIdAndDeletedFalseForUpdate(PUBLIC_ID))
                .thenReturn(Optional.of(applicant("old-hash")));
        var otherAccount = ApplicantKakaoAccount.builder()
                .applicant(Applicant.builder().applicantId(2L).email("other@example.com").build())
                .kakaoUserId(KAKAO_USER_ID).build();
        when(kakaoAccountRepository.findByKakaoUserId(KAKAO_USER_ID)).thenReturn(Optional.of(otherAccount));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> accountService.completeLink(PUBLIC_ID, KAKAO_USER_ID))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.KAKAO_ACCOUNT_ALREADY_LINKED));
        verify(kakaoAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    void getLoginMethods_usesCurrentApplicantsPasswordAndKakaoLink() {
        when(applicantRepository.findByPublicIdAndDeletedFalse(PUBLIC_ID)).thenReturn(Optional.of(applicant(null)));
        when(kakaoAccountRepository.existsByApplicant_ApplicantId(1L)).thenReturn(true);

        assertThat(accountService.getLoginMethods(PUBLIC_ID)).isEqualTo(new ApplicantLoginMethodsResponseDTO(false, true));
    }

    @Test
    void setupPassword_updatesSameAccountOnlyAfterVerifyingCurrentLink() {
        Applicant applicant = applicant(null);
        when(applicantRepository.findByPublicIdAndDeletedFalseForUpdate(PUBLIC_ID)).thenReturn(Optional.of(applicant));
        when(kakaoAccountRepository.findByApplicant_ApplicantId(1L)).thenReturn(Optional.of(link(applicant)));
        when(passwordEncoder.encode(PASSWORD_SETUP.newPassword())).thenReturn("new-hash");

        accountService.setupPassword(PUBLIC_ID, new KakaoProfile(KAKAO_USER_ID, "ignored@example.com"), PASSWORD_SETUP);

        verify(applicantAuthService).verifyPasswordSetupCode(applicant, PASSWORD_SETUP.emailCode());
        assertThat(applicant.getPublicId()).isEqualTo(PUBLIC_ID);
        assertThat(applicant.getEmail()).isEqualTo(EMAIL);
        assertThat(applicant.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void setupPassword_rejectsProofForDifferentKakaoLink() {
        Applicant applicant = applicant(null);
        when(applicantRepository.findByPublicIdAndDeletedFalseForUpdate(PUBLIC_ID)).thenReturn(Optional.of(applicant));
        when(kakaoAccountRepository.findByApplicant_ApplicantId(1L)).thenReturn(Optional.of(link(applicant)));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> accountService.setupPassword(PUBLIC_ID, new KakaoProfile(999L, EMAIL), PASSWORD_SETUP))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PASSWORD_REAUTH_TOKEN));
        verifyNoInteractions(passwordEncoder);
        assertThat(applicant.hasLocalPassword()).isFalse();
    }

    @Test
    void setupPassword_preservesExistingPassword() {
        Applicant applicant = applicant("old-hash");
        when(applicantRepository.findByPublicIdAndDeletedFalseForUpdate(PUBLIC_ID)).thenReturn(Optional.of(applicant));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> accountService.setupPassword(PUBLIC_ID, new KakaoProfile(KAKAO_USER_ID, EMAIL), PASSWORD_SETUP))
                .satisfies(error -> assertThat(error.getCode()).isEqualTo(ErrorCode.PASSWORD_ALREADY_CONFIGURED));
        verifyNoInteractions(kakaoAccountRepository, passwordEncoder);
        assertThat(applicant.getPassword()).isEqualTo("old-hash");
    }

    private Applicant applicant(String password) {
        return Applicant.builder().applicantId(1L).publicId(PUBLIC_ID).email(EMAIL)
                .password(password).status(ApplicantStatus.ACTIVE).build();
    }

    private ApplicantKakaoAccount link(Applicant applicant) {
        return ApplicantKakaoAccount.builder().applicant(applicant).kakaoUserId(KAKAO_USER_ID).build();
    }
}
