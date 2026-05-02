package com.weiver.jobposting.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record JobPostingPageResponseDTO(
        List<JobPostingsDetails> content,
        PageInfo pageable
) {
    public static JobPostingPageResponseDTO of(Page<?> page, List<JobPostingsDetails> content) {
        return new JobPostingPageResponseDTO(
                content,
                new PageInfo(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages(),
                        page.isLast()
                )
        );
    }
}