package com.weiver.jobposting.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.jobposting.dto.request.JobPostingRequestDTO;
import com.weiver.jobposting.service.JobPostingService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
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

    private static Long extractedId(Principal principal) {
        if(principal == null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return Long.parseLong(principal.getName());
    }
}
