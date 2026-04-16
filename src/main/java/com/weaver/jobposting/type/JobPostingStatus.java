package com.weaver.jobposting.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JobPostingStatus {

    DRAFT("작성중"),
    ACTIVE("공개중"),
    CLOSED("마감"),
    ON_HOLD("보류");

    private final String description;

}
