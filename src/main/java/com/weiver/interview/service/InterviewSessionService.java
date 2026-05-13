package com.weiver.interview.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.type.InterviewType;
import com.weiver.matching.dto.response.InterviewScriptDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InterviewSessionService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final ApplicantService applicantService;

    /**
     * 지원자의 면접 타입별(기술/컬처) 스크립트 조회
     */
    public List<InterviewScriptDTO> getInterviewScripts(String applicantPublicId, String interviewTypeStr) {
        Applicant applicant = applicantService.getApplicant(applicantPublicId);

        InterviewType type = parseInterviewType(interviewTypeStr);

        return interviewSessionRepository.findByApplicantAndInterviewType(applicant, type)
                .map(session -> {
                    List<InterviewScriptDTO> transcript = session.getTranscript();
                    return transcript != null ? transcript : Collections.<InterviewScriptDTO>emptyList();
                })
                .orElseGet(Collections::emptyList);
    }

    /**
     * 안전한 Enum 변환 헬퍼 메서드
     */
    private InterviewType parseInterviewType(String typeStr) {
        if (!StringUtils.hasText(typeStr)) {
            throw new BusinessException(ErrorCode.INVALID_INTERVIEW_TYPE);
        }

        try {
            return InterviewType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INTERVIEW_TYPE);
        }
    }
}