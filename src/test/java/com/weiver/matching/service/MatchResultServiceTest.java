package com.weiver.matching.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.global.email.dto.EmailSendRequest;
import com.weiver.global.email.service.EmailSender;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.jobposting.domain.EmailTemplate;
import com.weiver.jobposting.repository.EmailTemplateRepository;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.repository.MatchResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchResultServiceTest {

    @InjectMocks
    private MatchResultService matchResultService;

    @Mock
    private MatchResultRepository matchResultRepository;

    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    @Mock
    private EmailSender emailSender;

    @Test
    @DisplayName("성공: 템플릿의 {{name}} 치환 변수가 지원자 이름으로 정상 치환되어 메일이 발송된다.")
    void sendContactEmail_Success_WithTemplateReplacement() {
        // given
        Long jdId = 1L;
        String applicantPublicId = "app-123";
        String companyPublicId = "comp-456";

        Applicant applicant = Applicant.builder()
                .name("이현우")
                .email("lee@example.com")
                .build();

        MatchResult matchResult = MatchResult.builder()
                .applicant(applicant)
                .build();

        EmailTemplate template = EmailTemplate.builder()
                .emailTitle("서류 합격을 축하합니다.")
                .emailContent("안녕하세요 {{name}}님, 위버(Weiver)입니다.")
                .build();

        given(matchResultRepository.findMatchResultForContact(
                jdId, applicantPublicId, companyPublicId))
                .willReturn(Optional.of(matchResult));

        given(emailTemplateRepository.findWithJobPostingByJdId(jdId))
                .willReturn(Optional.of(template));

        // when
        matchResultService.sendContactEmail(jdId, applicantPublicId, companyPublicId);

        // then
        ArgumentCaptor<EmailSendRequest> requestCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailSender).send(requestCaptor.capture());

        EmailSendRequest sentRequest = requestCaptor.getValue();
        assertThat(sentRequest.to()).isEqualTo("lee@example.com");
        assertThat(sentRequest.subject()).isEqualTo("서류 합격을 축하합니다.");
        assertThat(sentRequest.textContent()).isEqualTo("안녕하세요 이현우님, 위버(Weiver)입니다.");
    }

    @Test
    @DisplayName("성공 엣지케이스: 템플릿 본문에 {{name}} 변수가 없어도 정상적으로 발송된다.")
    void sendContactEmail_Success_WithoutReplacement() {
        // given
        Long jdId = 1L;
        Applicant applicant = Applicant.builder().name("이현우").email("lee@example.com").build();
        MatchResult matchResult = MatchResult.builder().applicant(applicant).build();

        EmailTemplate template = EmailTemplate.builder()
                .emailTitle("안내 메일")
                .emailContent("안녕하세요. 서류 합격자 대상 안내입니다.")
                .build();

        given(matchResultRepository.findMatchResultForContact(any(), any(), any()))
                .willReturn(Optional.of(matchResult));
        given(emailTemplateRepository.findWithJobPostingByJdId(any()))
                .willReturn(Optional.of(template));

        // when
        matchResultService.sendContactEmail(jdId, "app-123", "comp-456");

        // then
        ArgumentCaptor<EmailSendRequest> requestCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailSender).send(requestCaptor.capture());

        assertThat(requestCaptor.getValue().textContent()).isEqualTo("안녕하세요. 서류 합격자 대상 안내입니다.");
    }

    @Test
    @DisplayName("엣지케이스: 템플릿 본문이나 지원자 이름이 null이어도 NPE 발생 없이 발송 파라미터가 만들어진다.")
    void sendContactEmail_Success_WhenBodyOrNameIsNull() {
        // given
        Long jdId = 1L;
        Applicant applicant = Applicant.builder().name(null).email("lee@example.com").build();
        MatchResult matchResult = MatchResult.builder().applicant(applicant).build();

        EmailTemplate template = EmailTemplate.builder()
                .emailTitle("제목만 있는 메일")
                .emailContent(null)
                .build();

        given(matchResultRepository.findMatchResultForContact(any(), any(), any()))
                .willReturn(Optional.of(matchResult));
        given(emailTemplateRepository.findWithJobPostingByJdId(any()))
                .willReturn(Optional.of(template));

        // when
        matchResultService.sendContactEmail(jdId, "app-123", "comp-456");

        // then
        ArgumentCaptor<EmailSendRequest> requestCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailSender).send(requestCaptor.capture());

        assertThat(requestCaptor.getValue().textContent()).isNull();
    }

    @Test
    @DisplayName("실패: 지원자의 이메일 주소가 없거나 비어있으면 EMAIL_NOT_FOUND 예외가 발생한다.")
    void sendContactEmail_ThrowsEmailNotFound_WhenEmailIsBlank() {
        // given
        Long jdId = 1L;
        // 이메일이 없는 구직자 세팅
        Applicant applicant = Applicant.builder().name("이현우").email("").build();
        MatchResult matchResult = MatchResult.builder().applicant(applicant).build();
        EmailTemplate template = EmailTemplate.builder().build();

        given(matchResultRepository.findMatchResultForContact(any(), any(), any()))
                .willReturn(Optional.of(matchResult));
        given(emailTemplateRepository.findWithJobPostingByJdId(any()))
                .willReturn(Optional.of(template));

        // when & then
        assertThatThrownBy(() -> matchResultService.sendContactEmail(jdId, "app-123", "comp-456"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("수신자 이메일이 존재하지 않습니다.");
    }

    @Test
    @DisplayName("실패: 해당 기업의 공고에 매칭된 이력이 없으면 FORBIDDEN 예외가 발생한다.")
    void sendContactEmail_ThrowsForbidden_WhenMatchResultNotFound() {
        // given
        Long jdId = 1L;
        String applicantPublicId = "app-123";
        String companyPublicId = "comp-456";

        given(matchResultRepository.findMatchResultForContact(
                jdId, applicantPublicId, companyPublicId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> matchResultService.sendContactEmail(jdId, applicantPublicId, companyPublicId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.FORBIDDEN.defaultMessage);
    }

    @Test
    @DisplayName("실패: 공고에 등록된 이메일 템플릿이 없으면 EMAIL_NOT_FOUND 예외가 발생한다.")
    void sendContactEmail_ThrowsEmailNotFound_WhenTemplateNotFound() {
        // given
        Long jdId = 1L;
        String applicantPublicId = "app-123";
        String companyPublicId = "comp-456";

        MatchResult matchResult = MatchResult.builder().build();

        given(matchResultRepository.findMatchResultForContact(
                jdId, applicantPublicId, companyPublicId))
                .willReturn(Optional.of(matchResult));

        given(emailTemplateRepository.findWithJobPostingByJdId(jdId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> matchResultService.sendContactEmail(jdId, applicantPublicId, companyPublicId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.EMAIL_NOT_FOUND.defaultMessage);
    }
}