package com.weiver.jobposting.domain;

import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "email_templates")
public class EmailTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "email_title", nullable = false)
    private String emailTitle;

    @Column(name = "email_content", nullable = false, columnDefinition = "TEXT")
    private String emailContent;

    @Column(name = "email_banner_url")
    private String emailBannerUrl;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jd_id", nullable = false, unique = true)
    private JobPosting jobPosting;
}
