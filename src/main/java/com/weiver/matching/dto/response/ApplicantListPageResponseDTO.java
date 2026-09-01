package com.weiver.matching.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "매칭된 지원자 리스트 페이징 응답 DTO")
public record ApplicantListPageResponseDTO(
        @Schema(description = "현재 페이지의 지원자 데이터 목록")
        List<ApplicantListResponseDTO> content,

        @Schema(description = "페이징 메타 정보")
        PageInfo pageable
) {
    public static ApplicantListPageResponseDTO from(Page<ApplicantListResponseDTO> page) {
        return new ApplicantListPageResponseDTO(
                page.getContent(),
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
