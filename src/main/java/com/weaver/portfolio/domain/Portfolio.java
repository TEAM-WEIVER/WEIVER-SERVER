package com.weaver.portfolio.domain;

import com.weaver.applicant.domain.Applicant;
import com.weaver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Portfolio extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long portfolioId;


    private String fileName;    // 원본 파일명
    private String fileType;    // 파일 유형

    @Column(columnDefinition = "TEXT")
    private String fileKey; // S3 PDF 저장 경로

    private Long fileSize;  // 파일 용량

    private String urlGithub; // 포트폴리오_깃허브
    private String urlTech;  // 포트폴리오_기술블로그
    private String urlEtc;  // 포트폴리오_기타

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime uploadedAt; // 파일 업로드 일시

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id")
    private Applicant applicant;

    public void assignApplicant(Applicant applicant) {
        this.applicant = applicant;
    }

}
