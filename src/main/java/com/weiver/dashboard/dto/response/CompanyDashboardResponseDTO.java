package com.weiver.dashboard.dto.response;

import com.weiver.company.domain.Company;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기업 대시보드 정보 카드 응답 DTO")
public record CompanyDashboardResponseDTO(
        @Schema(description = "기업 고유 ID", example = "1")
        Long companyId,

        @Schema(description = "기업 로고 이미지 URL", example = "https://s3.weiver.com/logo/company.png")
        String companyLogoUrl,

        @Schema(description = "대표자명", example = "이관형")
        String companyCeoName,

        @Schema(description = "기업 주소", example = "경기도 안산시 상록구 한양대학로 55")
        String address,

        @Schema(description = "직원 수", example = "150")
        Integer employeeNum,

        @Schema(description = "설립 연도 (문자열 형식)", example = "2015-05-10")
        String foundedYear,

        @Schema(description = "업무방식")
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
