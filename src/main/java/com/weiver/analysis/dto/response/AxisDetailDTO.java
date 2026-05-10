package com.weiver.analysis.dto.response;

import java.util.List;

public record AxisDetailDTO(
        String name,
        int percentage,
        List<SubTraitDTO> subTraits
) {}
