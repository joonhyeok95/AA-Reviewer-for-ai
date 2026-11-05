# spring-boilerplate 개발 가이드

> 기준 문서
> - 개발 가이드 발표 (25.08.13)
> - 개발가이드 (씨젠 프로젝트)
>
> 위 두 문서를 이 프로젝트에 맞게 통합하고, 부족하거나 현재 버전(Spring Boot 4 / Java 17)과 맞지 않는 내용을 보완했습니다.
> 보완한 항목은 **[보완]**, 기존 내용을 바꾼 항목은 **[변경]** 으로 표시합니다.

---

## 0. 기술 스택

| 구분 | 내용 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.x (Spring Framework 7) |
| Build | Maven (Wrapper: `mvnw`, `mvnw.cmd`) |
| DB | H2 (local, Oracle 호환 모드) → Oracle (dev/prod) |
| ORM | Spring Data JPA (Hibernate) |
| API 문서 | springdoc-openapi (Swagger UI) |
| 인증 | Keycloak 26 (OAuth2 BFF 로그인 + SAML2 SP + Resource Server, Spring Security 7) |
| 세션 | Spring Session + Redis 8 (AES-256-GCM 암호화 저장) |
| 운영 | Actuator 관리 포트 분리 + Micrometer Prometheus, Caffeine 캐시 |
| 기타 | Lombok, Apache Commons (lang3, collections4), AspectJ |

---

## 1. 아키텍처

Layered Architecture (Controller → Service → Repository)

| 계층 | 역할 |
|---|---|
| Presentation (`web`) | 요청 바인딩/검증, 응답 생성만 담당. 비즈니스 로직 금지 |
| Business (`domain/service`) | 비즈니스 로직, 트랜잭션 경계 |
| Data Access (`domain/repository`) | DB 접근 |

- 의존 방향은 항상 위 → 아래. Repository 가 Service 를, Service 가 Controller 를 참조하지 않는다.
- Controller 가 Repository 를 직접 호출하지 않는다.

---

## 2. 패키지 구조

```
com.flowlog.backend
 ├─ global                       # 공통
 │  ├─ aop                       # @Logging, LoggingAspect
 │  ├─ common
 │  │  ├─ domain
 │  │  │  ├─ repository          # 공통 Repository
 │  │  │  └─ service             # 공통 Service (MessageService 등)
 │  │  │     └─ dto              # 공통 DTO (PageResponse 등)
 │  │  └─ model                  # 공통 모델 (MessageResponse, BaseEntity 등)
 │  ├─ component                 # 공통 Bean (MessageHttpResponse 등)
 │  ├─ config                    # 설정 (Web, JPA, OpenAPI ...)
 │  ├─ constant                  # 상수 (MessageCode, HeaderConstants)
 │  ├─ exception                 # ErrorCode, BusinessException, GlobalExceptionHandler
 │  ├─ interceptor               # HandlerInterceptor
 │  ├─ security                  # [보완] 인증/인가 (Keycloak OAuth2 / SAML2)
 │  ├─ util                      # 공통 유틸
 │  └─ web                       # Filter (TraceIdFilter 등)
 └─ {업무도메인}                  # 예) sample
    ├─ web                       # XxxController
    ├─ exception                 # [보완] XxxErrorCode (도메인 오류 코드)
    └─ domain
       ├─ model                  # Entity
       ├─ repository             # XxxRepository
       └─ service                # XxxService
          └─ dto                 # XxxCreateRequest, XxxResponse ...
```

> 신규 업무 도메인은 `sample` 패키지를 복사해 시작한다.

---

## 3. 기본 코딩 규칙

[Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) 를 기본으로 한다. (`.editorconfig` 제공)

### 3.1 형식
- 들여쓰기 **2칸 (space)**, 연속 줄은 4칸. **[보완]** (Google Style 기준 명시)
- 한 줄은 **100자**를 넘지 않는다.
- 한 줄에 하나의 문장만 작성한다.
- 와일드카드 import (`import java.util.*;`) 금지.
- 배열은 Java 스타일로 선언한다. `String[] args` (O) / `String args[]` (X)

### 3.2 클래스 / 변수
- 클래스 멤버 순서: 상수(static final) → 필드 → 생성자 → 메서드
- static 변수는 항상 `static final` (상수) 로 선언한다.
- `public` 필드는 사용하지 않는다. (상수 제외)
- 필드 선언 시 주석으로 의미를 적는다. `private String name; // 샘플명`
- **[변경]** "변수는 선언 시 항상 초기화" → 지역 변수는 **사용 시점에 선언과 동시에 초기화**한다.
  생성자 주입 필드(`private final`)는 생성자에서 초기화되므로 예외.

### 3.3 제어문
- `switch` 는 반드시 `default` 를 정의한다.
- 조건문 중첩은 3단계를 넘지 않는다.
- 반복문 조건절에 연산을 넣지 않는다. 반복문 안에서 불필요한 객체 생성을 피한다.
- `do...while` 사용 금지.
- `continue` 사용 금지, `break` 는 `switch` 에서만 사용한다.
- 내용이 없는 블록 금지 (catch 블록이라도 최소 로그를 남긴다).
- `return` 문에서 연산하지 않는다. (변수에 담아 반환)
- **[변경]** "메서드 중간 return 금지" → 원칙은 유지하되, **메서드 첫 부분의 검증용 Guard Clause
  (`if (...) throw new BusinessException(...)`) 는 허용**한다. 중첩 if 를 줄이는 것이 가독성에 더 유리하다.

### 3.4 String
- **[변경]** "String 연결 시 StringBuffer 사용" →
  - 단순 연결은 `+` 사용 (컴파일러가 최적화함)
  - 반복문 안에서 누적 연결 시 `StringBuilder` 사용
  - `StringBuffer` 는 여러 스레드가 공유하는 경우에만 사용 (동기화 비용 있음)
  - 여러 줄 문자열(JPQL, JSON 등)은 Text Block(`"""`) 사용

### 3.5 날짜 / 유틸
- 날짜는 `java.time` (`LocalDate`, `LocalDateTime`, `OffsetDateTime`) 만 사용한다.
  `java.util.Date`, `Calendar`, `DateUtils` **사용 금지**.
- null-safe 체크는 Apache Commons 를 사용한다.
  - 타입을 모르거나 모든 타입 → `ObjectUtils.isEmpty()`
  - 문자열 공백까지 검사 → `StringUtils.isBlank()` / `isNotBlank()`
  - 컬렉션 → `CollectionUtils.isEmpty()` (collections4)
  - Map → `MapUtils.isEmpty()`, `MapUtils.getInteger(map, key, default)`
- 이미 Commons 에 있는 기능을 `util` 에 다시 만들지 않는다.

### 3.6 Lombok
- 허용: `@Getter`, `@Builder`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = PROTECTED)`, `@Slf4j`
- **[보완]** 금지/주의
  - `@Data` 금지 (Setter + equals/hashCode 가 함께 생성되어 Entity 에서 문제 발생)
  - Entity 에 `@Setter` 금지
  - `@SneakyThrows` 금지
  - `@ToString` 을 Entity 에 쓸 때 지연 로딩 필드 제외

### 3.7 주석
- 클래스: Javadoc (설명, `@author`, `@since`)
- public 메서드: Javadoc (설명, `@param`, `@return`, `@throws`)
- 필드: 줄 끝 주석 `// 설명`
- Eclipse 사용자는 배포된 Code Template (Alt+Shift+J) 사용

```java
/**
 * 설명
 *
 * @author 이름
 * @since 2026-11-05
 */
```

---

## 4. 레이어별 개발 가이드

### 4.1 Controller

```java
@Logging
@Tag(name = "Sample", description = "샘플 API")
@RestController
@RequestMapping("/api/v1/samples")
@RequiredArgsConstructor
public class SampleController {

  private final SampleService sampleService;
  private final MessageHttpResponse messageHttpResponse;

  @Operation(summary = "샘플 단건 조회")
  @GetMapping("/{id}")
  public ResponseEntity<MessageResponse<SampleResponse>> getSample(
      @PathVariable("id") @Positive Long id) {
    return messageHttpResponse.success(sampleService.getSample(id));
  }
}
```

- `@RestController` + `@RequestMapping` 으로 URI Context 를 지정한다.
- HTTP Method 별 `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping` 을 사용한다.
- 클래스에 `@Logging` 을 반드시 선언한다.
- `@RequestBody` 는 항상 `@Valid` 와 함께 쓰고, **Entity 가 아닌 요청 DTO** 로 받는다.
- 반환 타입은 `ResponseEntity<MessageResponse<T>>` 이며 **`MessageHttpResponse` 로만 생성**한다.
- **[변경]** `HttpServletRequest` 를 파라미터로 받아 넘기지 않는다.
  path / method / locale 은 `MessageHttpResponse` 가 현재 요청에서 직접 조회한다.
- **[보완]** Swagger 문서화: 클래스에 `@Tag`, 메서드에 `@Operation(summary)`, DTO 필드에 `@Schema` 를 작성한다.

### 4.2 Service

```java
@Logging
@Service("sampleService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SampleService {

  private final SampleRepository sampleRepository;

  @Transactional
  public SampleResponse createSample(SampleCreateRequest request) { ... }
}
```

- `@Service` 선언 시 `@Logging` 을 함께 선언하고, **Bean 이름을 명시**한다. (`@Service("sampleService")`)
- 다형성이 필요한 경우에만 Interface + Impl 구조를 사용하고, 그 외에는 Service 클래스만 만든다.
  - Impl 이름은 `사용용도 + 인터페이스명` (예: `PropertiesMessageService implements MessageService`)
- 의존성 주입은 **생성자 주입** (`private final` + `@RequiredArgsConstructor`) 을 기본으로 한다.
  필드 주입(`@Autowired` 필드)은 금지, Setter 주입은 반드시 필요한 경우에만.
- **[보완]** Service 는 Entity 를 밖으로 반환하지 않는다. DTO 로 변환해 반환한다.
- **[보완]** 다른 도메인 데이터가 필요하면 그 도메인의 Repository 가 아닌 **Service** 를 주입받는다.

### 4.3 Repository

- `JpaRepository<Entity, ID>` 를 상속한다.
- 단순 조건은 Query Method (`findByName`, `existsByNameAndIdNot` …), 선택 조건 검색은 `@Query`(JPQL) 로 작성한다.
- 복잡한 동적 쿼리/통계가 많아지면 QueryDSL 또는 MyBatis 도입을 검토한다.
  (MyBatis 도입 시 기존 가이드의 CommonService / CommonDao 패턴을 따른다.)

| 키워드 | 예시 |
|---|---|
| Is, Equals | `findByName(String name)` |
| Not | `findByStatusNot(String status)` |
| Between | `findByCreatedAtBetween(LocalDateTime s, LocalDateTime e)` |
| LessThan / GreaterThan | `findByAgeLessThan(int age)` |
| Containing | `findByNameContaining(String keyword)` |
| StartingWith / EndingWith | `findByNameStartingWith(String prefix)` |
| In | `findByStatusIn(Collection<String> statuses)` |
| IsNull / IsNotNull | `findByDeletedAtIsNull()` |
| OrderBy | `findByAgeOrderByNameAsc(int age)` |
| And / Or | `findByFirstNameAndLastName(String fn, String ln)` |

### 4.4 Entity

```java
@Getter
@Entity
@Table(name = "TB_SAMPLE")
@SequenceGenerator(name = "SQ_SAMPLE_GEN", sequenceName = "SQ_SAMPLE", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sample extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_SAMPLE_GEN")
  @Column(name = "SAMPLE_ID")
  private Long id; // 샘플 ID

  @Builder
  private Sample(String name, ...) { ... }

  public void update(String name, ...) { ... }
}
```

- **[변경]** Entity 에 `@Setter` 를 쓰지 않는다. 상태 변경은 의미 있는 메서드(`update`, `changeStatus` …)로 한다.
- **[변경]** Validation 어노테이션(`@NotBlank` 등)은 Entity 가 아닌 **요청 DTO** 에 선언한다.
  패키지는 `javax.validation` → **`jakarta.validation`**.
- 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 생성은 `@Builder` 로 한다.
- 생성/수정 일시·사용자가 필요한 Entity 는 `BaseEntity` (또는 `BaseTimeEntity`) 를 상속한다.
- 연관관계(`@ManyToOne`, `@OneToMany`)를 매핑하지 않고 **FK 컬럼을 직접 매핑**하는 방식을 기본으로 한다.
  (N+1, 불필요한 join 방지 / 조회 패턴이 명확한 업무에 유리)
  도메인 중심 설계가 꼭 필요한 경우에만 연관관계 + fetch join 을 사용한다.

### 4.5 DTO **[보완]**

| 용도 | 이름 | 예 |
|---|---|---|
| 등록 요청 | `XxxCreateRequest` | `SampleCreateRequest` |
| 수정 요청 | `XxxUpdateRequest` | `SampleUpdateRequest` |
| 검색 조건 | `XxxSearchRequest` | `SampleSearchRequest` |
| 응답 | `XxxResponse` | `SampleResponse` |

- DTO 는 Java `record` 로 작성한다. (불변, getter/equals/toString 자동)
- 변환 메서드: 요청 → Entity 는 `toEntity()`, Entity → 응답은 `static from(Entity)`.

---

## 5. API 설계 규칙 **[보완]**

### 5.1 URL
- `/api/v{버전}/{리소스 복수형}` — 예) `/api/v1/samples`, `/api/v1/samples/{id}`
- 소문자, 단어 구분은 kebab-case — 예) `/api/v1/user-roles`
- 동사 사용 금지 (`/getSamples` X). 행위는 HTTP Method 로 표현한다.
  리소스로 표현하기 어려운 행위만 하위 경로로 표현 — 예) `POST /api/v1/orders/{id}/cancel`

### 5.2 HTTP Method / 상태코드 / 메시지

| 동작 | Method | 성공 Status | messageId |
|---|---|---|---|
| 목록 조회 | GET | 200 | S0002 (`{n}건 조회되었습니다.`) |
| 단건 조회 | GET | 200 | S0001 |
| 등록 | POST | 201 | S0003 |
| 수정 (전체) | PUT | 200 | S0004 |
| 수정 (부분) | PATCH | 200 | S0004 |
| 삭제 | DELETE | 200 | S0005 |

### 5.3 표준 응답 형식

성공/실패 **모든 응답은 동일한 형식**이다. `result` 만 Object 이다.

```json
{
  "locale": "ko_KR",
  "timestamp": "2026-11-05T10:44:02.925+09:00",
  "httpStatus": 200,
  "messageId": "S0001",
  "messageTitle": "조회 완료",
  "messageContent": "조회되었습니다.",
  "path": "/api/v1/samples/1",
  "result": { "id": 1, "name": "샘플1" }
}
```

| 필드 | 설명 |
|---|---|
| locale | 응답 로케일 (`Accept-Language` 로 결정, 기본 `ko_KR`) |
| timestamp | 응답 시각 (ISO-8601, Asia/Seoul) |
| httpStatus | HTTP 상태 코드 (숫자) |
| messageId | 메시지 ID |
| messageTitle / messageContent | 다국어 처리된 메시지 |
| path | 요청 URI |
| result | 결과 데이터. 없으면 `null` |

- 목록 조회 `result` 는 `PageResponse` (`content, page, size, totalElements, totalPages, first, last`)
- 검증 오류 `result` 는 `FieldErrorDetail[]` (`field, rejectedValue, reason`)

### 5.4 페이징
- Query String: `page` (0부터), `size` (기본 20, 최대 100), `sort` (`id,desc`)
- 예) `GET /api/v1/samples?name=샘플&useYn=Y&page=0&size=20&sort=id,desc`

### 5.5 다국어
- **[변경]** Locale 은 커스텀 헤더(`LIS-Locale-ID`) 대신 표준 **`Accept-Language`** 헤더로 받는다.
- 응답 메시지 Locale: `ko_KR` (기본), `en_US`. 미지원 언어는 `ko_KR` 로 응답 (표준 응답 `locale` 필드).
- **데이터 다국어 (v0.4)** — 상세 [design/03_notice.md](design/03_notice.md)
  - 지원 언어는 공통코드 **`LANG`** (ISO 639-1: `ko`, `en`, USE_YN='Y', 정렬순서 첫 번째 = 기본 언어). **언어 목록을 하드코딩하지 않는다** — `LangService` 사용
  - `LANG.ATTR1` = 에디터 언어팩 (`ko-KR`, `en-US`). 화면 언어 탭/선택 목록/에디터 매핑은 `GET /api/v1/langs` 로 동적 생성
  - `*_LANG` 테이블(코드/메뉴/권한/공지)의 `LANG_CD` 는 `ko`/`en`
  - 사용자 언어 = `TB_USER.LANG_CD` → `Accept-Language` → 기본 언어 (`LangService.resolveUserLang`). 변경: `PUT /api/v1/users/me/lang`
  - 이름이 없는 언어는 기본 언어 이름으로 대체. 인사 마스터 명칭은 ko = 국문, 그 외 = 영문명(없으면 국문)
  - 새 언어 = LANG 코드 + 코드 다국어 + `messages_{locale}.properties` + 메일 템플릿(`TB_MAIL_TEMPLATE`) 등록 (코드 수정 없음)
- 메시지는 `resources/messages/messages_{locale}.properties` 에 정의한다.
  - Key: `{messageId}.title`, `{messageId}.content`
  - 치환 인자: `{0}`, `{1}` … (기존 `#` 치환 방식 → MessageFormat 방식) **[변경]**
  - 숫자 인자는 `1,000` 처럼 포맷되므로 ID 등은 `String.valueOf()` 로 넘긴다.
- 추후 DB/Redis 기반 메시지 관리가 필요하면 `MessageService` 구현체만 교체한다.

---

## 6. 예외 처리

### 6.1 전략
- `@RestControllerAdvice` (`GlobalExceptionHandler`) 에서 전역으로 처리한다.
- 도메인 전용 처리가 필요하면 별도 Advice 를 만들고 `@Order` 로 우선순위를 높인다.
- **오류 응답도 성공 응답과 동일한 표준 형식**으로 반환한다.
- 의도된 오류(데이터 없음, 중복, 정책 위반)는 `BusinessException` 을 던진다.

```java
throw new BusinessException(SampleErrorCode.SAMPLE_NOT_FOUND, String.valueOf(id));
```

- **[변경]** 비즈니스 예외의 HTTP Status 를 200 으로 고정하지 않고 **의미에 맞는 4xx** 를 사용한다.
  (404 데이터 없음, 409 중복/충돌, 400 정책 위반 등) — 모니터링/게이트웨이/클라이언트가 상태코드로 판단할 수 있도록.
  상태코드는 `ErrorCode` enum 에서 관리하므로 정책 변경 시 enum 만 수정하면 된다.

### 6.2 오류 코드 체계 **[보완]**

| 범위 | 의미 | 정의 위치 |
|---|---|---|
| S0000~ | 성공 | `MessageCode` |
| C0001~ | 공통 요청 오류 (검증, 형식, 경로, 인증/인가) | `CommonErrorCode` |
| B0001~B0999 | 공통 비즈니스 오류 | `CommonErrorCode` |
| B1001~B1999 | sample 도메인 | `SampleErrorCode` |
| B2001~B2999 | notice 도메인 (공지/첨부/메일 대상) | `NoticeErrorCode` |
| B3001~B3999 | user 도메인 (사용자 설정) | `UserErrorCode` |
| B4001~ | 다음 도메인부터 1000 단위로 할당 | `{도메인}ErrorCode` |
| E0001~ | 시스템 오류 | `CommonErrorCode` |

| 예외 | Status | messageId |
|---|---|---|
| `BusinessException` | ErrorCode 의 Status | ErrorCode 의 ID |
| `MethodArgumentNotValidException` / `BindException` | 400 | C0002 |
| `HandlerMethodValidationException` (`@PathVariable` 제약) | 400 | C0002 |
| `MethodArgumentTypeMismatchException` | 400 | C0003 |
| `MissingServletRequestParameterException` | 400 | C0004 |
| `HttpMessageNotReadableException` | 400 | C0005 |
| `HttpRequestMethodNotSupportedException` | 405 | C0006 |
| `HttpMediaTypeNotSupportedException` | 415 | C0007 |
| `NoResourceFoundException` | 404 | C0008 |
| `DataIntegrityViolationException` | 409 | B0003 |
| 그 외 `Exception` | 500 | E0001 |

### 6.3 주의사항
- 500 오류 응답에 예외 메시지/스택트레이스를 노출하지 않는다. (로그에만 기록)
- 응답이 이미 commit 된 이후(파일 다운로드, 대용량 스트림, SSE)의 예외는 ControllerAdvice 로 잡을 수 없다.
  이 경우 클라이언트에 예외를 던지지 말고 자체 처리(로깅, 리소스 정리)한다.
- Checked Exception 은 기본적으로 롤백되지 않는다. 롤백이 필요하면 RuntimeException 으로 감싸거나 `rollbackFor` 지정.

---

## 7. 트랜잭션

- Service 클래스에 `@Transactional(readOnly = true)`, 데이터 변경 메서드에만 `@Transactional`.
- 트랜잭션은 **public 메서드**에만 적용된다. 같은 클래스 내부 호출(self-invocation)은 프록시를 거치지 않아 적용되지 않는다.
- 전파 옵션은 기본(`REQUIRED`)을 사용하고, 독립 커밋이 필요한 경우(이력 저장 등)에만 `REQUIRES_NEW`.
- 동시성 제어
  - 충돌 가능성이 낮은 경우: 낙관적 락 (`@Version`)
  - 정합성이 절대적으로 중요한 경우(포인트, 잔액): 비관적 락 (`@Lock(PESSIMISTIC_WRITE)`)
- **[보완]** OSIV 비활성화 (`spring.jpa.open-in-view: false`). 지연 로딩은 트랜잭션(Service) 안에서 끝낸다.

---

## 8. DB / JPA 규칙 **[보완]**

Oracle 이관을 전제로 작성한다.

| 대상 | 규칙 | 예 |
|---|---|---|
| 테이블 | `TB_` + 대문자 snake_case | `TB_SAMPLE` |
| 컬럼 | 대문자 snake_case | `SAMPLE_ID`, `USE_YN` |
| PK 시퀀스 | `SQ_` + 테이블명 | `SQ_SAMPLE` |
| Unique 제약 | `UK_` + 테이블 + 컬럼 | `UK_SAMPLE_NAME` |
| 여부 컬럼 | `_YN`, `VARCHAR2(1)`, `Y`/`N` (`CHAR` 는 공백 패딩 비교 이슈로 사용 안 함) | `USE_YN` |
| 다국어 | 원본 테이블명 + `_LANG`, PK = 원본키 + `LANG_CD` | `TB_MENU_LANG` |
| 유효기간 | `START_DT`, `END_DT` (DATE, 무기한 = `9999-12-31`) | `TB_USER_ROLE` |
| 공통 컬럼 | `CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT` | `BaseEntity` |

- PK 는 `IDENTITY` 대신 **시퀀스** 사용.
- Oracle 예약어(`USER`, `DATE`, `LEVEL`, `COMMENT`, `ORDER` 등)를 테이블/컬럼명으로 쓰지 않는다.
- 문자열 길이는 `@Column(length)` 로 명시한다.
- **스키마의 기준은 DDL 스크립트**다. (`src/main/resources/db/ddl/NN_xxx.sql`, Oracle 문법)
  - local(H2 Oracle 모드)은 기동 시 이 DDL 로 스키마를 만들고 `db/data/NN_xxx.sql` 초기 데이터를 넣는다.
  - dev/prod 는 같은 DDL 을 DBA 가 적용한다. (`ddl-auto: validate`)
  - local 은 `ddl-auto: none` (H2 가 Oracle NUMBER 를 NUMERIC 으로 보고해 validate 오탐 발생).
    Entity-DDL 일치는 Repository 통합 테스트로 검증한다.
  - 테이블 추가 시: DDL 파일 추가 → `application-local.yml` 의 `schema-locations` 에 등록 → Entity 작성 → 테스트.
  - (테이블 변경 이력 관리가 필요해지면 Flyway 도입 검토)
- PK 를 직접 지정하는 Entity(공통코드, 다국어, 매핑)는 `AssignedIdEntity` 를 상속한다.
  (신규 저장 시 불필요한 SELECT 방지)
- 복합키는 `@EmbeddedId` + `XxxId` (`@Embeddable`, `Serializable`, `@EqualsAndHashCode`) 로 매핑한다.
- 시퀀스 값은 트랜잭션 롤백 대상이 아니므로 테스트에서 특정 ID 값을 기대하지 않는다.

---

## 9. 설정 / 프로필 **[보완]**

| 프로필 | DB | 용도 |
|---|---|---|
| `local` (기본) | H2 in-memory | 로컬 개발, 테스트 |
| `dev` | Oracle | 개발 서버 |
| `prod` | Oracle | 운영 (Swagger 비활성) |

- **DB 계정, Client Secret 등 민감정보는 설정 파일/소스/문서에 직접 쓰지 않는다.** 환경변수로 주입한다.
  (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KEYCLOAK_BFF_CLIENT_SECRET`, `SESSION_ENCRYPTION_KEY`, `REDIS_USERNAME/PASSWORD`)
- 시크릿 기본값은 `application-local.yml` 에만 둔다. 공통 `application.yml` 에 기본값을 두면 운영에서 환경변수 누락 시
  local 값으로 "조용히" 기동되기 때문이다.
- prod 는 `app.security.require-https=true` → 기동 시 IdP URL https, 세션 쿠키 Secure, local 기본 시크릿/키 미사용을 검사하고
  위반하면 기동 실패한다. (`SecurityStartupValidator`)
- 세션 쿠키는 기본 `Secure` (local 만 false).
- Actuator 는 관리 포트(`MANAGEMENT_PORT`, 기본 `127.0.0.1:9191`)에서만 제공한다. (health, info, prometheus)
- 위키/노션 등 공유 문서에도 비밀번호·Client Secret·암호화 키를 남기지 않는다.

---

## 10. 로깅 **[보완]**

- `@Slf4j` 사용. `System.out.println`, `e.printStackTrace()` 금지.
- 모든 요청에 `traceId` 가 부여된다 (`X-Trace-Id` 헤더 / 로그 `[traceId]`). Gateway 연동 시 헤더로 전달받아 이어 쓴다.
- 레벨
  - ERROR: 시스템 오류 (스택트레이스 포함)
  - WARN : 비즈니스/요청 오류 (스택트레이스 제외)
  - INFO : 주요 처리 흐름
  - DEBUG: 개발용 상세 (`@Logging` 의 진입/종료/소요시간)
- 개인정보, 비밀번호, 토큰은 로그에 남기지 않는다.
- 인증 감사 로그는 logger `AUDIT` (`AuthAuditLogger`) — 로그인 성공/실패, 로그아웃, 401/403 을 사용자·IP·URI 와 함께 기록한다.
  local 외 프로필은 별도 파일 `*-audit.log` (365일 보관).
- 문자열 연결 대신 placeholder 사용: `log.info("id={}", id)`

---

## 11. 외부 API 연동

| 구분 | 권장 |
|---|---|
| 동기 호출 (기본) | **[변경]** `RestClient` + HTTP Interface (`@HttpExchange`) — Spring 6.1+ 표준, 선언형 |
| 비동기/대량 | `WebClient` (WebFlux 의존) |
| MSA + Spring Cloud | OpenFeign (현재 기능 추가 없는 유지보수 모드) |
| 사용 금지 | `RestTemplate` (유지보수 모드, 신규 코드 사용 금지) |

공통 설정 체크리스트: 타임아웃(connect/read), 재시도(429/503 대상), 인증 헤더(인터셉터), 4xx/5xx 오류 처리, Base URL 외부 설정화, TLS.

---

## 12. 인증/인가 (Keycloak) — 상세: [design/02_auth_keycloak.md](design/02_auth_keycloak.md)

- Keycloak 을 인증 서버로 사용하며 **인증 방식별**로 지원한다.
  - 브라우저: **OAuth2 BFF** (`flowlog-bff`, confidential + PKCE) / SAML2 → **세션(Redis)**. 클라이언트는 `SESSION` 쿠키만 가진다.
    **토큰은 서버 세션에만 저장하고 응답·쿠키·로그에 노출하지 않는다.** (변경 요청은 CSRF: `XSRF-TOKEN` → `X-XSRF-TOKEN`)
  - 서버 간: Client Credentials (`flowlog-batch`) → Bearer JWT, stateless
- 세션: Spring Session + Redis (`flowlog:session:*`), 인스턴스 간 공유, 30분, flush-mode immediate
  - 값은 **AES-256-GCM 암호화 + 허용목록 JDK 직렬화** (`SessionStoreConfig`, 키 `SESSION_ENCRYPTION_KEY`, 인스턴스 간 동일).
    Redis 를 읽어도 토큰이 보이지 않고, 위·변조 값은 복호화 단계에서 거부된다. 키 변경 = 전체 재로그인.
- 사용자 식별은 **`sub`(Keycloak user id = `TB_USER.IDP_SUBJECT`)**. `preferred_username`/NameID 는 표시용이며 권한 매핑에 쓰지 않는다.
- 인가는 **scope 기준** — `SCOPE_{리소스}.{list|add|edit|del}`, **최종 scope = IdP scope ∩ DB 업무 권한**
  - IdP scope: BFF 세션 토큰 scope(요청마다, 만료 임박 시 refresh) / SAML `Role` 속성 / Bearer JWT scope. Keycloak 역할로 제한
  - DB 권한: 유효 권한(직접+부서, 기간 내)의 `TB_ROLE_MENU` LIST/ADD/EDIT/DEL × 메뉴 `PERM_CD` (`UserAuthorityService`, 캐시 60초)
    → 커스텀 권한(예: 조회 전용 매니저)이 API 에 그대로 반영된다. DB 미등록 사용자는 API 권한 없음
  - DB 유효 권한 → `ROLE_{권한코드}` (예: `ROLE_SYS_ADMIN` 은 전체 허용)
  - 서비스 계정(Client Credentials)은 `app.security.service-account-clients` 의 azp 이고 사용자명이 `service-account-{azp}` 일 때만 → 토큰 scope 만
  - 세션 로그인(BFF/SAML)은 로그인 시점 권한을 쓰지 않고 **요청마다 재계산**한다. SAML 은 `max-auth-age`(1h) 후 재로그인
- **기본 거부**: `SecurityConfig` 규칙이 없는 경로는 `denyAll`. 새 API = SecurityConfig 규칙 + 메뉴 `PERM_CD`/`TB_ROLE_MENU` + Keycloak 역할/scope
- Gateway 의 `X-Forwarded-*` 는 `app.security.trusted-proxies` 에서 온 요청만 반영 (`TrustedForwardedHeaderFilter`)
- URL 규칙은 `SecurityConfig`, 메서드 단위는 `@PreAuthorize("hasAuthority('ROLE_XXX')")`
- 인증 실패 401(C0009), 권한 없음 403(C0010) 을 표준 응답으로 반환한다. (Security Filter 단계도 다국어 적용)
- 생성/수정자 Auditing 은 로그인 사용자ID 로 기록된다. (인증 없으면 `system`)
- 신규 API 테스트는 `springSecurity()` + `jwt()` 로 인증을 붙인다.

---

## 13. 테스트

- 패턴: **given - when - then**
- 메서드명: `대상메서드_상황_기대결과` + `@DisplayName("한글 설명")`
- 범위
  - 단위 테스트: Service 비즈니스 로직 (Mockito) — `SampleServiceTest`
  - 통합 테스트: API 시나리오 (`@SpringBootTest` + MockMvc + H2) — `SampleApiIntegrationTest`
  - 인증/인가 테스트: `SecurityIntegrationTest` (JwtDecoder 만 Mock), 실제 Keycloak 연동은 `infra/keycloak/e2e/auth_e2e.py`
  - Oracle 검증: `OracleSchemaTest` (`@Tag("oracle")`, `./mvnw test -Poracle-it`) — 실제 Oracle Free 컨테이너에서 DDL·초기데이터·`ddl-auto=validate`.
    DDL/초기데이터/Entity 를 바꾸면 반드시 실행한다. (H2 Oracle 모드가 잡지 못하는 문법 차이 확인용)
- CI: `.github/workflows/ci.yml` (build + oracle 잡)
- 기능 개발 시 정상 케이스 + 주요 오류 케이스 테스트를 함께 작성한다. (Test First + Test Last 하이브리드)
- 통합 테스트는 `@Transactional` 로 테스트 간 데이터 격리.
- 외부 시스템 연동은 Mock, 실제 상호작용이 핵심인 부분(DB, 트랜잭션)은 Mock 하지 않는다.
- 빌드 전 `mvnw test` 통과 필수.

---

## 14. Git **[보완]**

- 커밋 메시지: `type: 제목` (Conventional Commits)
  - `feat` 기능, `fix` 버그, `refactor` 리팩토링, `docs` 문서, `test` 테스트, `chore` 빌드/설정
  - 예) `feat: 샘플 목록 조회 API 추가`
- 브랜치: `main` (운영) / `develop` (개발) / `feature/{기능}` / `fix/{내용}`
