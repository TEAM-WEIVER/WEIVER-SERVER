package com.weiver.applicant.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.WorkExperience;
import com.weiver.applicant.dto.request.post.WorkExperienceRequestDTO;
import com.weiver.applicant.dto.request.put.WorkExperienceUpdateDetailDTO;
import com.weiver.applicant.dto.request.put.WorkExperienceUpdateRequestDTO;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.repository.WorkExperienceRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.matching.dto.response.MajorCareerDTO;
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
public class WorkExperienceService {

    private final WorkExperienceRepository workExperienceRepository;
    private final ApplicantRepository applicantRepository;


    public void saveWorkExperienceInfo(String publicId, WorkExperienceRequestDTO requestDTO) {
        Applicant applicant = getApplicant(publicId);

        List<WorkExperience> experienceList = requestDTO.toEntityList(applicant);

        workExperienceRepository.saveAll(experienceList);
    }

    public void updateWorkExperienceInfo(String publicId, WorkExperienceUpdateRequestDTO requestDTO) {
        Applicant applicant = getApplicant(publicId);

        List<WorkExperience> existingExperiences = workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant);

        Set<Long> requestExperienceIds = requestDTO.workExperienceList().stream()
                .map(WorkExperienceUpdateDetailDTO::workExperienceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<WorkExperience> toDelete = existingExperiences.stream()
                .filter(we -> !requestExperienceIds.contains(we.getExperienceId()))
                .toList();
        workExperienceRepository.deleteAll(toDelete);

        List<WorkExperience> toSave = new ArrayList<>();
        for(WorkExperienceUpdateDetailDTO detailDTO : requestDTO.workExperienceList()) {
            if(detailDTO.workExperienceId() == null) {
                toSave.add(detailDTO.toEntity(applicant));
            } else {
                WorkExperience existingExperience = existingExperiences.stream()
                        .filter(we -> we.getExperienceId().equals(detailDTO.workExperienceId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.EXPERIENCE_NOT_FOUND));

                if(!existingExperience.getApplicant().getPublicId().equals(publicId)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }

                existingExperience.updateWorkExperience(detailDTO);
            }
        }

        if (!toSave.isEmpty()) {
            workExperienceRepository.saveAll(toSave);
        }
    }

    /**
     * 직급 불러오기
     * */
    public String getPositionName(String applicantPublicId) {
        Applicant applicant = getApplicant(applicantPublicId);

        List<WorkExperience> experiences = workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant);
        if(experiences.isEmpty()) {
            return null;
        }
        return experiences.get(0).getPosition();
    }

    /**
     * 경력 요약 조회
     * */
    public List<MajorCareerDTO> getCareerSummary(String applicantPublicId) {
        Applicant applicant = getApplicant(applicantPublicId);

        List<WorkExperience> experiences = workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant);
        return experiences.stream()
                .map(MajorCareerDTO::from)
                .toList();
    }

    private Applicant getApplicant(String publicId) {
        Applicant applicant = applicantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }
}
