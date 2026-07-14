package com.weiver.applicant.event;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Certificate;
import com.weiver.applicant.domain.Education;
import com.weiver.applicant.domain.WorkExperience;
import com.weiver.applicant.event.dto.ApplicantProfileChangedData;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.repository.CertificateRepository;
import com.weiver.applicant.repository.EducationRepository;
import com.weiver.applicant.repository.WorkExperienceRepository;
import com.weiver.applicant.type.Degree;
import com.weiver.applicant.type.EmploymentType;
import com.weiver.applicant.type.ProfileSyncStatus;
import com.weiver.applicant.type.Status;
import com.weiver.essay.domain.EssayAnswer;
import com.weiver.essay.domain.EssayQuestion;
import com.weiver.essay.repository.EssayAnswerRepository;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.publisher.DomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicantProfileEventServiceTest {

    @InjectMocks
    private ApplicantProfileEventService applicantProfileEventService;

    @Mock private ApplicantRepository applicantRepository;
    @Mock private EducationRepository educationRepository;
    @Mock private WorkExperienceRepository workExperienceRepository;
    @Mock private CertificateRepository certificateRepository;
    @Mock private EssayAnswerRepository essayAnswerRepository;
    @Mock private DomainEventPublisher domainEventPublisher;

    @Test
    @DisplayName("지원자 프로필 변경 이벤트 payload를 구성하고 동기화 상태를 REQUESTED로 바꾼다")
    void publishProfileChanged_BuildsPayloadAndMarksRequested() {
        Applicant applicant = Applicant.builder()
                .applicantId(1L)
                .name("홍길동")
                .email("hong@example.com")
                .phoneNumber("010-1234-5678")
                .birthday(LocalDate.of(2000, 1, 1))
                .build();
        Education education = Education.builder()
                .educationId(10L)
                .degree(Degree.BACHELOR)
                .schoolName("한양대학교")
                .major("컴퓨터공학")
                .gpa(BigDecimal.valueOf(4.0))
                .startDate(YearMonth.of(2020, 3))
                .endDate(YearMonth.of(2024, 2))
                .status(Status.GRADUATED)
                .applicant(applicant)
                .build();
        WorkExperience experience = WorkExperience.builder()
                .experienceId(20L)
                .companyName("Weiver")
                .startDate(LocalDate.of(2024, 1, 1))
                .employmentType(EmploymentType.FULL_TIME)
                .position("Backend Engineer")
                .duties("API 개발")
                .isRecognized(true)
                .applicant(applicant)
                .build();
        Certificate certificate = Certificate.builder()
                .certificateId(30L)
                .certificateName("정보처리기사")
                .issuer("한국산업인력공단")
                .acquisitionDate(LocalDate.of(2023, 6, 1))
                .applicant(applicant)
                .build();
        List<EssayAnswer> answers = List.of(
                essayAnswer(50L, 40L, 1, "자기소개", applicant),
                essayAnswer(51L, 41L, 2, "직무 역량", applicant),
                essayAnswer(52L, 42L, 3, "입사 후 포부", applicant)
        );

        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(educationRepository.findAllByApplicant(applicant)).willReturn(List.of(education));
        given(workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant)).willReturn(List.of(experience));
        given(certificateRepository.findAllByApplicant(applicant)).willReturn(List.of(certificate));
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(answers);

        applicantProfileEventService.publishProfileChanged(1L);

        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publish(captor.capture());

        EventEnvelope<?> envelope = captor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventType.APPLICANT_PROFILE_CHANGED);
        assertThat(envelope.data()).isInstanceOf(ApplicantProfileChangedData.class);

        ApplicantProfileChangedData data = (ApplicantProfileChangedData) envelope.data();
        assertThat(data.applicantId()).isEqualTo(1L);
        assertThat(data.educations()).hasSize(1);
        assertThat(data.educations().get(0).schoolName()).isEqualTo("한양대학교");
        assertThat(data.experiences().get(0).companyName()).isEqualTo("Weiver");
        assertThat(data.certifications()).containsExactly("정보처리기사");
        assertThat(data.essay().get(0).question()).isEqualTo("자기소개");
        assertThat(applicant.getProfileSyncStatus()).isEqualTo(ProfileSyncStatus.REQUESTED);
    }

    @Test
    @DisplayName("자기소개서 2문항까지는 발행하지 않고 3문항 완료 후 최초 발행한다")
    void publishProfileChanged_EssaySavedLast_PublishesAfterProfileBecomesComplete() {
        Applicant applicant = Applicant.builder()
                .applicantId(1L)
                .name("홍길동")
                .email("hong@example.com")
                .phoneNumber("010-1234-5678")
                .birthday(LocalDate.of(2000, 1, 1))
                .build();
        Education education = Education.builder()
                .educationId(10L)
                .schoolName("한양대학교")
                .applicant(applicant)
                .build();
        List<EssayAnswer> answers = List.of(
                essayAnswer(50L, 40L, 1, "자기소개", applicant),
                essayAnswer(51L, 41L, 2, "직무 역량", applicant),
                essayAnswer(52L, 42L, 3, "입사 후 포부", applicant)
        );

        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(educationRepository.findAllByApplicant(applicant)).willReturn(List.of(education));
        given(workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant)).willReturn(List.of());
        given(certificateRepository.findAllByApplicant(applicant)).willReturn(List.of());
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant))
                .willReturn(answers.subList(0, 2), answers);

        applicantProfileEventService.publishProfileChanged(1L);

        verifyNoInteractions(domainEventPublisher);
        assertThat(applicant.getProfileSyncStatus()).isEqualTo(ProfileSyncStatus.PENDING);

        applicantProfileEventService.publishProfileChanged(1L);

        verify(domainEventPublisher).publish(any());
        assertThat(applicant.getProfileSyncStatus()).isEqualTo(ProfileSyncStatus.REQUESTED);
    }

    private EssayAnswer essayAnswer(
            Long answerId,
            Long questionId,
            Integer sequence,
            String questionText,
            Applicant applicant
    ) {
        EssayQuestion question = EssayQuestion.builder()
                .questionId(questionId)
                .sequence(sequence)
                .maxLength(500)
                .question(questionText)
                .build();

        return EssayAnswer.builder()
                .answerId(answerId)
                .answer("답변 본문")
                .applicant(applicant)
                .essayQuestion(question)
                .build();
    }

    @Test
    @DisplayName("최초 동기화 이후에는 프로필 일부가 비어도 수정 이벤트를 발행한다")
    void publishProfileChanged_AfterInitialSync_PublishesIncompleteSnapshot() {
        Applicant applicant = Applicant.builder()
                .applicantId(1L)
                .name("홍길동")
                .profileSyncStatus(ProfileSyncStatus.COMPLETED)
                .build();

        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(educationRepository.findAllByApplicant(applicant)).willReturn(List.of());
        given(workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant)).willReturn(List.of());
        given(certificateRepository.findAllByApplicant(applicant)).willReturn(List.of());
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(List.of());

        applicantProfileEventService.publishProfileChanged(1L);

        verify(domainEventPublisher).publish(any());
        assertThat(applicant.getProfileSyncStatus()).isEqualTo(ProfileSyncStatus.REQUESTED);
    }
}
