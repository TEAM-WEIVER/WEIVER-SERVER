package com.weaver.coverletter.domain;

import com.weaver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CoverletterQuestion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long questionId;

    private Integer displayOrder;

    @Column(columnDefinition = "TEXT")
    private String question;

    @ToString.Exclude
    @OneToOne(mappedBy = "coverletterQuestion", fetch = FetchType.LAZY)
    private CoverletterAnswer coverletterAnswer;
}
