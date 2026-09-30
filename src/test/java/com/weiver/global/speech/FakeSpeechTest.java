package com.weiver.global.speech;

import com.weiver.global.speech.dto.SynthesizedAudio;
import com.weiver.global.speech.dto.Transcription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FakeSpeechTest {

    @Test
    @DisplayName("Fake STT는 API 없이 고정 텍스트와 길이를 돌려준다.")
    void fakeTranscriber() {
        Transcription result = new FakeSpeechTranscriber().transcribe(new byte[]{1}, "a.webm", "audio/webm");

        assertThat(result.text()).isNotBlank();
        assertThat(result.durationSeconds()).isPositive();
    }

    @Test
    @DisplayName("Fake TTS는 RIFF/WAVE 헤더를 가진 WAV를 돌려준다.")
    void fakeSynthesizer() {
        SynthesizedAudio audio = new FakeSpeechSynthesizer().synthesize("질문입니다.");

        assertThat(audio.mimeType()).isEqualTo("audio/wav");
        assertThat(new String(Arrays.copyOfRange(audio.data(), 0, 4), StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(Arrays.copyOfRange(audio.data(), 8, 12), StandardCharsets.US_ASCII)).isEqualTo("WAVE");
    }
}
