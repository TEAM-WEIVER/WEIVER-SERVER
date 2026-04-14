package com.weaver.applicant.type;

public enum Status {
    GRADUATED("졸업"),
    LEAVE_OF_ABSENCE("휴학"),
    GRADUATION_POSTPONED("졸업유예"),
    ACTIVE("재학");

    Status(String status) {}
}
