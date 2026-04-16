package com.weaver.notification.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

    // --- 기업용 ---
    RESUME_MATCH_TALENT("추천 인재 정보", Target.COMPANY),
    POST_EXPIRE_SOON("공고 마감 예고", Target.COMPANY);

    private final String description;
    private final Target target;

    public enum Target {
        COMPANY
    }
}
