package com.weiver.global.metrics.dto;

/**
 * 한 flow의 대기 집계 결과.
 *
 * @param count         대기 건수(요청 시각이 없는 행 포함)
 * @param oldestSeconds 요청 시각이 있는 대기 행 중 가장 오래 기다린 시간(초). 그런 행이 없으면 0
 */
public record AiPendingStat(long count, double oldestSeconds) {
}
