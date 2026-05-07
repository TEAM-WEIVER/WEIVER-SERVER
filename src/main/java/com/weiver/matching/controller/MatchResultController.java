package com.weiver.matching.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.matching.dto.request.ApplicantSearchCondition;
import com.weiver.matching.dto.response.ApplicantListResponseDTO;
import com.weiver.matching.service.MatchResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-postings/{jdId}/applicants")
@RequiredArgsConstructor
public class MatchResultController {

    private final MatchResultService matchResultService;

    @Operation(
            summary = "지원자 리스트 조회 (필터링 및 검색)",
            description = "특정 공고에 지원한 지원자 리스트를 스킬핏, 컬처핏, 기술 스택, 이름으로 필터링하여 페이징 조회합니다.<br>" +
                    "**[참고]** 기술 스택 다중 필터링 시 `techStacks=React&techStacks=Java` 형태로 요청하세요."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ApplicantListResponseDTO>>> searchApplicants(
            @Parameter(description = "채용 공고 고유 ID", example = "1")
            @PathVariable Long jdId,

            @Parameter(description = "지원자 이름 검색 키워드", example = "홍길동")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "스킬핏 점수 최소값 (ex. 80 이상)", example = "80")
            @RequestParam(required = false) Integer skillScoreMin,

            @Parameter(description = "컬처핏 스타일", example = "추진형 실행가")
            @RequestParam(required = false) String cultureStyle,

            @Parameter(description = "기술 스택 리스트", example = "[\"React\", \"Figma\"]")
            @RequestParam(required = false) List<String> techStacks,

            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지당 데이터 개수", example = "10")
            @RequestParam(defaultValue = "10") int size,

            @AuthenticationPrincipal AuthenticatedPrincipal principal) {

        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        ApplicantSearchCondition condition = new ApplicantSearchCondition(
                jdId,
                keyword,
                skillScoreMin,
                cultureStyle,
                techStacks
        );

        PageRequest pageable = PageRequest.of(page, size);

        Page<ApplicantListResponseDTO> responseDTOS = matchResultService.searchApplicantList(condition, pageable);

        return ResponseEntity.ok(ApiResponse.success(responseDTOS));
    }
}
