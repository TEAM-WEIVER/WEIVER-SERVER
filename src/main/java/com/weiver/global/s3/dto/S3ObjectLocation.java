package com.weiver.global.s3.dto;

/**
 * S3 객체의 위치(버킷, 디코딩된 객체 키).
 * 저장된 객체 URL을 외부 시스템(AI 서버)이 직접 내려받을 수 있는 형태로 넘길 때 쓴다.
 */
public record S3ObjectLocation(String bucket, String key) {
}
