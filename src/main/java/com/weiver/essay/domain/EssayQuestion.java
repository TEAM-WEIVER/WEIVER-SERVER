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
// [#127] 기존 데이터가 있는 essay_questions 테이블에 ddl-auto=update 가 NOT NULL
// create_time 을 추가하려다 실패하는 문제를 막기 위해, 이 테이블에 한해 create_time 을
// nullable 로 override 한다. 실제 백필과 NOT NULL 적용은 별도 SQL 로 수행한다.
@AttributeOverride(
        name = "createTime",
        column = @Column(name = "create_time", updatable = false, nullable = true)
)
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
