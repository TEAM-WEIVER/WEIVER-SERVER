package com.weiver.domain.auth.service;

import com.weiver.auth.service.EmailVerificationService;
import com.weiver.global.email.dto.EmailSendRequest;
import com.weiver.global.email.service.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Mock
    private EmailSender emailSender;

    @Captor
    private ArgumentCaptor<EmailSendRequest> requestCaptor;

    @Test
    @DisplayName("인증 코드 발송 시 HTML 이메일로 올바른 수신자, 제목, 코드를 포함해 전송한다.")
    void sendVerificationCode_sendsHtmlEmailWithCode() {
        // given
        String email = "user@test.com";
        String code = "123456";

        // when
        emailVerificationService.sendVerificationCode(email, code);

        // then
        verify(emailSender).send(requestCaptor.capture());
        EmailSendRequest captured = requestCaptor.getValue();

        assertThat(captured.to()).isEqualTo(email);
        assertThat(captured.subject()).contains("WEIVER");
        assertThat(captured.htmlContent()).contains(code);
        assertThat(captured.textContent()).isNull();
    }
}