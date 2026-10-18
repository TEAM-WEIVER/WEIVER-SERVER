package com.weiver.analysis.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalSkillReportTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    @DisplayName("job이 비어 있으면 분석 완료가 아니다")
    void isAnalysisCompleted_BlankJob_ReturnsFalse(String job) {
        // given
        TechnicalSkillReport report = TechnicalSkillReport.builder()
                .job(job)
                .role("BACKEND")
                .build();

        // when, then
        assertThat(report.isAnalysisCompleted()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    @DisplayName("role이 비어 있으면 분석 완료가 아니다")
    void isAnalysisCompleted_BlankRole_ReturnsFalse(String role) {
        // given
        TechnicalSkillReport report = TechnicalSkillReport.builder()
                .job("DEVELOPER")
                .role(role)
                .build();

        // when, then
        assertThat(report.isAnalysisCompleted()).isFalse();
    }

    @Test
    @DisplayName("job과 role이 모두 있으면 분석 완료이며 저장 값은 바꾸지 않는다")
    void isAnalysisCompleted_JobAndRole_ReturnsTrueWithoutMutating() {
        // given
        TechnicalSkillReport report = TechnicalSkillReport.builder()
                .job(" DEVELOPER ")
                .role(" BACKEND ")
                .skillTags(List.of())
                .build();

        // when
        boolean completed = report.isAnalysisCompleted();

        // then
        assertThat(completed).isTrue();
        assertThat(report.getJob()).isEqualTo(" DEVELOPER ");
        assertThat(report.getRole()).isEqualTo(" BACKEND ");
    }
}
