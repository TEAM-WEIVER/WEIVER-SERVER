package com.weiver.company.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorkPace {
    FAST_EXECUTION("빠른 실행, 이후 보완"),
    CAREFUL_EXECUTION("충분한 논의, 신중하게 실행");

    private final String description;
}
