package com.weiver.global.speech.dto;

/**
 * TTS 결과. Gemini 응답의 base64를 디코딩한 WAV 바이트.
 */
public record SynthesizedAudio(byte[] data, String mimeType) {
}
