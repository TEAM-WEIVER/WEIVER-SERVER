package com.weiver.essay.controller;

import com.weiver.essay.dto.request.EssayAnswerRequestDTO;
import com.weiver.essay.dto.request.EssayAnswerUpdateRequestDTO;
import com.weiver.essay.dto.response.EssayAnswerResponseDTO;
import com.weiver.essay.service.EssayAnswerService;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/essay-answers")
@RequiredArgsConstructor
public class EssayAnswerController {

    private final EssayAnswerService essayAnswerService;

    @PostMapping("")
    public ResponseEntity<ApiResponse<Void>> saveEssayanswer(@RequestBody @Valid EssayAnswerRequestDTO requestDTO,
                                                             Principal principal) {
        Long applicantId = extractedId(principal);

        essayAnswerService.saveEssayAnswer(requestDTO, applicantId);
        return ResponseEntity.ok(ApiResponse.success("자기소개서 저장 성공했습니다."));
    }

    @PatchMapping("/{answerId}")
    public ResponseEntity<ApiResponse<Void>> updateEssayanswer(@RequestBody @Valid EssayAnswerUpdateRequestDTO requestDTO,
                                                               @PathVariable long answerId,
                                                               Principal principal){
        Long applicantId = extractedId(principal);

        essayAnswerService.updateEssayAnswer(requestDTO, applicantId, answerId);

        return ResponseEntity.ok(ApiResponse.success("자기소개서 수정 성공했습니다."));
    }

    @GetMapping("")
    public ResponseEntity<ApiResponse<EssayAnswerResponseDTO>> searchEssayanswer(Principal principal){
        Long applicantId = extractedId(principal);

        EssayAnswerResponseDTO responseDTO = essayAnswerService.searchEssayAnswer(applicantId);

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
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
