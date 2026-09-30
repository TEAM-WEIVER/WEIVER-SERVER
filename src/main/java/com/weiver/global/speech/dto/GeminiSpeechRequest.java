package com.weiver.global.speech.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GeminiSpeechRequest(List<Content> contents, GenerationConfig generationConfig) {

    public static GeminiSpeechRequest of(String text, String style, String voice) {
        Part part = new Part(text, new SpeechMetadata(style));
        return new GeminiSpeechRequest(
                List.of(new Content("user", List.of(part))),
                new GenerationConfig(List.of("AUDIO"), new SpeechConfig(new VoiceConfig(voice)))
        );
    }

    public record Content(String role, List<Part> parts) {
    }

    public record Part(String text, @JsonProperty("speech_metadata") SpeechMetadata speechMetadata) {
    }

    public record SpeechMetadata(String style) {
    }

    public record GenerationConfig(List<String> responseModalities, SpeechConfig speechConfig) {
    }

    public record SpeechConfig(VoiceConfig voiceConfig) {
    }

    public record VoiceConfig(String voice) {
    }
}
