package com.weiver.interview.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.interview.dto.response.InterviewAnalysisSubmitResponse;
import com.weiver.interview.dto.response.InterviewRemainingResponse;
import com.weiver.interview.service.InterviewFlowService;
import com.weiver.interview.service.InterviewSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Interview", description = "AI 면접 세션 REST API")
@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewSessionService interviewSessionService;
    private final InterviewFlowService interviewFlowService;

    @Operation(summary = "AI 면접 잔여 횟수 · 재지원 D-day 조회", description = "로그인 구직자의 면접 진행 가능 여부와 재지원 D-day를 반환한다. 면접은 총 1회이며 완료 후 31일 뒤 재지원할 수 있다.")
    @GetMapping("/remaining")
    public ResponseEntity<ApiResponse<InterviewRemainingResponse>> getRemainingInterview(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal
    ) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        InterviewRemainingResponse response = interviewSessionService.getRemainingInterview(principal.publicId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "면접 분석 요청 (면접 결과 제출)",
            description = "종료된 면접 세션의 최종 평가 생성을 구직자가 직접 요청한다. "
                    + "AI 서버에 면접 스크립트 저장을 요청하고, 저장이 끝나면 서버가 이어서 리포트 생성을 요청한다.\n\n"
                    + "- 면접이 종료(FINISHED)된 세션만 요청할 수 있다(그 전이면 `INTERVIEW_NOT_FINISHED`).\n"
                    + "- 이미 요청했거나 복구 불가 상태면 `INTERVIEW_ANALYSIS_ALREADY_REQUESTED`로 거절된다.\n"
                    + "- **제출 시점부터 1개월간 면접을 다시 볼 수 없다.** 응답의 `next_available_interview_at`로 "
                    + "재응시 가능 시각을 바로 알 수 있고, 제출 대상 세션은 "
                    + "`GET /api/interviews/remaining`의 `pendingSubmissionSessionId`로 판단한다."
    )
    @PostMapping("/{interviewSessionId}/analysis")
    public ResponseEntity<ApiResponse<InterviewAnalysisSubmitResponse>> requestInterviewAnalysis(
            @Parameter(description = "분석을 요청할 면접 세션 ID(UUID)") @PathVariable UUID interviewSessionId,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal
    ) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        InterviewAnalysisSubmitResponse response =
                interviewFlowService.requestAnalysis(interviewSessionId, principal.publicId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
