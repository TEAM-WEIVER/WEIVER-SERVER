package com.weiver.applicant.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Certificate;
import com.weiver.applicant.dto.request.post.CertificateRequestDTO;
import com.weiver.applicant.dto.request.put.CertificateUpdateDetailDTO;
import com.weiver.applicant.dto.request.put.CertificateUpdateRequestDTO;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.repository.CertificateRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final ApplicantRepository applicantRepository;

    public void saveCertificateInfo(String publicId, CertificateRequestDTO requestDTO) {
        Applicant applicant = getApplicant(publicId);
        List<Certificate> certificateList = requestDTO.toEntityList(applicant);

        certificateRepository.saveAll(certificateList);
    }


    public void updateCertificateInfo(String publicId, CertificateUpdateRequestDTO requestDTO) {
        Applicant applicant = getApplicant(publicId);

        List<Certificate> existingCertificates = certificateRepository.findAllByApplicant(applicant);

        Set<Long> requestCertificateIds = requestDTO.certificateList().stream()
                .map(CertificateUpdateDetailDTO::certificateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Certificate> toDelete = existingCertificates.stream()
                .filter(certificate -> !requestCertificateIds.contains(certificate.getCertificateId()))
                .toList();
        certificateRepository.deleteAll(toDelete);

        List<Certificate> toSave = new ArrayList<>();
        for(CertificateUpdateDetailDTO detailDTO : requestDTO.certificateList()) {
            if(detailDTO.certificateId() == null) {
                toSave.add(detailDTO.toEntity(applicant));
            } else {
                Certificate existingCertificate = existingCertificates.stream()
                        .filter(certificate -> certificate.getCertificateId().equals(detailDTO.certificateId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.CERTIFICATION_NOT_FOUND));

                if(!existingCertificate.getApplicant().getPublicId().equals(publicId)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }

                existingCertificate.updateCertificate(detailDTO);
            }
        }

        if (!toSave.isEmpty()) {
            certificateRepository.saveAll(toSave);
        }
    }




    private Applicant getApplicant(String publicId) {
        Applicant applicant = applicantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }
}
