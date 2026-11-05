# 테스트 결과 리포트

- 실행일시: 2026-11-05 (KST) — 보안 점검 18건 조치 후
- 실행 명령: `mvnw clean test` (기본) / `mvnw test -Poracle-it` (실제 Oracle)
- 환경: Java 17.0.12 / Spring Boot 4.1.2 / Spring Security 7.1.2 / Spring Session / H2 (Oracle 호환 모드) · Oracle Free 23ai (Testcontainers)

## 요약

| 구분 | 전체 | 성공 | 실패 | 결과 |
|---|---|---|---|---|
| 기본 (`mvnw test`) | 81 | 81 | 0 | ✅ BUILD SUCCESS |
| Oracle (`-Poracle-it`) | 1 | 1 | 0 | ✅ BUILD SUCCESS |

| 테스트 클래스 | 유형 | 대상 | 건수 | 결과 |
|---|---|---|---|---|
| `SpringBoilerplateApplicationTests` | 기동 | 애플리케이션 Context | 1 | ✅ |
| `SampleServiceTest` | 단위 (Mockito) | SampleService 비즈니스 로직 | 3 | ✅ |
| `SampleApiIntegrationTest` | 통합 (MockMvc + H2) | 샘플 API, 표준 응답, 예외 처리, 다국어 | 15 | ✅ |
| `AuthMenuCodeRepositoryTest` | 통합 (JPA + H2) | 공통코드 / 사용자·부서 / 권한 / 메뉴 Entity·Repository | 21 | ✅ |
| `SecurityIntegrationTest` | 통합 (Spring Security + MockMvc + H2) | Bearer / BFF / SAML2, sub 매핑, scope ∩ DB 권한, 기본 거부, 메뉴 API, Forwarded 헤더 | 31 | ✅ |
| `SessionSerializationTest` | 단위 | 세션 AES-GCM 암호화 + 허용목록 직렬화 (복원·위변조·다른 키·차단 클래스) | 7 | ✅ |
| `SecurityStartupValidatorTest` | 단위 | prod 기동 보안 검사 | 3 | ✅ |
| `OracleSchemaTest` | 통합 (Oracle 컨테이너, `@Tag("oracle")`) | DDL·초기데이터·`validate`·권한/메뉴 쿼리 | 1 | ✅ |

## SpringBoilerplateApplicationTests

애플리케이션 Context — 기동

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `contextLoads` | 애플리케이션 Context 정상 기동 (DDL 스크립트 스키마 생성 + 초기 데이터 적재 포함) | 0.002 |

## SampleServiceTest

SampleService 비즈니스 로직 — 단위 (Mockito)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `createSample_duplicateName_throwsBusinessException` | 등록 - 샘플명이 중복이면 B1002 예외, 저장하지 않음 | 0.131 |
| ✅ PASS | `createSample_success` | 등록 - 정상 요청이면 저장 후 응답 DTO 반환 | 0.001 |
| ✅ PASS | `getSample_notFound_throwsBusinessException` | 단건 조회 - 존재하지 않는 ID 면 B1001 예외 | 0.001 |

## SampleApiIntegrationTest

샘플 API, 표준 응답, 예외 처리, 다국어 — 통합 (MockMvc + H2)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `createSample` | 등록 - 201, S0003, Auditing 생성자 = 로그인 사용자(admin) | 0.011 |
| ✅ PASS | `createSample_duplicate` | 오류 - 샘플명 중복: 409, B1002 | 0.008 |
| ✅ PASS | `createSample_invalidInput` | 오류 - 입력값 검증 실패: 400, C0002, result 에 필드 오류 목록 | 0.047 |
| ✅ PASS | `createSample_unreadableBody` | 오류 - JSON 형식 오류: 400, C0005 | 0.009 |
| ✅ PASS | `deleteSample` | 삭제 - 200, S0005, result null | 0.008 |
| ✅ PASS | `getSample` | 단건 조회 - 200, S0001 | 0.007 |
| ✅ PASS | `getSample_invalidPathVariable` | 오류 - PathVariable 제약조건 위반: 400, C0002 | 0.010 |
| ✅ PASS | `getSample_notFound` | 오류 - 존재하지 않는 샘플: 404, B1001, 메시지 인자 치환 | 0.007 |
| ✅ PASS | `getSample_typeMismatch` | 오류 - PathVariable 타입 불일치: 400, C0003 | 0.011 |
| ✅ PASS | `getSamples` | 목록 조회 - 200, S0002, 표준 응답 필드, 페이징 결과 | 0.024 |
| ✅ PASS | `locale_enUs` | 다국어 - Accept-Language: en-US 이면 영문 메시지, locale en_US | 0.008 |
| ✅ PASS | `locale_unsupported_fallbackToKorean` | 다국어 - 미지원 언어면 기본값 ko_KR | 0.009 |
| ✅ PASS | `methodNotAllowed` | 오류 - 지원하지 않는 Method: 405, C0006 | 0.006 |
| ✅ PASS | `pathNotFound` | 오류 - 존재하지 않는 경로(인가 규칙 범위 내): 404, C0008 | 0.012 |
| ✅ PASS | `updateSample` | 수정 - 200, S0004 | 0.037 |

## AuthMenuCodeRepositoryTest

공통코드 / 사용자·부서 / 권한 / 메뉴 Entity·Repository — 통합 (JPA + H2)

### 공통코드

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `codeWithoutGroup_violatesForeignKey` | FK 제약 - 존재하지 않는 그룹의 코드는 저장 불가 | 0.005 |
| ✅ PASS | `findCodeLang` | 코드명 다국어 - USER_TYPE 의 en_US 이름 | 0.032 |
| ✅ PASS | `findCodes` | 초기 코드그룹 5개와 MODULE 코드(TX, WS)를 정렬순서대로 조회 | 0.015 |
| ✅ PASS | `saveNewNaturalKeyEntity` | 자연키 Entity 신규 저장 - SELECT 없이 INSERT, Auditing 값 세팅 | 0.029 |

### 권한

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `effectiveRoles_afterPeriodEnds` | 유효 권한 - 기준일이 바뀌면 기간 만료된 권한이 빠짐 (2027-01-01) | 0.006 |
| ✅ PASS | `effectiveRoles_excludesExpiredAndFuture` | 유효 권한 - 만료(2025년)/시작 전(2099년) 권한은 제외 | 0.008 |
| ✅ PASS | `effectiveRoles_userAndDept` | 유효 권한 - 직접 부여 + 부서 부여 합산 (tx.viewer: TF 부서의 조회매니저 + 임직원) | 0.007 |
| ✅ PASS | `grantRole_defaultEndDate` | 권한 부여 저장 - 종료일 미지정 시 무기한(9999-12-31), 시퀀스는 고정 ID(1~16) 이후 101부터 | 0.012 |
| ✅ PASS | `grantRole_invalidPeriod` | 권한 부여 - 시작일이 종료일보다 늦으면 생성 불가 | 0.003 |
| ✅ PASS | `roleLang` | 권한명 다국어 | 0.011 |
| ✅ PASS | `roleLevels` | 레벨 모델 - 시스템관리자 1, 모듈마스터/매니저는 모듈별, 일반사용자 5종 | 0.009 |

### 메뉴 / 메뉴권한

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `buildMenuTree` | 메뉴 트리 - 전체 조회 후 메모리 구성, 4뎁스 (TX > 거래관리 > 거래목록 > 거래상세) | 0.021 |
| ✅ PASS | `contentMenu` | 본문(CONTENT) 메뉴 - 언어별 CLOB 본문 조회 | 0.005 |
| ✅ PASS | `menuAttributes` | 메뉴 속성 - 관리자메뉴, 사이드메뉴, 미노출, 조회유형 | 0.019 |
| ✅ PASS | `mergeRoleMenus` | 메뉴권한 합산 - 사용자 유효 권한들의 메뉴권한을 OR 로 합산 | 0.021 |
| ✅ PASS | `roleMenuAuthority` | 메뉴권한 - 조회매니저는 TX 메뉴 조회만, 매니저는 삭제 제외 | 0.014 |
| ✅ PASS | `saveMenu` | 신규 메뉴 저장 - 시퀀스 101부터, 하위메뉴 존재 여부 | 0.019 |

### 사용자 / 부서

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `deptHierarchy` | 부서 계층 - HQ 하위 3개 부서 | 0.008 |
| ✅ PASS | `userHasMultipleDepts` | 사용자는 N개 부서에 소속 - tx.viewer: WS_TEAM(주), TF_TEAM | 0.008 |

### 기타

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `compositeKeyEquality` | CodeId equals/hashCode - 복합키 비교 | 0.014 |
| ✅ PASS | `grantToUnknownUser_violatesForeignKey` | FK 제약 - 존재하지 않는 사용자에게 권한 부여 불가 | 0.040 |

## SecurityIntegrationTest

인증/인가 - Bearer / BFF 세션 / SAML2, sub 매핑, scope ∩ DB 권한, 기본 거부, 메뉴 API, Forwarded 헤더 — 통합 (Spring Security + MockMvc + H2)

### 미인증 / 공개 경로 / 기본 거부

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `unauthenticated` | 인증 없이 API 호출 - 401, C0009 표준 응답, WWW-Authenticate 헤더 | 0.01 |
| ✅ PASS | `unauthenticated_english` | 401 응답도 다국어 적용 - Accept-Language: en-US | 0.008 |
| ✅ PASS | `invalidToken` | 유효하지 않은 토큰 - 401, C0009 | 0.013 |
| ✅ PASS | `publicPath` | 공개 경로 - API 문서는 인증 없이 접근 | 0.694 |
| ✅ PASS | `unmappedPath_deniedByDefault` | 인가 규칙이 없는 경로는 시스템관리자도 403 (기본 거부) | 0.007 |

### OAuth2 (Bearer JWT)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `userToken_scopeIntersectsDbPermission` | 사용자 토큰 - sub 로 DB 권한 매핑, API 권한 = 토큰 scope ∩ DB 권한 (조회 전용 권한이면 add 제외) | 0.014 |
| ✅ PASS | `usernameSpoofing_noAdmin` | 사용자명 위조 - preferred_username=admin 이어도 sub 가 미등록이면 관리자 권한 없음 | 0.01 |
| ✅ PASS | `unregisteredUser` | TB_USER 미등록 사용자 - 인증은 되지만 IdP scope 가 있어도 API 권한 없음 | 0.014 |
| ✅ PASS | `clientCredentials_scopeAuthorized` | Client Credentials 토큰 - 허용된 client(azp)의 서비스 계정, scope 로만 인가 (삭제 허용) | 0.038 |
| ✅ PASS | `fakeServiceAccount_denied` | 서비스 계정 위조 - 사용자명만 service-account-* 이고 azp 가 다르면 사용자로 취급 (권한 없음) | 0.017 |
| ✅ PASS | `forbidden` | 인가 실패 - sample.del scope 도 SYS_ADMIN 권한도 없으면 삭제 403, C0010 | 0.011 |
| ✅ PASS | `sysAdmin_deleteAllowed` | 시스템관리자 사용자 토큰 - DB 권한(ROLE_SYS_ADMIN)으로 삭제 허용 | 0.011 |
| ✅ PASS | `bearer_noCsrf_auditing` | Bearer 요청은 CSRF 검사 제외 + sample.add(scope∩DB) 로 등록 + 생성자 = 토큰 사용자 | 0.012 |

### OAuth2 BFF (세션 - 토큰은 서버 세션에만)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `sessionLogin_me` | 세션 로그인 - authType OAUTH2_SESSION, 권한 = 세션 토큰 scope ∩ DB + DB ROLE, 응답에 토큰 없음 | 0.04 |
| ✅ PASS | `authorizesByTokenScope` | 인가는 로그인 시점 권한이 아닌 세션 토큰의 scope 기준 - 토큰에 sample.add 없으면 등록 403 | 0.065 |
| ✅ PASS | `customReadOnlyRole_limitsScope` | 커스텀 권한(조회 전용) 반영 - IdP 가 sample.add 를 줘도 DB 권한이 조회뿐이면 등록 403 | 0.008 |
| ✅ PASS | `sessionWithAddScope_create` | 세션 토큰 scope sample.add + DB 권한 add 가 있으면 등록 201, 생성자 = 세션 사용자 | 0.501 |
| ✅ PASS | `expiredToken_invalidatesSession` | 세션 토큰 만료 + refresh token 없음 - 세션 폐기 후 401 C0009 | 0.887 |
| ✅ PASS | `noTokenInSession` | 세션에 토큰이 없으면 (로그인 상태 위조/유실) 401 C0009 | 0.015 |

### SAML2 (세션)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `samlSession_me` | SAML 로그인 세션 - authType SAML2, sub 로 사용자 매핑, Role ∩ DB 권한 | 0.013 |
| ✅ PASS | `samlSession_csrf` | 세션 요청의 변경 API 는 CSRF 토큰 필수 - 없으면 403, 있으면 201 | 0.014 |
| ✅ PASS | `storedAuthoritiesIgnored` | 세션에 저장된 로그인 시점 권한은 무시 - 요청마다 DB 권한으로 재계산 (ROLE_SYS_ADMIN 위조 불가) | 0.017 |
| ✅ PASS | `nameIdSpoofing_noAdmin` | NameID 위조 - NameID=admin 이어도 sub 가 emp01 이면 관리자 권한 없음 | 0.011 |
| ✅ PASS | `samlAuthExpired` | SAML 로그인 유효시간(max-auth-age) 경과 - 세션 폐기 후 401 (재로그인 유도) | 0.01 |
| ✅ PASS | `assertionToAuthorities` | SAML Assertion sub/Role → 권한 (tx.viewer: 부서 부여 조회 전용 권한이라 add 제외) | 0.278 |
| ✅ PASS | `assertionWithoutSubject` | SAML Assertion 에 sub 가 없으면 권한 없음 | 0.007 |

### 메뉴 API (GET /api/v1/menus/me)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `viewerMenus` | tx.viewer - 유효 권한(부서 부여)의 메뉴만, 시스템관리 메뉴 없음, 거래목록은 조회만 | 0.024 |
| ✅ PASS | `adminMenus_english` | 시스템관리자 - 전체 메뉴/전체 권한, Accept-Language en-US 메뉴명 | 0.105 |
| ✅ PASS | `noMenus` | 서비스 계정 / 미등록 사용자 - 빈 메뉴 | 0.013 |

### Forwarded 헤더 (Gateway 뒤 동작)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `trustedProxy_applied` | 신뢰 프록시(Gateway)의 X-Forwarded-* 는 반영 - redirect_uri 가 외부 URL(/cm) 기준 | 0.018 |
| ✅ PASS | `untrustedClient_ignored` | 신뢰하지 않는 호출자의 X-Forwarded-Host 위조는 무시 - redirect_uri 가 조작되지 않음 | 0.007 |

## SessionSerializationTest

Redis 세션 저장 형식 - AES-256-GCM 암호화 + 허용목록 JDK 직렬화 — 단위

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `oauth2SessionRoundTrip` | OAuth2 BFF - SecurityContext + 토큰(Access/Refresh) 직렬화 후 복원 | 0.02 |
| ✅ PASS | `samlSessionRoundTrip` | SAML2 - 실제 로그인 결과 타입(Saml2AssertionAuthentication) 직렬화 후 복원 | 0.015 |
| ✅ PASS | `storedValueIsEncrypted` | Redis 에 저장되는 값은 암호문 - 토큰 원문이 보이지 않는다 | 0.001 |
| ✅ PASS | `tamperedValueRejected` | 위·변조된 값은 복호화(무결성 검증) 단계에서 거부 - 역직렬화하지 않음 | 0.002 |
| ✅ PASS | `otherKeyRejected` | 다른 키로 암호화된 값은 거부 (키가 다른 인스턴스/공격자가 넣은 값) | 0.006 |
| ✅ PASS | `disallowedClassRejected` | 허용목록 밖의 클래스는 역직렬화 차단 (예: java.io.File) | 0.002 |
| ✅ PASS | `invalidKey` | 암호화 키가 없거나 32바이트가 아니면 기동 실패 | 0.004 |

## SecurityStartupValidatorTest

운영 보안 설정 기동 검사 (prod require-https) — 단위

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `notRequired` | require-https=false (local/dev) 이면 검사하지 않는다 | 0.0 |
| ✅ PASS | `insecureProdSettings` | prod - http IdP URL, Secure 아닌 쿠키, local 기본 시크릿/키는 기동 실패 | 0.001 |
| ✅ PASS | `secureProdSettings` | prod - https + Secure 쿠키 + 운영 시크릿이면 정상 기동 | 0.0 |

## OracleSchemaTest (`-Poracle-it`)

실제 Oracle Free 23ai 컨테이너 — DDL·초기 데이터 실행, Entity 검증(`ddl-auto=validate`), 권한/메뉴 쿼리

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `schemaAndQueries` | Oracle - DDL/초기 데이터 실행, Entity 검증(validate) 통과, 권한·메뉴 조회 쿼리 동작 | 0.932 |

## 참고

- 실행 로그의 `[ERROR] ... BusinessException` DEBUG 로그는 실패가 아니라, 오류 케이스 테스트에서 `@Logging` 이 의도된 예외를 기록한 것이다.
- 권한 유효기간 테스트는 결과가 실행일에 따라 바뀌지 않도록 기준일을 `2026-11-05` 로 고정했다.
- 시퀀스는 트랜잭션 롤백 대상이 아니므로 신규 ID 는 특정 값이 아닌 범위(> 100)로 검증한다.
- `SecurityIntegrationTest` 는 JwtDecoder 만 Mock 하고 나머지(필터, 변환기, DB 권한 조회, Forwarded 헤더 필터)는 실제로 동작한다.
  테스트 사용자의 sub 는 realm import 와 같은 고정 ID 이다. 실제 Keycloak 왕복은 [AUTH_E2E_REPORT.md](AUTH_E2E_REPORT.md).
- 기본 거부 적용으로 인가 규칙 밖의 경로는 404 가 아니라 403 이다. `pathNotFound` 는 규칙 범위 안의 없는 경로(`/api/v1/samples/1/unknown`)로 검증한다.
- `OracleSchemaTest` 로 H2 에서는 통과하던 `ALTER SEQUENCE ... RESTART WITH` 가 Oracle 에서 실패(ORA-03048)함을 발견 → `RESTART START WITH` 로 수정.
- 상세 리포트: `target/surefire-reports/`
