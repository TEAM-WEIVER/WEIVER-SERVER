package com.weiver.applicant.service;

import com.weiver.applicant.dto.request.post.AwardRequestDTO;
import com.weiver.applicant.dto.request.post.CertificateRequestDTO;
import com.weiver.applicant.dto.request.post.EducationRequestDTO;
import com.weiver.applicant.dto.request.post.WorkExperienceRequestDTO;
import com.weiver.applicant.dto.request.put.ApplicantInfoRequestDTO;


public interface ApplicantService {
    void updateApplicantInfo(long applicantId, ApplicantInfoRequestDTO requestDTO);
    void saveEducationInfo(long applicantId, EducationRequestDTO requestDTO);
    void saveAwardInfo(long applicantId, AwardRequestDTO requestDTO);
    void saveCertificateInfo(long applicantId, CertificateRequestDTO requestDTO);
    void saveWorkExperienceInfo(long applicantId, WorkExperienceRequestDTO requestDTO);
}
