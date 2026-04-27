package com.weiver.applicant.service;

import com.weiver.applicant.dto.request.put.ApplicantInfoRequestDTO;


public interface ApplicantService {
    void updateApplicantInfo(long applicantId, ApplicantInfoRequestDTO requestDTO);
}
