package com.weiver.global.speech;

import com.weiver.global.speech.dto.Transcription;
import com.weiver.global.speech.service.SpeechTranscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * API 키 없이 로컬에서 면접 흐름을 돌리기 위한 대체 구현. 고정 텍스트를 돌려준다.
 */
@Slf4j
@Profile("!prod")
@Component
public class FakeSpeechTranscriber implements SpeechTranscriber {

    static final String FAKE_TEXT = "로컬 테스트용 음성 인식 결과입니다.";
    static final double FAKE_DURATION_SECONDS = 3.0;

    @Override
    public Transcription transcribe(byte[] audio, String fileName, String contentType) {
        log.info("[LOCAL STT] audioBytes={}, contentType={}", audio == null ? 0 : audio.length, contentType);
        return new Transcription(FAKE_TEXT, FAKE_DURATION_SECONDS);
    }
}
