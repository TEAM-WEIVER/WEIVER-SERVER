package com.weiver.essay.domain;

import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Builder
@Entity
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "essay_questions")
public class EssayQuestion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "max_length", nullable = false)
    private Integer maxLength;

    @Column(name = "question", nullable = false)
    private String question;
}
