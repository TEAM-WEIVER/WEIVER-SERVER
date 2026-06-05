package com.weiver.interview.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.interview.dto.response.InterviewTurnDTO;
import com.weiver.interview.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InterviewSessionService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final ApplicantService applicantService;

    /**
     * 지원자의 가장 최근 면접 세션 transcript 전체 조회
     */
    public List<InterviewTurnDTO> getLatestInterviewTurns(String applicantPublicId) {
        Applicant applicant = applicantService.getApplicant(applicantPublicId);

        return interviewSessionRepository.findAllByApplicantOrderByCreateTimeDesc(applicant).stream()
                .findFirst()
                .map(session -> {
                    List<InterviewTurnDTO> transcript = session.getTranscript();
                    if (transcript == null) {
                        return Collections.<InterviewTurnDTO>emptyList();
                    }
                    return transcript;
                })
                .orElseGet(Collections::emptyList);
    }
}
