package com.weiver.global.speech.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiSpeechResponse(List<Candidate> candidates) {

    /** candidates[0].content.parts[0].inlineData. 경로 중간이 비어 있으면 null */
    public InlineData firstInlineData() {
        if (candidates == null || candidates.isEmpty() || candidates.get(0) == null) {
            return null;
        }
        Content content = candidates.get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty() || content.parts().get(0) == null) {
            return null;
        }
        return content.parts().get(0).inlineData();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(List<Part> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(InlineData inlineData) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InlineData(String mimeType, String data) {
    }
}
