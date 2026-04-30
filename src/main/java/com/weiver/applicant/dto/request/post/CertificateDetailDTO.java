package com.weiver.applicant.dto.request.post;


import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Certificate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "자격증 생성 상세 정보 DTO")
public record CertificateDetailDTO (

        @Schema(description = "자격증 취득 날짜", example = "2000-01-01", type = "string")
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
