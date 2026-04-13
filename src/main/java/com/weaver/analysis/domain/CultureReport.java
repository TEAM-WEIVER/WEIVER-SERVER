package com.weaver.analysis.domain;


import com.weaver.analysis.type.CULTUREFITSTYLE;
import com.weaver.applicant.domain.Applicant;
import com.weaver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
public class CultureReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cultureReportId;

    @Enumerated(EnumType.STRING)
    private CULTUREFITSTYLE cultureFitStyle; // 컬처핏 스타일

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> cultureFitTag; // 컬처핏 리스트

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "applicant_id")
    @ToString.Exclude
    private Applicant applicant;

    public void assignApplicant(Applicant applicant) {
        this.applicant = applicant;
    }

}
