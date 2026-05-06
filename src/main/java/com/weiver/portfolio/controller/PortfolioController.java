package com.weiver.portfolio.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.portfolio.dto.request.PortfolioRequestDTO;
import com.weiver.portfolio.dto.request.PortfolioUpdateRequestDTO;
import com.weiver.portfolio.dto.response.PortfolioResponseDTO;
import com.weiver.portfolio.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "포트폴리오(Portfolio) API", description = "구직자의 포트폴리오 파일(PDF 등) 및 외부 링크 저장/조회 API입니다.")
@RestController
@RequestMapping("/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @Operation(
            summary = "포트폴리오 초기 저장",
            description = "회원가입 후 최초로 포트폴리오를 등록할 때 사용합니다.<br>" +
                    "**[Content-Type: multipart/form-data]** 로 전송해야 합니다.<br>" +
                    "- `requestDTO`: 포트폴리오 링크 등 (application/json 형식의 Blob으로 변환하여 전송)<br>" +
                    "- `portfolio`: 포트폴리오 파일 (PDF 등)"
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> savePortfolio(
            @Parameter(description = "포트폴리오 텍스트 데이터 (JSON)") @RequestPart(value = "requestDTO") @Valid PortfolioRequestDTO requestDTO,
            @Parameter(description = "포트폴리오 첨부 파일 (.pdf, .zip 등)") @RequestPart(value = "portfolio", required = false) MultipartFile portfolio,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        portfolioService.savePortfolio(requestDTO, portfolio, principal.publicId());

        return ResponseEntity.ok(ApiResponse.success("포트폴리오 저장 완료됐습니다."));
    }

    @Operation(
            summary = "포트폴리오 내용 및 파일 수정",
            description = "기존에 등록된 포트폴리오를 수정합니다.<br>" +
                    "**보안:** 본인의 포트폴리오만 수정 가능합니다.<br>" +
                    "파일을 변경하지 않고 텍스트만 변경할 경우 `portfolio` 필드는 생략하거나 null로 전송하세요."
    )
    @PatchMapping("/{portfolioId}")
    public ResponseEntity<ApiResponse<Void>> updatePortfolio(
            @Parameter(description = "수정할 텍스트 데이터 (JSON)") @RequestPart(value = "requestDTO") @Valid PortfolioUpdateRequestDTO requestDTO,
            @Parameter(description = "새로 등록할 포트폴리오 첨부 파일 (선택)") @RequestPart(value = "portfolio", required = false) MultipartFile portfolio,
            @Parameter(description = "수정할 포트폴리오의 고유 ID", example = "1") @PathVariable Long portfolioId,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        portfolioService.updatePortfolio(requestDTO, portfolio, principal.publicId(), portfolioId);
        
        return ResponseEntity.ok(ApiResponse.success("포트폴리오 수정 완료됐습니다."));
    }

    @Operation(
            summary = "내 포트폴리오 조회",
            description = "현재 로그인한 구직자의 포트폴리오 정보를 조회합니다.<br>" +
                    "**보안 다운로드:** 응답 데이터에는 S3에 직접 접근할 수 있는 URL이 아닌, " +
                    "**30분간 유효한 임시 다운로드 링크(Presigned URL)** 가 포함되어 반환됩니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PortfolioResponseDTO>> searchPortfolio(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        PortfolioResponseDTO responseDTO = portfolioService.searchPortfolio(principal.publicId());

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }
}
