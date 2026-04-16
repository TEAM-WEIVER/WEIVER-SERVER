package com.weiver.applicant.type;

public enum EmploymentType {
    FULL_TIME("정규직"),
    INTERN("인턴"),
    CONTRACT("계약직"),
    FREELANCER("프리랜서"),
    MILITARY_SERVICE_EXEMPTION("병역 특례"),
    PART_TIME("파트타임");

    EmploymentType(String s) {}
}
