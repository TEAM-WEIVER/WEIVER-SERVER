package com.weiver.matching.service;

import com.querydsl.core.Tuple;
import com.weiver.matching.dto.request.ApplicantSearchCondition;
import com.weiver.matching.dto.response.ApplicantListResponseDTO;
import com.weiver.matching.repository.MatchResultRepositoryImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


import static com.weiver.analysis.domain.QCultureReport.cultureReport;
import static com.weiver.analysis.domain.QTechnicalSkillReport.technicalSkillReport;
import static com.weiver.matching.domain.QMatchResult.matchResult;
import static java.util.Objects.requireNonNull;

@Service
@RequiredArgsConstructor
public class MatchResultService {

    private final MatchResultRepositoryImpl matchResultRepository;

    /**
     * 매핑된 구직자 리스트 조회
     * */
    public Page<ApplicantListResponseDTO> searchApplicantList(ApplicantSearchCondition condition, Pageable pageable) {
        Page<Tuple> tuplePage = matchResultRepository.searchApplicantsTuple(condition, pageable);

        // Tuple 순회하면서 DTO 매핑
        return tuplePage.map(tuple -> {
            String position = tuple.get(3, String.class);

            return ApplicantListResponseDTO.of(
                    requireNonNull(tuple.get(matchResult)),
                    requireNonNull(tuple.get(cultureReport)),
                    requireNonNull(tuple.get(technicalSkillReport)),
                    position
            );
        });
    }
}
