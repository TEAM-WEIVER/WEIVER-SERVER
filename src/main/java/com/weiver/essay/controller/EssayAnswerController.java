package com.weiver.essay.controller;

import com.weiver.essay.dto.request.EssayAnswerRequestDTO;
import com.weiver.essay.dto.request.EssayAnswerUpdateRequestDTO;
import com.weiver.essay.dto.response.EssayAnswerResponseDTO;
import com.weiver.essay.service.EssayAnswerService;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "자기소개서(Essay) API", description = "구직자의 자기소개서 조회, 저장 및 수정 API입니다.")
@RestController
@RequestMapping("/essay-answers")
@RequiredArgsConstructor
public class EssayAnswerController {

    private final EssayAnswerService essayAnswerService;

    @Operation(
            summary = "자기소개서 초기 저장",
            description = "구직자가 최초로 자기소개서를 작성하고 저장할 때 호출합니다.<br>" +
                    "**주의:** 요청 헤더에 JWT Access Token이 포함되어야 하며, 해당 토큰의 구직자 ID로 매핑되어 저장됩니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> saveEssayanswer(
            @RequestBody @Valid EssayAnswerRequestDTO requestDTO,
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        essayAnswerService.saveEssayAnswer(requestDTO, principal.publicId());
        return ResponseEntity.ok(ApiResponse.success("자기소개서 저장 성공했습니다."));
    }

    @Operation(
            summary = "자기소개서 내용 수정",
            description = "이미 작성된 자기소개서의 내용을 수정합니다.<br>" +
                    "**보안:** URL의 `answerId`와 현재 로그인한 사용자의 ID를 검증하여, 본인의 자기소개서만 수정할 수 있습니다."
    )
    @PatchMapping("/{answerId}")
    public ResponseEntity<ApiResponse<Void>> updateEssayanswer(
            @RequestBody @Valid EssayAnswerUpdateRequestDTO requestDTO,
            @Parameter(description = "수정할 자기소개서의 고유 ID (PK)", example = "1") @PathVariable long answerId,
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        essayAnswerService.updateEssayAnswer(requestDTO, principal.publicId(), answerId);

        return ResponseEntity.ok(ApiResponse.success("자기소개서 수정 성공했습니다."));
    }

    @Operation(
            summary = "내 자기소개서 조회",
            description = "마이페이지 또는 자기소개서 관리 탭에서 현재 로그인한 구직자의 자기소개서 내용을 조회합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<EssayAnswerResponseDTO>> searchEssayanswer(
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        EssayAnswerResponseDTO responseDTO = essayAnswerService.searchEssayAnswer(principal.publicId());

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }
}
