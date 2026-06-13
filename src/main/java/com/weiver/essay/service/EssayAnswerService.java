package com.weiver.essay.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.essay.domain.EssayAnswer;
import com.weiver.essay.domain.EssayQuestion;
import com.weiver.essay.dto.request.EssayAnswerItemDTO;
import com.weiver.essay.dto.request.EssayAnswerRequestDTO;
import com.weiver.essay.dto.request.EssayAnswerUpdateItemDTO;
import com.weiver.essay.dto.request.EssayAnswerUpdateRequestDTO;
import com.weiver.essay.dto.response.EssayAnswerItemResponseDTO;
import com.weiver.essay.dto.response.EssayAnswerResponseDTO;
import com.weiver.essay.repository.EssayAnswerRepository;
import com.weiver.essay.repository.EssayQuestionRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@Transactional
@RequiredArgsConstructor
public class EssayAnswerService {

    private final EssayAnswerRepository essayAnswerRepository;
    private final EssayQuestionRepository essayQuestionRepository;
    private final ApplicantRepository applicantRepository;


    public void saveEssayAnswer(EssayAnswerRequestDTO requestDTO, String publicId) {
        Applicant applicant = getApplicant(publicId);

        if (essayAnswerRepository.existsByApplicant(applicant)) {
            throw new BusinessException(ErrorCode.ESSAY_ANSWER_ALREADY_EXISTS);
        }

        List<EssayAnswer> essayAnswers = requestDTO.answers().stream()
                .map(answerItem -> createEssayAnswer(answerItem, applicant))
                .toList();

        essayAnswerRepository.saveAll(essayAnswers);
    }

    public void updateEssayAnswers(EssayAnswerUpdateRequestDTO requestDTO, String publicId) {
        requestDTO.answers().forEach(answerItem -> updateEssayAnswer(answerItem, publicId));
    }

    @Transactional(readOnly = true)
    public EssayAnswerResponseDTO getEssayAnswers(String publicId) {
        Applicant applicant = getApplicant(publicId);

        List<EssayAnswer> essayAnswers = essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant);
        if (essayAnswers.isEmpty()) {
            throw new BusinessException(ErrorCode.ESSAY_ANSWER_NOT_FOUND);
        }

        List<EssayAnswerItemResponseDTO> answers = essayAnswers.stream()
                .map(EssayAnswerItemResponseDTO::from)
                .toList();

        return new EssayAnswerResponseDTO(answers);
    }

    private Applicant getApplicant(String publicId) {
        Applicant applicant = applicantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }

    private EssayAnswer createEssayAnswer(EssayAnswerItemDTO answerItem, Applicant applicant) {
        EssayQuestion essayQuestion = essayQuestionRepository.findById(answerItem.questionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ESSAY_QUESTION_NOT_FOUND));

        return EssayAnswer.builder()
                .answer(answerItem.answer())
                .applicant(applicant)
                .essayQuestion(essayQuestion)
                .build();
    }

    private void updateEssayAnswer(EssayAnswerUpdateItemDTO answerItem, String publicId) {
        EssayAnswer essayAnswer = essayAnswerRepository.findById(answerItem.answerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ESSAY_ANSWER_NOT_FOUND));

        if (!essayAnswer.getApplicant().getPublicId().equals(publicId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        essayAnswer.updateAnswer(answerItem);
    }
}
