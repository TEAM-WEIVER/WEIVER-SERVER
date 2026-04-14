package com.weaver.essay.domain;


import com.weaver.applicant.domain.Applicants;
import com.weaver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "essay_answers")
public class EssayAnswers extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long answerId;

    @Column(name = "answer",columnDefinition = "TEXT")
    private String answer;  // 답변

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "applicant_id", nullable = false)
    @ToString.Exclude
    private Applicants applicants;

    public void assignApplicant(Applicants applicants) {
        this.applicants = applicants;
    }
}
