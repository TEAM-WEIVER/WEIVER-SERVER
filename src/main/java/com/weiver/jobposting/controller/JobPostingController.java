package com.weiver.jobposting.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.jobposting.dto.request.JobPostingRequestDTO;
import com.weiver.jobposting.dto.request.JobPostingUpdateDTO;
import com.weiver.jobposting.dto.response.JobPostingPageResponseDTO;
import com.weiver.jobposting.service.JobPostingService;
import com.weiver.jobposting.type.JobPostingStatus;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/api/job-postings")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService jobPostingService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> saveJobPosting(
            @RequestPart(value = "requestDTO") @Valid JobPostingRequestDTO requestDTO,
            @RequestPart(value = "emailBannerImage", required = false) MultipartFile emailBannerImage,
            @Parameter(hidden = true) Principal principal) {
        Long companyId = extractedId(principal);

        jobPostingService.saveJobPosting(companyId, requestDTO, emailBannerImage);

        return ResponseEntity.ok(ApiResponse.success("채용 공고가 성공적으로 등록되었습니다."));
    }

    @PutMapping("/{jdId}")
    public ResponseEntity<ApiResponse<Void>> updateJobPosting(
            @RequestPart(value = "requestDTO") @Valid JobPostingUpdateDTO updateDTO,
            @RequestPart(value = "emailBannerImage", required = false) MultipartFile emailBannerImage,
            @Parameter(hidden = true) Principal principal,
            @PathVariable Long jdId){
        Long companyId = extractedId(principal);

        jobPostingService.updateJobPosting(jdId, companyId, updateDTO, emailBannerImage);

        return ResponseEntity.ok(ApiResponse.success("채용 공고가 성공적으로 수정되었습니다."));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<JobPostingPageResponseDTO>> getJobPostings(
            @RequestParam(required = false) JobPostingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size,
            @Parameter(hidden = true) Principal principal
    ) {
        Long companyId = extractedId(principal);

        JobPostingPageResponseDTO responseDTO = jobPostingService.searchJobPostings(companyId, status, page, size);

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }

    private static Long extractedId(Principal principal) {
        if(principal == null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return Long.parseLong(principal.getName());
    }
}
