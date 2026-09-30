package com.weiver.interview.type;

/**
 * QUESTION_READY 메시지의 질문 음성 상태.
 */
public enum AudioStatus {
    /** audio_url로 질문 음성을 받을 수 있다. */
    READY,
    /** 음성을 만들지 못해 텍스트만 보낸다. 브라우저 기본 TTS로 읽는다. */
    UNAVAILABLE
}
