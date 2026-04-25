package com.weiver.applicant.dto.request.put;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Award;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;


/**
 * 수정 시 스냅샷 느낌으로 DTO 사용.
 * 즉, 화면에 보여줄 데이터는 전부 필수값으로 받고, 수정 시에도 전부 업데이트 하는 방식.
 * */
public record AwardUpdateDetailDTO(
    Long awardId,
    @NotBlank(message = "취득 날짜는 필수 입력값입니다.")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "취득 날짜는 YYYY-MM-DD 형식이어야 합니다.")
    LocalDate awardDate,
    @NotBlank(message = "수상 이름은 필수 입력값입니다.")
    String awardName,
    @NotBlank(message = "발급 기관은 필수 입력값입니다.")
    String issuer){
    /**
     * Award 추가 시 사용 할 메소드
     * */
    public Award toEntity(Applicant applicant) {
        return Award.builder()
                .awardDate(this.awardDate())
                .awardName(this.awardName())
                .issuer(this.issuer())
                .applicant(applicant)
                .build();
    }
}
