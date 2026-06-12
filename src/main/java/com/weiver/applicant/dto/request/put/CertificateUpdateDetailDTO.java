package com.weiver.applicant.dto.request.put;


import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Certificate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "자격증 수정 상세 정보 DTO")
public record CertificateUpdateDetailDTO(

        @Schema(description = "자격증 고유 ID (기존 데이터 수정 시 필수, 신규 추가 시 null)", example = "1")
        Long certificateId,

        @Schema(description = "취득 날짜", example = "2025-11-25", type = "string")
        @NotNull(message = "취득 날짜는 필수 입력값입니다.")
        LocalDate acquisitionDate,

        @Schema(description = "자격증 이름", example = "SQLD")
        @NotBlank(message = "자격증 이름은 필수 입력값입니다.")
        String certificateName,

        @Schema(description = "발급 기관", example = "한국데이터산업진흥원")
        @NotBlank(message = "발급 기관은 필수 입력값입니다.")
        String issuer){
    public Certificate toEntity(Applicant applicant){
        return Certificate.builder()
                .acquisitionDate(this.acquisitionDate)
                .certificateName(this.certificateName)
                .issuer(this.issuer)
                .applicant(applicant)
                .build();
    }
}
