package com.weiver.global.speech.dto;

/**
 * STT 결과.
 *
 * @param text            인식된 텍스트 (공급자가 비워 보내면 빈 문자열)
 * @param durationSeconds 공급자가 알려준 오디오 길이(초). 응답에 없으면 null
 */
public record Transcription(String text, Double durationSeconds) {
}
