package com.weiver.jobposting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "채용 공고 리스트 페이징 응답 DTO")
public record JobPostingPageResponseDTO(
        @Schema(description = "현재 페이지의 채용 공고 데이터 목록")
        List<JobPostingsDetails> content,

        @Schema(description = "페이징 메타 정보")
        PageInfo pageable
) {
    public static JobPostingPageResponseDTO of(Page<?> page, List<JobPostingsDetails> content) {
        return new JobPostingPageResponseDTO(
                content,
                new PageInfo(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages(),
                        page.isLast()
                )
        );
    }
}