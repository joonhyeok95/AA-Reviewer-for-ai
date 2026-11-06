# 02. 인증 (Keycloak · BFF 세션 · Redis · scope 인가)

| 항목 | 내용 |
|---|---|
| 상태 | 구현 완료 · 보안 점검 18건 조치 · 실제 연동 E2E 40/40 (Gateway 경유) · 테스트 81/81 + Oracle 1/1 |
| 작성일 | 2026-11-05 |
| 구성 | Keycloak 26.8.1 · Redis 8.10.3 · spring-gateway(9909) · spring-boilerplate(9091) |
| Spring | Boot 4.1.2 / Spring Security 7.1.2 / Spring Session Data Redis |
| 관련 문서 | [E2E 결과](../reports/AUTH_E2E_REPORT.md) · [보안 점검 및 조치](../reports/SECURITY_REVIEW.md) |

---

## 1. 핵심 원칙

1. **브라우저는 토큰을 갖지 않는다.** 로그인 성공 시 클라이언트는 `SESSION` 쿠키(HttpOnly)만 받는다.
   Access/Refresh/ID Token 은 서버가 받아 **Redis 세션**에만, **암호화(AES-256-GCM)** 해서 저장한다. (BFF)
2. **세션은 Redis 에 저장**되어 애플리케이션 인스턴스 간에 공유된다. (Spring Session)
3. **사용자 식별은 `sub`** (Keycloak user id = `TB_USER.IDP_SUBJECT`). 사용자명(`preferred_username`, NameID)은 표시용.
4. **API 권한 = IdP scope ∩ DB 업무 권한.** IdP(Keycloak 역할 기반 scope)는 상한, DB(기간·부서·커스텀 권한)는 실제 업무 권한.
   세션 로그인도 **요청마다** 다시 계산한다.
5. **기본 거부.** 인가 규칙이 없는 경로는 시스템관리자도 403.

---

## 2. 구성

```
 Browser ──(SESSION 쿠키만)──▶ spring-gateway :9909 ──(/cm 제거)──▶ spring-boilerplate :9091 / :9092 ...
                               │ PathSecurity (우회 경로 400, 차단 404) │ TrustedForwardedHeaderFilter (Gateway IP 만)
                               │ RateLimit (Redis Lua, 실재 세션/IP)   │ Spring Security
                               │ CircuitBreaker (Resilience4j)         │  ├ oauth2Login (BFF, confidential + PKCE)
                               ▼                                       │  ├ SessionTokenAuthorizationFilter (BFF/SAML 요청마다 재계산)
                          ┌──────────── Redis :6379 (127.0.0.1) ──┐    │  ├ saml2Login (sub/Role 속성)
                          │ request_rate_limiter.{route.key}.*    │◀───┤  └ oauth2ResourceServer (Bearer, 서버 간)
                          │ flowlog:session:sessions:{id}         │    │       └ AuthorityResolver: IdP scope ∩ DB 권한
                          │   └ AES-GCM 암호문 (SecurityContext+토큰)│    ▼
                          └───────────────────────────────────────┘  Keycloak :7000 (127.0.0.1, realm flowlog)
```

| 인증 방식 | Keycloak client | 클라이언트가 가진 것 | 상태 저장 | IdP scope 출처 |
|---|---|---|---|---|
| **OAuth2 BFF** (브라우저) | `flowlog-bff` (confidential, PKCE) | `SESSION` 쿠키 | Redis 세션 (토큰 암호화) | 세션 토큰 scope (요청마다) |
| **SAML2** (브라우저) | SAML client | `SESSION` 쿠키 | Redis 세션 | Assertion `Role` 속성 |
| **Client Credentials** (서버 간) | `flowlog-batch` (confidential) | 자기 Access Token | 없음 (Stateless) | JWT scope |

---

## 3. 요청 처리 흐름

### 3.1 로그인 (OAuth2 BFF)

```
Browser                    Gateway/App                                Keycloak             Redis
  │ GET /cm/oauth2/authorization/keycloak ─▶│ 세션 생성, state/PKCE verifier 저장 ─────────────────────▶│
  │◀── 302 Keycloak 로그인 (code_challenge) ─│                                                          │
  │ ID/PW 입력 (5회 실패 시 일시 잠금) ──────────────────────────────────▶│                           │
  │◀── 302 /cm/login/oauth2/code/keycloak?code ──────────────────────────│                           │
  │ GET (code) ─────────────────────────────▶│ code + verifier + secret → 토큰 교환 (서버 간) ─▶│      │
  │                                          │◀── access / refresh / id token ─────────────────│      │
  │                                          │ 세션ID 교체 + 로그인 시각 + 감사로그, 세션 암호화 저장 ──▶│
  │◀── 302 returnTo (화면) 또는 /cm/api/v1/auth/me  + Set-Cookie: SESSION (HttpOnly) ─│ ※ 토큰은 응답 어디에도 없음 │
```

로그인 실패는 로그인 페이지 redirect 대신 401 표준 응답 + 감사 로그(`LOGIN_FAILURE`).

### 3.1.1 화면(SPA, `vue-frontend`) 연동

화면은 Gateway 의 `/**` 라우트로 서비스되어 API(`/cm/**`)와 **같은 Origin** 이다. (CORS 없음, 쿠키 그대로)

| 항목 | 동작 |
|---|---|
| 로그인 시작 | `/cm/oauth2/authorization/keycloak?returnTo=/support/notices` (SAML 은 `/cm/saml2/authenticate/keycloak?returnTo=`) |
| 복귀 경로 | `LoginReturnUrlFilter` 가 `returnTo` 를 검증해 세션에 저장 → 성공 핸들러가 그 경로로 302. **같은 호스트 절대 경로만** (`/` 시작, `//`·역슬래시·허용 외 문자 거부, 500자) - Open Redirect 방지. 없거나 거부되면 `success-url` |
| CSRF 쿠키 | `XSRF-TOKEN` 경로 **`/`** (Gateway 뒤 context path 는 `/cm` 이라 기본값이면 화면 스크립트가 못 읽음). `CsrfCookieFilter` 가 **첫 요청부터** 발급 (있으면 재발급 안 함) |
| 요청 캐시 | 미사용 (`NullRequestCache`). 미인증 요청은 세션을 만들지 않는다 → 익명 요청으로 Redis 세션을 늘리거나 Gateway Rate Limit 세션 버킷을 새로 받을 수 없음 |
| 표시 언어 | `GET /auth/me` 의 `langCd`(사용자 설정 → Accept-Language → 기본) / `userLangCd`(설정값, 없으면 null) |
| 로그아웃 | `POST /cm/logout` (X-XSRF-TOKEN) → 200, 화면이 로그인 화면으로 이동 |

### 3.2 API 요청 (인가)

1. 브라우저 → `SESSION` 쿠키 → Spring Session 이 Redis 에서 세션 로드(복호화 + 무결성 검증) → SecurityContext 복원
2. `SessionTokenAuthorizationFilter`
   - **BFF**: 세션 토큰 조회. 만료 10초 전이면 refresh — **세션 단위로 직렬화**하고, 다른 요청/인스턴스가 먼저 갱신해 Redis 에
     저장한 더 새로운 토큰이 있으면 그것을 사용 (refresh token rotation 대비, flush-mode immediate).
     토큰 없음/만료/갱신 실패 → 세션 폐기 + 401 C0009
   - **SAML**: 로그인 후 `max-auth-age`(1h) 경과 → 세션 폐기 + 401 (재로그인으로 최신 Assertion). 아니면 세션의 sub/Role 로 재계산
   - 권한 = `AuthorityResolver.forUser(sub, IdP scope)` → `SCOPE_*`(교집합) + `ROLE_*`(DB 유효 권한). 이번 요청에만 사용
3. Bearer: `KeycloakJwtAuthenticationConverter` — 서비스 계정(아래)은 토큰 scope 만, 사용자는 `forUser(sub, scope)`
4. `AuthorizationFilter` 가 URL 규칙으로 인가 → 403 C0010 (감사 로그 `FORBIDDEN`)

### 3.3 권한 모델

| 구분 | 내용 |
|---|---|
| IdP scope | Keycloak 이 발급한 scope (BFF/Bearer) 또는 SAML `Role` 속성 중 `xxx.list\|add\|edit\|del` 형식 (동의어 `write`=add, `update`=edit, `delete`=del) |
| DB 권한 | 유효 권한(직접+부서, 기간 내)의 `TB_ROLE_MENU` 플래그 × `TB_MENU.PERM_CD` → `{PERM_CD}.{list\|add\|edit\|del}` ([설계 01](01_auth_menu_code.md)) |
| 최종 `SCOPE_*` | 둘 다 있는 것만. DB 미등록/미사용 사용자는 없음 |
| `ROLE_*` | DB 유효 권한코드 (예: `ROLE_SYS_ADMIN` = 전체 허용) |
| 서비스 계정 | `azp` ∈ `app.security.service-account-clients` **이고** 사용자명 = `service-account-{azp}` 일 때만 → 토큰 scope 만 |
| 캐시 | `UserAuthorityService.loadBySubject` Caffeine 60초 (`AUTHORITY_CACHE_TTL`). 관리 화면 변경은 커밋 후 Redis Pub/Sub(`flowlog:cache:invalidate`)로 **모든 인스턴스** 캐시를 비움 (`CacheConfig`, `app.cache.broadcast-enabled`). Redis 장애 시 TTL 뒤 반영 |

예) tx.viewer — Keycloak 은 `sample.list` 만 발급, DB 는 `TX_MANAGER_READ`(조회) → `SCOPE_sample.list`.
Keycloak 이 `sample.add` 까지 허용하더라도 DB 가 조회 전용이면 등록 403 (커스텀 권한 반영).

### 3.4 인가 규칙 (`ApiAuthorizationRules` 규칙표 + `SecurityConfig`)

| 요청 | 필요 권한 |
|---|---|
| `GET /api/v1/samples/**` | `SCOPE_sample.list` |
| `POST /api/v1/samples/**` | `SCOPE_sample.add` |
| `PUT, PATCH /api/v1/samples/**` | `SCOPE_sample.edit` |
| `DELETE /api/v1/samples/**` | `SCOPE_sample.del` |
| `GET /api/v1/notices/*/edit`, `GET /api/v1/notices/*/mail-histories`, `DELETE /api/v1/notices/*/files/*` | `SCOPE_board.update` |
| `POST /api/v1/notices/*/files` | `SCOPE_board.write` 또는 `SCOPE_board.update` |
| `GET / POST / PUT / DELETE /api/v1/notices/**` (공지사항 = 게시판) | `SCOPE_board.list / write / update / delete` |
| 위 전체 | 또는 `ROLE_SYS_ADMIN` |
| `GET /api/v1/auth/me`, `/api/v1/menus/me`, `/api/v1/menus/*/content`, `/api/v1/langs`, `/api/v1/codes/*`, `/api/v1/org/tree`, `PUT /api/v1/users/me/lang` | 로그인만 |
| `/api/v1/code-groups/**` · `/api/v1/menus`, `/api/v1/menus/*` · `/api/v1/roles/**`, `GET /api/v1/users` | `SCOPE_code.*` · `SCOPE_menu.*` · `SCOPE_role.*` (메서드별 list/add/edit/del, 부여·메뉴 접근은 role.edit) |
| `/actuator/health·info·prometheus`(관리 포트), `/swagger-ui/**`, `/v3/api-docs/**`, `/h2-console/**`, `/error` | 공개 |
| **그 외** | **거부 (`denyAll`)** |

> 새 API = `ApiAuthorizationRules` 규칙표(crud / 예외 rule, 구체적인 규칙을 앞에) + 메뉴 `PERM_CD` / `TB_ROLE_MENU` + Keycloak 같은 이름 역할·client scope(setup.sh) + BFF scope. 누락은 `ScopeConsistencyTest` 가 검사.

### 3.5 로그아웃

`POST /logout` (+ `X-XSRF-TOKEN`) → ① 감사 로그 ② Keycloak SSO 세션 종료 (세션의 refresh token 으로 서버 간 호출, 타임아웃 3/5초)
③ Redis 세션 삭제 → 200. 이후 재로그인 시 Keycloak 로그인 화면이 다시 표시된다.

---

## 4. Keycloak 설정 (`infra/keycloak`)

### scope 와 역할

| realm 역할 = client scope | admin | tx.master | tx.manager | tx.viewer | emp01 | partner01 | guest01 | 서비스계정 flowlog-batch |
|---|---|---|---|---|---|---|---|---|
| `sample.list` | O | O | O | O | O | O |  | O |
| `sample.add` | O | O | O |  |  |  |  |  |
| `sample.edit` | O | O | O |  |  |  |  |  |
| `sample.del` | O | O |  |  |  |  |  | O |
| `board.list` (공지 조회) | O | O | O | O | O | O |  |  |
| `board.write` (공지 등록) | O | O | O |  |  |  |  |  |
| `board.update` (공지 수정) | O | O | O |  |  |  |  |  |
| `board.delete` (공지 삭제) | O | O |  |  |  |  |  |  |
| `code.*` `menu.*` `role.*` (관리, 12개) | O |  |  |  |  |  |  |  |

### 게시판(공지사항) 테스트 계정 (board scope)

공지사항 API 의 권한 리소스는 `board` 다 (공지사항 = 게시판, 이전 `notice.*` scope 는 폐지).
scope 동사 `write`/`update`/`delete` 는 DB 작업 ADD/EDIT/DEL 에 대응한다 (`ScopeActions`, 화면 `auth.can()` 도 동일). 권한 이름은 scope 그대로 (`SCOPE_board.write`).
DB : 공지사항 메뉴(15, `/support/notices`, PERM_CD `board`) + 권한 BOARD_MANAGER(전체) / BOARD_WRITER(삭제 제외) / BOARD_READER(조회). 기존 역할(모듈마스터 전체, TX 모듈매니저 삭제 제외, 일반 사용자 조회)도 같은 메뉴 권한으로 board.* 를 받는다.

| 사용자 | Keycloak 역할 (= 토큰 scope) | DB 권한 | 최종 API 권한 (교집합) |
|---|---|---|---|
| `board.manager` | board.list/write/update/delete | BOARD_MANAGER | list, write, update, delete |
| `board.writer` | board.list/write/update | BOARD_WRITER | list, write, update |
| `board.reader` | board.list | BOARD_READER | list |
| `board.limited` | board.list/write/update/delete | BOARD_READER | **list 만** (DB 가 상한) |
| `admin` | board.* 전체 | SYS_ADMIN | 전체 (시스템관리자) |

Keycloak user id 는 `00000000-0000-4000-8000-0000000000{08~11}` (TB_USER.IDP_SUBJECT 와 동일).

- `board.*` (공지사항): realm 역할 + 같은 이름 client scope (`setup/setup.sh`), BFF 요청 scope 에 추가. DB 권한은 메뉴 15(`PERM_CD=board`)의 `TB_ROLE_MENU` (모듈마스터 전체, TX 모듈매니저 삭제 제외, 일반 사용자 조회). 상세 [03_notice.md](03_notice.md)

- client scope 에 같은 이름 realm 역할을 **scope mapping** → 역할이 있는 사용자/서비스계정에게만 발급.
- SAML client mapper: `role list`(→ `Role` 속성), **`subject id`**(user property `id` → `sub` 속성, 권한 매핑 기준).

### realm 보안 설정

| 항목 | 값 |
|---|---|
| 사용자 ID | realm import 에서 **고정** (`00000000-0000-4000-8000-00000000000N`) = `TB_USER.IDP_SUBJECT` |
| 무차별 대입 방어 | `bruteForceProtected` — 5회 실패 시 60초부터 증가(최대 15분) 일시 잠금 |
| `sslRequired` | `external` (사설/로컬 IP 외에는 HTTPS 필수) |
| `editUsernameAllowed` | false |
| 포트 바인딩 | `127.0.0.1:7000` (docker compose) |
| 관리자 | `KC_ADMIN_PASSWORD` 환경변수 (기본값은 local 전용) |

### client

| client | 유형 | 용도 | 비고 |
|---|---|---|---|
| `flowlog-bff` | confidential + PKCE(S256) | 브라우저 로그인 (BFF) | redirect: `9909/cm` + `9091`, **local Access Token 60초** (refresh 확인용) |
| `flowlog-batch` | confidential, service account (standard flow off) | 서버 간 호출 | 서비스계정 역할: sample.list, sample.del |
| SAML SP | SAML | 브라우저 SSO | entityId = `http://localhost:9909/cm/saml2/service-provider-metadata/keycloak` |

---

## 5. 애플리케이션 설정 (`application.yml`)

```yaml
spring:
  data.redis: { host: ${REDIS_HOST}, port: ${REDIS_PORT}, username: ${REDIS_USERNAME:}, password: ${REDIS_PASSWORD:}, ssl.enabled: ${REDIS_SSL:false} }
  session:
    timeout: 30m
    data.redis: { namespace: flowlog:session, flush-mode: immediate }
  security.oauth2.resourceserver.jwt: { issuer-uri: ${KEYCLOAK_ISSUER_URI}, audiences: flowlog-api }
app.security:
  oauth2.client-secret: ${KEYCLOAK_BFF_CLIENT_SECRET}      # 기본값 없음 (local 만 application-local.yml)
  saml.max-auth-age: ${SAML_MAX_AUTH_AGE:1h}
  service-account-clients: ${SERVICE_ACCOUNT_CLIENTS:flowlog-batch}
  trusted-proxies: ${APP_TRUSTED_PROXIES:loopback}         # X-Forwarded-* 를 믿을 Gateway IP 정규식
  authority-cache-ttl: ${AUTHORITY_CACHE_TTL:60s}
  require-https: false                                      # prod: true (기동 검사)
  session.encryption-key: ${SESSION_ENCRYPTION_KEY:}        # Base64 32바이트, local 만 기본값
server:
  forward-headers-strategy: none                            # TrustedForwardedHeaderFilter 사용
  servlet.session.cookie: { name: SESSION, http-only: true, same-site: lax, secure: ${SESSION_COOKIE_SECURE:true} }
management.server: { port: ${MANAGEMENT_PORT:9191}, address: ${MANAGEMENT_ADDRESS:127.0.0.1} }
```

### 5.1 세션 저장 형식 (`SessionStoreConfig`)

- `springSessionDefaultRedisSerializer` = **AES-256-GCM 암호화 + 허용목록 JDK 직렬화**
  - 기밀성: Redis 를 직접 읽어도 토큰이 보이지 않음 (E2E 확인)
  - 무결성: 키 없이 위·변조한 값은 복호화 단계에서 거부 → 역직렬화 공격 입력이 도달하지 않음
  - 허용목록(`ObjectInputFilter`): `java.lang/util/time`, `java.net.URL/URI`, `org.springframework.security/session.**`, `com.flowlog.backend.**` 외 거부 + 깊이/크기 제한
- 키는 인스턴스 간 동일해야 하며, 바꾸면 기존 세션은 복호화되지 않아 재로그인. (Spring Security Jackson 직렬화는 SAML 객체 복원 문제로 사용 안 함)

### 5.2 Forwarded 헤더 (`TrustedForwardedHeaderFilter`)

직전 호출자 IP 가 `app.security.trusted-proxies` 와 일치할 때만 `Forwarded`/`X-Forwarded-*` 를 반영하고, 아니면 헤더를 숨긴다.
→ 9091 에 직접 접근해 `X-Forwarded-Host: evil` 을 보내도 redirect / SAML ACS / Swagger URL 이 조작되지 않는다. (Gateway 쪽은 클라이언트 값을 덮어씀)

### 5.3 감사 로그 (`AuthAuditLogger`, logger `AUDIT`)

`LOGIN_SUCCESS / LOGIN_FAILURE / LOGOUT / UNAUTHORIZED / FORBIDDEN` — user, type, ip, method, uri, reason (+ traceId).
local 외 프로필은 `*-audit.log` 별도 파일(365일). 토큰·비밀번호는 기록하지 않음, 개행 제거(로그 인젝션 방지).

### 5.4 운영 기동 검사 (`SecurityStartupValidator`, `require-https=true`)

issuer / SAML 메타데이터 / SP entityId 가 https, 세션 쿠키 Secure, client secret·세션 키가 local 기본값이 아님 — 하나라도 위반 시 기동 실패.
(SAML 메타데이터에는 서명 검증 인증서가 들어 있어 http 로 받으면 중간자가 위조 Assertion 을 만들 수 있다)

---

## 6. 구현 파일

| 파일 | 역할 |
|---|---|
| `global/security/SecurityConfig` | Filter Chain, 인가 규칙(기본 거부), 로그인 성공/실패 핸들러, 로그아웃 |
| `global/security/AuthorityResolver` | IdP scope ∩ DB 권한 → `SCOPE_*` + `ROLE_*` |
| `role/domain/service/UserAuthorityService` | sub → 사용자 → 유효 권한 → 메뉴 권한(PERM_CD) 계산, 캐시 |
| `global/security/SessionTokenAuthorizationFilter` | BFF/SAML 요청마다 권한 재계산, refresh 직렬화, SAML max-auth-age |
| `global/security/KeycloakJwtAuthenticationConverter` | Bearer JWT → 권한, 서비스 계정 판별(azp 허용목록) |
| `global/security/SamlRoleAuthoritiesConverter` | SAML sub/Role → 권한 |
| `global/security/AuthenticationSubjects` | 인증 객체 → sub |
| `global/security/OAuth2ClientConfig` | ClientRegistration, 세션 토큰 저장소, AuthorizedClientManager(refresh) |
| `global/security/KeycloakLogoutHandler` | 로그아웃 시 Keycloak SSO 세션 종료 |
| `global/security/AuthAuditLogger` / `SecurityErrorHandler` | 감사 로그 / 401·403·로그인 실패 표준 응답 |
| `global/security/SecurityStartupValidator` | prod 기동 검사 |
| `global/config/SessionStoreConfig` | 세션 암호화 직렬화 |
| `global/web/TrustedForwardedHeaderFilter` + `global/config/ForwardedHeaderConfig` | 신뢰 프록시 Forwarded 헤더 |
| `global/config/CacheConfig`, `global/cache/*` | Caffeine 캐시 + 인스턴스 간 무효화 전파 (Redis Pub/Sub) |
| `menu/web/MenuController` | `GET /api/v1/menus/me` |
| `infra/keycloak/realm/flowlog-realm.json` · `setup/setup.sh` | realm, client, 사용자(고정 ID), mapper / client scope 역할 제한 |
| `infra/keycloak/e2e/auth_e2e.py` | 실제 연동 E2E 40건 |

---

## 7. 로컬 실행

```bash
docker compose -f infra/redis/docker-compose.yml up -d
docker compose -f infra/keycloak/docker-compose.yml up -d     # realm 변경 시 --force-recreate (dev 모드 H2)
./mvnw spring-boot:run                                        # :9091 (관리 :9191)
SERVER_PORT=9092 MANAGEMENT_PORT=9192 java -jar target/*.jar  # 세션 공유 점검용 두 번째 인스턴스
(cd ../spring-gateway && ./mvnw spring-boot:run)              # :9909 (관리 :9910)
python infra/keycloak/e2e/auth_e2e.py
```

테스트 계정 비밀번호·client secret 은 `infra/keycloak` 파일의 local 전용 값을 사용한다. (문서에 기재하지 않음)
E2E 의 무차별 대입 시나리오는 guest01 을 일시 잠근다 (60초 이상 후 재실행).

---

## 8. 구현 중 확인된 이슈

| 이슈 | 원인 | 조치 |
|---|---|---|
| scope 요청만 하면 누구나 `sample.del` 발급 | optional client scope 는 기본적으로 역할 제한 없음 | client scope 에 같은 이름 realm 역할 scope mapping |
| 세션 키가 `spring:session:*` 로 저장 | Boot 4 속성명 `spring.session.data.redis.*` | 속성명 수정 |
| issuer-uri discovery 시 기동 실패 위험 | 기동 시 Keycloak 호출 | ClientRegistration 직접 구성 |
| 기본 토큰 저장소 인메모리 | 기본 AuthorizedClientService | HttpSession 저장소 → Redis |
| Redis Jackson 역직렬화 (SAML) | `Saml2PostAuthenticationRequest` 등 | JDK 직렬화 + **AES-GCM + 허용목록** |
| 관리 포트 `/actuator/prometheus` 401 | 관리 컨텍스트에도 Security 적용 | 공개 경로에 info/prometheus 추가 (관리 포트는 127.0.0.1) |
| `@Cacheable` null 키 오류 (sub 없는 SAML) | `unless` 는 키 계산 후 평가 | `condition = "#subject != null"` |
| 화면에서 XSRF-TOKEN 을 읽지 못함 | Gateway 뒤 context path `/cm` → 쿠키 경로 `/cm`, 토큰도 지연 발급 | 쿠키 경로 `/` + `CsrfCookieFilter` (첫 요청 발급) |
| 익명 401 마다 SESSION 생성 | 요청 캐시가 미인증 요청을 세션에 저장 | `NullRequestCache` (로그인 후 이동은 `returnTo`/`success-url`) |
| 로그인 후 JSON 화면(/auth/me)으로 이동 | 고정 success-url | `returnTo` (화면 경로) 지원, 없으면 기존 success-url |

---

## 9. 운영 전환 체크리스트

- [ ] 환경변수: `KEYCLOAK_BFF_CLIENT_SECRET`, `SESSION_ENCRYPTION_KEY`(인스턴스 공통), `REDIS_USERNAME/PASSWORD`, `REDIS_SSL=true`, `APP_TRUSTED_PROXIES`(Gateway 대역)
- [ ] prod 프로필(`require-https=true`) 기동 검사 통과 — https IdP URL, Secure 쿠키
- [ ] Redis: ACL + TLS + HA(Sentinel/Cluster), 외부 네트워크 비노출
- [ ] Keycloak 운영 realm: production 모드 + HTTPS + 외부 DB, `sslRequired=all`, brute force 정책 확정, client secret 재발급,
      Access Token 수명(local 60초 → 운영 5분 등), redirect URI 운영 도메인 한정, 관리 콘솔 내부망 한정, 테스트 사용자 미import
- [ ] 운영 사용자 `TB_USER.IDP_SUBJECT` 매핑 절차 (인사 I/F 또는 최초 로그인 연결) 확정
- [ ] SAML: SP 서명키 + SLO 검토 (현재 max-auth-age 1h + 요청마다 DB 재계산으로 완화)
- [ ] OIDC back-channel logout 검토 (현재 BFF 는 refresh 시점(≤ Access Token 수명)에 Keycloak 세션 종료를 감지)
- [ ] LB 가 클라이언트의 `X-Forwarded-*` 를 덮어쓰도록 설정, 백엔드는 Gateway 에서만 접근
- [ ] 관리 포트(9191/9910)는 모니터링 시스템에서만 접근, 감사 로그 보관/수집
