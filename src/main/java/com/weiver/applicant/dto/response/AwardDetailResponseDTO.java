package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.Award;

public record AwardDetailResponseDTO(
        long awardId,
        String awardName,
        String awardDate,
        String issuer
) {
    public static AwardDetailResponseDTO from(Award award){
        return new AwardDetailResponseDTO(
                award.getAwardId(),
                award.getAwardName(),
                award.getAwardDate().toString(),
                award.getIssuer()
        );
    }
}