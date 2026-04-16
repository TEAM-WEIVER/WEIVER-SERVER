package com.weaver.interview.domain;

import com.weaver.applicant.domain.Applicant;
import com.weaver.global.common.BaseTimeEntity;
import com.weaver.interview.type.InterviewType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "interview_sessions")
public class InterviewSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interview_id")
    private Long InterviewId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", nullable = false, length = 10)
    private InterviewType interviewType;

    @Column(name = "quarter", nullable = false, length = 10)
    private String quarter; // "2026Q1" 형식

    @Column(name = "transcript", nullable = false, columnDefinition = "jsonb")
    private String transcript; // 추후 구조 확정 시 전용 DTO 클래스를 만들어서 매핑 예정

    @Column(name = "video_url")
    private String videoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;
}
