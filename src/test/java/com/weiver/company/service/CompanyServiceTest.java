package com.weiver.company.service;

import com.weiver.company.domain.Company;
import com.weiver.company.dto.response.CompanyInfoResponseDTO;
import com.weiver.company.repository.CompanyRepository;
import com.weiver.company.type.CompanyType;
import com.weiver.company.type.DecisionMaking;
import com.weiver.company.type.OperationStyle;
import com.weiver.company.type.RoleDefinition;
import com.weiver.company.type.WorkPace;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @InjectMocks
    private CompanyService companyService;

    @Mock private CompanyRepository companyRepository;

    @Test
    @DisplayName("기업 정보 조회 성공 시 Company를 CompanyInfoResponseDTO로 변환해 반환한다.")
    void getMyCompanyInfo_success() {
        // given
        String publicId = "uuid-company-1";

        Company company = Company.builder()
                .publicId(publicId)
                .companyName("위버")
                .companyType(CompanyType.STARTUP)
                .employeeNum(50)
                .companyCeoName("김대표")
                .foundedYear(LocalDate.of(2020, 1, 1))
                .avgSale(100)
                .address("서울시 강남구")
                .cultureDescription("수평적인 문화")
                .directionDescription("성장 지향")
                .workPace(WorkPace.FAST_EXECUTION)
                .decisionMaking(DecisionMaking.TEAM_CONSENSUS)
                .roleDefinition(RoleDefinition.CLEAR_RESPONSIBILITY)
                .operationStyle(OperationStyle.EXPERIMENT_ORIENTED)
                .build();

        when(companyRepository.findByPublicId(publicId)).thenReturn(Optional.of(company));

        // when
        CompanyInfoResponseDTO result = companyService.getMyCompanyInfo(publicId, UserRole.COMPANY);

        // then
        assertThat(result.companyName()).isEqualTo("위버");
        assertThat(result.employeeNum()).isEqualTo(50);
        assertThat(result.address()).isEqualTo("서울시 강남구");
        assertThat(result.workPace()).isEqualTo(WorkPace.FAST_EXECUTION);
    }

    @Test
    @DisplayName("존재하지 않는 publicId로 조회 시 COMPANY_NOT_FOUND 예외 발생")
    void getMyCompanyInfo_companyNotFound() {
        // given
        String invalidPublicId = "invalid-id";
        when(companyRepository.findByPublicId(invalidPublicId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> companyService.getMyCompanyInfo(invalidPublicId, UserRole.COMPANY))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.COMPANY_NOT_FOUND.defaultMessage);
    }

    @Test
    @DisplayName("기업 회원이 아닌 경우 FORBIDDEN 예외 발생")
    void getMyCompanyInfo_forbidden() {
        // given & when & then
        assertThatThrownBy(() -> companyService.getMyCompanyInfo("any-id", UserRole.APPLICANT))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}