package com.weiver.jobposting.dto.request;

import com.weiver.jobposting.domain.EmailTemplate;
import com.weiver.jobposting.domain.JobPosting;
import jakarta.validation.constraints.NotNull;

public record EmailTemplateRequestDTO(
         @NotNull String emailTitle,
         @NotNull String emailContent,
         String emailBannerUrl // 디자인에는 이메일 배너 이미지 넣는 부분 없는데 23일 회의 후 결정
) {
    public EmailTemplate toEntity(JobPosting jobPosting){
        return EmailTemplate.builder()
                .emailTitle(this.emailTitle)
                .emailContent(this.emailContent)
                .emailBannerUrl(this.emailBannerUrl)
                .build();
    }
}
