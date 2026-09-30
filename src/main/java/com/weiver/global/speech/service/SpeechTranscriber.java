package com.weiver.global.speech.service;

import com.weiver.global.speech.dto.Transcription;

public interface SpeechTranscriber {

    /**
     * 녹음 파일 전체를 1회 텍스트로 변환한다(Non-Streaming). 파일은 트랜스코딩 없이 그대로 전달한다.
     *
     * @throws com.weiver.global.exception.BusinessException 한도 초과(SPEECH_PROVIDER_RATE_LIMITED),
     *                                                        그 외 공급자 오류·타임아웃(SPEECH_TRANSCRIPTION_FAILED)
     */
    Transcription transcribe(byte[] audio, String fileName, String contentType);
}
