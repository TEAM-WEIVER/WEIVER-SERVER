package com.weaver.interview.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewType {

    TECH("기술 인터뷰"),
    CULTURE("컬처핏 인터뷰");

    private final String type;
}
