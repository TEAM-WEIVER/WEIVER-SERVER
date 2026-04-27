package com.weiver.applicant.controller;

import com.weiver.applicant.dto.request.put.ApplicantInfoRequestDTO;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;


@RestController
@RequestMapping("/applicant")
@RequiredArgsConstructor
public class ApplicantController {

    private final ApplicantService applicantService;

    @PutMapping("/applicantInfo")
    public ResponseEntity<ApiResponse<Void>> updateApplicantInfo(ApplicantInfoRequestDTO requestDTO,
                                                               Principal principal){
        if(principal == null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }


        applicantService.updateApplicantInfo(Long.parseLong(principal.getName()), requestDTO);

        return ResponseEntity.ok(ApiResponse.success("개인정보 저장에 성공했습니다."));
    }
}
