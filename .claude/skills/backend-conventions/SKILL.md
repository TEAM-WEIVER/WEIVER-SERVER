---
name: backend-conventions
description: WEIVER 서버의 계층형 구조에서 컨트롤러(presentation)·서비스(business)·도메인 엔티티·DTO를 만드는 워크플로우와, 서비스에 로직을 두되 엔티티에 상태 전이를 위임하는 기준, 계층별 JUnit5/Mockito/AssertJ 테스트 전략을 정의한다. 새 기능이나 REST API를 추가하거나, 서비스/컨트롤러/이벤트 핸들러 테스트를 작성할 때 사용한다. 계층 배치·컨텍스트 통신·이벤트는 architecture-conventions, 명명·예외·응답 봉투·보안·로깅은 global-conventions, JPA 엔티티·리포지토리·QueryDSL·트랜잭션은 persistence-conventions, Redis 토큰 저장은 redis-conventions를 따른다.
---

# backend-conventions (WEIVER)

Java 21 · Spring Boot 3.5.13 · Lombok · JUnit5 + Mockito + AssertJ(`spring-boot-starter-test`) · Testcontainers.
`.\gradlew.bat test` (Windows) / `./gradlew test`. 단일 실행 `.\gradlew.bat test --tests "com.weiver.applicant.service.*"`

패키지는 `com.weiver.<context>.<layer>` / `com.weiver.global.<area>` 다 → global-conventions §1

---

## 1. 기능 추가 워크플로우 (바깥에서 안으로)

계층형이므로 **HTTP 계약과 데이터 흐름을 먼저** 잡고 안으로 들어간다.

```
1. 엔티티/컬럼 확인   → domain/            (기존 엔티티가 정본. 새 컬럼/상태 필요 시 추가)
2. 요청/응답 계약     → dto/request, dto/response  (record + Bean Validation)
3. 리포지토리         → repository/        (파생 쿼리 / QueryDSL) → persistence-conventions
4. 서비스             → service/           (@Transactional, 로직·오케스트레이션)
5. 컨트롤러           → controller/        (ResponseEntity<ApiResponse<T>>)
6. (필요 시) 이벤트   → event/             (AI·비동기 파이프라인) → architecture-conventions §5
```

각 단계에 테스트를 붙인다. 서비스·컨트롤러·이벤트 핸들러 테스트는 Spring/DB 없이 Mockito로 돈다(§7).

---

## 2. 컨트롤러 (presentation)

```java
@RestController
@RequestMapping("/api/applicants")
@RequiredArgsConstructor
@Tag(name = "Applicant", description = "구직자 프로필 API")
public class ApplicantController {

    private final ApplicantService applicantService;

    @Operation(summary = "내 프로필 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<ApplicantInfoResponseDTO>> searchApplicant(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {

        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        ApplicantInfoResponseDTO response = applicantService.searchApplicant(principal.publicId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

**규칙**
- `@RestController` + `@RequestMapping("/api/<복수-하이픈명사>")` (`/api/applicants`, `/api/job-postings`).
- 반환은 **`ResponseEntity<ApiResponse<T>>`**. `ApiResponse.success(data)` / `success(message)` /
  `created(data, message)`를 쓴다 → global-conventions §2.
- 인증 주체는 `@AuthenticationPrincipal AuthenticatedPrincipal principal`. **null이면
  `throw new BusinessException(ErrorCode.UNAUTHORIZED)`** — 이 관용구를 핸들러 상단에 둔다.
- 입력 검증은 `@Valid`(`@RequestBody @Valid`, `@RequestPart(...) @Valid`).
- **Swagger 애너테이션을 붙인다**: 클래스 `@Tag`, 메서드 `@Operation`, 파라미터 `@Parameter`,
  멀티파트는 `@Content`/`@Schema`. 인증 principal 파라미터는 `@Parameter(hidden = true)`.
- 파일 업로드는 `consumes = MULTIPART_FORM_DATA_VALUE` + `@RequestPart MultipartFile`.
- **컨트롤러는 얇다.** DTO를 그대로 서비스에 넘긴다. **DTO↔엔티티 변환·분기·조회를 하지 않는다.**
- **try/catch 금지.** `GlobalExceptionHandler`가 처리한다 → global-conventions §3.

---

## 3. 서비스 (business)

```java
@Service
@Transactional
@RequiredArgsConstructor
public class ApplicantService {

    private final ApplicantRepository applicantRepository;
    private final S3Service s3Service;

    @Transactional(readOnly = true)
    public ApplicantInfoResponseDTO searchApplicant(String publicId) {
        Applicant applicant = applicantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return ApplicantInfoResponseDTO.from(applicant);
    }

    public void updateInfo(String publicId, ApplicantUpdateDTO dto, MultipartFile photo) {
        Applicant applicant = applicantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        String photoUrl = (photo == null) ? null : s3Service.publicUpload(photo, "profile");
        applicant.updateInfo(dto, photoUrl);      // 상태 변경은 엔티티에 위임
    }
}
```

**규칙**
- `@Service @Transactional @RequiredArgsConstructor`. **생성자 주입만.** 필드 주입·`@Autowired` 금지.
- 트랜잭션 정책 → persistence-conventions §5:
  - 쓰기 위주 서비스: **클래스 `@Transactional`** + 조회 메서드에 `@Transactional(readOnly = true)`.
  - 조회 위주 서비스: **클래스 `@Transactional(readOnly = true)`** + 쓰기 메서드에 `@Transactional`.
- **조회 실패는 `orElseThrow(() -> new BusinessException(ErrorCode.XXX))`** → global-conventions §3.
- **DTO 변환은 서비스에서** 정적 팩토리로: `XxxResponseDTO.from(entity)`,
  `list.stream().map(XxxResponseDTO::from).toList()`.
- **상태 변경은 엔티티 메서드에 위임한다**(§4). setter로 필드를 직접 바꾸지 않는다.
- 인터페이스+구현 분리는 기본이 **아니다.** 서비스는 단일 클래스가 기본이다. 현재 인터페이스+Impl은
  `auth/service/AuthService`(+`AuthServiceImpl`) 하나뿐이다. 다형성/구현 교체가 실제로 필요할 때만
  인터페이스를 만든다.
- 다른 컨텍스트의 서비스·리포지토리를 직접 주입해 조합해도 된다(느슨한 격리) → architecture-conventions §4.1.
  단 AI·장기 파이프라인은 이벤트로 → architecture-conventions §4.2.
- 이벤트 발행이 필요하면 컨텍스트의 `*EventService`를 주입해 **커밋 후**(`publishAfterCommit`) 발행한다.

---

## 4. 도메인 엔티티 — 빈약하지 않게

`domain/`의 클래스는 **JPA 엔티티이자 도메인 모델**이다(둘을 분리하지 않는다). setter 대신
**의미 있는 상태 전이 메서드**를 둔다. 매핑 규칙은 persistence-conventions §2.

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "applicants")
public class Applicant extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "applicant_id")
    private Long applicantId;

    @Builder.Default
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private String publicId = UUID.randomUUID().toString();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ApplicantStatus status = ApplicantStatus.ACTIVE;

    /** 정보 업데이트 편의 메서드 */
    public void updateInfo(ApplicantUpdateDTO dto, String photoUrl) { ... }

    /** 탈퇴 처리 */
    public void withdraw() { this.status = ApplicantStatus.WITHDRAWN; }
}
```

**리팩토링 신호**
- 서비스가 엔티티의 getter를 여러 번 호출해 상태를 조립·판단한다 → **엔티티 메서드로** (Tell, Don't Ask).
- 서비스에 `if (status == X) ...` 상태 분기가 반복된다 → 엔티티/`type` enum 메서드로.
- setter로 여러 필드를 순차 변경한다 → 의미 있는 이름의 전이 메서드 하나로.

---

## 5. DTO

```java
// dto/response — 응답. 정적 팩토리 from(entity)
public record ApplicantDetailResponseDTO(String photoUrl, String name, String phoneNumber) {
    public static ApplicantDetailResponseDTO from(Applicant a) {
        return new ApplicantDetailResponseDTO(a.getPhotoUrl(), a.getName(), a.getPhoneNumber());
    }
}

// dto/request — 요청. Bean Validation + toEntity
public record EducationRequestDTO(
        @NotBlank(message = "school은 필수입니다.") String school,
        @NotNull  EducationLevel level) {
    public Education toEntity(Applicant applicant) { return Education.builder()...build(); }
}
```

**규칙**
- **DTO는 Java `record`.** class + `@Getter/@Setter`를 쓰지 않는다.
- `dto/request` / `dto/response`로 나눈다(applicant는 `request/post`, `request/put`로 더 세분).
- **응답 DTO는 `from(entity)`, 요청 DTO는 `toEntity(...)`/`toEntityList(...)`** 정적/인스턴스 팩토리.
- **Bean Validation은 record 컴포넌트에 직접**(`@NotBlank`, `@NotNull`, 중첩은 `@Valid`), 메시지는 한글.
- 외부 JSON 필드가 snake_case면 `@JsonProperty("snake_case")`, 내부는 camelCase.
- 도메인 DTO는 **`~DTO` 접미사**(`JobPostingRequestDTO`, `JobPostingResponseDTO`). `global` 공통 DTO는
  무접미사 의미 이름(`ApiResponse`, `ErrorResponse`) → global-conventions §1.2.
- 페이징 응답은 컨텍스트별 `PageInfo` + `*PageResponseDTO.of(Page, content)` → global-conventions §2.1.
- Swagger `@Schema(description=, example=)`를 컴포넌트에 붙인다.

---

## 6. 예외 처리

- 도메인/유스케이스 오류는 **`throw new BusinessException(ErrorCode.XXX)`** 로만 표현한다.
- `ErrorCode`가 `HttpStatus`·기본 메시지를 갖고, `GlobalExceptionHandler`가 `ErrorResponse`로 변환한다.
- 새 오류는 `ErrorCode` enum의 **해당 도메인 섹션**에 추가한다. 상세는 global-conventions §3.
- 서비스/컨트롤러에서 오류 응답을 직접 조립하지 않는다.

---

## 7. 테스트 전략

### 7.1 계층별 표준

| 대상 | 스타일 | Spring | DB |
|---|---|---|---|
| 서비스 (`service/*Test`) | `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` + BDDMockito + AssertJ | ✕ | ✕ |
| 컨트롤러 (`controller/*Test`) | `@WebMvcTest` + `MockMvc` + `@MockitoBean` + `@AutoConfigureMockMvc(addFilters=false)` | 슬라이스 | ✕ |
| 리포지토리 (`repository/*Test`) | `@DataJpaTest` + Testcontainers PostgreSQL + `@ServiceConnection` | ✓ | **컨테이너** |
| 이벤트 핸들러/발행 (`event/**/*Test`) | 순수 Mockito. 핸들러를 `new`로 생성, `ObjectMapper`는 실제 인스턴스 | ✕ | ✕ |
| WebSocket 컨트롤러 | 순수. `mock()` + 컨트롤러 `new` | ✕ | ✕ |

- **공통 `IntegrationTestSupport` 베이스 클래스는 없다.** `@SpringBootTest`는 `contextLoads` 하나뿐.
- **`src/test/resources`·`test` 프로파일이 없다.** 통합 테스트 DB 접속은 `@ServiceConnection`이
  Testcontainers에서 런타임 주입한다. 더미 설정값은 파일이 아니라 테스트 코드에서 생성자로 주입한다.
- **전용 Fixture 클래스는 없다.** 테스트 데이터는 엔티티 `@Builder`로 인라인 생성한다.

### 7.2 서비스 테스트 — Mockito

```java
@ExtendWith(MockitoExtension.class)
class ApplicantServiceImplTest {

    @Mock private ApplicantRepository applicantRepository;
    @Mock private S3Service s3Service;
    @InjectMocks private ApplicantService applicantService;
    @Captor private ArgumentCaptor<List<Education>> educationCaptor;

    @Test
    @DisplayName("프로필 사진 없이 텍스트 정보만 업데이트하면 S3 통신은 발생하지 않는다")
    void updateApplicantInfo_WithoutPhoto() {
        // given
        given(applicantRepository.findByPublicId("pub-1")).willReturn(Optional.of(anApplicant()));
        // when
        applicantService.updateInfo("pub-1", updateDto(), null);
        // then
        then(s3Service).shouldHaveNoInteractions();
    }
}
```

- 스텁 `BDDMockito`(`given/willReturn`), 검증 `then(...).should()`, 단언 **AssertJ**
  (`assertThat`, `assertThatThrownBy`). 예외는 `BusinessException` + `ErrorCode`까지 단언한다.
- 리포지토리에 넘어가는 인자는 `@Captor ArgumentCaptor`로 검증한다.

### 7.3 컨트롤러 테스트 — `@WebMvcTest`

```java
@WebMvcTest(ApplicantController.class)
@AutoConfigureMockMvc(addFilters = false)          // 보안 필터 off
class ApplicantControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean ApplicantService applicantService;
    @MockitoBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach void tearDown() { SecurityContextHolder.clearContext(); }

    private RequestPostProcessor customAuth(String publicId) {
        return req -> {
            var principal = new AuthenticatedPrincipal(publicId, UserRole.APPLICANT);
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))));
            return req;
        };
    }
}
```

- **`@MockitoBean`** 을 쓴다(`@MockBean` 아님 — Spring Boot 3.4+). 협력 빈(서비스, JWT 필터/프로바이더,
  쿠키·토큰 리졸버)을 `@MockitoBean`으로 대체한다.
- 보안은 `addFilters = false`로 끄고, 인증이 필요하면 `RequestPostProcessor`로 `SecurityContext`에
  `AuthenticatedPrincipal`을 수동 주입한다. **`@WithMockUser`는 쓰지 않는다.**
- 검증은 `status()` / `jsonPath()`. **실패 경로는 HTTP 상태와 응답 `errorCode`까지 단언**한다.

### 7.4 이벤트 핸들러 테스트

```java
@ExtendWith(MockitoExtension.class)
class ApplicantAnalysisCompletedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();   // 실제 인스턴스
    @Mock TechnicalSkillReportRepository technicalSkillReportRepository;

    @Test
    @DisplayName("지원자 분석 완료 이벤트를 수신하면 기술/컬처 리포트를 upsert한다")
    void handle_UpsertsReports() {
        var handler = new ApplicantAnalysisCompletedHandler(objectMapper, applicantRepository,
                technicalSkillReportRepository, cultureReportRepository);
        handler.handle(envelope(data()));                          // 헬퍼로 EventEnvelope 조립
        then(technicalSkillReportRepository).should().save(any());
    }
}
```

- 핸들러를 **생성자로 직접 `new`** 하고 `ObjectMapper`는 실제 인스턴스를 쓴다(payload 직렬화 실검증).
- payload 검증 실패는 `assertThatThrownBy(...).isInstanceOf(NonRetryableEventException.class)`.
- 이벤트 payload 계약은 `global/event/DomainEventPayloadContractTest`처럼 실제 `ObjectMapper`로 검증한다.

### 7.5 리포지토리 / 외부 HTTP 테스트

- **리포지토리**: `@DataJpaTest @AutoConfigureTestDatabase(replace = NONE)` + `@Testcontainers` +
  `@Container @ServiceConnection static PostgreSQLContainer<?>("postgres:15-alpine")`. QueryDSL은
  `@TestConfiguration`으로 `JPAQueryFactory` 빈 + `@EnableJpaAuditing`을 주입한다 → persistence-conventions §4.
  **Docker 데몬이 떠 있어야 한다.**
- **WebClient**: okhttp3 `MockWebServer`를 띄우고 그 baseUrl로 실제 `WebClient`를 만들어 주입한다
  (`ResendEmailSenderTest`가 예).

### 7.6 테스트 위치와 이름

- 테스트 패키지는 **main 구조를 미러링**한다: `src/test/java/com/weiver/<context>/<layer>/<대상>Test.java`.
- 클래스 `<대상>Test`, 메서드는 **영어 camelCase + 언더스코어**(`updateApplicantInfo_WithoutPhoto`),
  **`@DisplayName`은 한글 문장으로 필수**. `// given / // when / // then` 주석으로 구간을 나눈다.

---

## 8. 체크리스트

**컨트롤러**
- [ ] `ResponseEntity<ApiResponse<T>>`를 반환하는가
- [ ] `@AuthenticationPrincipal` null 체크 → `BusinessException(UNAUTHORIZED)`가 있는가
- [ ] `@Valid`와 Swagger(`@Tag`/`@Operation`)가 있고 로직·변환이 없는가

**서비스**
- [ ] `@Transactional` 정책(클래스 기본 + 조회 `readOnly` 오버라이드)이 맞는가
- [ ] 조회 실패가 `orElseThrow(() -> new BusinessException(...))`인가
- [ ] 상태 변경을 엔티티 메서드에 위임했는가 (setter 남용 없음)
- [ ] 인터페이스를 불필요하게 만들지 않았는가 (기본은 단일 클래스)

**DTO**
- [ ] `record` + `from`/`toEntity` 팩토리 + Bean Validation(한글 메시지)인가
- [ ] 도메인 DTO는 `~DTO` 접미사, 응답에 엔티티를 그대로 노출하지 않는가

**테스트**
- [ ] 서비스=Mockito+BDD+AssertJ, 컨트롤러=`@WebMvcTest`+`@MockitoBean`+`addFilters=false`인가
- [ ] 리포지토리 테스트가 `@DataJpaTest`+Testcontainers PostgreSQL+`@ServiceConnection`인가
- [ ] `@DisplayName`(한글)과 given/when/then 주석이 있는가
- [ ] 실패 경로에서 `errorCode`까지 단언하는가
- [ ] `.\gradlew.bat test` 통과
