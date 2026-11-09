package com.weiver.matching.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import com.weiver.matching.event.dto.MatchingCompletedData;
import com.weiver.matching.service.MatchingResultReplacementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MatchingCompletedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private MatchingResultReplacementService replacementService;
    private MatchingCompletedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MatchingCompletedHandler(objectMapper, replacementService);
    }

    @Test
    @DisplayName("매칭 완료 이벤트를 지원한다")
    void support_ReturnsMatchingCompleted() {
        assertThat(handler.support()).isEqualTo(EventType.MATCHING_COMPLETED);
    }

    @Test
    @DisplayName("유효한 봉투를 역직렬화하고 event_id와 payload를 서비스에 위임한다")
    void handle_DelegatesValidatedPayload() {
        // given
        MatchingCompletedData data = new MatchingCompletedData(10L,
                List.of(new MatchingCompletedData.MatchData(1L, 87.5f, 91f, 89f, "요약")));

        // when
        handler.handle(envelope("event-1", data));

        // then
        verify(replacementService).replace("event-1", data);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("event_id가 없거나 공백이면 서비스 호출 전에 거부한다")
    void handle_RejectsMissingEventId(String eventId) {
        // given
        var envelope = envelope(eventId, new MatchingCompletedData(10L, List.of()));

        // when / then
        assertThatThrownBy(() -> handler.handle(envelope))
                .isInstanceOf(NonRetryableEventException.class).hasMessage("event_id is required");
        verifyNoInteractions(replacementService);
    }

    @Test
    @DisplayName("공고 ID가 없으면 서비스 호출 전에 거부한다")
    void handle_RejectsMissingJobPostingId() {
        // given
        var envelope = envelope("event-1", new MatchingCompletedData(null, List.of()));

        // when / then
        assertThatThrownBy(() -> handler.handle(envelope))
                .isInstanceOf(NonRetryableEventException.class).hasMessage("jd_id is required");
        verifyNoInteractions(replacementService);
    }

    @Test
    @DisplayName("매칭 목록이 없으면 서비스 호출 전에 거부한다")
    void handle_RejectsMissingMatches() {
        // given
        var envelope = envelope("event-1", new MatchingCompletedData(10L, null));

        // when / then
        assertThatThrownBy(() -> handler.handle(envelope))
                .isInstanceOf(NonRetryableEventException.class).hasMessage("matches is required");
        verifyNoInteractions(replacementService);
    }

    @Test
    @DisplayName("지원자 ID가 없으면 서비스 호출 전에 거부한다")
    void handle_RejectsMissingApplicantId() {
        // given
        var data = new MatchingCompletedData(10L,
                List.of(new MatchingCompletedData.MatchData(null, 80f, 81f, 82f, "요약")));

        // when / then
        assertThatThrownBy(() -> handler.handle(envelope("event-1", data)))
                .isInstanceOf(NonRetryableEventException.class).hasMessage("matches.applicant_id is required");
        verifyNoInteractions(replacementService);
    }

    @Test
    @DisplayName("중복 지원자 ID는 서비스 호출 전에 거부한다")
    void handle_RejectsDuplicateApplicantId() {
        // given
        var data = new MatchingCompletedData(10L, List.of(
                new MatchingCompletedData.MatchData(1L, 80f, 81f, 82f, "첫째"),
                new MatchingCompletedData.MatchData(1L, 90f, 91f, 92f, "둘째")));

        // when / then
        assertThatThrownBy(() -> handler.handle(envelope("event-1", data)))
                .isInstanceOf(NonRetryableEventException.class).hasMessage("Duplicate applicant_id in matches: 1");
        verifyNoInteractions(replacementService);
    }

    private EventEnvelope<JsonNode> envelope(String eventId, MatchingCompletedData data) {
        return new EventEnvelope<>(eventId, EventType.MATCHING_COMPLETED, null,
                OffsetDateTime.now(), EventEnvelope.CURRENT_VERSION, objectMapper.valueToTree(data));
    }
}