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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "자기소개서(Essay) API", description = "구직자의 자기소개서 조회, 저장 및 수정 API입니다.")
@RestController
@RequestMapping("/api/essay-answers")
@RequiredArgsConstructor
public class EssayAnswerController {

    private final EssayAnswerService essayAnswerService;

    @Operation(
            summary = "자기소개서 초기 저장",
            description = "각 문항의 고유 ID(questionId)와 작성한 answer를 배열(List)에 담아서 전송해야 합니다.<br>" +
                    "**주의:** 요청 헤더에 JWT Access Token이 포함되어야 하며, 해당 토큰의 구직자 ID로 매핑되어 저장됩니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> saveEssayanswer(
            @RequestBody @Valid EssayAnswerRequestDTO requestDTO,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        essayAnswerService.saveEssayAnswer(requestDTO, principal.publicId());
        return ResponseEntity.ok(ApiResponse.success("자기소개서 저장 성공했습니다."));
    }

    @Operation(
            summary = "자기소개서 전체 수정",
            description = """
                    UI 기획상 개별 문항 저장이나 자동 저장 기능이 없으므로 **PUT 방식을 통한 전체 덮어쓰기**로 동작합니다.

                    **[필독 주의사항: 전체 덮어쓰기 방식]** 부분 수정이 불가능합니다. 내용이 변경되지 않은 문항을 포함하여, 화면에 존재하는 3개 문항의 모든 답변 데이터를 배열에 담아 한 번에 전송해야 합니다.
                    """
    )
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> updateEssayanswers(
            @RequestBody @Valid EssayAnswerUpdateRequestDTO requestDTO,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        essayAnswerService.updateEssayAnswers(requestDTO, principal.publicId());

        return ResponseEntity.ok(ApiResponse.success("자기소개서 수정 성공했습니다."));
    }

    @Operation(
            summary = "내 자기소개서 전체 조회",
            description = "작성된 자기소개서 문항과 답변 리스트를 전체 조회합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<EssayAnswerResponseDTO>> getEssayAnswers(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        EssayAnswerResponseDTO responseDTO = essayAnswerService.getEssayAnswers(principal.publicId());

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }
}
