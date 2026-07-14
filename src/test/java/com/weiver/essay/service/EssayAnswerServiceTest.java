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
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayAnswerRequestDTO requestDTO = new EssayAnswerRequestDTO(List.of(
                new EssayAnswerItemDTO(1L, "answer 1"),
                new EssayAnswerItemDTO(2L, "answer 2"),
                new EssayAnswerItemDTO(3L, "answer 3")
        ));

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayQuestionRepository.findAll()).willReturn(List.of(
                createQuestion(1L, 1),
                createQuestion(2L, 2),
                createQuestion(3L, 3)
        ));

        essayAnswerService.saveEssayAnswer(requestDTO, publicId);

        verify(essayAnswerRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("존재하지 않는 구직자 ID로 자기소개서 저장 시 예외 발생")
    void saveEssayAnswer_ApplicantNotFound_ThrowsException() {
        String invalidPublicId = "2222";
        EssayAnswerRequestDTO requestDTO = new EssayAnswerRequestDTO(List.of(
                new EssayAnswerItemDTO(1L, "answer")
        ));

        given(applicantRepository.findByPublicId(invalidPublicId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> essayAnswerService.saveEssayAnswer(requestDTO, invalidPublicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.APPLICANT_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 문항 ID로 자기소개서 저장 시 예외 발생")
    void saveEssayAnswer_QuestionNotFound_ThrowsException() {
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayAnswerRequestDTO requestDTO = new EssayAnswerRequestDTO(List.of(
                new EssayAnswerItemDTO(1L, "answer 1"),
                new EssayAnswerItemDTO(2L, "answer 2"),
                new EssayAnswerItemDTO(999L, "answer 3")
        ));

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayQuestionRepository.findAll()).willReturn(List.of(
                createQuestion(1L, 1),
                createQuestion(2L, 2),
                createQuestion(3L, 3)
        ));

        assertThatThrownBy(() -> essayAnswerService.saveEssayAnswer(requestDTO, publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ESSAY_QUESTION_NOT_FOUND);
    }

    @Test
    @DisplayName("저장 시 요청 문항 ID가 중복되면 예외 발생")
    void saveEssayAnswer_DuplicateQuestionIds_ThrowsException() {
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayAnswerRequestDTO requestDTO = new EssayAnswerRequestDTO(List.of(
                new EssayAnswerItemDTO(1L, "answer 1"),
                new EssayAnswerItemDTO(1L, "answer 2"),
                new EssayAnswerItemDTO(2L, "answer 3")
        ));

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayQuestionRepository.findAll()).willReturn(List.of(
                createQuestion(1L, 1),
                createQuestion(2L, 2),
                createQuestion(3L, 3)
        ));

        assertThatThrownBy(() -> essayAnswerService.saveEssayAnswer(requestDTO, publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("저장 시 필수 문항 전체가 포함되지 않으면 예외 발생")
    void saveEssayAnswer_RequiredQuestionIdsMismatch_ThrowsException() {
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayAnswerRequestDTO requestDTO = new EssayAnswerRequestDTO(List.of(
                new EssayAnswerItemDTO(1L, "answer 1"),
                new EssayAnswerItemDTO(2L, "answer 2"),
                new EssayAnswerItemDTO(4L, "answer 3")
        ));

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayQuestionRepository.findAll()).willReturn(List.of(
                createQuestion(1L, 1),
                createQuestion(2L, 2),
                createQuestion(4L, 4),
                createQuestion(5L, 5)
        ));

        assertThatThrownBy(() -> essayAnswerService.saveEssayAnswer(requestDTO, publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("저장 시 답변이 문항 최대 길이를 초과하면 예외 발생")
    void saveEssayAnswer_AnswerExceedsMaxLength_ThrowsException() {
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayAnswerRequestDTO requestDTO = new EssayAnswerRequestDTO(List.of(
                new EssayAnswerItemDTO(1L, "123456"),
                new EssayAnswerItemDTO(2L, "answer 2"),
                new EssayAnswerItemDTO(3L, "answer 3")
        ));

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayQuestionRepository.findAll()).willReturn(List.of(
                createQuestion(1L, 1, 5),
                createQuestion(2L, 2),
                createQuestion(3L, 3)
        ));

        assertThatThrownBy(() -> essayAnswerService.saveEssayAnswer(requestDTO, publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("자기소개서 문항과 답변 리스트 조회 성공")
    void getEssayAnswers_Success() {
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayQuestion question1 = createQuestion(1L, 1);
        EssayQuestion question2 = createQuestion(2L, 2);
        List<EssayAnswer> essayAnswers = List.of(
                createAnswer(10L, "answer 1", applicant, question1),
                createAnswer(11L, "answer 2", applicant, question2)
        );

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(essayAnswers);

        EssayAnswerResponseDTO responseDTO = essayAnswerService.getEssayAnswers(publicId);

        assertThat(responseDTO.answers()).hasSize(2);
        assertThat(responseDTO.answers().get(0).answerId()).isEqualTo(10L);
        assertThat(responseDTO.answers().get(0).questionId()).isEqualTo(1L);
        assertThat(responseDTO.answers().get(0).sequence()).isEqualTo(1);
        assertThat(responseDTO.answers().get(0).answer()).isEqualTo("answer 1");
    }

    @Test
    @DisplayName("작성된 자기소개서가 없으면 조회 시 예외 발생")
    void getEssayAnswers_NotWrittenYet_ThrowsException() {
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(List.of());

        assertThatThrownBy(() -> essayAnswerService.getEssayAnswers(publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ESSAY_ANSWER_NOT_FOUND);
    }

    @Test
    @DisplayName("자기소개서 전체 수정 성공")
    void updateEssayAnswers_Success() {
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayAnswer essayAnswer1 = createAnswer(10L, "old answer 1", applicant, createQuestion(1L, 1));
        EssayAnswer essayAnswer2 = createAnswer(11L, "old answer 2", applicant, createQuestion(2L, 2));
        EssayAnswer essayAnswer3 = createAnswer(12L, "old answer 3", applicant, createQuestion(3L, 3));
        EssayAnswerUpdateRequestDTO requestDTO = new EssayAnswerUpdateRequestDTO(List.of(
                new EssayAnswerUpdateItemDTO(10L, "new answer 1"),
                new EssayAnswerUpdateItemDTO(11L, "new answer 2"),
                new EssayAnswerUpdateItemDTO(12L, "new answer 3")
        ));

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant))
                .willReturn(List.of(essayAnswer1, essayAnswer2, essayAnswer3));

        essayAnswerService.updateEssayAnswers(requestDTO, publicId);

        assertThat(essayAnswer1.getAnswer()).isEqualTo("new answer 1");
        assertThat(essayAnswer2.getAnswer()).isEqualTo("new answer 2");
        assertThat(essayAnswer3.getAnswer()).isEqualTo("new answer 3");
    }

    @Test
    @DisplayName("요청 답변 ID가 기존 답변 ID 전체와 다르면 예외 발생")
    void updateEssayAnswers_MismatchedAnswerIds_ThrowsException() {
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        List<EssayAnswer> existingAnswers = List.of(
                createAnswer(10L, "old answer 1", applicant, createQuestion(1L, 1)),
                createAnswer(11L, "old answer 2", applicant, createQuestion(2L, 2)),
                createAnswer(12L, "old answer 3", applicant, createQuestion(3L, 3))
        );
        EssayAnswerUpdateRequestDTO requestDTO = new EssayAnswerUpdateRequestDTO(List.of(
                new EssayAnswerUpdateItemDTO(10L, "new answer 1"),
                new EssayAnswerUpdateItemDTO(11L, "new answer 2"),
                new EssayAnswerUpdateItemDTO(999L, "new answer 3")
        ));

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(existingAnswers);

        assertThatThrownBy(() -> essayAnswerService.updateEssayAnswers(requestDTO, publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("요청 답변 ID가 중복되면 예외 발생")
    void updateEssayAnswers_DuplicateAnswerIds_ThrowsException() {
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        List<EssayAnswer> existingAnswers = List.of(
                createAnswer(10L, "old answer 1", applicant, createQuestion(1L, 1)),
                createAnswer(11L, "old answer 2", applicant, createQuestion(2L, 2)),
                createAnswer(12L, "old answer 3", applicant, createQuestion(3L, 3))
        );
        EssayAnswerUpdateRequestDTO requestDTO = new EssayAnswerUpdateRequestDTO(List.of(
                new EssayAnswerUpdateItemDTO(10L, "new answer 1"),
                new EssayAnswerUpdateItemDTO(10L, "new answer 2"),
                new EssayAnswerUpdateItemDTO(11L, "new answer 3")
        ));

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(existingAnswers);

        assertThatThrownBy(() -> essayAnswerService.updateEssayAnswers(requestDTO, publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("수정 시 답변이 문항 최대 길이를 초과하면 예외 발생")
    void updateEssayAnswers_AnswerExceedsMaxLength_ThrowsException() {
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayAnswer essayAnswer1 = createAnswer(10L, "old answer 1", applicant, createQuestion(1L, 1, 5));
        EssayAnswer essayAnswer2 = createAnswer(11L, "old answer 2", applicant, createQuestion(2L, 2, 20));
        EssayAnswer essayAnswer3 = createAnswer(12L, "old answer 3", applicant, createQuestion(3L, 3, 20));
        EssayAnswerUpdateRequestDTO requestDTO = new EssayAnswerUpdateRequestDTO(List.of(
                new EssayAnswerUpdateItemDTO(10L, "123456"),
                new EssayAnswerUpdateItemDTO(11L, "new answer 2"),
                new EssayAnswerUpdateItemDTO(12L, "new answer 3")
        ));

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant))
                .willReturn(List.of(essayAnswer1, essayAnswer2, essayAnswer3));

        assertThatThrownBy(() -> essayAnswerService.updateEssayAnswers(requestDTO, publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("작성된 자기소개서가 없으면 수정 시 예외 발생")
    void updateEssayAnswers_NotWrittenYet_ThrowsException() {
        String publicId = "3333";
        Applicant applicant = createApplicant(publicId);
        EssayAnswerUpdateRequestDTO requestDTO = new EssayAnswerUpdateRequestDTO(List.of(
                new EssayAnswerUpdateItemDTO(10L, "new answer 1"),
                new EssayAnswerUpdateItemDTO(11L, "new answer 2"),
                new EssayAnswerUpdateItemDTO(12L, "new answer 3")
        ));

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(List.of());

        assertThatThrownBy(() -> essayAnswerService.updateEssayAnswers(requestDTO, publicId))
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
        return createQuestion(questionId, sequence, 500);
    }

    private EssayQuestion createQuestion(Long questionId, Integer sequence, Integer maxLength) {
        return EssayQuestion.builder()
                .questionId(questionId)
                .sequence(sequence)
                .maxLength(maxLength)
                .question(sequence + " question")
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
