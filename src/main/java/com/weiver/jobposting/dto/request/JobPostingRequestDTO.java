package com.weiver.jobposting.dto.request;

import com.weiver.company.domain.Company;
import com.weiver.jobposting.domain.EmailTemplate;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.type.JobPostingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "채용 공고 생성 요청 DTO (multipart/form-data의 requestDTO 파트에 사용)")
public record JobPostingRequestDTO(
        @Schema(description = "채용 공고 제목", example = "2026년 상반기 백엔드 엔지니어 채용")
        @NotBlank String title,

        @Schema(description = "공고 마감일 (YYYY-MM-DD 형식)", example = "2026-05-31")
        @NotNull LocalDate deadline,

        @Schema(description = "상위 직무 카테고리", example = "개발")
        @NotBlank String jobCategory,

        @Schema(description = "세부 직무", example = "백엔드 개발자")
        @NotBlank String detailedJob,

        @Schema(description = "주요 업무 소개", example = "대규모 트래픽 처리 및 서버 아키텍처 설계")
        String jobDescription,

        @Schema(description = "지원 자격", example = "관련 학과 전공자 또는 그에 준하는 지식 보유자")
        String qualifications,

        @Schema(description = "필수 요구 사항", example = "Java, Spring Boot 프레임워크 사용 경험 3년 이상")
        String requirements,

        @Schema(description = "우대 사항", example = "MSA 기반 아키텍처 설계 및 운영 경험 우대")
        String preferredQualifications,

        @Schema(description = "역량 평가 우선순위 태그 배열", example = "[\"문제해결력\", \"성장가능성\", \"커뮤니케이션\"]")
        List<String> competencyPriorities,

        @Schema(description = "요구 기술 스택 태그 배열", example = "[\"Java\", \"Spring Boot\", \"MySQL\"]")
        List<String> requiredTechs,

        @Schema(description = "지원자 성향 우선순위 배열", example = "[\"자율·혁신\", \"안정·질서\"]")
        List<String> traitPriorities,

        @Schema(description = "합격/불합격 안내 자동 발송 이메일 제목", example = "[Weaver] 서류 전형 결과 안내")
        @NotBlank String emailTitle,

        @Schema(description = "안내 이메일 본문 내용", example = "안녕하세요. Weaver 지원에 감사드립니다...")
        @NotBlank String emailContent

) {
    public JobPosting toJobPosting(Company company){
        return JobPosting.builder()
                .title(this.title)
                .jobCategory(this.jobCategory)
                .deadline(this.deadline)
                .status(JobPostingStatus.ACTIVE)
                .jobDescription(this.jobDescription)
                .qualifications(this.qualifications)
                .requirements(this.requirements)
                .preferredQualifications(this.preferredQualifications)
                .competencyPriorities(this.competencyPriorities)
                .requiredTech(this.requiredTechs)
                .traitPriorities(this.traitPriorities)
                .build();
    }

    public EmailTemplate toEmailTemplate(JobPosting jobPosting, String emailBannerUrl){
        return EmailTemplate.builder()
                .emailTitle(this.emailTitle)
                .emailContent(this.emailContent)
                .emailBannerUrl(emailBannerUrl)
                .jobPosting(jobPosting)
                .build();
    }
}
