package com.weiver.essay.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.essay.domain.EssayAnswer;
import com.weiver.essay.domain.EssayQuestion;
import com.weiver.essay.dto.request.EssayAnswerItemDTO;
import com.weiver.essay.dto.request.EssayAnswerRequestDTO;
import com.weiver.essay.dto.request.EssayAnswerUpdateItemDTO;
import com.weiver.essay.dto.request.EssayAnswerUpdateRequestDTO;
import com.weiver.essay.dto.response.EssayAnswerResponseDTO;
import com.weiver.essay.repository.EssayAnswerRepository;
import com.weiver.essay.repository.EssayQuestionRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EssayAnswerServiceTest {

    @Mock
    private EssayAnswerRepository essayAnswerRepository;

    @Mock
    private EssayQuestionRepository essayQuestionRepository;

    @Mock
    private ApplicantRepository applicantRepository;

    @InjectMocks
    private EssayAnswerService essayAnswerService;

    @Test
    @DisplayName("자기소개서 답변 목록 저장 성공")
    void saveEssayAnswer_Success() {
        // Given
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayQuestion question = createQuestion(1L, 1);
        EssayAnswerRequestDTO requestDTO = new EssayAnswerRequestDTO(List.of(
                new EssayAnswerItemDTO(1L, "지원 동기입니다.")
        ));

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayQuestionRepository.findById(1L)).willReturn(Optional.of(question));

        // When
        essayAnswerService.saveEssayAnswer(requestDTO, publicId);

        // Then
        verify(essayAnswerRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("존재하지 않는 구직자 ID로 자기소개서 저장 시 예외 발생")
    void saveEssayAnswer_ApplicantNotFound_ThrowsException() {
        // Given
        String invalidPublicId = "2222";
        EssayAnswerRequestDTO requestDTO = new EssayAnswerRequestDTO(List.of(
                new EssayAnswerItemDTO(1L, "지원 동기입니다.")
        ));

        given(applicantRepository.findByPublicId(invalidPublicId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> essayAnswerService.saveEssayAnswer(requestDTO, invalidPublicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.APPLICANT_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 문항 ID로 자기소개서 저장 시 예외 발생")
    void saveEssayAnswer_QuestionNotFound_ThrowsException() {
        // Given
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayAnswerRequestDTO requestDTO = new EssayAnswerRequestDTO(List.of(
                new EssayAnswerItemDTO(999L, "지원 동기입니다.")
        ));

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayQuestionRepository.findById(999L)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> essayAnswerService.saveEssayAnswer(requestDTO, publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ESSAY_QUESTION_NOT_FOUND);
    }

    @Test
    @DisplayName("자기소개서 문항과 답변 리스트 조회 성공")
    void getEssayAnswers_Success() {
        // Given
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayQuestion question1 = createQuestion(1L, 1);
        EssayQuestion question2 = createQuestion(2L, 2);
        List<EssayAnswer> essayAnswers = List.of(
                createAnswer(10L, "지원 동기입니다.", applicant, question1),
                createAnswer(11L, "직무 역량입니다.", applicant, question2)
        );

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(essayAnswers);

        // When
        EssayAnswerResponseDTO responseDTO = essayAnswerService.getEssayAnswers(publicId);

        // Then
        assertThat(responseDTO.answers()).hasSize(2);
        assertThat(responseDTO.answers().get(0).answerId()).isEqualTo(10L);
        assertThat(responseDTO.answers().get(0).questionId()).isEqualTo(1L);
        assertThat(responseDTO.answers().get(0).sequence()).isEqualTo(1);
        assertThat(responseDTO.answers().get(0).answer()).isEqualTo("지원 동기입니다.");
    }

    @Test
    @DisplayName("작성된 자기소개서가 없으면 조회 시 예외 발생")
    void getEssayAnswers_NotWrittenYet_ThrowsException() {
        // Given
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(List.of());

        // When & Then
        assertThatThrownBy(() -> essayAnswerService.getEssayAnswers(publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ESSAY_ANSWER_NOT_FOUND);
    }

    @Test
    @DisplayName("자기소개서 전체 수정 성공")
    void updateEssayAnswers_Success() {
        // Given
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayQuestion question = createQuestion(1L, 1);
        EssayAnswer essayAnswer = createAnswer(10L, "기존 답변입니다.", applicant, question);
        EssayAnswerUpdateRequestDTO requestDTO = new EssayAnswerUpdateRequestDTO(List.of(
                new EssayAnswerUpdateItemDTO(10L, "수정된 답변입니다.")
        ));

        given(essayAnswerRepository.findById(10L)).willReturn(Optional.of(essayAnswer));

        // When
        essayAnswerService.updateEssayAnswers(requestDTO, publicId);

        // Then
        assertThat(essayAnswer.getAnswer()).isEqualTo("수정된 답변입니다.");
    }

    @Test
    @DisplayName("다른 구직자의 자기소개서 수정 시 예외 발생")
    void updateEssayAnswers_Forbidden_ThrowsException() {
        // Given
        Applicant owner = createApplicant("3333");
        EssayQuestion question = createQuestion(1L, 1);
        EssayAnswer essayAnswer = createAnswer(10L, "기존 답변입니다.", owner, question);
        EssayAnswerUpdateRequestDTO requestDTO = new EssayAnswerUpdateRequestDTO(List.of(
                new EssayAnswerUpdateItemDTO(10L, "해커가 바꾼 내용")
        ));

        given(essayAnswerRepository.findById(10L)).willReturn(Optional.of(essayAnswer));

        // When & Then
        assertThatThrownBy(() -> essayAnswerService.updateEssayAnswers(requestDTO, "2222"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("존재하지 않는 답변 ID 수정 시 예외 발생")
    void updateEssayAnswers_EssayNotFound_ThrowsException() {
        // Given
        EssayAnswerUpdateRequestDTO requestDTO = new EssayAnswerUpdateRequestDTO(List.of(
                new EssayAnswerUpdateItemDTO(999L, "수정할 내용")
        ));

        given(essayAnswerRepository.findById(999L)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> essayAnswerService.updateEssayAnswers(requestDTO, "3333"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ESSAY_ANSWER_NOT_FOUND);
    }

    private Applicant createApplicant(String publicId) {
        return Applicant.builder()
                .applicantId(1L)
                .publicId(publicId)
                .build();
    }

    private EssayQuestion createQuestion(Long questionId, Integer sequence) {
        return EssayQuestion.builder()
                .questionId(questionId)
                .sequence(sequence)
                .maxLength(500)
                .question(sequence + "번 문항")
                .build();
    }

    private EssayAnswer createAnswer(Long answerId, String answer, Applicant applicant, EssayQuestion essayQuestion) {
        return EssayAnswer.builder()
                .answerId(answerId)
                .answer(answer)
                .applicant(applicant)
                .essayQuestion(essayQuestion)
                .build();
    }
}
