package com.weiver.essay.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.essay.domain.EssayAnswer;
import com.weiver.essay.dto.request.EssayAnswerRequestDTO;
import com.weiver.essay.dto.request.EssayAnswerUpdateRequestDTO;
import com.weiver.essay.dto.response.EssayAnswerResponseDTO;
import com.weiver.essay.repository.EssayAnswerRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EssayAnswerServiceImplTest {

    @Mock
    private EssayAnswerRepository essayAnswerRepository;

    @Mock
    private ApplicantRepository applicantRepository;

    @InjectMocks
    private EssayAnswerServiceImpl essayAnswerService;

    @Test
    @DisplayName("자기소개서 저장 정상 수행")
    void saveEssayAnswer_Success() {
        // Given
        long applicantId = 1L;
        Applicant applicant = Applicant.builder().applicantId(applicantId).build();
        EssayAnswerRequestDTO requestDTO = new EssayAnswerRequestDTO("안녕. 날 뽑아봐.");

        given(applicantRepository.findById(applicantId)).willReturn(Optional.of(applicant));

        // When
        essayAnswerService.saveEssayAnswer(requestDTO, applicantId);

        // Then
        verify(essayAnswerRepository, times(1)).save(any(EssayAnswer.class));
    }

    @Test
    @DisplayName("자기소개서 수정 정상 수행")
    void updateEssayAnswer_Success() {
        // Given
        long applicantId = 1L;
        long answerId = 100L;

        Applicant me = Applicant.builder().applicantId(applicantId).build();

        EssayAnswer myEssay = EssayAnswer.builder()
                .answerId(answerId)
                .answer("안녕. 날 뽑아봐.")
                .applicant(me)
                .build();

        EssayAnswerUpdateRequestDTO updateDTO = new EssayAnswerUpdateRequestDTO("안녕, 나 수정했어.");

        given(essayAnswerRepository.findById(answerId)).willReturn(Optional.of(myEssay));

        // When
        essayAnswerService.updateEssayAnswer(updateDTO, applicantId, answerId);

        // Then
        assertThat(myEssay.getAnswer()).isEqualTo("안녕, 나 수정했어.");
    }

    @Test
    @DisplayName("자기소개서 수정 예외 발생 - 다른 사람의 자소서를 수정하려고 할 때 (FORBIDDEN)")
    void updateEssayAnswer_Forbidden_ThrowsException() {
        // Given
        long myApplicantId = 1L;
        long hackerApplicantId = 2L; //  공격자 ID
        long answerId = 100L;

        Applicant me = Applicant.builder().applicantId(myApplicantId).build();

        // 진짜 주인이 있는 자소서 객체 생성
        EssayAnswer myEssay = EssayAnswer.builder()
                .answerId(answerId)
                .answer("내 소중한 자소서")
                .applicant(me)
                .build();

        EssayAnswerUpdateRequestDTO updateDTO = new EssayAnswerUpdateRequestDTO("해커가 마음대로 바꾼 내용");

        given(essayAnswerRepository.findById(answerId)).willReturn(Optional.of(myEssay));

        // When & Then
        assertThatThrownBy(() -> essayAnswerService.updateEssayAnswer(updateDTO, hackerApplicantId, answerId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("자기소개서 조회 정상 수행")
    void searchEssayAnswer_Success() {
        // Given
        long applicantId = 1L;
        Applicant applicant = Applicant.builder().applicantId(applicantId).build();

        EssayAnswer essayAnswer = EssayAnswer.builder()
                .answerId(10L)
                .answer("이것은 내 자소서입니다.")
                .applicant(applicant)
                .build();

        given(applicantRepository.findById(applicantId)).willReturn(Optional.of(applicant));
        given(essayAnswerRepository.findByApplicant(applicant)).willReturn(Optional.of(essayAnswer));

        // When
        EssayAnswerResponseDTO responseDTO = essayAnswerService.searchEssayAnswer(applicantId);

        // Then
        assertThat(responseDTO.answer()).isEqualTo("이것은 내 자소서입니다.");
    }

    @Test
    @DisplayName("엣지 케이스: 존재하지 않는 회원의 ID로 자소서 저장 시 APPLICANT_NOT_FOUND 예외 발생")
    void saveEssayAnswer_ApplicantNotFound_ThrowsException() {
        // Given
        long invalidApplicantId = 999L; // DB에 없는 유령 회원
        EssayAnswerRequestDTO requestDTO = new EssayAnswerRequestDTO("지원 동기입니다.");

        given(applicantRepository.findById(invalidApplicantId)).willReturn(Optional.empty());

        // When & Then!
        assertThatThrownBy(() -> essayAnswerService.saveEssayAnswer(requestDTO, invalidApplicantId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.APPLICANT_NOT_FOUND);
    }

    @Test
    @DisplayName("엣지 케이스: 존재하지 않는 자소서를 수정하려고 할 때 ESSAY_ANSWER_NOT_FOUND 예외 발생")
    void updateEssayAnswer_EssayNotFound_ThrowsException() {
        // Given
        long applicantId = 1L;
        long invalidAnswerId = 999L;
        EssayAnswerUpdateRequestDTO updateDTO = new EssayAnswerUpdateRequestDTO("수정할 내용");

        given(essayAnswerRepository.findById(invalidAnswerId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> essayAnswerService.updateEssayAnswer(updateDTO, applicantId, invalidAnswerId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ESSAY_ANSWER_NOT_FOUND);
    }

    @Test
    @DisplayName("엣지 케이스: 자소서 수정 시 DTO의 내용이 null일 경우 기존 데이터를 덮어 씌우면 안됨")
    void updateEssayAnswer_NullContent_KeepsExistingData() {
        // Given
        long applicantId = 1L;
        long answerId = 100L;
        Applicant me = Applicant.builder().applicantId(applicantId).build();

        // 기존 자소서 내용
        EssayAnswer myEssay = EssayAnswer.builder()
                .answerId(answerId)
                .answer("내 소중한 기존 자소서 내용")
                .applicant(me)
                .build();

        EssayAnswerUpdateRequestDTO nullUpdateDTO = new EssayAnswerUpdateRequestDTO(null);

        given(essayAnswerRepository.findById(answerId)).willReturn(Optional.of(myEssay));

        // When
        essayAnswerService.updateEssayAnswer(nullUpdateDTO, applicantId, answerId);

        // Then
        assertThat(myEssay.getAnswer()).isEqualTo("내 소중한 기존 자소서 내용");
    }

    @Test
    @DisplayName("엣지 케이스: 회원은 존재하지만, " +
            "아직 자소서를 한 번도 작성하지 않은 상태에서 조회 시 ESSAY_ANSWER_NOT_FOUND 예외 발생")
    void searchEssayAnswer_NotWrittenYet_ThrowsException() {
        // Given
        long applicantId = 1L;
        Applicant applicant = Applicant.builder().applicantId(applicantId).build();

        given(applicantRepository.findById(applicantId)).willReturn(Optional.of(applicant));

        given(essayAnswerRepository.findByApplicant(applicant)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> essayAnswerService.searchEssayAnswer(applicantId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ESSAY_ANSWER_NOT_FOUND);
    }
}