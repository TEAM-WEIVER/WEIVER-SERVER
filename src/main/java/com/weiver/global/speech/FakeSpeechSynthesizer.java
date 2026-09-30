package com.weiver.global.speech;

import com.weiver.global.speech.dto.SynthesizedAudio;
import com.weiver.global.speech.service.SpeechSynthesizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * API 키 없이 로컬에서 면접 흐름을 돌리기 위한 대체 구현. 무음 WAV(0.5초)를 돌려준다.
 */
@Slf4j
@Profile("!prod")
@Component
public class FakeSpeechSynthesizer implements SpeechSynthesizer {

    private static final int SAMPLE_RATE = 24_000;
    private static final int SILENCE_SAMPLES = SAMPLE_RATE / 2;
    private static final short BITS_PER_SAMPLE = 16;
    private static final short CHANNELS = 1;

    @Override
    public SynthesizedAudio synthesize(String text) {
        log.info("[LOCAL TTS] textLength={}", text == null ? 0 : text.length());
        return new SynthesizedAudio(silentWav(), "audio/wav");
    }

    static byte[] silentWav() {
        int dataSize = SILENCE_SAMPLES * CHANNELS * BITS_PER_SAMPLE / 8;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);                                       // PCM
        buffer.putShort(CHANNELS);
        buffer.putInt(SAMPLE_RATE);
        buffer.putInt(SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8);      // byte rate
        buffer.putShort((short) (CHANNELS * BITS_PER_SAMPLE / 8));        // block align
        buffer.putShort(BITS_PER_SAMPLE);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataSize);
        return buffer.array();                                            // 나머지는 0(무음)
    }
}
