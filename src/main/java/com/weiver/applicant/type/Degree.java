package com.weiver.applicant.type;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Degree {
    HIGH_SCHOOL("고등학교 졸업"),
    ASSOCIATE("전문대(2,3년제) 졸업"),
    BACHELOR("대학교(4년제) 졸업"),
    MASTER("대학원(석사) 졸업"),
    DOCTOR("대학원(박사) 졸업");

    private final String description;
}
