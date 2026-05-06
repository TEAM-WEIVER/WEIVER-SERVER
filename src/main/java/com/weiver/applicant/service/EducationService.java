package com.weiver.applicant.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Education;
import com.weiver.applicant.dto.request.post.EducationRequestDTO;
import com.weiver.applicant.dto.request.put.EducationUpdateDetailDTO;
import com.weiver.applicant.dto.request.put.EducationUpdateRequestDTO;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.repository.EducationRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class EducationService {

    private final EducationRepository educationRepository;
    private final ApplicantRepository applicantRepository;

    public void saveEducationInfo(String publicId, EducationRequestDTO requestDTO) {

        Applicant applicant = getApplicant(publicId);

        List<Education> educationList = requestDTO.toEntityList(applicant);

        educationRepository.saveAll(educationList);
    }


    public void updateEducationInfo(String publicId, EducationUpdateRequestDTO requestDTO) {
        Applicant applicant = getApplicant(publicId);

        List<Education> existingEducations = educationRepository.findAllByApplicant(applicant);

        // DTO 에서 수정 대상만 추출
        Set<Long> requestEducationIds = requestDTO.educationList().stream()
                .map(EducationUpdateDetailDTO::educationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 삭제 할 데이터
        List<Education> toDelete = existingEducations.stream()
                .filter(education -> !requestEducationIds.contains(education.getEducationId()))
                .toList();

        educationRepository.deleteAll(toDelete);

        List<Education> toSave = new ArrayList<>();

        for (EducationUpdateDetailDTO detailDTO : requestDTO.educationList()) {
            // educationId 가 없으면 새로운 값 추가
            if (detailDTO.educationId() == null) {
                toSave.add(detailDTO.toEntity(applicant));
            } else {
                Education existingEducation = existingEducations.stream()
                        .filter(education -> education.getEducationId().equals(detailDTO.educationId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.EDUCATION_NOT_FOUND));

                if (!existingEducation.getApplicant().getPublicId().equals(publicId)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }

                existingEducation.updateEducation(detailDTO);
            }
        }

        if (!toSave.isEmpty()) {
            educationRepository.saveAll(toSave);
        }
    }



    private Applicant getApplicant(String publicId) {
        Applicant applicant = applicantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }
}
