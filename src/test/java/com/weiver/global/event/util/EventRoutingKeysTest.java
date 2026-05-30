package com.weiver.global.event.util;

import com.weiver.global.event.dto.EventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventRoutingKeysTest {

    @Test
    void convertsEventTypeToTopicRoutingKey() {
        assertThat(EventRoutingKeys.from(EventType.JD_ANALYSIS_REQUESTED))
                .isEqualTo("jd.analysis.requested");

        assertThat(EventRoutingKeys.from(EventType.INTERVIEW_QUESTION_GENERATED))
                .isEqualTo("interview.question.generated");

        assertThat(EventRoutingKeys.from(EventType.APPLICANT_PROFILE_CHANGED))
                .isEqualTo("applicant.profile.changed");
    }
}
