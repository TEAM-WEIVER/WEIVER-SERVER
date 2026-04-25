package com.weiver.company.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OperationStyle {
    EXPERIMENT_ORIENTED("실험과 빠른 학습"),
    STABILITY_ORIENTED("안정적인 운영과 지속성");

    private final String description;
}
