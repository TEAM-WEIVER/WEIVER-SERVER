package com.weiver.auth.service;

import com.weiver.global.email.dto.EmailSendRequest;
import com.weiver.global.email.service.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailSender emailSender;

    public void sendVerificationCode(String email, String code) {
        String subject = "[WEIVER] 이메일 인증번호 안내";

        String htmlContent = """
                                <div style="font-family: Arial, sans-serif; line-height: 1.6;">
                                    <h2>WEIVER 이메일 인증</h2>
                                    <p>아래 인증번호를 입력해 주세요.</p>
                                    <div style="font-size: 28px; font-weight: bold; letter-spacing: 4px;">
                                        %s
                                    </div>
                                    <p>인증번호는 일정 시간 후 만료됩니다.</p>
                                </div>
                """.formatted(code);

        emailSender.send(EmailSendRequest.ofHtml(email, subject, htmlContent));
    }

}
