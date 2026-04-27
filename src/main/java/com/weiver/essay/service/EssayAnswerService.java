package com.weiver.essay.service;

import com.weiver.essay.dto.request.EssayAnswerRequestDTO;
import com.weiver.essay.dto.request.EssayAnswerUpdateRequestDTO;
import com.weiver.essay.dto.response.EssayAnswerResponseDTO;


public interface EssayAnswerService {
    void saveEssayAnswer(EssayAnswerRequestDTO requestDTO, long applicantId);
    void updateEssayAnswer(EssayAnswerUpdateRequestDTO requestDTO, long applicantId, long answerId);
    EssayAnswerResponseDTO searchEssayAnswer(long applicantId);
}
