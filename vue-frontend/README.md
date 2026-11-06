# vue-frontend

Flowlog 화면 (Vue 3 / TypeScript / Vite). [spring-gateway](../spring-gateway) 를 통해 [spring-boilerplate](../spring-boilerplate) API 를 사용한다.

| 항목 | 내용 |
|---|---|
| 접속 | **http://localhost:9909** (Gateway - 화면 `/**`, API `/cm/**`) |
| 개발 서버 | Vite **5173** (직접 접속하지 않음, Gateway 가 전달) |
| 인증 | Keycloak OIDC(BFF) / SAML 로그인 → SESSION 쿠키 (화면은 토큰을 다루지 않음) |
| 테스트 | `npm test` 단위 **30** · `npm run e2e` 실연동 **50/50** (Edge, 운영 CSP) |

## 화면 / 연동 API

| 화면 | 경로 | API |
|---|---|---|
| 로그인 | `/login` | `/oauth2/authorization/keycloak`, `/saml2/authenticate/keycloak` (`returnTo`), `POST /logout` |
| 공통 레이아웃 | - | `GET /auth/me` (사용자·권한·표시 언어), `GET /menus/me` (메뉴), `GET /langs` (언어 전환) |
| 홈 | `/` | 최근 공지, 내 업무 권한 표, 바로가기 |
| 공지사항 목록 | `/support/notices` | `GET /notices` (검색·분류·예약 포함·페이징, 언어 배지, 원문 표시) |
| 공지 상세 | `/support/notices/{id}` | `GET /notices/{id}?lang=` (언어 전환·대체 안내), 첨부 다운로드, `DELETE`, `GET /mail-histories` |
| 공지 등록/수정 | `/support/notices/new`, `/{id}/edit` | `POST`/`PUT /notices`, `GET /{id}/edit`, 첨부 `POST`/`DELETE /files`, 공통코드, 조직도(메일 대상) |
| 팝업 공지 | 로그인 후 | `GET /notices/popups` (오늘 하루 보지 않기) |
| 거래 목록 | `/tx/trades/list` | `GET`/`POST`/`PUT`/`DELETE /samples` (검색·정렬·페이징) |
| 공통코드관리 | `/system/codes` | `/code-groups` 그룹·코드 등록/수정/삭제, 언어별 이름 (`code.*`) |
| 메뉴관리 | `/system/menus` | `/menus` 트리(위/아래 이동)·등록/수정/삭제, 유형별 URL, 권한코드, 언어별 이름·본문 (`menu.*`) |
| 권한관리 | `/system/roles` | `/roles` 권한 등록/수정/삭제, 메뉴 권한(조회/등록/수정/삭제), 사용자·부서 부여·기간·회수, `/users` 검색 (`role.*`) |
| 조직도 | `/org` | `GET /org/tree` |
| 내 정보 | `/me` | `GET /auth/me`, `PUT /users/me/lang` |
| 메뉴 유형 | `/frame/{id}`, `/content/{id}`, 그 외 | IFRAME / CONTENT 본문(`GET /menus/{id}/content`) / 화면 없는 메뉴 "준비 중" |

## 실행

```bash
# 1) 인프라 + 백엔드 + Gateway (각 저장소 README 참고)
docker compose -f ../spring-boilerplate/infra/redis/docker-compose.yml up -d
docker compose -f ../spring-boilerplate/infra/keycloak/docker-compose.yml up -d
(cd ../spring-boilerplate && ./mvnw spring-boot:run)     # :9091
(cd ../spring-gateway && ./mvnw spring-boot:run)         # :9909

# 2) 화면
npm install
npm run dev                  # :5173 → 브라우저는 http://localhost:9909
```

```bash
npm run type-check           # vue-tsc
npm test                     # Vitest 단위 테스트
npm run build                # dist/
npm run preview              # 빌드 결과 서빙 (:5173, 운영과 같은 CSP 헤더)
npm run e2e                  # 실연동 E2E (설치된 Edge, E2E_BROWSER=chrome 가능) → e2e/screenshots/
```

테스트 계정은 [인증 설계](../spring-boilerplate/docs/design/02_auth_keycloak.md) 참고 (`admin`, `tx.viewer`, `emp01` …, local 전용).

## 운영 배포

```bash
docker build -t flowlog/vue-frontend .     # nginx(비루트, 8080), SPA fallback, CSP·보안 헤더
```

Gateway 에 `WEB_SERVICE_URI=http://{nginx}:8080` 을 지정한다. 화면에는 환경별 설정값이 없다. (API 는 같은 Origin 의 `/cm`)

## 문서

- [개발 가이드](docs/DEVELOPMENT_GUIDE.md) - 패키지 구조, API 연동, 인증/권한, 다국어, 디자인 규칙, 테스트
