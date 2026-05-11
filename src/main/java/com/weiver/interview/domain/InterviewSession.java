package com.weiver.interview.domain;

import com.weiver.applicant.domain.Applicant;
import com.weiver.global.common.BaseTimeEntity;
import com.weiver.interview.type.InterviewType;
import com.weiver.matching.dto.response.InterviewScriptDTO;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcript", nullable = false, columnDefinition = "jsonb")
    private List<InterviewScriptDTO> transcript;

    @Column(name = "video_url")
    private String videoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;
}
