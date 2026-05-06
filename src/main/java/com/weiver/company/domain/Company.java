package com.weiver.company.domain;

import com.weiver.company.type.*;
import com.weiver.global.common.BaseTimeEntity;
import com.weiver.global.common.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

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

    @Builder.Default
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private String publicId = UUID.randomUUID().toString();

    @Column(name = "login_id", length = 50, nullable = false, unique = true)
    private String loginId;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role = UserRole.COMPANY;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_type", nullable = false)
    private CompanyType companyType;

    @Column(name = "employee_num", nullable = false)
    private Integer employeeNum;

    @Column(name = "company_ceo_name", nullable = false)
    private String companyCeoName;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "work_pace", nullable = false)
    private WorkPace workPace;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_making", nullable = false)
    private DecisionMaking decisionMaking;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_definition", nullable = false)
    private RoleDefinition roleDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_style", nullable = false)
    private OperationStyle operationStyle;

    @Column(name = "additional_work_style", nullable = true, columnDefinition = "TEXT")
    private String additionalWorkStyle;

}
