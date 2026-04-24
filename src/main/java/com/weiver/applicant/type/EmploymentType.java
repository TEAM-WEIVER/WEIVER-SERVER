package com.weiver.applicant.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmploymentType {
    FULL_TIME("정규직"),
    INTERN("인턴"),
    CONTRACT("계약직"),
    FREELANCER("프리랜서"),
    MILITARY_SERVICE_EXEMPTION("병역 특례"),
    PART_TIME("파트타임");

    private final String description;
}
