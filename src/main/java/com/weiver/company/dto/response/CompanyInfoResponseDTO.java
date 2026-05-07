package com.weiver.company.dto.response;

import com.weiver.company.domain.Company;
import com.weiver.company.type.*;

import java.time.LocalDate;

public record CompanyInfoResponseDTO(
        String companyName,
        CompanyType companyType,
        Integer employeeNum,
        String companyCeoName,
        LocalDate foundedYear,
        String companyLogoUrl,
        String companyUrl,
        Integer avgSale,
        String address,
        String cultureDescription,
        String directionDescription,
        WorkPace workPace,
        DecisionMaking decisionMaking,
        RoleDefinition roleDefinition,
        OperationStyle operationStyle,
        String additionalWorkStyle
) {
    public static CompanyInfoResponseDTO from(Company company) {
        return new CompanyInfoResponseDTO(
                company.getCompanyName(),
                company.getCompanyType(),
                company.getEmployeeNum(),
                company.getCompanyCeoName(),
                company.getFoundedYear(),
                company.getCompanyLogoUrl(),
                company.getCompanyUrl(),
                company.getAvgSale(),
                company.getAddress(),
                company.getCultureDescription(),
                company.getDirectionDescription(),
                company.getWorkPace(),
                company.getDecisionMaking(),
                company.getRoleDefinition(),
                company.getOperationStyle(),
                company.getAdditionalWorkStyle()
        );
    }
}
