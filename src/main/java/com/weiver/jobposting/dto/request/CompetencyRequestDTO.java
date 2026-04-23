package com.weiver.jobposting.dto.request;

import java.util.List;

public record CompetencyRequestDTO (
        List<String> competencyPriorities,
        List<String> requiredTechs
){}
