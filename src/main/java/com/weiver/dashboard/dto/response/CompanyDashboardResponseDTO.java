package com.weiver.dashboard.dto.response;

import com.weiver.company.domain.Company;

public record CompanyDashboardResponseDTO(
        Long companyId,
        String companyLogoUrl,
        String companyCeoName,
        String address,
        Integer employeeNum,
        String foundedYear,
        WayOfWorkingDetail wayOfWorkingDetail
) {
    public static CompanyDashboardResponseDTO from(Company company, String companyLogoUrl){
        return new CompanyDashboardResponseDTO(
                company.getCompanyId(),
                companyLogoUrl,
                company.getCompanyCeoName(),
                company.getAddress(),
                company.getEmployeeNum(),
                company.getFoundedYear().toString(),

                new WayOfWorkingDetail(
                        company.getWorkPace().getDescription(),
                        company.getDecisionMaking().getDescription(),
                        company.getRoleDefinition().getDescription(),
                        company.getOperationStyle().getDescription()
                )
        );
    }
}
