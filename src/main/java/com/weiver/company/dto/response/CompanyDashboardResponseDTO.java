package com.weiver.company.dto.response;

public record CompanyDashboardResponseDTO(
        long companyId,
        String companyLogoUrl,
        String companyCeoName
) {
}
