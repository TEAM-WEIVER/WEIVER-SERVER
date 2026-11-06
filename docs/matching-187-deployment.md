# #187 배포 필수 절차와 운영 정책

## 배포 전 반드시 실행

**[DDL·데이터 전환 SQL](sql/matching-187-transition.sql)을 실행하지 않은 상태로 새 버전을 기동하거나 이벤트 소비를 재개하지 않는다.**

기존 `notifications.match_id` FK가 남으면 알림이 참조한 매칭 결과를 벌크 삭제할 때 FK 위반이 발생한다.
`jd_id`가 없거나 기존 데이터가 채워지지 않으면 알림 INSERT·조회가 실패한다. `ddl-auto=update`는 기존
FK 제거와 데이터 채우기를 대신하지 않는다.

### 실행 책임과 시점

- 실행 책임: 이 변경을 배포하는 **릴리스 실행 담당자(DB 변경 권한 보유자)**가 SQL 실행·검증 결과 기록을 담당한다.
- 확인 책임: **#187 기존 담당자 LeeHyunWoo02**가 배포 전에 실제 실행자, 백업 위치, 적용 대상 DB를 확인한다.
  실제 실행자가 확인되지 않으면 배포를 진행하지 않는다. 이 문서는 배포 승인이나 실행 완료 기록이 아니다.
- 실행 시점: **구버전 스케줄러·이벤트 소비를 포함한 모든 쓰기 중지 후, 새 버전 기동 전**.
- 실패 시: 데이터 누락 등 SQL 오류가 나면 배포를 중단한다. 처리 기록이나 기존 알림을 임의 삭제해 우회하지 않는다.

### 실행 순서

1. 대상 DB와 `search_path`, 백업·복구 지점을 확인하고 실행자를 배포 기록에 남긴다.
2. 구버전 인스턴스의 스케줄러·이벤트 소비·관련 쓰기를 중지하고 진행 중인 트랜잭션 종료를 확인한다.
3. `psql -v ON_ERROR_STOP=1 -f docs/sql/matching-187-transition.sql`로 전환한다.
   접속 대상과 인증 정보는 배포 환경에서 별도로 지정하며 로그나 PR에 비밀값을 기록하지 않는다.
4. 아래 검증 결과를 배포 기록에 남긴다.
5. 새 버전을 기동하고 스키마와 알림 조회·매칭 이벤트 처리를 확인한 뒤 소비를 재개한다.

```sql
-- 누락은 0이어야 한다.
SELECT COUNT(*) AS missing_jd_id FROM notifications WHERE jd_id IS NULL;

-- NOT NULL이 적용되어 있어야 한다(attnotnull = true).
SELECT attnotnull FROM pg_attribute
WHERE attrelid = 'notifications'::regclass AND attname = 'jd_id';

-- 결과 행이 없어야 한다.
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
WHERE conrelid = 'notifications'::regclass
  AND confrelid = 'match_results'::regclass AND contype = 'f';

-- event_id UNIQUE를 확인한다.
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint
WHERE conrelid = 'matching_processed_events'::regclass AND contype = 'u';
```

SQL은 `jd_id`를 nullable로 추가한 뒤 데이터를 채우고, 누락 검사·FK 제거·NOT NULL 설정·처리 기록
테이블 생성을 한 트랜잭션으로 수행한다. 구버전과 새 버전을 혼합 실행하면 공통 잠금 보장이 깨진다.
새 결과로 교체한 뒤에는 알림의 `match_id`가 삭제된 행의 스냅샷일 수 있어 FK만 복원하는 롤백은 불가능하다.
실제 운영 DB 적용은 개발·테스트 완료와 별개의 배포 작업이다.

## 공통 잠금과 대기 제한

- 스케줄러는 공통 advisory lock을 즉시 얻지 못하면 해당 정규 실행을 건너뛴다.
- 매칭 결과 교체는 `weiver.matching.lock-timeout-ms`만큼 잠금을 기다린다. 기본값은 **5000ms**이며
  환경 변수 `MATCHING_LOCK_TIMEOUT_MS`로 조정한다. 0 이하의 무제한 대기는 허용하지 않는다.
- 대기 제한은 advisory lock 획득 구간에만 `SET LOCAL`로 적용한다. 획득 후 기존 `lock_timeout`을 복원한다.
  제한을 초과하면 이벤트 처리 기록과 결과 교체를 함께 롤백하고, 기존 리스너의 `requeue=false` 정책으로 DLQ에 보낸다.
  자동 재시도나 DLQ 재처리는 추가하지 않는다.
- 향후 MatchResult 생성·삭제 또는 `isNotified`를 바꾸는 경로도 `MatchingProcessingLockRepository`의
  동일 잠금 키에 참여해야 한다.

## 처리 기록 보존과 같은 브랜치에서 남기는 후속 검토 항목

현재 `matching_processed_events`는 **자동 삭제 없이 보존**한다. 기록을 지우면 그 이벤트의 중복 차단
보장이 사라지므로 근거 없이 TTL이나 정리 스케줄러를 추가하지 않는다. 적용 범위는
`MATCHING_COMPLETED`이며 다른 이벤트의 도메인 상태 기반 멱등성은 유지한다.

다음 항목은 운영 정책 결정이 필요한 미완료 항목이다. 구현 완료로 체크하지 않는다.

- [ ] 이벤트 처리량과 테이블·유일 인덱스 크기, 증가율 측정
- [ ] 이벤트 재전달·DLQ 수동 재처리·백업 복원 시 재수신 가능한 기간 확인
- [ ] 중복 차단 보장 기간과 보존 기간 결정·승인
- [ ] 보존 기간 이후 도착한 이벤트의 처리 방식 결정
- [ ] 정리가 필요하면 보관 방식·청크 크기·잠금 영향·실패 복구 설계
- [ ] 정리 전후 및 오래된 이벤트 재수신 검증

이 검토 목록은 만료·삭제 정책의 승인을 뜻하지 않는다. 정책 확정 전에는 기존 처리 기록을 정리하지 않는다.
