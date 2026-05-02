package com.weiver.jobposting.dto.response;

public record PageInfo(
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast
) {}
