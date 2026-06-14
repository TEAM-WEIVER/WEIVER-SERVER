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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


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

        Map<Long, EssayQuestion> requiredQuestionMap = getRequiredQuestionMap(requestDTO);

        List<EssayAnswer> essayAnswers = requestDTO.answers().stream()
                .map(answerItem -> createEssayAnswer(answerItem, applicant, requiredQuestionMap.get(answerItem.questionId())))
                .toList();

        essayAnswerRepository.saveAll(essayAnswers);
    }

    public void updateEssayAnswers(EssayAnswerUpdateRequestDTO requestDTO, String publicId) {
        Applicant applicant = getApplicant(publicId);
        List<EssayAnswer> existingAnswers = essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant);
        if (existingAnswers.isEmpty()) {
            throw new BusinessException(ErrorCode.ESSAY_ANSWER_NOT_FOUND);
        }

        Map<Long, EssayAnswer> existingAnswerMap = existingAnswers.stream()
                .collect(Collectors.toMap(EssayAnswer::getAnswerId, Function.identity()));

        Set<Long> requestAnswerIds = requestDTO.answers().stream()
                .map(EssayAnswerUpdateItemDTO::answerId)
                .collect(Collectors.toSet());

        if (requestAnswerIds.size() != requestDTO.answers().size()
                || !requestAnswerIds.equals(existingAnswerMap.keySet())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        requestDTO.answers().forEach(answerItem -> updateAnswer(answerItem, existingAnswerMap.get(answerItem.answerId())));
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

    private Map<Long, EssayQuestion> getRequiredQuestionMap(EssayAnswerRequestDTO requestDTO) {
        List<EssayQuestion> requiredQuestions = essayQuestionRepository.findAll();
        if (requiredQuestions.isEmpty()) {
            throw new BusinessException(ErrorCode.ESSAY_QUESTION_NOT_FOUND);
        }

        Map<Long, EssayQuestion> requiredQuestionMap = requiredQuestions.stream()
                .collect(Collectors.toMap(EssayQuestion::getQuestionId, Function.identity()));

        Set<Long> requestQuestionIds = requestDTO.answers().stream()
                .map(EssayAnswerItemDTO::questionId)
                .collect(Collectors.toSet());

        if (requestQuestionIds.size() != requestDTO.answers().size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        if (!requiredQuestionMap.keySet().containsAll(requestQuestionIds)) {
            throw new BusinessException(ErrorCode.ESSAY_QUESTION_NOT_FOUND);
        }

        if (!requestQuestionIds.equals(requiredQuestionMap.keySet())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        return requiredQuestionMap;
    }

    private EssayAnswer createEssayAnswer(EssayAnswerItemDTO answerItem, Applicant applicant, EssayQuestion essayQuestion) {
        validateAnswerLength(answerItem.answer(), essayQuestion);

        return EssayAnswer.builder()
                .answer(answerItem.answer())
                .applicant(applicant)
                .essayQuestion(essayQuestion)
                .build();
    }

    private void validateAnswerLength(String answer, EssayQuestion essayQuestion) {
        if (answer == null || answer.length() > essayQuestion.getMaxLength()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private void updateAnswer(EssayAnswerUpdateItemDTO answerItem, EssayAnswer essayAnswer) {
        validateAnswerLength(answerItem.answer(), essayAnswer.getEssayQuestion());
        essayAnswer.updateAnswer(answerItem);
    }
}
