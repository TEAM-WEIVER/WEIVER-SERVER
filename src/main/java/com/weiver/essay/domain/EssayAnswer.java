package com.weiver.essay.domain;


import com.weiver.applicant.domain.Applicant;
import com.weiver.essay.dto.request.EssayAnswerUpdateRequestDTO;
import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "essay_answers")
public class EssayAnswer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long answerId;

    @Column(name = "answer", columnDefinition = "TEXT", nullable = false)
    private String answer;  // 답변

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "applicant_id", nullable = false)
    @ToString.Exclude
    private Applicant applicant;

    /**
     * 편의메소드
     * */
    public void updateAnswer(EssayAnswerUpdateRequestDTO requestDTO){
        if(requestDTO.answer() != null) {
            this.answer = requestDTO.answer();
        }
    }

}
