package com.weiver.jobposting.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.jobposting.dto.request.JobPostingRequestDTO;
import com.weiver.jobposting.dto.request.JobPostingUpdateDTO;
import com.weiver.jobposting.dto.response.JobPostingPageResponseDTO;
import com.weiver.jobposting.dto.response.JobPostingResponseDTO;
import com.weiver.jobposting.service.JobPostingService;
import com.weiver.jobposting.type.JobPostingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

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
                    "2. `emailBannerImage`: 이미지 파일 객체 전송 (선택 사항)"
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> saveJobPosting(
            @Parameter(description = "채용 공고 및 이메일 템플릿 정보 (JSON)")
            @RequestPart(value = "requestDTO") @Valid JobPostingRequestDTO requestDTO,

            @Parameter(description = "이메일 상단 배너 이미지 파일 (선택)", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestPart(value = "emailBannerImage", required = false) MultipartFile emailBannerImage,

            @Parameter(hidden = true) Principal principal) {

        Long companyId = extractedId(principal);
        jobPostingService.saveJobPosting(companyId, requestDTO, emailBannerImage);
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

            @Parameter(hidden = true) Principal principal,

            @Parameter(description = "수정할 공고의 고유 ID(jdId)", example = "1")
            @PathVariable Long jdId){

        Long companyId = extractedId(principal);
        jobPostingService.updateJobPosting(jdId, companyId, updateDTO, emailBannerImage);
        return ResponseEntity.ok(ApiResponse.success("채용 공고가 성공적으로 수정되었습니다."));
    }

    @Operation(
            summary = "공고 리스트 조회 (페이징)",
            description = "기업이 작성한 채용 공고 리스트를 최신순으로 페이징하여 조회합니다.<br>" +
                    "각 공고별 '새로운 지원자 수(newApplicantCount)'가 함께 반환됩니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<JobPostingPageResponseDTO>> getJobPostingsList(
            @Parameter(description = "공고 상태 필터링 (DRAFT, ACTIVE, CLOSED, ON_HOLD). 미입력 시 전체 조회", example = "ACTIVE")
            @RequestParam(required = false) JobPostingStatus status,

            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지당 데이터 개수", example = "3")
            @RequestParam(defaultValue = "3") int size,

            @Parameter(hidden = true) Principal principal) {

        Long companyId = extractedId(principal);
        JobPostingPageResponseDTO responseDTO = jobPostingService.searchJobPostingsList(companyId, status, page, size);
        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }

    @Operation(
            summary = "공고 상세 보기",
            description = "단일 채용 공고의 상세 정보와 이메일 템플릿 정보를 조회합니다."
    )
    @GetMapping("/{jdId}")
    public ResponseEntity<ApiResponse<JobPostingResponseDTO>> getJobPosting(
            @Parameter(hidden = true) Principal principal,
            @Parameter(description = "조회할 공고의 고유 ID", example = "1")
            @PathVariable Long jdId){

        Long companyId = extractedId(principal);
        JobPostingResponseDTO responseDTO = jobPostingService.searchJobPosting(companyId, jdId);
        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }

    private static Long extractedId(Principal principal) {
        if(principal == null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return Long.parseLong(principal.getName());
    }
}
