package com.weiver.matching.repository.projection;

import java.time.LocalDateTime;

public record MatchingNotificationGroupDTO(
        Long jdId,
        String title,
        Long companyId,
        Long matchCount,
        LocalDateTime latestCreateTime,
        Long representativeMatchId
) {}
