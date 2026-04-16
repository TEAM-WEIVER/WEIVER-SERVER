package com.weaver.company.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CompanyType {

    LARGE_ENTERPRISE("대기업"),
    MIDDLE_MARKET("중견기업"),
    SME("중소기업"),
    STARTUP("스타트업"),
    VENTURE("벤처기업");

    private final String name;
}
