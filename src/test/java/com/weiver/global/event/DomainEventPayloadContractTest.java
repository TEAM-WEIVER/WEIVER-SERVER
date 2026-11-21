package com.weiver.global.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.weiver.analysis.event.dto.ApplicantAnalysisCompletedData;
import com.weiver.analysis.event.dto.ApplicantAnalysisRequestedData;
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
                analysisRequested(null)
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
    @DisplayName("Spring에서 AI로 보내는 6개 이벤트 payload를 계약 필드명으로 직렬화한다")
    void serializesSpringToAiPayloads() {
        JsonNode profile = objectMapper.valueToTree(analysisRequested(
                new ApplicantAnalysisRequestedData.PortfolioData(
                        12L,
                        ApplicantAnalysisRequestedData.PortfolioFileData.of(
                                "private-bucket", "portfolios/3f2a-e1.pdf", "포트폴리오.pdf", 2481934L)
                )
        ));
        assertThat(profile.get("applicant_id").asLong()).isEqualTo(1L);
        assertThat(profile.get("submission_version").asInt()).isEqualTo(2);
        assertThat(profile.get("applicant_name").asText()).isEqualTo("홍길동");
        JsonNode education = profile.get("educations").get(0);
        JsonNode experience = profile.get("experiences").get(0);
        assertThat(education.get("education_level").asText()).isEqualTo("BACHELOR");
        assertThat(education.get("graduation_status").asText()).isEqualTo("ACTIVE");
        assertThat(education.has("id")).isFalse();
        assertThat(education.has("gpa")).isFalse();
        assertThat(education.has("start_date")).isFalse();
        assertThat(education.has("end_date")).isFalse();
        assertThat(experience.get("experience_name").asText()).isEqualTo("Weiver");
        assertThat(experience.get("position_title").asText()).isEqualTo("인턴");
        assertThat(experience.get("employment_type").asText()).isEqualTo("INTERN");
        assertThat(experience.get("start_date").asText()).isEqualTo("2026-01-01");
        assertThat(experience.get("end_date").isNull()).isTrue();
        assertThat(experience.get("responsibilities").isNull()).isTrue();
        assertThat(experience.has("id")).isFalse();
        assertThat(experience.has("is_recognized")).isFalse();
        assertThat(profile.get("certifications").get(0).asText()).isEqualTo("정보처리기사");
        assertThat(profile.get("essay").get(0).get("question").asText()).isEqualTo("질문");
        JsonNode award = profile.get("awards").get(0);
        assertThat(award.get("award_name").asText()).isEqualTo("해커톤 대상");
        assertThat(award.get("issuer").isNull()).isTrue();
        assertThat(award.get("award_date").asText()).isEqualTo("2026-05-01");
        JsonNode portfolioFile = profile.get("portfolio").get("file");
        assertThat(profile.get("portfolio").get("portfolio_id").asLong()).isEqualTo(12L);
        assertThat(portfolioFile.get("key").asText()).isEqualTo("portfolios/3f2a-e1.pdf");
        assertThat(portfolioFile.get("file_size").asLong()).isEqualTo(2481934L);
        assertThat(portfolioFile.has("version_id")).isTrue();
        assertThat(portfolioFile.get("version_id").isNull()).isTrue();
        assertThat(portfolioFile.size()).isEqualTo(5);

        JsonNode withoutPortfolio = objectMapper.valueToTree(analysisRequested(null));
        assertThat(withoutPortfolio.has("portfolio")).isTrue();
        assertThat(withoutPortfolio.get("portfolio").isNull()).isTrue();

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
                  "skill_tags":["Spring","Java"],
                  "user_provided_tags":["Java"],
                  "evaluation":{"criteria_summary":{},"overall_score":0.82},
                  "extracted_culturefit":{
                    "자기방향":0.72,"자극":0.55,"쾌락":0.31,"성취":0.84,"권력":0.22,
                    "안전":0.61,"순응":0.48,"전통":0.35,"호의":0.76,"보편주의":0.69
                  },
                  "culture_axis":{"x_axis":0.1234,"y_axis":-0.0567}
                }
                """,
                InterviewReportCompletedData.class
        );
        assertThat(report.interviewSessionId()).isEqualTo(sessionId);
        assertThat(report.evaluation())
                .containsKey("criteria_summary")
                .containsEntry("overall_score", 0.82);
        assertThat(report.skillTags()).containsExactly("Spring", "Java");
        assertThat(report.userProvidedTags()).containsExactly("Java");
        assertThat(report.extractedCulturefit())
                .hasSize(10)
                .containsEntry("자기방향", 0.72)
                .containsEntry("보편주의", 0.69);
        assertThat(report.cultureAxis().xAxis()).isEqualTo(0.1234);
        assertThat(report.cultureAxis().yAxis()).isEqualTo(-0.0567);
    }

    @Test
    @DisplayName("AI 지원자 분석 완료 예시 payload 3종(성공 pdf, 성공 unavailable+encrypted, 실패)을 역직렬화한다")
    void deserializesApplicantAnalysisCompletedExamples() throws Exception {
        ApplicantAnalysisCompletedData pdf = objectMapper.readValue(
                """
                {"applicant_id":1,"submission_version":2,"outcome":"SUCCEEDED",
                 "skill_tags":["Java","Spring"],"job":"DEVELOPER","role":"BACKEND",
                 "portfolio_status":"pdf","portfolio_issue":null,"error":null}
                """,
                ApplicantAnalysisCompletedData.class
        );
        assertThat(pdf.applicantId()).isEqualTo(1L);
        assertThat(pdf.submissionVersion()).isEqualTo(2);
        assertThat(pdf.outcome()).isEqualTo("SUCCEEDED");
        assertThat(pdf.isFailed()).isFalse();
        assertThat(pdf.skillTags()).containsExactly("Java", "Spring");
        assertThat(pdf.portfolioStatus()).isEqualTo("pdf");
        assertThat(pdf.portfolioIssue()).isNull();
        assertThat(pdf.error()).isNull();

        ApplicantAnalysisCompletedData unavailable = objectMapper.readValue(
                """
                {"applicant_id":1,"submission_version":3,"outcome":"SUCCEEDED",
                 "skill_tags":["Java"],"job":"DEVELOPER","role":"BACKEND",
                 "portfolio_status":"unavailable","portfolio_issue":"encrypted","error":null}
                """,
                ApplicantAnalysisCompletedData.class
        );
        assertThat(unavailable.isFailed()).isFalse();
        assertThat(unavailable.portfolioStatus()).isEqualTo("unavailable");
        assertThat(unavailable.portfolioIssue()).isEqualTo("encrypted");

        ApplicantAnalysisCompletedData failed = objectMapper.readValue(
                """
                {"applicant_id":1,"submission_version":4,"outcome":"FAILED",
                 "skill_tags":null,"job":null,"role":null,
                 "portfolio_status":null,"portfolio_issue":null,
                 "error":{"stage":"PORTFOLIO_DOWNLOAD","code":"S3_DOWNLOAD_FAILED"}}
                """,
                ApplicantAnalysisCompletedData.class
        );
        assertThat(failed.isFailed()).isTrue();
        assertThat(failed.job()).isNull();
        assertThat(failed.role()).isNull();
        assertThat(failed.error().stage()).isEqualTo("PORTFOLIO_DOWNLOAD");
        assertThat(failed.error().code()).isEqualTo("S3_DOWNLOAD_FAILED");
    }

    private ApplicantAnalysisRequestedData analysisRequested(ApplicantAnalysisRequestedData.PortfolioData portfolio) {
        return new ApplicantAnalysisRequestedData(
                1L,
                2,
                "홍길동",
                List.of(new ApplicantAnalysisRequestedData.EducationData("BACHELOR", "한양대학교", "컴퓨터공학", "ACTIVE")),
                List.of(new ApplicantAnalysisRequestedData.ExperienceData(
                        "Weiver", "인턴", "INTERN", LocalDate.parse("2026-01-01"), null, null)),
                List.of("정보처리기사"),
                List.of(new ApplicantAnalysisRequestedData.EssayData(1L, "질문", "답변")),
                List.of(new ApplicantAnalysisRequestedData.AwardData(40L, "해커톤 대상", null, LocalDate.parse("2026-05-01"))),
                portfolio
        );
    }
}
