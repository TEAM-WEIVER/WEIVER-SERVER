package com.weiver.jobposting.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.jobposting.dto.request.JobPostingRequestDTO;
import com.weiver.jobposting.dto.request.JobPostingUpdateDTO;
import com.weiver.jobposting.dto.response.JobPostingResponseDTO;
import com.weiver.jobposting.service.JobPostingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@Tag(name = "기업 채용 공고 API", description = "기업의 채용 공고 생성, 수정, 리스트 및 상세 조회 API")
@RestController
@RequestMapping("/api/job-postings")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService jobPostingService;

    @Operation(
            summary = "채용 공고 통합 생성",
            description = "채용 공고 기본 정보와 합격/불합격 이메일 템플릿을 한 번에 생성합니다.<br>" +
                    "**[주의] Content-Type은 multipart/form-data로 전송해야 합니다.**<br>" +
                    "1. `requestDTO`: application/json 타입의 Blob 객체로 변환하여 전송<br>" +
                    "2. `emailBannerImage`: 이미지 파일 객체 전송 (선택 사항)<br>" +
                    "3. `isTemp`: 임시저장 여부 (기본값: false)"
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> saveJobPosting(
            @Parameter(description = "채용 공고 및 이메일 템플릿 정보 (JSON)")
            @RequestPart(value = "requestDTO") @Valid JobPostingRequestDTO requestDTO,

            @Parameter(description = "이메일 상단 배너 이미지 파일 (선택)", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestPart(value = "emailBannerImage", required = false) MultipartFile emailBannerImage,

            @Parameter(description = "임시 저장 여부 (기본값 : false) ")
            @RequestParam(value = "isTemp", defaultValue = "false") boolean isTemp,

            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {

        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        jobPostingService.saveJobPosting(isTemp, principal.publicId(), requestDTO, emailBannerImage);
        return ResponseEntity.ok(ApiResponse.success("채용 공고가 성공적으로 등록되었습니다."));
    }

    @Operation(
            summary = "채용 공고 통합 수정 (스냅샷 방식)",
            description = "기존 공고를 전체 덮어쓰기 방식으로 수정합니다. 배열 데이터는 전송된 그대로 덮어씌워집니다.<br>" +
                    "**[이미지 처리 플래그 필독]**<br>" +
                    "- 이미지 유지: `isEmailBannerDeleted: false` + 파일 미전송<br>" +
                    "- 새 이미지 변경: `isEmailBannerDeleted: false` + 새 파일 전송<br>" +
                    "- 기존 이미지 삭제: `isEmailBannerDeleted: true` + 파일 미전송"
    )
    @PutMapping(value = "/{jdId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> updateJobPosting(
            @Parameter(description = "수정할 공고 정보 (JSON, isEmailBannerDeleted 플래그 포함 필수)")
            @RequestPart(value = "requestDTO") @Valid JobPostingUpdateDTO updateDTO,

            @Parameter(description = "새로 변경할 배너 이미지 파일 (선택)", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestPart(value = "emailBannerImage", required = false) MultipartFile emailBannerImage,

            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal,

            @Parameter(description = "수정할 공고의 고유 ID(jdId)", example = "1")
            @PathVariable Long jdId){

        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        jobPostingService.updateJobPosting(jdId, principal.publicId(), updateDTO, emailBannerImage);
        return ResponseEntity.ok(ApiResponse.success("채용 공고가 성공적으로 수정되었습니다."));
    }



    @Operation(
            summary = "공고 상세 보기",
            description = "단일 채용 공고의 상세 정보와 이메일 템플릿 정보를 조회합니다." +
                    "기업 대시보드의 공고리스트에서 수정 버튼을 눌렀을 때, 해당 공고의 상세 정보를 조회하는 API입니다.<br>"
    )
    @GetMapping("/{jdId}")
    public ResponseEntity<ApiResponse<JobPostingResponseDTO>> getJobPosting(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal,
            @Parameter(description = "조회할 공고의 고유 ID", example = "1")
            @PathVariable Long jdId){

        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        JobPostingResponseDTO responseDTO = jobPostingService.searchJobPosting(principal.publicId(), jdId);
        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }

    @Operation(summary = "공고 삭제", description = "채용 공고를 삭제합니다. 삭제된 공고는 복구할 수 없습니다.")
    @DeleteMapping("/{jdId}")
    public ResponseEntity<ApiResponse<Void>> deleteJobPosting(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal,
            @Parameter(description = "삭제할 공고의 고유 ID", example = "1")
            @PathVariable Long jdId){

        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        jobPostingService.deleteJobPosting(jdId, principal.publicId());
        return ResponseEntity.ok(ApiResponse.success("채용 공고가 성공적으로 삭제되었습니다."));
    }

}
