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
@Table(name = "portfolios")
public class Portfolio extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(name = "file_name", nullable = false)
    private String fileName;    // 원본 파일명

    @Column(name = "file_type", nullable = false)
    private String fileType;    // 파일 유형

    @Column(name = "file_key", columnDefinition = "TEXT", nullable = false)
    private String fileKey; // S3 PDF 저장 경로

    @Column(name = "file_size")
    private Long fileSize;  // 파일 용량

    @Column(name = "url_github")
    private String urlGithub; // 포트폴리오_깃허브

    @Column(name = "url_tech")
    private String urlTech;  // 포트폴리오_기술블로그

    @Column(name = "url_etc")
    private String urlEtc;  // 포트폴리오_기타

    @Column(name = "uploaded_at", columnDefinition = "TIMESTAMP", nullable = false)
    private LocalDateTime uploadedAt; // 파일 업로드 일시

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    public void assignApplicant(Applicant applicant) {
        this.applicant = applicant;
    }

}
