package com.weiver.jobposting.dto.response;

import com.weiver.jobposting.domain.JobPosting;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채용 공고 리스트의 단일 아이템 정보")
public record JobPostingsDetails(
        @Schema(description = "공고 고유 ID", example = "101")
        Long jdId,

        @Schema(description = "공고 제목", example = "백엔드 엔지니어 모집")
        String title,

        @Schema(description = "현재 공고 상태 (작성중, 공개중, 마감 등)", example = "공개중",
                allowableValues = {"GRADUATED", "LEAVE_OF_ABSENCE", "GRADUATION_POSTPONED", "ACTIVE"})
        String status,

        @Schema(description = "상위 직무 카테고리", example = "개발")
        String jobCategory,

        @Schema(description = "세부 직무", example = "백엔드 개발자")
        String detailedJob,

        @Schema(description = "이 공고의 읽지 않은 새로운 지원자 수", example = "12")
        Long newApplicantCount
) {
    public static JobPostingsDetails of(JobPosting jobPosting, long newApplicantCount){
        return new JobPostingsDetails(
                jobPosting.getJdId(),
                jobPosting.getTitle(),
                jobPosting.getStatus().getDescription(),
                jobPosting.getJobCategory(),
                jobPosting.getDetailedJob(),
                newApplicantCount
        );
    }
}
