package com.weiver.jobposting.dto.request;

import java.util.List;

public record TraitPrioritiesUpdateRequestDTO(
        List<String> traitPriorities
) {}
