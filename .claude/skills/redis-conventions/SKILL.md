---
name: redis-conventions
description: WEIVER 서버에서 Redis를 다루는 규칙 — Redis는 Spring 캐시 추상화(@Cacheable)가 아니라 RedisTemplate<String,String> 기반의 임시 키-값 저장소로만 쓴다(JWT 리프레시 토큰·블랙리스트·토큰 버전, 이메일 인증/회원가입 토큰). @Repository로 감싸는 저장소 패턴, 키 네임스페이스·접두어 상수·generateKey 헬퍼, 필수 TTL, 비밀값 SHA-256 해시 저장, 일회성 토큰 getAndDelete, WATCH/MULTI/EXEC 낙관적 회전을 정의한다. Redis에 무언가를 저장·조회하거나 토큰/인증 저장소를 만들 때 사용한다. 계층·이벤트는 architecture-conventions, 명명·보안·예외는 global-conventions, JPA 영속성은 persistence-conventions, 서비스·테스트는 backend-conventions를 따른다.
---

# redis-conventions (WEIVER)

Redis 7(로컬 `docker-compose`, 운영은 관리형). 이 프로젝트에서 Redis는 **범용 캐시가 아니다.**
`@Cacheable`/`@CacheEvict`/`RedisCacheManager`/`@EnableCaching`을 **쓰지 않는다.** 대신
`RedisTemplate<String, String>`을 감싼 `@Repository` 클래스로 **TTL 있는 임시 키-값**을 직접 다룬다.

용도는 현재 **인증·토큰 상태**뿐이다:
- JWT 리프레시 토큰 / 액세스 토큰 블랙리스트 / 토큰 버전 (`global/security/jwt/repository`)
- 이메일 인증 코드·검증 토큰·시도 횟수 / 회원가입 토큰 (`auth/repository`)

---

## 1. 인프라 — `RedisConfig`

```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, String> t = new RedisTemplate<>();
        t.setConnectionFactory(cf);
        StringRedisSerializer s = new StringRedisSerializer();
        t.setKeySerializer(s); t.setValueSerializer(s);
        t.setHashKeySerializer(s); t.setHashValueSerializer(s);
        t.afterPropertiesSet();
        return t;
    }
}
```

- 키·값·해시 직렬화가 **전부 `StringRedisSerializer`**다. 즉 값은 **문자열만** 저장한다.
- 객체를 넣어야 하면 문자열(코드/토큰/숫자/이메일)로 풀어서 저장한다. **JSON 직렬화 RedisTemplate을
  새로 만들지 말 것** — 이 프로젝트는 문자열 저장으로 통일돼 있다.

---

## 2. 저장소 패턴

Redis 접근은 **`@Repository @RequiredArgsConstructor` 클래스 하나**에 가둔다. `RedisTemplate`이 이
클래스 밖(서비스·컨트롤러)으로 새어나가지 않게 한다.

```java
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";     // 접두어 상수
    private final RedisTemplate<String, String> redisTemplate;

    public void save(String publicId, UserRole role, String refreshToken, long ttlMillis) {
        redisTemplate.opsForValue().set(
                generateKey(publicId, role),
                TokenHashUtil.sha256(refreshToken),                    // 비밀값은 해시 저장(§4)
                ttlMillis, TimeUnit.MILLISECONDS);                     // TTL 필수(§3)
    }

    public Optional<String> findHashByPublicId(String publicId, UserRole role) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(generateKey(publicId, role)));
    }

    public boolean existsByPublicId(String publicId, UserRole role) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(generateKey(publicId, role)));
    }

    public void deleteByPublicId(String publicId, UserRole role) {
        redisTemplate.delete(generateKey(publicId, role));
    }

    private String generateKey(String publicId, UserRole role) {       // 키 조립은 private 헬퍼로
        return REFRESH_TOKEN_PREFIX + role.name().toLowerCase() + ":" + publicId;
    }
}
```

**규칙**
- 위치: 인증/토큰 저장소는 `global/security/jwt/repository`, 컨텍스트 고유 토큰은 그 컨텍스트의 `repository/`
  (예: `auth/repository/ApplicantEmailVerificationRepository`).
- **키 접두어는 `private static final String ..._PREFIX` 상수**로 두고, **`private generateKey(...)`
  헬퍼**에서 조립한다. 리터럴 키를 메서드마다 흩뿌리지 않는다.
- 읽기는 **`Optional`로 감싼다**(`Optional.ofNullable(get(...))`). 존재 확인은 `hasKey` →
  `Boolean.TRUE.equals(...)`.
- `RedisTemplate.keys()`(전체 스캔)를 쓰지 않는다.

---

## 3. TTL — 항상 지정

Redis에 넣는 모든 값은 **수명이 유한**하다. `set`에 반드시 TTL을 준다.

```java
redisTemplate.opsForValue().set(key, value, ttlMillis, TimeUnit.MILLISECONDS);   // 토큰류
redisTemplate.opsForValue().set(key, code, Duration.ofMinutes(5));               // 이메일 코드류
```

- 블랙리스트는 **액세스 토큰의 남은 수명**만큼만 산다(`ttlMillis <= 0`이면 저장하지 않는다).
- `increment` 후 최초(`count == 1`)에만 `expire`를 걸어 카운터에도 TTL을 준다(시도 횟수 제한).
- TTL 없는 영구 키를 만들지 않는다. 예외는 `token-version` 카운터처럼 **단조 증가 상태**뿐이며,
  이 경우도 새로 추가할 때 팀과 합의한다.

---

## 4. 비밀값은 해시로, 일회성은 getAndDelete

- **리프레시/액세스 토큰 원문을 저장하지 않는다.** `TokenHashUtil.sha256(token)`으로 해시해서 넣고,
  검증은 해시 비교로 한다(`savedHash.equals(sha256(input))`). 키에 토큰을 쓸 때도 해시를 쓴다
  (블랙리스트 키 = `BLACKLIST:` + `sha256(accessToken)`).
- **일회성 토큰은 `getAndDelete`** 로 읽는 즉시 소멸시킨다(이메일 검증 토큰 등):
  ```java
  redisTemplate.opsForValue().getAndDelete(generateVerifiedTokenKey(token));
  ```
- 로그아웃/토큰 무효화는 `token-version`을 `increment`해서 기존 토큰을 한 번에 무효화한다
  (`JwtAuthenticationFilter`가 토큰의 `tokenVersion`과 현재 버전을 비교).

---

## 5. 원자적 회전 — WATCH/MULTI/EXEC

리프레시 토큰 회전처럼 **읽고-비교하고-쓰는** 연산은 낙관적 락(`SessionCallback` + `WATCH/MULTI/EXEC`)으로
한다. 애플리케이션에서 get→set을 나눠 하지 않는다(경합 시 재사용 탐지가 뚫린다).

```java
redisTemplate.execute(new SessionCallback<RefreshTokenRotationResult>() {
    public RefreshTokenRotationResult execute(RedisOperations ops) {
        ops.watch(key);
        String saved = (String) ops.opsForValue().get(key);
        if (saved == null)               { ops.unwatch(); return NOT_FOUND; }
        if (!saved.equals(oldHash))      { ops.unwatch(); return MISMATCH; }   // 재사용/불일치
        ops.multi();
        ops.opsForValue().set(key, newHash, ttlMillis, TimeUnit.MILLISECONDS);
        List<Object> r = ops.exec();
        return (r == null || r.isEmpty()) ? CONCURRENT_MODIFIED : ROTATED;     // 경합 감지
    }
});
```

- 결과는 enum(`RefreshTokenRotationResult`: `NOT_FOUND`/`MISMATCH`/`CONCURRENT_MODIFIED`/`ROTATED`)로 돌려
  서비스가 분기한다. 실패(`MISMATCH`)는 토큰 재사용 신호이므로 상위에서 `TOKEN_REUSE_DETECTED`로 처리한다
  → global-conventions §3.3.

---

## 6. 키 네임스페이스

`prefix:` + 하위 구분자 `:` 로 콜론 네임스페이스를 만든다.

| 저장소 | 키 형태 | 값 | TTL |
|---|---|---|---|
| 리프레시 토큰 | `refresh:<role_lower>:<publicId>` | `sha256(refreshToken)` | 있음 |
| 블랙리스트 | `BLACKLIST:<sha256(accessToken)>` | `"logout"` | 남은 토큰 수명 |
| 토큰 버전 | `token-version:<role>:<publicId>` | 정수(문자열) | 없음(단조 증가) |
| 이메일 코드/검증/시도 | `applicant:email:code:` / `:verified:` / `:attempts:` + 값 | 코드/이메일/카운트 | 있음 |

> ⚠️ **기존 키 접두어의 대소문자가 일관적이지 않다**(`refresh:` 소문자 vs `BLACKLIST:` 대문자,
> `token-version`은 role을 소문자화하지 않음). **새 키는 소문자 콜론 네임스페이스**(`refresh:`처럼)로
> 통일하고, 기존 키의 형식을 바꿀 때는 이미 저장된 키와의 호환을 먼저 확인한다.

---

## 7. 체크리스트

- [ ] `RedisTemplate<String, String>`을 `@Repository` 저장소 클래스 안에만 두었는가 (서비스로 노출 안 함)
- [ ] 키 접두어를 `..._PREFIX` 상수 + `generateKey(...)` 헬퍼로 조립했는가
- [ ] `set`에 TTL을 지정했는가 (영구 키를 만들지 않았는가)
- [ ] 토큰 원문 대신 `TokenHashUtil.sha256(...)` 해시를 저장/비교하는가
- [ ] 일회성 토큰을 `getAndDelete`로 소멸시키는가
- [ ] 읽기를 `Optional`로 감싸고 `keys()` 전체 스캔을 쓰지 않았는가
- [ ] 읽고-쓰는 연산에 `WATCH/MULTI/EXEC` 낙관적 락을 썼는가
- [ ] 값 저장에 JSON RedisTemplate을 새로 만들지 않고 문자열로 저장했는가
- [ ] `.\gradlew.bat test` 통과
