package com.weiver.global.speech.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqTranscriptionResponse(String text, Double duration) {
}
