package com.weiver.applicant.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.applicant.event.dto.ApplicantProfileSyncCompletedData;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ApplicantProfileSyncCompletedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicantProfileSyncCompletedHandler handler = new ApplicantProfileSyncCompletedHandler();

    @Test
    @DisplayName("과거 동기화 완료 이벤트 타입을 계속 등록해 알 수 없는 이벤트 타입 오류가 나지 않는다")
    void support_KeepsLegacyEventType() {
        assertThat(handler.support()).isEqualTo(EventType.APPLICANT_PROFILE_SYNC_COMPLETED);
    }

    @ParameterizedTest(name = "synced={0}")
    @ValueSource(strings = {"true", "false"})
    @DisplayName("큐에 남은 과거 동기화 완료 이벤트는 예외 없이 무시한다")
    void handle_IgnoresLegacyEvent(String synced) {
        // given
        EventEnvelope<JsonNode> envelope = envelope(objectMapper.valueToTree(
                new ApplicantProfileSyncCompletedData(1L, Boolean.valueOf(synced))));

        // when & then
        assertThatCode(() -> handler.handle(envelope)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("필수값이 빠진 과거 이벤트도 DLQ로 보내지 않고 무시한다")
    void handle_IgnoresMalformedLegacyEvent() {
        // given
        EventEnvelope<JsonNode> envelope = envelope(objectMapper.createObjectNode());

        // when & then
        assertThatCode(() -> handler.handle(envelope)).doesNotThrowAnyException();
        assertThatCode(() -> handler.handle(envelope(null))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("핸들러는 지원자 저장소·분석 요청 발행·지연 지표를 갖지 않아 상태와 지표를 바꿀 수 없다")
    void handler_HasNoCollaborators() {
        // 상태(지원자·제출 건)와 지표(AiRoundtripMetrics)를 바꾸려면 협력 객체가 필요하다. 필드가 없으면 바꿀 수 없다.
        assertThat(Arrays.stream(ApplicantProfileSyncCompletedHandler.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers())))
                .isEmpty();
    }

    private EventEnvelope<JsonNode> envelope(JsonNode data) {
        return new EventEnvelope<>(
                "event-1",
                EventType.APPLICANT_PROFILE_SYNC_COMPLETED,
                null,
                OffsetDateTime.now(),
                EventEnvelope.CURRENT_VERSION,
                data
        );
    }
}
