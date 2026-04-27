package com.weiver.applicant.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.dto.request.put.ApplicantInfoRequestDTO;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
@RequiredArgsConstructor
public class ApplicantServiceImpl implements  ApplicantService {

    private final ApplicantRepository applicantRepository;

    @Override
    @Transactional
    public void updateApplicantInfo(long applicantId, ApplicantInfoRequestDTO requestDTO) {

        Applicant applicant = applicantRepository.findByApplicantId(applicantId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        applicant.updateInfo(requestDTO);
    }
}
