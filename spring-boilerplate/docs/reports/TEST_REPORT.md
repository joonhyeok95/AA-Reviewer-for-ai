# 테스트 결과 리포트

- 실행일시: 2026-11-06 (KST) — 다중 인스턴스 보완(캐시 무효화 전파·첨부 저장소·메일 발송 복구)·권한 부여 이력·scope 설정 검사 추가 후
- 실행 명령: `mvnw clean test` (기본) / `mvnw test -Poracle-it` (실제 Oracle)
- 환경: Java 17.0.12 / Spring Boot 4.1.2 / Spring Security 7.1.2 / Spring Session / H2 (Oracle 호환 모드) · Oracle Free 23ai (Testcontainers)

## 요약

| 구분 | 전체 | 성공 | 실패 | 결과 |
|---|---|---|---|---|
| 기본 (`mvnw test`) | 175 | 175 | 0 | ✅ BUILD SUCCESS |
| Oracle (`-Poracle-it`) | 3 | 3 | 0 | ✅ BUILD SUCCESS |

| 테스트 클래스 | 유형 | 대상 | 건수 | 결과 |
|---|---|---|---|---|
| `SpringBoilerplateApplicationTests` | 기동 | 애플리케이션 Context | 1 | ✅ |
| `SampleServiceTest` | 단위 (Mockito) | SampleService 비즈니스 로직 | 3 | ✅ |
| `SampleApiIntegrationTest` | 통합 (MockMvc + H2) | 샘플 API, 표준 응답, 예외 처리, 다국어, 이름 검색(와일드카드 이스케이프) | 16 | ✅ |
| `AuthMenuCodeRepositoryTest` | 통합 (JPA + H2) | 공통코드 / 사용자·부서 / 권한 / 메뉴 Entity·Repository | 21 | ✅ |
| `SecurityIntegrationTest` | 통합 (Spring Security + MockMvc + H2) | Bearer / BFF / SAML2, sub 매핑, scope ∩ DB 권한, 기본 거부, 메뉴 API(사용자 언어 우선), Forwarded 헤더, 화면 연동(returnTo 검증, /auth/me 표시 언어) | 35 | ✅ |
| `NoticeApiIntegrationTest` | 통합 (Spring Security + MockMvc + H2) | 공지 다국어 NT-60~66, 지원 언어(LANG), 사용자 언어, 조직도, 첨부, 권한 | 23 | ✅ |
| `NoticeMailServiceTest` | 통합 (H2, MailGateway Mock) | 메일 NT-47~4A, 발송 시점 인사 마스터, 중복 발송 방지, 실패 이력, 발송 중단 복구(성공 수신자 제외) | 7 | ✅ |
| `NoticeFileStorageTest` | 통합 (H2) | 첨부 DB 저장소(`storage-type=db`) 업로드·다운로드·삭제 | 1 | ✅ |
| `BroadcastingCacheManagerTest` | 단위 | 캐시 무효화 전파 (커밋 후 전파, 다른 인스턴스 수신, 자기 메시지 무시, Redis 장애 무시) | 4 | ✅ |
| `ScopeConsistencyTest` | 통합 (설정 파일 + H2) | scope 설정 누락 검사 (Keycloak 역할 = setup.sh = BFF scope = 인가 규칙표, 메뉴 PERM_CD, 예외 규칙 순서) | 4 | ✅ |
| `BoardScopeIntegrationTest` | 통합 (Spring Security + MockMvc + H2) | 게시판(공지사항) scope 동의어(write/update/delete), IdP scope ∩ DB 권한, 공지사항 메뉴 권한 | 6 | ✅ |
| `AdminApiIntegrationTest` | 통합 (Spring Security + MockMvc + H2) | 공통코드·메뉴·권한 관리 API, 즉시 반영(캐시), 권한 상승 방지, 본문 메뉴, 부여 변경 이력 | 20 | ✅ |
| `BrowserCookieWebTest` | 통합 (실제 포트 HTTP) | XSRF-TOKEN 첫 요청 발급·경로 `/`·재발급 없음, 미인증 401 은 세션 미생성 | 2 | ✅ |
| `SessionSerializationTest` | 단위 | 세션 AES-GCM 암호화 + 허용목록 직렬화 (복원·위변조·다른 키·차단 클래스) | 7 | ✅ |
| `SecurityStartupValidatorTest` | 단위 | prod 기동 보안 검사 | 3 | ✅ |
| `CalendarOccurrencesTest` | 단위 | 생일(캘린더) 매년 반복 펼치기 - 나이·주년, 2월 29일, 해 넘김, 시작 전 미발생, 여러 해 | 6 | ✅ |
| `CalendarApiIntegrationTest` | 통합 (Spring Security + MockMvc + H2) | 캘린더 API 기간 조회·등록·수정·삭제, 검증(B7002~B7005), 연도 모름(나이 미표시), calendar.* 인가 | 7 | ✅ |
| `ChurchApiIntegrationTest` | 통합 (Spring Security + MockMvc + H2) | 주보(이번 주·등록·수정·삭제·검증·작성 중 노출), 교리문답(트리·단위 문답 교체·검증), 전례문(줄 교체·사용 중 삭제 불가·인가) | 9 | ✅ |
| `OracleSchemaTest` | 통합 (Oracle 컨테이너, `@Tag("oracle")`) | DDL·초기데이터·`validate`·권한/메뉴 쿼리·공지 MyBatis 검색·샘플 LIKE ESCAPE·관리 조회(그룹별 건수, 사용자 검색) | 3 | ✅ |

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
| ✅ PASS | `findCodeLang` | 코드명 다국어 - USER_TYPE 의 en 이름 | 0.032 |
| ✅ PASS | `findCodes` | 초기 코드그룹 10개(공지 포함, WORKPLACE 그룹 없음)와 MODULE 코드(TX, WS)를 정렬순서대로 조회 | 0.015 |
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
| ✅ PASS | `adminMenus_userLangFirst` | 시스템관리자 - 전체 메뉴/전체 권한. 메뉴명은 사용자 설정 언어(ko)가 Accept-Language 보다 우선 | 0.041 |
| ✅ PASS | `managerMenus_english` | 사용자 설정 언어 en (tx.manager) - 영문 메뉴명 | 0.100 |
| ✅ PASS | `noMenus` | 서비스 계정 / 미등록 사용자 - 빈 메뉴 | 0.013 |

### Forwarded 헤더 (Gateway 뒤 동작)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `trustedProxy_applied` | 신뢰 프록시(Gateway)의 X-Forwarded-* 는 반영 - redirect_uri 가 외부 URL(/cm) 기준 | 0.018 |
| ✅ PASS | `untrustedClient_ignored` | 신뢰하지 않는 호출자의 X-Forwarded-Host 위조는 무시 - redirect_uri 가 조작되지 않음 | 0.007 |

## AdminApiIntegrationTest (관리 API, 2026-11-06)

| 영역 | 테스트 | 검증 내용 |
|---|---|---|
| 공통코드 | `groups` | 그룹 목록 - 미사용 포함, 언어별 그룹명, 시스템 여부, 코드 수 |
| | `createGroupAndCode_reflectedImmediately` | 그룹·코드 등록 → 사용자 코드 API 즉시 반영(캐시 삭제), 빈 영문명은 저장 안 함 |
| | `validation` | 기본 언어 이름 필수 B4002, LANG 에 없는 언어 B4001, 그룹코드 중복 B4004, 형식 C0002 |
| | `systemGroupProtected` | 시스템 그룹 삭제 B4007, 시스템 그룹의 코드 삭제 B4008 |
| | `lastLangRequired` | 마지막 사용 중 언어 미사용 불가 B4009, 미사용 언어는 지원 언어에서 빠지고 그 언어 이름은 보존 |
| | `deleteGroup` / `forbidden` | 일반 그룹 삭제(코드 포함) / DB 권한 없으면 scope 가 있어도 403 |
| 메뉴 | `treeAndDetail` | 미노출 메뉴 포함 트리, 상세의 언어별 이름·본문 |
| | `createContentMenu_sanitized` | CONTENT 본문 script 제거 후 저장 |
| | `urlValidation` | 유형별 URL (javascript:, 상대 LINK, //host, CONTENT+URL) B5004, 없는 유형 B5005 |
| | `treeIntegrity` / `reorder` | 순환 B5003, 하위 메뉴 있으면 삭제 B5002 / 같은 상위만 순서 변경 B5006 |
| | `permCdChange_evictsAuthorityCache` | 권한코드 변경 → 즉시 API 권한 상실 (권한 캐시 삭제) |
| | `content` | 본문 메뉴 - 내 메뉴면 조회, 아니면 B5007 |
| 권한 | `roles` / `roleCrud` | 목록·건수 / 등록·수정(키 불변)·중복 B6002·기본 권한 B6003·부여 있으면 삭제 B6005 |
| | `roleMenus_appliedImmediately` | 메뉴 접근 저장 → 상위 메뉴 조회 자동, 즉시 메뉴·API 권한 반영 |
| | `grants` | 사용자 부여·기간 중복 B6010·대상 없음 B6007·기간 오류 B6009·기간 변경(시작 전)·회수 |
| | `delegatedAdmin_cannotTouchSysAdmin` | role.* 를 위임받아도 시스템관리자 권한 변경·부여 B6004 |
| | `searchUsers` | ID·이름 부분 일치, 주부서명, `%` 는 문자 그대로, role.list 없으면 403 |

실연동 : 인증 E2E 48/48, 화면 E2E 50/50 (공통코드·메뉴·권한 관리 화면, 부여 → 다른 사용자 메뉴 반영 → 회수)

## 화면 연동 보완 (2026-11-06)

`SecurityIntegrationTest` (화면(SPA) 연동 - 로그인 복귀 경로, 표시 언어, CSRF 쿠키)

| 결과 | 테스트 | 검증 내용 |
|---|---|---|
| ✅ PASS | `loginStart_safeReturnUrlSaved` | 로그인 시작 returnTo - 같은 호스트 절대 경로만 세션에 저장 |
| ✅ PASS | `loginStart_unsafeReturnUrlIgnored` | `//evil`, `https://evil`, 역슬래시, 상대 경로, 공백 → 무시 (Open Redirect 방지) |
| ✅ PASS | `me_langCd` | /auth/me 표시 언어 - 설정 없으면 Accept-Language, 설정 있으면 사용자 설정 |

`BrowserCookieWebTest` (실제 포트 - MockMvc `springSecurity()` 는 CSRF 저장소를 테스트용으로 바꾸므로)

| 결과 | 테스트 | 검증 내용 |
|---|---|---|
| ✅ PASS | `csrfCookie_issuedOnFirstRequest` | XSRF-TOKEN 첫 요청부터 발급, Path=/, HttpOnly 아님, 쿠키가 있으면 재발급 없음 |
| ✅ PASS | `anonymousRequest_noSession` | 미인증 API 요청(401)은 SESSION 을 만들지 않음 (요청 캐시 미사용) |

`SampleApiIntegrationTest.getSamples_nameSearch` : 이름 부분 일치, `%`·`_` 는 문자 그대로 (수정 전 local 에서는 이름 검색이 항상 0건 - Hibernate H2 Dialect 의 `escape ''` + H2 Oracle 모드)

실연동 E2E (2026-11-06) : 인증 `auth_e2e.py --quick` **48/48** (위조 SESSION Rate Limit 시나리오를 동시 150건으로 변경), 화면 `vue-frontend npm run e2e` **35/35**

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

## NoticeApiIntegrationTest

공지사항 다국어 API — 통합 (Spring Security + MockMvc + H2). 사용자 언어: admin=ko, tx.manager=en, tx.viewer=미설정

### 지원 언어 (LANG 공통코드 기반 - 하드코딩 없음)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `langs` | 언어 목록 = LANG 코드(USE_YN=Y) : ko/en, 에디터 언어팩 = ATTR1, 언어명은 사용자 언어 | 0.017 |
| ✅ PASS | `addLanguageWithoutCodeChange` | LANG 코드에 ja 를 추가하면 코드 수정 없이 언어 목록·공지 입력에 바로 반영 | 0.062 |
| ✅ PASS | `changeUserLang` | 사용자 언어 변경 - 이후 조회 언어에 반영, 지원하지 않는 언어는 B3002 | 0.054 |

### 등록 규칙 (NT-60 ~ NT-62)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `englishOnly_baseLangIsFirst` | 영어만 입력 - 대표 언어 미지정 시 처음 입력한 언어(en) | 0.038 |
| ✅ PASS | `bothLanguages_explicitBase` | 둘 다 입력 + 대표 언어 지정(en) | 0.038 |
| ✅ PASS | `contentRequired` | NT-61 - 어떤 언어도 제목·본문이 없으면 B2002, 제목만 있으면 B2003 | 0.031 |
| ✅ PASS | `invalidLanguage` | 지원하지 않는 언어 B2004, 입력하지 않은 언어를 대표 언어로 지정 B2006 | 0.038 |
| ✅ PASS | `sanitizeHtml` | 본문 HTML 정화 - script/이벤트 핸들러/javascript: 링크 제거, 서식은 유지 | 0.089 |
| ✅ PASS | `mailTargets` | 메일 대상 지정 발송 - 대상 없으면 B2009, 인사 마스터에 없는 부서 B2010, 정상이면 READY | 0.251 |
| ✅ PASS | `popupAndCodes` | 팝업 사용 시 기간 필수 (B2008), 분류 코드 오류 (B2007) | 0.028 |

### 조회 언어 / 배지 / 검색 (NT-64 ~ NT-66)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `englishUserList` | NT-64 영어 사용자 목록 - 영어 버전 우선, 한국어만 있는 공지는 대표 언어(ko)로 대체 표시 | 0.097 |
| ✅ PASS | `koreanUserList` | NT-64 언어 미설정 사용자 - Accept-Language(ko) → 한국어 버전, 영어만 있는 공지는 대체 | 0.026 |
| ✅ PASS | `searchByDisplayedVersion` | NT-66 검색 - 사용자 언어로 표시되는 버전 기준 (대표 언어로 대체 표시되는 공지 포함) | 0.057 |
| ✅ PASS | `detailLanguageSwitch` | NT-65 상세 - 제공 언어 배지 + 언어 전환(lang=en), 없는 언어 요청은 사용자 언어/대표 언어로 | 0.037 |
| ✅ PASS | `scheduledNotice` | 예약 공지(게시 전) - 일반 사용자 목록/상세 제외(404), 관리자는 includeScheduled 로 조회 | 0.050 |
| ✅ PASS | `viewCountOncePerDay` | 조회수 - 같은 사용자는 하루 1회만 증가 | 0.045 |
| ✅ PASS | `popups` | 팝업 공지 - 기간 내 팝업만 | 0.026 |

### 수정 / 삭제 / 권한

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `update` | 수정 - 언어 버전 교체 (영어 추가, 대표 언어 변경), 수정 화면에 전체 버전 | 0.041 |
| ✅ PASS | `permissions` | 권한 - 조회 전용 403(등록), 매니저 등록 가능·삭제 불가, 관리자 삭제 후 404 | 0.050 |

### 첨부파일 (게시물 공통)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `uploadDownloadDelete` | 업로드 → 상세에 표시 → 다운로드(attachment, 원본 파일명) → 삭제 | 0.069 |
| ✅ PASS | `rejectExtension` | 허용되지 않은 확장자(.exe, 경로 포함 이름) 거부 - B2012 | 0.028 |

### 인사 마스터 조직도 / 공통코드

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `orgTreeEnglish` | 조직도 - 사업장별 부서 트리, 영어 사용자에게 영문 명칭 (영문명 없으면 국문) | 0.025 |
| ✅ PASS | `codes` | 공통코드 목록 - 사용자 언어 코드명 | 0.020 |

## NoticeMailServiceTest

공지 메일 발송 (NT-47 ~ NT-4A) — 통합 (H2, `MailGateway` Mock). 수원 사업장 = TF_TEAM(tx.viewer, ko) + TF_LAB(partner01, en)

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `groupByRecipientLanguage` | 수신자 언어별 그룹 일괄 발송 - 템플릿은 수신자 언어, 내용은 해당 언어 없으면 대표 언어(ko) | 0.027 |
| ✅ PASS | `noDuplicateAndScheduledSkipped` | 다시 실행해도 중복 발송 없음 (상태 선점), 예약 공지(2099)는 발송 안 함 | 0.026 |
| ✅ PASS | `recipientsAtSendTime` | 수신자는 발송 시점 인사 마스터 기준 - 등록 후 수원으로 이동한 사용자(emp01)도 포함 | 0.027 |
| ✅ PASS | `sendFailure` | 발송 실패 - 수신자별 FAIL 이력, 공지 상태 FAIL | 0.118 |
| ✅ PASS | `englishVersionForEnglishRecipient` | 영어 버전이 있으면 영어 수신자에게 영어 본문 | 0.034 |

## OracleSchemaTest (`-Poracle-it`)

실제 Oracle Free 23ai 컨테이너 — DDL·초기 데이터 실행(01, 02_notice), Entity 검증(`ddl-auto=validate`), 권한/메뉴 쿼리, 공지 검색

| 결과 | 테스트 | 검증 내용 | 시간(s) |
|---|---|---|---|
| ✅ PASS | `schemaAndQueries` | Oracle - DDL/초기 데이터 실행, Entity 검증(validate) 통과, 권한·메뉴 조회 쿼리 동작 | 0.285 |
| ✅ PASS | `noticeSearch` | Oracle - 공지 검색 (사용자 언어 버전 → 대표 언어 대체, CLOB 본문 LIKE 검색) | 0.772 |

## 참고

- 실행 로그의 `[ERROR] ... BusinessException` DEBUG 로그는 실패가 아니라, 오류 케이스 테스트에서 `@Logging` 이 의도된 예외를 기록한 것이다.
- 권한 유효기간 테스트는 결과가 실행일에 따라 바뀌지 않도록 기준일을 `2026-11-05` 로 고정했다.
- 시퀀스는 트랜잭션 롤백 대상이 아니므로 신규 ID 는 특정 값이 아닌 범위(> 100)로 검증한다.
- `SecurityIntegrationTest` 는 JwtDecoder 만 Mock 하고 나머지(필터, 변환기, DB 권한 조회, Forwarded 헤더 필터)는 실제로 동작한다.
  테스트 사용자의 sub 는 realm import 와 같은 고정 ID 이다. 실제 Keycloak 왕복은 [AUTH_E2E_REPORT.md](AUTH_E2E_REPORT.md).
- 기본 거부 적용으로 인가 규칙 밖의 경로는 404 가 아니라 403 이다. `pathNotFound` 는 규칙 범위 안의 없는 경로(`/api/v1/samples/1/unknown`)로 검증한다.
- `OracleSchemaTest` 로 H2 에서는 통과하던 `ALTER SEQUENCE ... RESTART WITH` 가 Oracle 에서 실패(ORA-03048)함을 발견 → `RESTART START WITH` 로 수정.
- 공지 검색은 CLOB 본문의 `LOWER/LIKE` 를 Hibernate JPQL 이 허용하지 않아 MyBatis 매퍼(`NoticeSearchMapper`)로 구현했고, Oracle 실DB 에서 `noticeSearch` 로 검증했다.
- `NoticeMailServiceTest` 는 `MailGateway` 만 Mock 하고 수신자 추출(인사 마스터)·템플릿·이력 저장은 실제로 동작한다.
- 상세 리포트: `target/surefire-reports/`
