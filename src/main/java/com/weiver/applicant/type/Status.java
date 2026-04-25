package com.weiver.applicant.type;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Status {
    GRADUATED("졸업"),
    LEAVE_OF_ABSENCE("휴학"),
    GRADUATION_POSTPONED("졸업유예"),
    ACTIVE("재학");

    private final String description;
}
