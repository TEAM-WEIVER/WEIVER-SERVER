package com.weiver.global.speech.service;

import com.weiver.global.speech.dto.SynthesizedAudio;

public interface SpeechSynthesizer {

    /**
     * 질문 원문을 음성(WAV)으로 변환한다. 입력은 가공하지 않고 그대로 보낸다.
     *
     * @throws com.weiver.global.exception.BusinessException 모든 실패(SPEECH_SYNTHESIS_FAILED).
     *                                                        호출자는 텍스트만 푸시로 폴백한다.
     */
    SynthesizedAudio synthesize(String text);
}
