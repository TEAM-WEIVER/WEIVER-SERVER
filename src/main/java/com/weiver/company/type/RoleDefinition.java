package com.weiver.company.type;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleDefinition {
    CLEAR_RESPONSIBILITY("역할과 책임이 비교적 명확"),
    FLEXIBLE_ROLE("상황에 따라 유연한 역할");

    private final String description;
}
