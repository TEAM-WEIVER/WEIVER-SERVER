package com.weiver.analysis.dto.response;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.TechnicalSkillReport;

public record AnalysisReportDTO(
        CultureReport cultureReport,
        TechnicalSkillReport technicalSkillReport
) {}
