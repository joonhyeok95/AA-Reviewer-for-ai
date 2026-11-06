# spring-boilerplate

Flowlog Backend API Boilerplate (Spring Boot 4.1 / Java 17 / Maven)

| 항목 | 내용 |
|---|---|
| 포트 | 서비스 **9091** / 관리(Actuator) **9191** (127.0.0.1 전용) |
| 진입점 | [spring-gateway](../spring-gateway) **9909**, `/cm/**` → 9091 (`/cm` 제거), 그 외 `/**` → 화면 |
| 화면 | [vue-frontend](../vue-frontend) (Vue 3) — **http://localhost:9909** 접속, 모든 API 연동 |
| 영속성 | JPA (기본) + MyBatis (DB 종속/복잡 조회 - `resources/mapper/**/*.xml`, 예: 공지 목록 검색) |
| 테스트 | `./mvnw test` **142/142** · `./mvnw test -Poracle-it` (실제 Oracle) **3/3** · 인증 E2E **52/52** (`--quick`) · 화면 E2E **50/50** |
| 주요 기능 | 인증/인가(Keycloak BFF·SAML2·Bearer), 권한/메뉴/공통코드, **관리 API (공통코드·메뉴·권한 - 언어별 이름, 메뉴 접근, 사용자/부서 부여)**, **공지사항 다국어 (v0.4)** — 언어 탭/대표 언어/대체 표시/검색, 첨부, 수신자 언어별 메일 |
| CI | 저장소 루트 `.github/workflows/backend-ci.yml` (build: `./mvnw -B verify`, oracle: `-Poracle-it`) |

## 실행

### 한 번에 실행 (인프라 + 앱 + Gateway + 화면)

`spring-boilerplate`, `spring-gateway`, `vue-frontend` 를 같은 폴더 아래 나란히 두고 실행한다.
이미 쓰는 포트가 있으면 시작하지 않고 어떤 프로세스인지 알려준다. 로그는 `.run/*.log`.

```powershell
./scripts/dev-up.ps1                 # Windows PowerShell (-Instances 2 : 9092 두 번째 인스턴스, -NoFrontend)
./scripts/dev-down.ps1               # 종료 (-Infra : Redis·Keycloak 컨테이너도 중지)
```
```bash
./scripts/dev-up.sh                  # macOS / Linux / Git Bash (--instances 2, --no-frontend)
./scripts/dev-down.sh                # 종료 (--infra)
```
접속은 http://localhost:9909 (Gateway). 화면 개발 서버 5173 에 직접 접속하지 않는다.

#### 같은 네트워크의 다른 PC·모바일에서 접속 (LAN 모드)

```powershell
# 최초 1회, 관리자 PowerShell : 9909 인바운드 허용 (Wi-Fi 가 "개인" 네트워크일 때)
New-NetFirewallRule -DisplayName 'Flowlog LAN 9909' -Direction Inbound -Protocol TCP -LocalPort 9909 -Action Allow -Profile Private

./scripts/dev-up.ps1 -Lan                 # IP 자동 감지 (-LanHost 192.168.x.x 로 지정 가능)
```
```bash
./scripts/dev-up.sh --lan                 # 또는 --lan-host 192.168.x.x
```
- 접속 : `http://{이 PC IP}:9909` (이 PC 에서도 IP 로 접속. localhost 와 세션이 따로 잡힘)
- 로그인 화면(Keycloak)도 Gateway 9909 경유 (`/realms/flowlog/**`, `/resources/**`) → **여는 포트는 9909 하나**. Keycloak 관리 콘솔은 계속 localhost:7000 에서만
- Keycloak 공개 URL·백엔드 issuer·redirect URI 가 IP 기준으로 바뀌어 Keycloak 컨테이너가 다시 만들어진다 (dev 모드라 realm 재import). 일반 모드로 돌아갈 때도 마찬가지
- Keycloak 로그인 쿠키(`Secure; SameSite=None`)는 http://{IP} 에서 브라우저가 버리므로 Gateway 가 이 경로에서만 `Secure` 를 빼고 `SameSite=Lax` 로 바꾼다 (HTTP 개발 전용)

### 개별 실행

```bash
# 인프라: Redis (세션 저장소 + Gateway Rate Limit), Keycloak (realm/client/사용자 자동 구성)
#  - 두 컨테이너 모두 127.0.0.1 에만 바인딩
docker compose -f infra/redis/docker-compose.yml up -d
docker compose -f infra/keycloak/docker-compose.yml up -d

# 테스트
./mvnw test                 # Windows: mvnw.cmd test
./mvnw test -Poracle-it     # Oracle Free 컨테이너로 DDL·초기데이터·Entity 검증 (Docker 필요)

# 실행 (기본 프로필 local - H2 in-memory)
./mvnw spring-boot:run
```

| URL | 설명 |
|---|---|
| http://localhost:9909/cm/swagger-ui.html | Swagger UI (Gateway 경유) |
| http://localhost:9909/cm/api/v1/samples | 샘플 API (인증 + scope 필요) |
| http://localhost:9909/cm/api/v1/auth/me | 현재 로그인 사용자 |
| http://localhost:9909/cm/api/v1/menus/me | 내 메뉴 트리 (유효 권한, 다국어, 메뉴별 list/add/edit/del) |
| http://localhost:9909/cm/api/v1/notices | 공지사항 (목록/상세/등록/수정/삭제, 첨부, 메일 이력) — `board.*` 권한 (공지사항 = 게시판) |
| http://localhost:9909/cm/api/v1/langs | 지원 언어 (LANG 코드 기반, 에디터 언어팩) |
| http://localhost:9909/cm/api/v1/org/tree | 조직도 (인사 마스터 사업장 → 부서, 국문/영문) |
| `PUT` http://localhost:9909/cm/api/v1/users/me/lang | 내 언어 설정 변경 |
| http://localhost:9909/cm/api/v1/code-groups · `/menus` · `/roles` | 관리 API (공통코드·메뉴·권한, `code.*`/`menu.*`/`role.*`) |
| http://localhost:9909/cm/saml2/authenticate/keycloak | SAML 로그인 시작 |
| http://localhost:9091/h2-console | H2 Console (직접 접속만, Gateway 는 `/cm/h2-console` 차단) |
| http://127.0.0.1:9191/actuator/health · `/prometheus` | Health / 메트릭 (관리 포트) |
| http://localhost:7000 | Keycloak 콘솔 (local 전용, 관리자 비밀번호는 `KC_ADMIN_PASSWORD`) |

> Gateway 의 `X-Forwarded-*` 는 `app.security.trusted-proxies`(기본 loopback)에서 온 요청만 반영한다. (`TrustedForwardedHeaderFilter`)

### 인증 (Keycloak)

| 방식 | 사용법 |
|---|---|
| OAuth2 BFF (브라우저) | `/cm/oauth2/authorization/keycloak` → 로그인 → **SESSION 쿠키만** 발급 (토큰은 Redis 세션에만, 암호화 저장) |
| SAML2 (브라우저) | `/cm/saml2/authenticate/keycloak` → 로그인 → SESSION 쿠키 (1시간마다 재로그인) |
| Client Credentials (서버 간) | `flowlog-batch` 로 토큰 발급 후 `Authorization: Bearer` |

- **API 권한 = IdP scope ∩ DB 업무 권한** (`TB_ROLE_MENU` × `TB_MENU.PERM_CD`). 시스템관리자(`ROLE_SYS_ADMIN`)는 전체 허용.
- 사용자 매핑은 Keycloak user id(`sub` = `TB_USER.IDP_SUBJECT`) 기준. 사용자명은 표시용.
- 규칙이 없는 경로는 **기본 거부**.

테스트 계정: `admin`, `tx.master`, `tx.manager`, `tx.viewer`, `emp01`, `partner01`, `guest01`, 게시판(공지) scope 용 `board.manager` · `board.writer` · `board.reader` · `board.limited` (비밀번호는 realm import 파일 참조, local 전용) — 상세는 [인증 설계](docs/design/02_auth_keycloak.md)

### 환경변수 (local 외 필수)

| 변수 | 설명 |
|---|---|
| `KEYCLOAK_BFF_CLIENT_SECRET` | BFF client secret (local 외 기본값 없음) |
| `SESSION_ENCRYPTION_KEY` | 세션 암호화 키, Base64 32바이트 (`openssl rand -base64 32`). 인스턴스 간 동일 |
| `REDIS_USERNAME` / `REDIS_PASSWORD` / `REDIS_SSL` | Redis ACL / TLS |
| `APP_TRUSTED_PROXIES` | Gateway IP 정규식 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Oracle (dev / prod) |

공지사항 (선택, 기본값 있음):

| 변수 | 설명 |
|---|---|
| `NOTICE_FILE_PATH` | 첨부 저장 경로 (기본 `./data/notice-files`, 웹 경로 밖) |
| `NOTICE_MAIL_SCHEDULER_ENABLED` / `NOTICE_MAIL_POLL_INTERVAL` / `NOTICE_MAIL_BATCH_SIZE` | 메일 스케줄러 사용 / 주기(60s) / BCC 묶음 크기(100) |
| `MAIL_FROM` / `NOTICE_LINK_TEMPLATE` | 발신 주소 / 메일 속 공지 링크(`{id}`) |
| `spring.mail.host` 등 (`SPRING_MAIL_HOST` …) | SMTP. 미설정이면 메일은 로그로 대체 |
| `UPLOAD_MAX_FILE_SIZE` / `UPLOAD_MAX_REQUEST_SIZE` | 업로드 크기 (20MB / 100MB) |

prod 프로필은 기동 시 IdP URL https, 세션 쿠키 Secure, local 기본 시크릿 미사용을 검사해 위반 시 기동 실패한다. (`SecurityStartupValidator`)

## 응답 형식

```json
{
  "locale": "ko_KR",
  "timestamp": "2026-11-05T10:44:02.925+09:00",
  "httpStatus": 200,
  "messageId": "S0001",
  "messageTitle": "조회 완료",
  "messageContent": "조회되었습니다.",
  "path": "/api/v1/samples/1",
  "result": {}
}
```

다국어: 사용자 설정 언어(`TB_USER.LANG_CD`) → `Accept-Language: ko-KR` / `en-US` → 기본(ko). 지원 언어는 공통코드 `LANG` 으로 확장 (코드 수정 없음)

## 문서

- [개발 가이드](docs/DEVELOPMENT_GUIDE.md)
- [설계 01 - 권한/메뉴/공통코드](docs/design/01_auth_menu_code.md)
- [설계 02 - 인증 (Keycloak OAuth2/SAML2)](docs/design/02_auth_keycloak.md)
- [설계 03 - 공지사항 다국어 (v0.4)](docs/design/03_notice.md)
- [테스트 결과](docs/reports/TEST_REPORT.md) / [인증 E2E 결과](docs/reports/AUTH_E2E_REPORT.md) / [보안 점검 및 조치](docs/reports/SECURITY_REVIEW.md)
