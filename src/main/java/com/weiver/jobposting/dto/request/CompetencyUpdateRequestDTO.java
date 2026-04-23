package com.weiver.jobposting.dto.request;

import java.util.List;

public record CompetencyUpdateRequestDTO (
        List<String> competencyPriorities,
        List<String> requiredTechs
){}
