# 인증 · 세션 · Gateway E2E 점검 리포트 (실제 연동)

- 실행일시: 2026-11-05 (KST) — v0.4 공지사항 다국어 반영 후 재실행
- 실행 명령: `python infra/keycloak/e2e/auth_e2e.py` (Gateway 경유 `http://localhost:9909/cm`, 세션 공유 대상 `http://localhost:9092`)
- 환경: Keycloak 26.8.1 (127.0.0.1:7000, realm 재import) · Redis 8.10.3 (127.0.0.1:6379) · spring-gateway (:9909, 관리 :9910) ·
  spring-boilerplate 인스턴스 2개 (:9091 / :9092, 관리 :9191 / :9192)
- 방식: 브라우저/클라이언트와 동일한 HTTP 흐름을 스크립트로 재현 (Keycloak 로그인 폼 제출, 콜백, SAMLResponse 중계) + Redis 직접 조회

## 요약

| 구분 | 결과 |
|---|---|
| 인증·세션·인가·Rate Limit·보안 점검·공지 다국어 | ✅ 49/49 |
| 서킷브레이커 (실제 앱 중지) | ✅ 5건 G0002 → OPEN → G0004, Prometheus `state="open"` 1 (조치 후 재확인) |

### 요구사항별 확인

| 요구사항 | 확인 내용 |
|---|---|
| 토큰 노출 금지 | 클라이언트 쿠키는 `SESSION`(HttpOnly) + `XSRF-TOKEN` 뿐, 로그인 과정 애플리케이션 응답에서 JWT 0건 |
| 세션 암호화 | Redis 세션을 직접 읽어도 JWT 원문 없음 (AES-256-GCM). refresh 후 토큰 속성 암호문 교체 |
| Redis 세션 공유 | 9091 에서 로그인한 `SESSION` 쿠키로 9092 호출 → 같은 사용자 (OAuth2·SAML 모두) |
| 권한 = scope ∩ DB | 사용자별 scope 200/403, SAML 은 sub 로 사용자 매핑, 내 메뉴 API 는 유효 권한 메뉴만 |
| 토큰 자동 갱신 · 로그아웃 | 60초 토큰 만료 임박 → 갱신 200 / Redis 세션 삭제 + Keycloak SSO 종료 |
| Rate Limit (Lua) | IP 60건 → 429 G0005. **요청마다 위조 SESSION 쿠키 60건도 IP 버킷으로 429** (우회 불가) |
| Gateway 경로 보안 | `/cm/h2-console`, `/cm/actuator/*` 404 G0001, 우회 형식(`/./`, `//`) 400 G0006, 9909 `/actuator/gateway` 404 |
| 관리 포트 | 9191 / 9910 `/actuator/prometheus` 200 (127.0.0.1), 9091 서비스 포트 actuator 404 |
| 공지 다국어 (v0.4) | 언어 목록(LANG/ATTR1), 대표 언어 대체 표시·배지, 대체 표시분 검색, 언어 전환, 조직도 영문 명칭, XSS 정화, Gateway 경유 첨부, 권한, 수신자 언어별 메일 (수원 사업장 → ko 1명 / en 1명) |
| 무차별 대입 | 비밀번호 5회 실패 후 올바른 비밀번호도 로그인 불가 (Keycloak 일시 잠금) |

## 공통 — 미인증

| 결과 | 점검 내용 | 상세 |
|---|---|---|
| ✅ PASS | 인증 없이 /api/v1/auth/me → 401 C0009 | status=401 |

## Bearer — Client Credentials (flowlog-batch, 서비스계정 역할: sample.list, sample.del)

| 결과 | 점검 내용 | 상세 |
|---|---|---|
| ✅ PASS | 4개 scope 요청 → 역할에 매핑된 list, del 만 발급 (Keycloak 역할 제한) | scope=sample.list sample.del profile email |
| ✅ PASS | /me → OAUTH2_BEARER, 서비스계정, SCOPE_sample.del/list | scopes=['SCOPE_sample.del', 'SCOPE_sample.list'] |
| ✅ PASS | GET /samples (sample.list) → 200 | status=200 |
| ✅ PASS | POST /samples (sample.add 없음) → 403 | status=403 |
| ✅ PASS | DELETE /samples/3 (sample.del) → 200 | status=200 |

## BFF — OAuth2 로그인 (flowlog-bff) - 토큰은 서버 세션(Redis)에만

| 결과 | 점검 내용 | 상세 |
|---|---|---|
| ✅ PASS | 로그인 → 콜백 → 성공 URL(/api/v1/auth/me), authType OAUTH2_SESSION, client flowlog-bff | final=http://localhost:9909/cm/api/v1/auth/me |
| ✅ PASS | scope 4개를 요청했지만 tx.viewer 역할(sample.list)만 발급 → 권한 SCOPE_sample.list | scopes=['SCOPE_sample.list'] |
| ✅ PASS | 클라이언트 쿠키 = SESSION(HttpOnly) + CSRF 쿠키뿐 (토큰 쿠키 없음) | cookies=['SESSION', 'XSRF-TOKEN'], SESSION path=/cm |
| ✅ PASS | 로그인 과정의 모든 애플리케이션 응답(헤더/본문/쿠키)에 토큰(JWT) 없음 | 검사 4건, 노출 0건 |
| ✅ PASS | Redis 에 세션 저장 + 세션 안에 토큰 보관 (서버 전용) | key=flowlog:session:sessions:aa21b6ab... |
| ✅ PASS | Redis 세션 값은 암호화 - Redis 를 직접 읽어도 토큰(JWT) 원문이 보이지 않음 |  |
| ✅ PASS | 내 메뉴 API → 유효 권한 메뉴만 (시스템관리 없음), 거래목록은 조회 전용 | roots=[1, 7, 15, 16] |
| ✅ PASS | GET /samples (세션 토큰 scope sample.list) → 200 | status=200 |
| ✅ PASS | POST /samples (세션 토큰에 sample.add 없음) → 403 C0010 | status=403 |
| ✅ PASS | 다른 인스턴스(http://localhost:9092)에 같은 SESSION 쿠키 → 200 (Redis 세션 공유) | status=200 |
| ✅ PASS | 토큰 만료 임박 후 요청 → refresh token 으로 자동 갱신, 세션(Redis) 토큰(암호문) 교체, 200 | status=200, token 변경=True |
| ✅ PASS | POST /logout → 200, Redis 세션 삭제, 이후 /me 401 | logout=200, redis=0, me=401 |
| ✅ PASS | 로그아웃 시 Keycloak SSO 세션도 종료 → 재로그인 시 로그인 화면 표시 |  |

## BFF — 사용자별 scope 인가 (tx.manager: list/add/edit, admin: 전체)

| 결과 | 점검 내용 | 상세 |
|---|---|---|
| ✅ PASS | tx.manager 세션 scope = list, add, edit | scopes=['SCOPE_sample.add', 'SCOPE_sample.edit', 'SCOPE_sample.list'] |
| ✅ PASS | POST /samples → 201, 생성자 = tx.manager | status=201 |
| ✅ PASS | DELETE (sample.del 없음) → 403 | status=403 |
| ✅ PASS | admin DELETE → 200 | status=200, scopes=['SCOPE_sample.list'] |

## SAML2 — SP-initiated SSO (tx.manager) - sub/Role 속성 → scope ∩ DB 권한, Redis 세션

| 결과 | 점검 내용 | 상세 |
|---|---|---|
| ✅ PASS | SAML 로그인 → authType SAML2, sub 로 사용자 매핑, scope = Role ∩ DB(list, add, edit) | ACS=http://localhost:9909/cm/login/saml2/sso/keycloak, scopes=['SCOPE_sample.add', 'SCOPE_sample.edit', 'SCOPE_sample.list'] |
| ✅ PASS | POST /samples (SCOPE_sample.add) → 201 | status=201 |
| ✅ PASS | DELETE (sample.del 없음) → 403 | status=403 |
| ✅ PASS | Redis 에 SAML 세션 저장 |  |
| ✅ PASS | 다른 인스턴스(http://localhost:9092)에 같은 SESSION 쿠키 → SAML2 세션 공유 | status=200 |

## 공지 — 다국어 공지 (tx.manager = 언어 en / tx.viewer = 언어 미설정 → ko)

| 결과 | 점검 내용 | 상세 |
|---|---|---|
| ✅ PASS | 언어 목록 = LANG 코드(ko, en) + 에디터 언어팩(ATTR1), 언어명은 사용자 언어(en) | langs=[('ko', 'Korean'), ('en', 'English')] |
| ✅ PASS | 목록(en 사용자) - 1번 영어 버전, 2번(한국어만)은 대표 언어 대체 표시, 배지 KO/EN | titles=[(3, 'Global town hall', 'en'), (2, '정기 점검 안내', 'ko'), (1, 'System launch', 'en')] |
| ✅ PASS | 검색(en 사용자) - 대표 언어로 대체 표시되는 한국어 공지도 검색됨 |  |
| ✅ PASS | 상세 언어 전환(lang=ko) → 한국어 버전 |  |
| ✅ PASS | 조직도(인사 마스터) - 사업장 → 부서 트리, 영문 명칭 | ['Seoul Headquarters', 'Suwon Campus'] |
| ✅ PASS | 등록(한국어만, 대표 언어 자동 ko, 메일 대상 수원 사업장) → 201, 메일 READY, script 제거 | status=201, id=101 |
| ✅ PASS | 첨부 업로드(Gateway 경유 multipart) → 다운로드 내용 일치 | fileId=1, status=200 |
| ✅ PASS | tx.viewer(조회 전용) 등록 → 403 | status=403 |
| ✅ PASS | 메일 - 수원 사업장 소속(발송 시점 인사 마스터)을 수신자 언어별 그룹으로 발송, 수신자당 1건 | histories=[('partner01', 'en', 'SUCCESS'), ('tx.viewer', 'ko', 'SUCCESS')] |

메일 발송 로그 (SMTP 미설정 → 로그 대체, 스케줄러 `NOTICE_MAIL_POLL_INTERVAL=5s`):

```
[Mail] (SMTP 미설정 - 로그 대체) subject=[Notice] E2E 메일 공지 lang=en bcc=1건
[Mail] (SMTP 미설정 - 로그 대체) subject=[공지] E2E 메일 공지 lang=ko bcc=1건
[NoticeMail] noticeId=101 결과=DONE 수신자=2명
```

> local 인스턴스 2대는 H2 가 각자 분리되어 있어 "다중 인스턴스 중복 발송 방지"는 E2E 가 아닌 `NoticeMailServiceTest.noDuplicateAndScheduledSkipped`(상태 선점)로 검증했다.

## Gateway — Rate Limit (Redis Lua 토큰 버킷, IP 키: 초당 20 / 최대 40)

| 결과 | 점검 내용 | 상세 |
|---|---|---|
| ✅ PASS | 60건 연속 요청 → 버킷 소진 후 429 G0005 표준 응답 | 401(통과)=53, 429=7 |
| ✅ PASS | Redis 에 토큰 버킷 키 (request_rate_limiter.{cm.ip:...}.tokens/.timestamp) | request_rate_limiter.{cm.ip:0:0:0:0:0:0:0:1}.tokens, request_rate_limiter.{cm.ip:0:0:0:0:0:0:0:1}.timestamp |

## Gateway — 보안 - 위조 세션 쿠키 Rate Limit 우회 불가, 차단 경로, actuator 비노출

| 결과 | 점검 내용 | 상세 |
|---|---|---|
| ✅ PASS | 요청마다 다른 위조 SESSION 쿠키 60건 → IP 버킷으로 제한되어 429 발생 (우회 불가) | 429=19 |
| ✅ PASS | Gateway 차단 경로 /cm/h2-console/ → 404 | messageId=G0001 |
| ✅ PASS | Gateway 차단 경로 /cm/actuator/health → 404 | messageId=G0001 |
| ✅ PASS | Gateway 차단 경로 /cm/./h2-console → 400 | messageId=G0006 |
| ✅ PASS | Gateway 차단 경로 /cm//actuator/env → 400 | messageId=G0006 |
| ✅ PASS | Gateway 서비스 포트(9909)에 /actuator/gateway 없음 (라우트 조작 불가) | status=404 |

## 보안 — Actuator 관리 포트 분리 (localhost 전용) + Prometheus 메트릭

| 결과 | 점검 내용 | 상세 |
|---|---|---|
| ✅ PASS | spring-boilerplate 관리 포트 9191 /actuator/prometheus → 200 | status=200 |
| ✅ PASS | spring-gateway 관리 포트 9910 /actuator/prometheus → 200 | status=200 |
| ✅ PASS | spring-boilerplate 서비스 포트(9091)에는 actuator 없음 | status=404 |

## 보안 — 로그인 무차별 대입 잠금 (Keycloak brute force detection, guest01)

| 결과 | 점검 내용 | 상세 |
|---|---|---|
| ✅ PASS | 비밀번호 5회 실패 후에는 올바른 비밀번호로도 로그인 불가 (일시 잠금) | final=http://localhost:7000/realms/flowlog/login-actions/authenticate?sessio |

## Gateway — 서킷브레이커 (실제 앱 중지 → 재기동)

```
[1] 정상 (CLOSED)
  401 0.013166 "messageId":"C0009"
[2] 9091 중지 → 연속 요청
  #1 503 0.010976 "messageId":"G0002"
  #2 503 0.009942 "messageId":"G0002"
  #3 503 0.009564 "messageId":"G0002"
  #4 503 0.009092 "messageId":"G0002"
  #5 503 0.012745 "messageId":"G0002"
  #6 503 0.007112 "messageId":"G0004"
  #7 503 0.007327 "messageId":"G0004"
  #8 503 0.007364 "messageId":"G0004"
[3] 앱 재기동 (OPEN 동안 차단 유지 확인 후 HALF_OPEN 대기)
  9091 UP (재기동 완료)
  직후 요청: 401 0.070771 "messageId":"C0009"
[4] wait-duration(10s) 경과 → HALF_OPEN 시험 호출 → CLOSED
  #1 401 0.016100 "messageId":"C0009"
  #2 401 0.016048 "messageId":"C0009"
  #3 401 0.015147 "messageId":"C0009"
  #4 401 0.014712 "messageId":"C0009"
```

- 앱 중지 후 5건(최소 집계 건수)은 연결 실패 → 503 G0002, 실패율 100% 로 **OPEN** → 이후 백엔드 호출 없이 즉시 503 G0004 (~7ms).
- 재기동에 10초 이상 걸려 그 사이 HALF_OPEN 으로 전환되었고, 첫 시험 호출이 성공해 CLOSED 로 복구되었다.
- "OPEN 동안 백엔드가 살아 있어도 호출하지 않음"은 `GatewayResilienceTest.circuit_recovers` 에서 백엔드 호출 수로 검증했다.

- 보안 조치 후 재확인 (Gateway 재기동, 9091 중지): `#1~#5 503 G0002` → `#6~#8 503 G0004`,
  관리 포트 메트릭 `resilience4j_circuitbreaker_state{name="cm",state="open"} 1.0`.

## 참고

- Keycloak 26 은 http 에서도 세션 쿠키에 `Secure` 를 붙인다. 스크립트는 브라우저처럼 localhost 를 보안 컨텍스트로 취급한다.
- local `flowlog-bff` Access Token 수명은 refresh 확인을 위해 60초로 설정했다.
- 삭제 시나리오가 샘플 데이터를 변경하므로, 재실행 전 애플리케이션을 재기동하면 초기 데이터로 돌아간다.
- 무차별 대입 시나리오는 guest01 을 일시 잠근다. 연속 재실행 시 잠금이 유지될 수 있다 (점검 결과에는 영향 없음).
- realm 을 바꾼 뒤에는 `docker compose -f infra/keycloak/docker-compose.yml up -d --force-recreate` 로 재import 한다.
