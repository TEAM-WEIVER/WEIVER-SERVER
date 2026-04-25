package com.weiver.company.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DecisionMaking {
    RESPECT_INDIVIDUAL("담당자의 판단 존중"),
    TEAM_CONSENSUS("팀 논의 및 합의가 중요");

    private final String description;
}
