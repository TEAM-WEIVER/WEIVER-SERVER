package com.weiver.interview.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.interview.type.AudioStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewWebSocketMessageResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("QUESTION_READY는 audio_url·audio_status를 snake_case로 직렬화한다")
    void questionReady_serializesAudioFields() {
        UUID sessionId = UUID.randomUUID();

        JsonNode json = objectMapper.valueToTree(InterviewWebSocketMessageResponse.questionReady(
                sessionId, "S_02_00", 2, "질문입니다", "https://s3.example.com/a.wav?sig=1", AudioStatus.READY));

        assertThat(json.get("type").asText()).isEqualTo("QUESTION_READY");
        assertThat(json.get("interview_session_id").asText()).isEqualTo(sessionId.toString());
        assertThat(json.get("status").asText()).isEqualTo("QUESTION_READY");
        assertThat(json.get("question_code").asText()).isEqualTo("S_02_00");
        assertThat(json.get("sequence").asInt()).isEqualTo(2);
        assertThat(json.get("question").asText()).isEqualTo("질문입니다");
        assertThat(json.get("audio_url").asText()).isEqualTo("https://s3.example.com/a.wav?sig=1");
        assertThat(json.get("audio_status").asText()).isEqualTo("READY");
    }

    @Test
    @DisplayName("음성이 없으면 audio_url은 null, audio_status는 UNAVAILABLE이다")
    void questionReady_unavailable() {
        JsonNode json = objectMapper.valueToTree(InterviewWebSocketMessageResponse.questionReady(
                UUID.randomUUID(), "S_02_00", 2, "질문입니다", null, AudioStatus.UNAVAILABLE));

        assertThat(json.get("audio_url").isNull()).isTrue();
        assertThat(json.get("audio_status").asText()).isEqualTo("UNAVAILABLE");
    }

    @Test
    @DisplayName("다른 메시지 타입은 오디오 필드가 null이라 기존 클라이언트와 호환된다")
    void otherMessages_haveNullAudioFields() {
        JsonNode json = objectMapper.valueToTree(InterviewWebSocketMessageResponse.interviewFinished(UUID.randomUUID()));

        assertThat(json.get("type").asText()).isEqualTo("INTERVIEW_FINISHED");
        assertThat(json.get("audio_url").isNull()).isTrue();
        assertThat(json.get("audio_status").isNull()).isTrue();
    }
}
