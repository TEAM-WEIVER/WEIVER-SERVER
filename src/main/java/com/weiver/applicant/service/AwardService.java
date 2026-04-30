package com.weiver.applicant.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Award;
import com.weiver.applicant.dto.request.post.AwardRequestDTO;
import com.weiver.applicant.dto.request.put.AwardUpdateDetailDTO;
import com.weiver.applicant.dto.request.put.AwardUpdateRequestDTO;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.repository.AwardRepository;
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
public class AwardService {
    private final AwardRepository awardRepository;
    private final ApplicantRepository applicantRepository;

    public void saveAwardInfo(long applicantId, AwardRequestDTO requestDTO) {
        Applicant applicant = getApplicant(applicantId);

        List<Award> awardList = requestDTO.toEntityList(applicant);

        awardRepository.saveAll(awardList);
    }


    public void updateAwardInfo(long applicantId, AwardUpdateRequestDTO requestDTO) {
        Applicant applicant = getApplicant(applicantId);

        List<Award> existingAwards = awardRepository.findAllByApplicant(applicant);

        // DTO 에서 수정 대상만 추출
        Set<Long> requestAwardIds = requestDTO.awardList().stream()
                .map(AwardUpdateDetailDTO::awardId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 삭제 할 데이터
        List<Award> toDelete = existingAwards.stream()
                .filter(award -> !requestAwardIds.contains(award.getAwardId()))
                .toList();
        awardRepository.deleteAll(toDelete);

        List<Award> toSave = new ArrayList<>();
        for(AwardUpdateDetailDTO detailDTO : requestDTO.awardList()) {
            if(detailDTO.awardId() == null) {
                toSave.add(detailDTO.toEntity(applicant));
            } else {
                Award existingAward = existingAwards.stream()
                        .filter(award -> award.getAwardId().equals(detailDTO.awardId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.AWARD_NOT_FOUND));

                if(!existingAward.getApplicant().getApplicantId().equals(applicantId)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }

                existingAward.updateAward(detailDTO);
            }
        }

        if (!toSave.isEmpty()) {
            awardRepository.saveAll(toSave);
        }
    }



    private Applicant getApplicant(long applicantId) {
        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }


}
