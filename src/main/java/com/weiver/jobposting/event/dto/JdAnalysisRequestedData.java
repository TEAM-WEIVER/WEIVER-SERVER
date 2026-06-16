package com.weiver.jobposting.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.company.type.DecisionMaking;
import com.weiver.company.type.OperationStyle;
import com.weiver.company.type.RoleDefinition;
import com.weiver.company.type.WorkPace;

import java.util.List;

public record JdAnalysisRequestedData(
        @JsonProperty("jd_id")
        Long jdId,
        @JsonProperty("company_id")
        Long companyId,
        String title,
        @JsonProperty("job_category")
        String jobCategory,
        @JsonProperty("detailed_job")
        String detailedJob,
        @JsonProperty("job_description")
        String jobDescription,
        String requirements,
        String qualifications,
        @JsonProperty("preferred_qualifications")
        String preferredQualifications,
        @JsonProperty("required_skills")
        List<String> requiredSkills,
        @JsonProperty("company_culture")
        String companyCulture,
        @JsonProperty("work_style")
        WorkStyleData workStyle
) {
    public record WorkStyleData(
            @JsonProperty("progress_speed") // 업무 진행 속도
            WorkPace progressSpeed,
            @JsonProperty("decision_maker") // 의사결정 주체
            DecisionMaking decisionMaker,
            @JsonProperty("role_definition") // 역할 정의 방식
            RoleDefinition roleDefinition,
            @JsonProperty("operating_system") // 운영 방식
            OperationStyle operatingSystem
    ) {
    }
}
