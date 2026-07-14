package com.weiver.global.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.weiver.analysis.event.dto.ApplicantAnalysisCompletedData;
import com.weiver.analysis.event.dto.ApplicantAnalysisRequestedData;
import com.weiver.applicant.event.dto.ApplicantProfileChangedData;
import com.weiver.applicant.event.dto.ApplicantProfileSyncCompletedData;
import com.weiver.company.type.DecisionMaking;
import com.weiver.company.type.OperationStyle;
import com.weiver.company.type.RoleDefinition;
import com.weiver.company.type.WorkPace;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.interview.event.dto.InterviewQuestionGeneratedData;
import com.weiver.interview.event.dto.InterviewQuestionRequestedData;
import com.weiver.interview.event.dto.InterviewReportCompletedData;
import com.weiver.interview.event.dto.InterviewReportRequestedData;
import com.weiver.interview.event.dto.InterviewTranscriptSaveRequestedData;
import com.weiver.interview.event.dto.InterviewTranscriptSavedData;
import com.weiver.jobposting.event.dto.JdAnalysisCompletedData;
import com.weiver.jobposting.event.dto.JdAnalysisRequestedData;
import com.weiver.matching.event.dto.MatchingCompletedData;
import com.weiver.matching.event.dto.MatchingRequestedData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventPayloadContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("공통 이벤트 envelope를 snake_case 계약으로 직렬화한다")
    void serializesCommonEnvelope() {
        EventEnvelope<ApplicantAnalysisRequestedData> envelope = new EventEnvelope<>(
                "event-1",
                EventType.APPLICANT_ANALYSIS_REQUESTED,
                null,
                OffsetDateTime.parse("2026-07-14T22:00:00+09:00"),
                "1.0",
                new ApplicantAnalysisRequestedData(1L)
        );

        JsonNode json = objectMapper.valueToTree(envelope);

        assertThat(json.get("event_id").asText()).isEqualTo("event-1");
        assertThat(json.get("event_type").asText()).isEqualTo("APPLICANT_ANALYSIS_REQUESTED");
        assertThat(json.get("correlation_id").isNull()).isTrue();
        assertThat(json.get("occurred_at").asText()).contains("+09:00");
        assertThat(json.get("version").asText()).isEqualTo("1.0");
        assertThat(json.get("data").get("applicant_id").asLong()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Spring에서 AI로 보내는 7개 이벤트 payload를 계약 필드명으로 직렬화한다")
    void serializesSpringToAiPayloads() {
        JsonNode profile = objectMapper.valueToTree(new ApplicantProfileChangedData(
                1L,
                "홍길동",
                List.of(new ApplicantProfileChangedData.EducationData(
                        10L, "BACHELOR", "한양대학교", "컴퓨터공학",
                        BigDecimal.valueOf(4.0), "2021-03", "2027-03", "ACTIVE"
                )),
                List.of(new ApplicantProfileChangedData.ExperienceData(
                        20L, "Weiver", LocalDate.parse("2026-01-01"), null,
                        "INTERN", "인턴", "백엔드 개발", true
                )),
                List.of("정보처리기사"),
                List.of(new ApplicantProfileChangedData.EssayData(1L, "질문", "답변"))
        ));
        JsonNode education = profile.get("educations").get(0);
        JsonNode experience = profile.get("experiences").get(0);
        assertThat(education.get("education_level").asText()).isEqualTo("BACHELOR");
        assertThat(education.get("graduation_status").asText()).isEqualTo("ACTIVE");
        assertThat(education.has("degree")).isFalse();
        assertThat(education.has("status")).isFalse();
        assertThat(experience.get("experience_name").asText()).isEqualTo("Weiver");
        assertThat(experience.get("position_title").asText()).isEqualTo("인턴");
        assertThat(experience.get("responsibilities").asText()).isEqualTo("백엔드 개발");
        assertThat(experience.has("company_name")).isFalse();
        assertThat(experience.has("position")).isFalse();
        assertThat(experience.has("duties")).isFalse();

        JsonNode jd = objectMapper.valueToTree(new JdAnalysisRequestedData(
                1L, 2L, "백엔드 채용", "개발", "백엔드", "주요 업무", "필수 요건",
                "지원 자격", "우대 사항", List.of("Java"), "회사 문화",
                new JdAnalysisRequestedData.WorkStyleData(
                        WorkPace.FAST_EXECUTION,
                        DecisionMaking.TEAM_CONSENSUS,
                        RoleDefinition.FLEXIBLE_ROLE,
                        OperationStyle.EXPERIMENT_ORIENTED
                )
        ));
        assertThat(jd.get("jd_id").asLong()).isEqualTo(1L);
        assertThat(jd.get("job_category").asText()).isEqualTo("개발");
        assertThat(jd.get("required_skills").get(0).asText()).isEqualTo("Java");
        assertThat(jd.get("work_style").get("progress_speed").asText()).isEqualTo("FAST_EXECUTION");

        JsonNode applicantAnalysis = objectMapper.valueToTree(new ApplicantAnalysisRequestedData(1L));
        assertThat(applicantAnalysis.get("applicant_id").asLong()).isEqualTo(1L);

        JsonNode matching = objectMapper.valueToTree(new MatchingRequestedData(
                2L,
                1L,
                List.of("Java"),
                "Spring 경험",
                List.of(new MatchingRequestedData.PriorityData(1, "PROBLEM_SOLVING", "문제해결력")),
                List.of(new MatchingRequestedData.PriorityData(1, "AUTONOMY_INNOVATION", "자율·혁신"))
        ));
        assertThat(matching.get("competency_priorities").get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(matching.get("competency_priorities").get(0).get("code").asText())
                .isEqualTo("PROBLEM_SOLVING");
        assertThat(matching.get("trait_priorities").get(0).get("name").asText()).isEqualTo("자율·혁신");

        UUID sessionId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        JsonNode question = objectMapper.valueToTree(new InterviewQuestionRequestedData(
                1L, "홍길동", sessionId, null, 1, "DEVELOPER", "BACKEND",
                new InterviewQuestionRequestedData.LastInterviewData(null, null)
        ));
        assertThat(question.get("interview_session_id").asText()).isEqualTo(sessionId.toString());
        assertThat(question.get("last_question_code").isNull()).isTrue();
        assertThat(question.get("last_interview").get("question").isNull()).isTrue();

        JsonNode transcript = objectMapper.valueToTree(new InterviewTranscriptSaveRequestedData(
                1L,
                sessionId,
                new InterviewTranscriptSaveRequestedData.TranscriptSectionData(List.of(
                        new InterviewTranscriptSaveRequestedData.TranscriptTurnData("기술 질문", "기술 답변")
                )),
                new InterviewTranscriptSaveRequestedData.TranscriptSectionData(List.of(
                        new InterviewTranscriptSaveRequestedData.TranscriptTurnData("컬처 질문", "컬처 답변")
                ))
        ));
        assertThat(transcript.get("skill_interview").get("turns").get(0).get("question").asText())
                .isEqualTo("기술 질문");
        assertThat(transcript.get("culture_interview").get("turns").get(0).get("answer").asText())
                .isEqualTo("컬처 답변");

        JsonNode report = objectMapper.valueToTree(new InterviewReportRequestedData(1L, sessionId));
        assertThat(report.get("applicant_id").asLong()).isEqualTo(1L);
        assertThat(report.get("interview_session_id").asText()).isEqualTo(sessionId.toString());
    }

    @Test
    @DisplayName("AI에서 Spring으로 보내는 7개 이벤트 payload를 계약 DTO로 역직렬화한다")
    void deserializesAiToSpringPayloads() throws Exception {
        ApplicantProfileSyncCompletedData profileSync = objectMapper.readValue(
                "{\"applicant_id\":1,\"synced\":true}",
                ApplicantProfileSyncCompletedData.class
        );
        assertThat(profileSync.applicantId()).isEqualTo(1L);
        assertThat(profileSync.synced()).isTrue();

        JdAnalysisCompletedData jd = objectMapper.readValue(
                "{\"jd_id\":1,\"company_id\":2,\"original_text\":\"JD\",\"embedding\":[0.1,0.2]}",
                JdAnalysisCompletedData.class
        );
        assertThat(jd.jdId()).isEqualTo(1L);
        assertThat(jd.embedding()).containsExactly(0.1, 0.2);

        ApplicantAnalysisCompletedData applicant = objectMapper.readValue(
                "{\"applicant_id\":1,\"skill_tags\":[\"Java\"],\"job\":\"DEVELOPER\",\"role\":\"BACKEND\"}",
                ApplicantAnalysisCompletedData.class
        );
        assertThat(applicant.job()).isEqualTo("DEVELOPER");
        assertThat(applicant.role()).isEqualTo("BACKEND");

        MatchingCompletedData matching = objectMapper.readValue(
                """
                {"jd_id":1,"matches":[{"applicant_id":1,"skill_score":0.7,"culture_score":0.6,"final_score":0.65,"reason":"적합"}]}
                """,
                MatchingCompletedData.class
        );
        assertThat(matching.matches()).hasSize(1);
        assertThat(matching.matches().get(0).applicantId()).isEqualTo(1L);

        UUID sessionId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        InterviewQuestionGeneratedData question = objectMapper.readValue(
                """
                {"applicant_id":1,"interview_session_id":"550e8400-e29b-41d4-a716-446655440000","next_question_code":"S_01_00","sequence":1,"question":"질문"}
                """,
                InterviewQuestionGeneratedData.class
        );
        assertThat(question.interviewSessionId()).isEqualTo(sessionId);

        InterviewTranscriptSavedData transcript = objectMapper.readValue(
                """
                {"applicant_id":1,"interview_session_id":"550e8400-e29b-41d4-a716-446655440000","saved":true}
                """,
                InterviewTranscriptSavedData.class
        );
        assertThat(transcript.saved()).isTrue();

        InterviewReportCompletedData report = objectMapper.readValue(
                """
                {
                  "applicant_id":1,
                  "interview_session_id":"550e8400-e29b-41d4-a716-446655440000",
                  "applicant_name":"홍길동",
                  "skill_tags":["Java"],
                  "user_provided_tags":["Backend"],
                  "evaluation":{"criteria_summary":{},"overall_score":4.0}
                }
                """,
                InterviewReportCompletedData.class
        );
        assertThat(report.interviewSessionId()).isEqualTo(sessionId);
        assertThat(report.evaluation()).containsKey("criteria_summary");
    }
}
