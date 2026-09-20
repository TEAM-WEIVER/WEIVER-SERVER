package com.weiver.analysis.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 컬처핏 상위 4개 문화 축과 각 축에 속하는 Schwartz 기본 가치(10개).
 *
 * <p>축 점수 계산({@code CulturefitAxisService})과 기업 리포트 표시({@code MatchResultReportService})가
 * <b>같은 매핑</b>을 써야 게이지 값과 하위 분해가 어긋나지 않는다. 축→가치 매핑을 바꿀 일이 생기면
 * 반드시 이 enum 한 곳만 고친다.
 *
 * <p>{@code key}는 저장되는 jsonb({@code culture_axis_scores})의 키이고, {@code displayName}은 화면 표기다.
 * {@code traits}는 AI payload {@code extracted_culturefit}의 키와 같다.
 */
@Getter
@RequiredArgsConstructor
public enum CultureAxis {

    /** 자율·혁신 (Openness to change) */
    OPENNESS_TO_CHANGE("openness_to_change", "자율·혁신", List.of("자기방향", "자극", "쾌락")),

    /** 성과·영향 (Self-enhancement) */
    SELF_ENHANCEMENT("self_enhancement", "성과·영향", List.of("성취", "권력")),

    /** 안정·질서 (Conservation) */
    CONSERVATION("conservation", "안정·질서", List.of("안전", "순응", "전통")),

    /** 관계·공동체 (Self-transcendence) */
    SELF_TRANSCENDENCE("self_transcendence", "관계·공동체", List.of("호의", "보편주의"));

    private final String key;
    private final String displayName;
    private final List<String> traits;
}
