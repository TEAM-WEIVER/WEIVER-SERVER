package com.weiver.applicant.dto.request.put;


import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Certificate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CertificateUpdateDetailDTO(
    long certificateId,
    @NotBlank(message = "취득 날짜는 필수 입력값입니다.")
    @Pattern(regexp = "^\\d{4}\\.\\d{2}\\.\\d{2}$", message = "취득 날짜는 YYYY.MM.DD 형식이어야 합니다.")
    String acquisitionDate,
    @NotBlank(message = "자격증 이름은 필수 입력값입니다.")
    String certificateName,
    @NotBlank(message = "발급 기관은 필수 입력값입니다.")
    String issuer){
    public Certificate toEntity(Applicant applicant){
        return Certificate.builder()
                .acquisitionDate(LocalDate.parse(this.acquisitionDate))
                .certificateName(this.certificateName)
                .issuer(this.issuer)
                .applicant(applicant)
                .build();
    }
}
