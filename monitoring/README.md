# monitoring

Spring 서버와 RabbitMQ 지표를 Prometheus로 모으고, Grafana 대시보드 1장과 알림 규칙을 파일로 둔다(#180).
운영 배포와 실제 알림 채널 연결은 아직 하지 않았다.

| 파일 | 내용 |
| --- | --- |
| `docker-compose.yml` | Prometheus·Grafana. 로컬 인프라(`../docker-compose.yml`)와 다른 프로젝트(`weiver-monitoring`)이고, `weiver-net`에 external로 붙는다 |
| `prometheus/prometheus.yml` | 로컬용 수집 설정. Spring `host.docker.internal:8081/actuator/prometheus`, RabbitMQ `:15692/metrics`, 큐별 `:15692/metrics/detailed` |
| `prometheus/prometheus.prod.yml` | 운영 EC2용 수집 설정. Spring 대상만 `my-server:8081`이고 나머지는 같다 |
| `grafana/dashboards/weiver-overview.json` | 대시보드 "WEIVER 서버 개요" |
| `grafana/provisioning/alerting/weiver-alerts.yml` | 알림 규칙: DLQ 메시지 있음, 소비자 없이 큐 적체, AI 최장 대기 초과(면접 질문 2분, 그 밖의 flow 15분), 수집 대상 다운(`up` 0이 1분) |
| `grafana/provisioning/alerting/contact-points.yml` | 알림 받을 곳. 채널을 정하지 않아 webhook 1개만 있고, 주소는 `ALERT_WEBHOOK_URL`로 받는다 |

## 로컬에서 띄우기

1. 로컬 인프라를 띄운다: 저장소 루트에서 `docker compose up -d`.
   `rabbitmq:4.2.6-management` 이미지는 `rabbitmq_prometheus` 플러그인이 기본으로 켜져 있어 `:15692`를 따로 열 필요가 없다.
2. Spring을 관리 포트를 나눠 띄운다. local 프로필은 관리 포트가 없어 앱 포트의 `/actuator/prometheus`가 401이다.
   관리 포트는 인증 없이 지표와 health 상세를 내주므로 127.0.0.1에만 연다.

   ```bash
   MANAGEMENT_SERVER_PORT=8081 MANAGEMENT_SERVER_ADDRESS=127.0.0.1 ./gradlew bootRun
   ```

   macOS·Windows의 Docker Desktop은 `host.docker.internal`을 호스트 루프백으로 넘겨 주므로 이대로 수집된다.
   Linux에서는 `host-gateway`가 루프백이 아니어서 127.0.0.1에 묶으면 수집되지 않는다. 이때는 Docker 브리지 주소에 묶는다(Linux는 확인하지 않았다).

3. 이 디렉터리에서 Prometheus·Grafana를 띄운다. 두 값은 필수다.

   ```bash
   cd monitoring
   GRAFANA_ADMIN_PASSWORD=<로컬 비밀번호> ALERT_WEBHOOK_URL=<알림 받을 주소> docker compose up -d
   ```

   - Prometheus: http://127.0.0.1:9090 (수집 상태는 Status → Targets)
   - Grafana: http://127.0.0.1:3000 (WEIVER 폴더의 "WEIVER 서버 개요", 알림은 Alerting → Alert rules)
   - `GRAFANA_ADMIN_PASSWORD`는 `grafana-data` 볼륨을 처음 만들 때만 적용된다. 나중에 바꾸려면 Grafana에서 바꾸거나 볼륨을 지운다.
4. 내릴 때는 이 디렉터리에서 `docker compose down`. `-v`를 붙여도 이 프로젝트의 볼륨(`prometheus-data`, `grafana-data`)만 지워진다.

## 운영 EC2에 올릴 때

운영 compose(`/home/ubuntu/weiver`)는 이 저장소 밖에 있다. 운영에서는 Spring이 `weiver-net`의 `my-server` 컨테이너로 돌고, 관리 포트 8081은 호스트에 publish하지 않는다. 같은 네트워크의 Prometheus는 `my-server:8081`로 수집한다.

```bash
cd monitoring
PROMETHEUS_CONFIG=prometheus.prod.yml GRAFANA_ADMIN_PASSWORD=<비밀번호> ALERT_WEBHOOK_URL=<알림 받을 주소> docker compose up -d
```

- 9090·3000은 이 compose에서 127.0.0.1에만 열린다. 보안 그룹에 9090·3000·8081·15692를 추가하지 않는다. 접속은 SSH 터널로 한다(예: `ssh -L 3000:127.0.0.1:3000 ubuntu@<EC2>`).
- 2026-11-08 운영에서 확인한 값: `weiver-net` 안에서 `my-server:8081/actuator/prometheus`와 `weiver-rabbitmq:15692/metrics`가 모두 200.

## 지표를 읽을 때 주의할 점

- AI 대기 지표(`weiver_ai_pending`, `weiver_ai_pending_oldest_seconds`)는 서버마다 같은 전체 값을 낸다. 여러 대를 묶을 때 `sum`이 아니라 `max`를 쓴다.
- HTTP·요청→결과 지연은 히스토그램이 꺼져 있어 평균과 최대값만 있다. 백분위수(p95 등)는 계산할 수 없다.
- #184가 고쳐지기 전에는 처리 실패 1건이 `spring.domain.events.dlq`와 `ai.domain.events.dlq`에 각각 들어간다. DLQ 합계는 실제 실패 건수의 두 배로 보인다.
- 면접 스크립트 저장에서 `saved=false`를 받은 세션은 `TRANSCRIPT_SAVE_REQUESTED`에 남는다. `interview_transcript_save`의 최장 대기는 그 세션이 정리될 때까지 계속 늘고, 비동기 flow 알림도 계속 울린다.
- "소비자 없이 큐 적체"는 최근 5분 동안 대기 메시지가 늘었을 때만 울린다. 메시지가 더 들어오지 않으면 적체가 남아 있어도 5분 뒤 해소된다.
  - AI 쪽(`ai.domain.events.queue`)은 해소된 뒤에도 AI 최장 대기 알림이 이어서 잡는다.
  - Spring 쪽(`spring.domain.events.queue`)은 Spring 서버가 내려간 경우라 AI 대기 지표도 사라진다. 이때는 "수집 대상 다운" 알림이 잡는다.
- 수집 대상 다운 외의 규칙은 데이터가 없으면 정상(`noDataState: OK`)으로 본다. 수집 대상이 내려가 지표가 사라지면 그 규칙들은 울리지 않고, "수집 대상 다운"만 울린다.
- 로컬에서 Spring을 띄우지 않고 이 스택만 올리면 "수집 대상 다운"(`weiver-backend`)이 1분 뒤 울린다.
