package com.weiver.matching.controller;

import com.weiver.analysis.dto.response.CultureFitSummaryDTO;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.exception.ErrorResponse;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.matching.dto.response.ApplicantCardResponseDTO;
import com.weiver.matching.dto.response.DocumentTabSummaryDTO;
import com.weiver.matching.dto.response.SkillFitSummaryDTO;
import com.weiver.matching.dto.response.SummaryCardResponseDTO;
import com.weiver.matching.service.MatchResultReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "매칭 리포트 API", description = "채용 공고별 지원자의 카드 요약, AI 요약, 스킬핏, 컬처핏 리포트를 조회하는 API")
@RestController
@RequestMapping("/api/job-postings/{jdId}/applicants/{applicantPublicId}/reports")
@RequiredArgsConstructor
public class MatchResultReportController {

    private final MatchResultReportService matchResultReportService;

    @Operation(
            summary = "지원자 카드 요약 조회",
            description = "특정 채용 공고에 지원한 지원자의 프로필, 스킬핏 점수, 컬처핏 스타일, 보유 기술 태그, 기업 담당자 메모를 카드 형태로 조회합니다.<br>" +
                    "**[보안]** 로그인한 기업 담당자가 소유한 채용 공고에 지원한 지원자에 대해서만 조회할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "지원자 카드 요약 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApplicantCardResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "매칭 결과, 지원자 또는 분석 리포트를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/card-summary")
    public ResponseEntity<ApiResponse<ApplicantCardResponseDTO>> getCardSummary(
            @Parameter(description = "채용 공고 고유 ID", example = "1")
            @PathVariable("jdId") Long jdId,

            @Parameter(description = "조회할 지원자의 공개 Public ID", example = "applicant-8f4a2c1e")
            @PathVariable("applicantPublicId") String applicantPublicId,

            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal){
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        ApplicantCardResponseDTO responseDTO = matchResultReportService.getCardSummary(jdId, applicantPublicId, principal.publicId());
        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }

    @Operation(
            summary = "지원자 AI 요약 조회",
            description = "특정 지원자에 대해 AI가 생성한 종합 평가 요약과 주요 경력 목록을 조회합니다.<br>" +
                    "지원자 상세 화면의 요약 카드 또는 상단 AI 코멘트 영역에서 사용할 수 있는 데이터입니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/ai-summary")
    public ResponseEntity<ApiResponse<SummaryCardResponseDTO>> getAiSummary(
            @Parameter(description = "채용 공고 고유 ID", example = "1")
            @PathVariable("jdId") Long jdId,

            @Parameter(description = "조회할 지원자의 공개 Public ID", example = "applicant-8f4a2c1e")
            @PathVariable("applicantPublicId") String applicantPublicId,

            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal){
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        SummaryCardResponseDTO responseDTO = matchResultReportService.getSummaryCard(jdId, applicantPublicId, principal.publicId());
        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }

    @Operation(
            summary = "지원자 스킬핏 요약 조회",
            description = "채용 공고의 역량 우선순위와 지원자의 AI 역량 분석 결과를 기준으로 스킬핏 요약 정보를 조회합니다.<br>" +
                    "응답에는 역량별 퍼센트 점수, AI 역량 평가 문장, 보유 기술 태그, 전체 매칭률이 포함됩니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/skill-fit")
    public ResponseEntity<ApiResponse<SkillFitSummaryDTO>> getSkillFitSummary(
            @Parameter(description = "채용 공고 고유 ID", example = "1")
            @PathVariable("jdId") Long jdId,

            @Parameter(description = "조회할 지원자의 공개 Public ID", example = "applicant-8f4a2c1e")
            @PathVariable("applicantPublicId") String applicantPublicId,

            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {

        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        SkillFitSummaryDTO responseDTO = matchResultReportService.getSkillFitSummary(
                jdId,
                applicantPublicId,
                principal.publicId()
        );

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }

    @Operation(
            summary = "지원자 컬처핏 요약 조회",
            description = "지원자의 컬처핏 스타일, 채용 공고와의 적합 상태, 상위 문화 성향 축, AI 요약, 4개 문화 축 상세 점수를 조회합니다.<br>" +
                    "응답의 퍼센트 값은 0부터 100까지의 정수이며, 컬처핏 리포트 화면의 차트와 상세 분석 영역에서 사용할 수 있습니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/culture-fit")
    public ResponseEntity<ApiResponse<CultureFitSummaryDTO>> getCultureFitSummary(
            @Parameter(description = "채용 공고 고유 ID", example = "1")
            @PathVariable("jdId") Long jdId,

            @Parameter(description = "조회할 지원자의 공개 Public ID", example = "applicant-8f4a2c1e")
            @PathVariable("applicantPublicId") String applicantPublicId,

            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {

        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        CultureFitSummaryDTO responseDTO = matchResultReportService.getCultureFitSummary(
                jdId,
                applicantPublicId,
                principal.publicId()
        );

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }

    @Operation(
            summary = "지원자 제출 서류 및 면접 스크립트 요약 조회",
            description = "지원자의 포트폴리오 링크와 기술면접/컬처핏 면접 스크립트 목록을 조회합니다.<br>" +
                    "포트폴리오가 없는 경우 포트폴리오 필드는 null 값으로 반환되며, 면접 스크립트가 없으면 빈 목록이 반환될 수 있습니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/document-summary")
    public ResponseEntity<ApiResponse<DocumentTabSummaryDTO>> getDocumentTabSummary(
            @Parameter(description = "채용 공고 고유 ID", example = "1")
            @PathVariable("jdId") Long jdId,

            @Parameter(description = "조회할 지원자의 공개 Public ID", example = "applicant-8f4a2c1e")
            @PathVariable("applicantPublicId") String applicantPublicId,

            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {

        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        DocumentTabSummaryDTO responseDTO = matchResultReportService.getDocumentTabSummary(
                jdId,
                applicantPublicId,
                principal.publicId()
        );

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }
}
