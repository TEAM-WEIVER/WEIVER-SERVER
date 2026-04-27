package com.weiver.applicant.controller;

import com.weiver.applicant.dto.request.post.AwardRequestDTO;
import com.weiver.applicant.dto.request.post.CertificateRequestDTO;
import com.weiver.applicant.dto.request.post.EducationRequestDTO;
import com.weiver.applicant.dto.request.post.WorkExperienceRequestDTO;
import com.weiver.applicant.dto.request.put.*;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;


@RestController
@RequestMapping("/applicants")
@RequiredArgsConstructor
public class ApplicantController {

    private final ApplicantService applicantService;

    @PutMapping("/info")
    public ResponseEntity<ApiResponse<Void>> updateApplicantInfo(@RequestPart(value = "requestDTO") @Valid ApplicantInfoRequestDTO requestDTO,
                                                                 @RequestPart(value = "profileImage", required = false) MultipartFile profileImage,
                                                               Principal principal){
        Long applicantId = extractedId(principal);
        applicantService.updateApplicantInfo(applicantId, requestDTO, profileImage);

        return ResponseEntity.ok(ApiResponse.success("개인정보 저장에 성공했습니다."));
    }

    @PostMapping("/education")
    public ResponseEntity<ApiResponse<Void>> saveEducationInfo(@RequestBody @Valid EducationRequestDTO requestDTO,
                                                               Principal principal){
        Long applicantId = extractedId(principal);
        applicantService.saveEducationInfo(applicantId, requestDTO);

        return ResponseEntity.ok(ApiResponse.success("학력 저장에 성공했습니다."));
    }

    @PutMapping("/education")
    public ResponseEntity<ApiResponse<Void>> updateEducationInfo(@RequestBody @Valid EducationUpdateRequestDTO requestDTO,
                                                                 Principal principal){
        Long applicantId = extractedId(principal);
        applicantService.updateEducationInfo(applicantId, requestDTO);

        return ResponseEntity.ok(ApiResponse.success("학력 업데이트 성공했습니다."));
    }

    @PostMapping("/award")
    public ResponseEntity<ApiResponse<Void>> saveAwardInfo(@RequestBody @Valid AwardRequestDTO requestDTO,
                                                           Principal principal){
        Long applicantId = extractedId(principal);
        applicantService.saveAwardInfo(applicantId, requestDTO);

        return ResponseEntity.ok(ApiResponse.success("수상이력 저장에 성공했습니다."));
    }

    @PutMapping("/award")
    public ResponseEntity<ApiResponse<Void>> updateAwardInfo(@RequestBody @Valid AwardUpdateRequestDTO requestDTO,
                                                             Principal principal){
        Long applicantId = extractedId(principal);
        applicantService.updateAwardInfo(applicantId, requestDTO);

        return ResponseEntity.ok(ApiResponse.success("수상목록 업데이트 성공했습니다."));
    }

    @PostMapping("/certificate")
    public ResponseEntity<ApiResponse<Void>> saveCertificateInfo(@RequestBody @Valid CertificateRequestDTO requestDTO,
                                                                 Principal principal){
        Long applicantId = extractedId(principal);
        applicantService.saveCertificateInfo(applicantId, requestDTO);

        return ResponseEntity.ok(ApiResponse.success("자격증 저장에 성공했습니다."));
    }

    @PutMapping("/certificate")
    public ResponseEntity<ApiResponse<Void>> updateCertificateInfo(@RequestBody @Valid CertificateUpdateRequestDTO requestDTO,
                                                                   Principal principal){
        Long applicantId = extractedId(principal);
        applicantService.updateCertificateInfo(applicantId, requestDTO);

        return ResponseEntity.ok(ApiResponse.success("자격증 목록 업데이트 성공했습니다."));
    }

    @PostMapping("/experience")
    public ResponseEntity<ApiResponse<Void>> saveWorkExperienceInfo(@RequestBody @Valid WorkExperienceRequestDTO requestDTO,
                                                                    Principal principal){
        Long applicantId = extractedId(principal);
        applicantService.saveWorkExperienceInfo(applicantId, requestDTO);

        return ResponseEntity.ok(ApiResponse.success("경력 저장에 성공했습니다."));
    }

    @PutMapping("/experience")
    public ResponseEntity<ApiResponse<Void>> updateExperienceInfo(@RequestBody @Valid WorkExperienceUpdateRequestDTO requestDTO,
                                                             Principal principal){
        Long applicantId = extractedId(principal);
        applicantService.updateWorkExperienceInfo(applicantId, requestDTO);

        return ResponseEntity.ok(ApiResponse.success("경력 업데이트 성공했습니다."));
    }




    /**
     * 편의 메소드
     * */
    private static Long extractedId(Principal principal) {
        if(principal == null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return Long.parseLong(principal.getName());
    }
}
