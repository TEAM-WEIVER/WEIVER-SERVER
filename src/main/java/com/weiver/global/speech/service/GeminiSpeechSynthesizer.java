package com.weiver.global.speech.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.speech.config.SpeechConfig;
import com.weiver.global.speech.config.SpeechProperties;
import com.weiver.global.speech.dto.GeminiSpeechRequest;
import com.weiver.global.speech.dto.GeminiSpeechResponse;
import com.weiver.global.speech.dto.SynthesizedAudio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;

/**
 * Gemini TTS(generateContent). 서버 자동 재시도는 하지 않으며, 실패는 호출자가 텍스트만 푸시로 폴백한다.
 */
@Profile("prod")
@Slf4j
@Component
public class GeminiSpeechSynthesizer implements SpeechSynthesizer {

    private static final String PROVIDER = "gemini";
    private static final String API_KEY_HEADER = "x-goog-api-key";
    private static final String WAV_MIME_TYPE = "audio/wav";

    private final WebClient webClient;
    private final SpeechProperties.Tts tts;

    public GeminiSpeechSynthesizer(@Qualifier(SpeechConfig.GEMINI_WEB_CLIENT) WebClient webClient,
                                   SpeechProperties properties) {
        this.webClient = webClient;
        this.tts = properties.tts();
    }

    @Override
    public SynthesizedAudio synthesize(String text) {
        if (!StringUtils.hasText(text)) {
            log.warn("[Speech] TTS 요청 검증 실패. provider={}, 입력이 비어 있습니다.", PROVIDER);
            throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_FAILED);
        }
        if (!StringUtils.hasText(tts.apiKey())) {
            log.error("[Speech] API 키가 설정되지 않았습니다. provider={}", PROVIDER);
            throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_FAILED);
        }

        long startedAt = System.nanoTime();
        try {
            GeminiSpeechResponse response = webClient.post()
                    .uri("/models/{model}:generateContent", tts.model())
                    .header(API_KEY_HEADER, tts.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    // 질문 원문은 그대로 보내고, 톤은 speech_metadata.style로 분리한다.
                    .bodyValue(GeminiSpeechRequest.of(text, tts.style(), tts.voice()))
                    .retrieve()
                    .bodyToMono(GeminiSpeechResponse.class)
                    .timeout(tts.timeout())
                    .block();

            SynthesizedAudio audio = toAudio(response);
            log.info("[Speech] TTS 성공. provider={}, model={}, elapsedMs={}, audioBytes={}, status=200, fallback=false",
                    PROVIDER, tts.model(), elapsedMs(startedAt), audio.data().length);
            return audio;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw handleFailure(e, startedAt);
        }
    }

    private SynthesizedAudio toAudio(GeminiSpeechResponse response) {
        GeminiSpeechResponse.InlineData inlineData = response == null ? null : response.firstInlineData();
        if (inlineData == null || !StringUtils.hasText(inlineData.data())) {
            log.error("[Speech] TTS 응답에 오디오가 없습니다. provider={}, model={}", PROVIDER, tts.model());
            throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_FAILED);
        }
        if (!isWav(inlineData.mimeType())) {
            log.error("[Speech] TTS 응답 형식이 audio/wav가 아닙니다. provider={}, model={}, mimeType={}",
                    PROVIDER, tts.model(), inlineData.mimeType());
            throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_FAILED);
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(inlineData.data());
        } catch (IllegalArgumentException e) {
            log.error("[Speech] TTS 오디오 base64 디코딩 실패. provider={}, model={}", PROVIDER, tts.model());
            throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_FAILED);
        }
        if (decoded.length == 0) {
            log.error("[Speech] TTS 오디오가 비어 있습니다. provider={}, model={}", PROVIDER, tts.model());
            throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_FAILED);
        }
        return new SynthesizedAudio(decoded, WAV_MIME_TYPE);
    }

    private boolean isWav(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        // "audio/wav; ..." 처럼 파라미터가 붙어도 타입만 비교한다.
        String type = mimeType.split(";", 2)[0].trim();
        return WAV_MIME_TYPE.equalsIgnoreCase(type);
    }

    private BusinessException handleFailure(RuntimeException e, long startedAt) {
        SpeechProviderFailure failure = SpeechProviderFailure.of(e);
        if (failure.isRateLimited()) {
            log.warn("[Speech] TTS 한도 초과. provider={}, model={}, elapsedMs={}, status=429, fallback=true",
                    PROVIDER, tts.model(), elapsedMs(startedAt));
        } else {
            // 5xx·타임아웃·요청/인증 오류(400·401·403)는 모두 log.error(Sentry). 응답 본문은 남기지 않는다.
            log.error("[Speech] TTS 실패. provider={}, model={}, elapsedMs={}, reason={}, errorType={}, causeType={}, fallback=true",
                    PROVIDER, tts.model(), elapsedMs(startedAt), failure.reason(), e.getClass().getSimpleName(), failure.causeType());
        }
        return new BusinessException(ErrorCode.SPEECH_SYNTHESIS_FAILED);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
