package com.weaver.company.domain;

import com.weaver.company.type.CompanyType;
import com.weaver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "companies")
public class Company extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "login_id", length = 50, nullable = false, unique = true)
    private String loginId;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_type", nullable = false)
    private CompanyType companyType;

    @Column(name = "employee_num", nullable = false)
    private Integer employeeNum;

    @Column(name = "founded_year", nullable = false)
    private LocalDate foundedYear;

    @Column(name = "company_logo_url", nullable = true, columnDefinition = "TEXT")
    private String companyLogoUrl;

    @Column(name = "company_url", nullable = true, columnDefinition = "TEXT")
    private String companyUrl;

    @Column(name = "avg_sale", nullable = false)
    private Integer avgSale;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "culture_description", nullable = false, columnDefinition = "TEXT")
    private String cultureDescription;

    @Column(name = "direction_description", nullable = false, columnDefinition = "TEXT")
    private String directionDescription;

    @Column(name = "work_pace", nullable = false)
    private String workPace;

    @Column(name = "decision_making", nullable = false)
    private String decisionMaking;

    @Column(name = "role_definition", nullable = false)
    private String roleDefinition;

    @Column(name = "operation_style", nullable = false)
    private String operationStyle;

    @Column(name = "additional_work_style", nullable = true, columnDefinition = "TEXT")
    private String additionalWorkStyle;

}
