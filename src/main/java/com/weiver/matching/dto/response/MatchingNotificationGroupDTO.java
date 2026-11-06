package com.weiver.matching.dto.response;

import java.time.LocalDateTime;

public record MatchingNotificationGroupDTO(
        Long jdId,
        String title,
        Long companyId,
        Long matchCount,
        LocalDateTime latestCreateTime,
        Long representativeMatchId
) {}
