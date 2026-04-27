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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
@RequiredArgsConstructor
public class EssayAnswerServiceImpl implements EssayAnswerService {

    private final EssayAnswerRepository essayAnswerRepository;
    private final ApplicantRepository applicantRepository;


    @Override
    public void saveEssayAnswer(EssayAnswerRequestDTO requestDTO, long applicantId) {
        Applicant applicant = getApplicant(applicantId);
        EssayAnswer essayAnswer = requestDTO.toEntity(applicant);

        essayAnswerRepository.save(essayAnswer);
    }

    @Override
    public void updateEssayAnswer(EssayAnswerUpdateRequestDTO requestDTO, long applicantId, long answerId) {

        EssayAnswer essayAnswer = essayAnswerRepository.findById(answerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESSAY_ANSWER_NOT_FOUND));

        if (!essayAnswer.getApplicant().getApplicantId().equals(applicantId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if(requestDTO != null){
            essayAnswer.updateAnswer(requestDTO);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EssayAnswerResponseDTO searchEssayAnswer(long applicantId) {
        Applicant applicant = getApplicant(applicantId);

        EssayAnswer essayAnswer = essayAnswerRepository.findByApplicant(applicant)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        EssayAnswerResponseDTO responseDTO = EssayAnswerResponseDTO.from(essayAnswer);
        return responseDTO;
    }

    private Applicant getApplicant(long applicantId) {
        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }
}
