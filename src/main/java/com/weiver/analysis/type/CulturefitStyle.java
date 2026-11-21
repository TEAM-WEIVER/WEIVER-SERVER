package com.weiver.analysis.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum CulturefitStyle {
    AGGRESSIVE_INNOVATOR("공격적 혁신가"),
    INCLUSIVE_INNOVATOR("포용적 혁신가"),
    STRATEGIC_GUARDIAN("전략적 수호자"),
    STEADY_SUPPORTER("안정적 조력가");

    private final String description;

    /** AI가 보낸 컬처핏 스타일 값을 enum 이름 또는 한글 설명으로 찾는다. 없으면 빈 값이다. */
    public static Optional<CulturefitStyle> fromNameOrDescription(String value) {
        return Arrays.stream(values())
                .filter(style -> style.name().equals(value) || style.getDescription().equals(value))
                .findFirst();
    }
}
