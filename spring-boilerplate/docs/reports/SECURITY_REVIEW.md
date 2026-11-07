# 보안 점검 및 조치 리포트

- 점검일: 2026-11-05 (KST)
- 대상: spring-boilerplate · spring-gateway · infra (Redis, Keycloak)
- 결과: 18건 도출 → **전건 조치**. 남은 과제는 항목별 "남은 과제"와 [마지막 절](#남은-과제-요약)에 정리
- 검증: 단위/통합 테스트 boilerplate 81/81 + Oracle 1/1, gateway 48/48, 실연동 E2E 40/40, 서킷브레이커 실연동 재확인

| 등급 | 건수 | 항목 |
|---|---|---|
| 🔴 높음 | 4 | 1~4 |
| 🟠 중간 | 8 | 5~12 |
| 🟡 미비 | 6 | 13~18 |

---

## 🔴 높음

### 1. Gateway `/actuator/gateway` 무인증 노출 (라우트 추가/삭제/refresh 가능 → SSRF·트래픽 탈취)
- **조치**: Gateway Actuator 를 관리 포트 `127.0.0.1:9910` 으로 분리, 노출은 health·info·prometheus 만 (`gateway` 제거)
- **검증**: `GatewaySecurityTest` (9909 `/actuator/gateway/routes` 404, 쓰기 불가) · E2E "9909 에 /actuator/gateway 없음"
- **남은 과제**: 관리 포트는 모니터링 시스템에서만 접근하도록 네트워크 정책

### 2. 위조 SESSION 쿠키로 Rate Limit 우회 + Redis 키 폭증
- **조치**: `SessionOrIpKeyResolver` — SESSION 쿠키를 Base64 디코딩·형식 검사 후 Redis 에 **실제 존재하는 세션**일 때만 세션 키, 아니면 IP 키 (논블로킹 EXISTS, Redis 오류 시 IP)
- **검증**: `SessionOrIpKeyResolverTest`, `GatewayResilienceTest` · E2E "요청마다 다른 위조 SESSION 60건 → 429 발생"
- **남은 과제**: 없음 (세션 namespace 변경 시 `RATE_LIMIT_SESSION_KEY_PREFIX` 동기화)

### 3. Gateway 경유 H2 콘솔 노출 (localhost 제한 무력화 → `CREATE ALIAS` RCE)
- **조치**: Gateway `PathSecurityWebFilter` — 우회 형식 경로(`;`, `//`, `/./`, `/../`, `%2e/%2f/%5c/%3b/%25`, 역슬래시) 400 G0006, 차단 경로(`/cm/h2-console/**`, `/cm/actuator/**`, 대소문자 무시) 404 G0001, 백엔드 미호출
- **검증**: `GatewaySecurityTest` (우회 시도 포함, 백엔드 호출 0건) · E2E 차단 경로 4건
- **남은 과제**: 없음 (H2 콘솔은 local 프로필에서만 활성)

### 4. local 인프라 포트 전체 인터페이스 노출 (Redis 무인증 → 토큰 탈취·역직렬화 / Keycloak admin)
- **조치**: docker compose 포트를 `127.0.0.1` 바인딩. Keycloak 관리자 비밀번호 `KC_ADMIN_PASSWORD`. 앱·Gateway 에 `REDIS_USERNAME/PASSWORD/SSL` 지원. **세션 값 AES-256-GCM 암호화 + 허용목록 역직렬화** (`SessionStoreConfig`)
- **검증**: `SessionSerializationTest` 7건 (암호문 확인, 위·변조/다른 키 거부, `java.io.File` 차단) · E2E "Redis 를 직접 읽어도 JWT 원문 없음"
- **남은 과제**: Spring Security Jackson(JSON) 직렬화는 SAML 객체 복원 문제로 채택하지 않음 (암호화+허용목록으로 대체). 세션 키 교체 시 전체 재로그인. 운영 Redis ACL/TLS 는 운영 작업

## 🟠 중간

### 5. 기본 허용 인가 (`anyRequest().authenticated()`)
- **조치**: `anyRequest().denyAll()`. 로그인만 필요한 API(`/api/v1/auth/me`, `/api/v1/menus/me`)는 명시
- **검증**: `SecurityIntegrationTest.unmappedPath_deniedByDefault` (시스템관리자도 403)
- **남은 과제**: 새 API 마다 SecurityConfig 규칙 필수 (CLAUDE.md 규칙화)

### 6. 변경 가능한 `preferred_username` 으로 권한 매핑 / 서비스 계정 접두사 판별
- **조치**: `TB_USER.IDP_SUBJECT`(UK) 추가, `sub` 기준 매핑. Keycloak 사용자 ID 고정 import. SAML 은 `sub` user-property mapper. 서비스 계정은 `azp ∈ service-account-clients` + `service-account-{azp}` 일 때만
- **검증**: `usernameSpoofing_noAdmin`, `nameIdSpoofing_noAdmin`, `fakeServiceAccount_denied` · E2E SAML sub 매핑
- **남은 과제**: 운영 사용자 IDP_SUBJECT 매핑 절차(인사 I/F 또는 최초 로그인 연결) 확정

### 7. SAML 세션 권한이 로그인 시점에 고정
- **조치**: 요청마다 sub/Role 로 DB 권한 재계산 (`SessionTokenAuthorizationFilter`), `app.security.saml.max-auth-age`(1h) 경과 시 세션 폐기·재로그인
- **검증**: `storedAuthoritiesIgnored`, `samlAuthExpired`
- **남은 과제**: **SAML SLO 미구현** (SP 서명키 필요). IdP 역할 회수는 최대 1시간 뒤 반영 (DB 권한 회수는 캐시 60초 내 반영)

### 8. IdP 메타데이터·issuer 를 http 로 수신 (서명 인증서 위조 위험)
- **조치**: prod `app.security.require-https=true` → `SecurityStartupValidator` 가 issuer / SAML 메타데이터 / SP entityId https 아니면 기동 실패
- **검증**: `SecurityStartupValidatorTest` 3건
- **남은 과제**: 운영 Keycloak HTTPS 구성 (운영 작업)

### 9. LB 뒤 IP Rate Limit 이 LB IP 하나로 집계
- **조치**: `app.gateway.rate-limit.trusted-proxy-hops` (0: TCP IP, n: `XForwardedRemoteAddressResolver.maxTrustedIndex(n)`)
- **검증**: `SessionOrIpKeyResolverTest`
- **남은 과제**: 운영 LB 단수에 맞춰 hops 설정

### 10. X-Forwarded-* 신뢰 범위
- **점검 결과**: Gateway 는 비신뢰 클라이언트 헤더를 원래도 교체했으나, 신뢰 프록시(local loopback) 경로에서는 클라이언트 값이 앞에 덧붙어 백엔드가 `evil.example` / `/evil/cm` 을 사용할 수 있었음. 백엔드는 호출자 구분 없이 신뢰
- **조치**: Gateway `ForwardedHeaderSanitizerGlobalFilter` + `host-append/prefix-append=false` (Host/Prefix 단일 값). 백엔드 `TrustedForwardedHeaderFilter` — `app.security.trusted-proxies` 에서 온 요청만 반영 (`forward-headers-strategy=none`)
- **검증**: `GatewayRoutingTest`, `GatewayUntrustedProxyTest`, `SecurityIntegrationTest` Forwarded 2건 (redirect_uri 조작 불가)
- **남은 과제**: 운영 LB 가 클라이언트 `X-Forwarded-*` 를 덮어쓰도록 설정, 백엔드는 Gateway 에서만 접근 (운영 작업)

### 11. 운영 설정의 안전하지 않은 기본값 (client secret 기본값, 쿠키 Secure=false)
- **조치**: 시크릿·세션 키 기본값은 `application-local.yml` 에만. 공통 쿠키 `secure` 기본 true (local 만 false). prod 기동 검사에서 local 기본값 사용 시 실패
- **검증**: `SecurityStartupValidatorTest` (local 기본 시크릿·키, Secure 아닌 쿠키 → 기동 실패)
- **남은 과제**: 없음

### 12. Keycloak realm 보안 설정 부재
- **조치**: `bruteForceProtected`(5회, 60초~15분 잠금), `sslRequired=external`, `editUsernameAllowed=false`
- **검증**: E2E "5회 실패 후 올바른 비밀번호로도 로그인 불가"
- **남은 과제**: 운영 realm(`sslRequired=all`, production 모드, 외부 DB, 테스트 사용자 제외) 별도 구성. local 반복 E2E 시 guest01 잠금 유지 가능

## 🟡 미비

### 13. 권한 체계 이원화 (DB 권한·커스텀 권한이 API 에 미반영)
- **조치**: **API 권한 = IdP scope ∩ DB 업무 권한**. DB 권한은 `TB_ROLE_MENU` 플래그 × `TB_MENU.PERM_CD` (`AuthorityResolver`, `UserAuthorityService`)
- **검증**: `userToken_scopeIntersectsDbPermission`, `customReadOnlyRole_limitsScope` · E2E 사용자별 scope
- **남은 과제**: Keycloak 역할과 DB 권한을 함께 관리해야 함 (Keycloak = 상한, DB = 업무 권한). 자동 동기화는 미구현

### 14. 메뉴 권한 미사용
- **조치**: `GET /api/v1/menus/me` — 유효 권한 메뉴 트리, 다국어, 메뉴별 list/add/edit/del, 상위 권한 없으면 하위 미노출
- **검증**: `SecurityIntegrationTest` 메뉴 3건 · E2E 메뉴 API
- **남은 과제**: 메뉴/권한 관리(등록·수정) API

### 15. 동시 refresh 경합 (rotation 시 세션 강제 종료)
- **조치**: 세션 단위 refresh 직렬화(striped lock), Redis 에 더 새로운 토큰이 있으면 채택, 갱신 실패 시 Redis 재조회, `flush-mode: immediate`
- **검증**: E2E refresh 시나리오 (토큰 암호문 교체, 200)
- **남은 과제**: 인스턴스 간 동시 refresh 는 실패 후 재조회로 완화 (분산 락 미사용). Keycloak refresh rotation 활성화 시 부하 상황 재검증

### 16. 매 요청 DB 권한 조회
- **조치**: Caffeine 캐시 (`userAuthority`, 60초, 최대 10,000)
- **검증**: 통합 테스트 / E2E 정상
- **남은 과제**: 권한 변경 API 도입 시 캐시 즉시 무효화

### 17. 검증 공백 (Oracle 미검증, CI 없음, 미커밋)
- **조치**: `OracleSchemaTest` (`-Poracle-it`, Oracle Free 컨테이너) — **H2 에서 통과하던 `ALTER SEQUENCE ... RESTART WITH` 가 Oracle 에서 ORA-03048** → `RESTART START WITH` 수정. 두 저장소 GitHub Actions CI 추가 (boilerplate 는 build + oracle 잡)
- **검증**: Oracle 1/1, `ddl-auto=validate` 통과
- **남은 과제**: 원격 저장소 연결 후 CI 실제 실행

### 18. 운영 관측성 부족
- **조치**: Micrometer Prometheus (관리 포트), Resilience4j 서킷 메트릭, `gateway.ratelimit.rejected{route}` 카운터, 인증 감사 로그 `AUDIT`(로그인 성공/실패, 로그아웃, 401/403 → `*-audit.log`)
- **검증**: E2E 관리 포트 prometheus 200, 서킷 OPEN 시 `resilience4j_circuitbreaker_state{state="open"} 1`
- **남은 과제**: 대시보드/알람, 감사 로그 수집

---

## 추가 점검 (2026-11-06, 화면 연동 중 발견)

### 19. 미인증 요청마다 Redis 세션 생성 (🟠 중간)
- **현상**: 401 응답 시 요청 캐시(`HttpSessionRequestCache`)가 요청을 세션에 저장 → 익명 요청마다 SESSION 쿠키 + Redis 세션 생성.
  Redis 키 증가, 그리고 Gateway Rate Limit 은 "Redis 에 실존하는 세션 = 세션 버킷" 이므로 익명으로 받은 세션 쿠키를 돌려 쓰면 IP 버킷을 우회할 여지
- **조치**: `requestCache(NullRequestCache)` — 로그인 후 이동은 `returnTo`(검증된 같은 호스트 경로) / `success-url`
- **검증**: `BrowserCookieWebTest.anonymousRequest_noSession`, 실연동 `curl /cm/api/v1/langs` → Set-Cookie 에 SESSION 없음

### 20. 로그인 후 복귀 경로 (Open Redirect 방지)
- **조치**: `LoginReturnUrlFilter` — `returnTo` 는 `/` 로 시작하는 같은 호스트 경로만 (`//`, 역슬래시, 허용 외 문자, 500자 초과 거부)
- **검증**: `SecurityIntegrationTest.loginStart_unsafeReturnUrlIgnored`, 화면 E2E (OIDC/SAML 로그인 후 원래 화면 복귀)

### 21. LIKE 검색 이스케이프
- **현상**: 샘플 이름 검색이 local 에서 항상 0건 (Hibernate H2 Dialect 가 `escape ''` 추가 → H2 Oracle 모드에서 NULL). 사용자 입력 `%`·`_` 도 와일드카드로 동작
- **조치**: `LikePatterns.contains` (와일드카드 이스케이프) + 쿼리에 ESCAPE 명시 (공지 검색과 같은 방식)
- **검증**: `SampleApiIntegrationTest.getSamples_nameSearch`, `OracleSchemaTest` (실제 Oracle)

### 22. 관리 API 의 권한 상승 (관리 기능 추가 시 점검)
- **위험**: 권한 관리 권한(`role.edit`)으로 자신에게 더 높은 권한을 부여 → 시스템관리자 획득
- **조치**: 시스템관리자 권한(레벨 1 / 유형 SYSTEM_ADMIN)의 생성·수정·메뉴 접근·부여는 요청자가 시스템관리자일 때만 (B6004). 관리 API 도 IdP scope(`role.*`) ∩ DB 메뉴 권한, 기본 거부 규칙 유지
- **검증**: `AdminApiIntegrationTest.delegatedAdmin_cannotTouchSysAdmin`
- **남은 과제**: `role.edit` 보유자는 자기 권한에 다른 메뉴 접근을 줄 수 있어 사실상 관리자 → 시스템관리자 외 부여 비권장(설계 01 6-1). 관리 변경 이력(감사 로그) 미구현

## 추가 점검 (2026-11-07, 생일·교회·영속 DB 추가분 - 첨부·업로드 중심)

대상 : 공지 첨부, 주보 대표사진 업로드, 주보 PDF 생성, 교회(주보·교리문답·전례문)·생일 API, 영속 DB(Oracle 볼륨).
7건 도출 → 전건 조치. 검증 : 기본 188/188 (보안 테스트 9건 추가), 화면 E2E 70/70, 실데이터 저장 사진 정리 확인

### 23. 예약(게시 전) 공지의 첨부를 누구나 내려받음 (🔴 높음)
- **현상**: 공지 상세는 게시일시 전이면 관리 권한자만 보지만, 첨부 다운로드(`GET /notices/{id}/files/{fileId}`)는 삭제 여부만 확인 →
  board.list 만 있으면 ID 를 바꿔 가며 공개 전 자료를 받을 수 있었음 (파일 ID 는 순번)
- **조치**: 다운로드도 상세와 같은 규칙 - 게시 전 공지는 관리 권한자(board.update)만, 아니면 B2001(404)
- **검증**: `NoticeApiIntegrationTest.scheduledNoticeFileHidden`

### 24. 등록 권한만으로 남의 게시물에 파일을 붙이거나 바꿈 (🟠 중간)
- **현상**: 첨부 등록은 board.write 또는 board.update, 주보 사진은 bulletin.add 또는 bulletin.edit 로 열어 두어
  쓰기(등록) 권한만 있는 사용자가 수정 권한 없이 **다른 사람의 공지에 파일을 추가**하거나 **다른 주보의 대표사진을 교체**할 수 있었음
- **조치**: 수정 권한(board.update / bulletin.edit, 시스템관리자)이 없으면 **본인이 등록한 게시물만** (CREATED_BY = 요청자) - B2017 / B8015 (403)
- **검증**: `NoticeApiIntegrationTest.writerCannotAttachToOthersNotice`, `ChurchApiIntegrationTest.photoOwnership`
- **남은 과제**: 소유 판단은 감사 컬럼(CREATED_BY = 로그인 ID). Keycloak 에서 사용자명을 바꾸면 이전 게시물 소유가 끊긴다

### 25. 사진의 촬영 위치(EXIF GPS)·기기 정보 노출 (🟠 중간)
- **현상**: 휴대폰 사진을 그대로 저장·제공 → 주보를 보는 모든 사람, PDF 를 받은 사람이 촬영 위치(GPS)·기기 정보를 볼 수 있음.
  실데이터 점검 시 저장된 사진 2장 모두 EXIF 포함
- **조치**: `ImageNormalizer` 로 **다시 그려 저장** - 메타데이터(EXIF·XMP·주석·PNG 텍스트) 제거, 회전값은 픽셀에 반영(세로 사진 유지),
  결과는 JPEG/PNG. 이미 저장된 사진은 기동 시 `BulletinPhotoMaintenance` 가 정리 (메타데이터 없는 사진은 건너뜀 - 2건 정리, 원본 삭제 확인)
- **검증**: `ChurchApiIntegrationTest.photoStripsMetadata` (GPS 문자열·Exif 사라짐, 8x4 → 4x8 회전), `UploadSecurityTest`

### 26. 이미지 해상도 폭탄 (🟠 중간)
- **현상**: 수 KB 파일이 2만 x 2만 화소를 주장하면 화면·PDF 생성(이미지 디코딩) 때 수 GB 메모리 → 서버 장애
- **조치**: 픽셀을 풀기 전에 헤더의 가로·세로로 **최대 4천만 화소**(B8014), 저장 시 긴 변 2560px 로 축소. 손상된 이미지(CRC·압축 오류)도 거부
- **검증**: `ChurchApiIntegrationTest.photoRejectsPixelBomb`, `UploadSecurityTest.limits`

### 27. 확장자만 바꾼 위장 파일 (🟡 미비)
- **현상**: 첨부는 확장자 허용 목록만 확인 → 실행 파일·HTML 을 `.pdf`·`.png` 로 이름만 바꿔 올리면 통과 (내려받은 사람이 다른 프로그램으로 열 위험)
- **조치**: `FileSignatures` - 내용이 확장자와 맞아야 등록 (PDF `%PDF-`, Office/한글 OOXML·HWPX = ZIP, 구 Office·HWP = OLE, 이미지 시그니처, txt/csv 는 NUL 없음), B2016.
  사진은 시그니처 확인 후 다시 그려 저장하므로 이미지에 끼운 HTML·스크립트(폴리글랏)도 사라짐. 응답은 기존대로 attachment + nosniff
- **검증**: `NoticeApiIntegrationTest.rejectDisguisedFile`, `UploadSecurityTest.signatures`
- **남은 과제**: 악성 코드 검사(백신 연동)는 미구현 - 외부 공개 시 ClamAV 등 연동 권장. ZIP 은 내용 검사 없이 그대로 보관(서버가 풀지 않음)

### 28. PDF 생성 자원 고갈 (🟠 중간)
- **현상**: 주보 PDF 는 단마다 맞춤 렌더링(최대 수십 회)으로 CPU 사용이 커서, 조회 권한만으로 연속 요청 시 서버가 느려짐
- **조치**: 동시 생성 2건(공정 대기 20초, 초과 시 B8016/429). Gateway Rate Limit(초당 20, 순간 40)은 그대로 적용
- **검증**: 코드 리뷰 + PDF 테스트 (`ChurchApiIntegrationTest.pdf`, `pdfFitsLongContent`)

### 29. PDF 렌더러의 외부 자원 접근 (🟡 미비, 방어 심화)
- **현상**: 사용자 입력은 모두 이스케이프해 현재 경로는 없으나, 렌더러는 기본적으로 `file:`·`http:` 자원을 읽을 수 있음 (SSRF·로컬 파일 노출 잠재 위험)
- **조치**: `useExternalResourceAccessControl` 로 `data:` URI(대표사진)만 허용 (URI 해석 전·후 모두)
- **검증**: 코드 리뷰, PDF 테스트

### 점검했으나 이상 없음
- 첨부 경로 조작 (저장 키는 서버 UUID, 저장소 밖 경로 거부), 다운로드 파일명 (제어문자 제거 + RFC 5987)
- 사진 응답 Content-Type 은 서버가 판별한 형식 + nosniff, SVG 불허. 작성 중 주보의 사진·PDF 는 편집 권한자만
- 주보·교리문답·전례문·생일 화면 : 사용자 입력은 텍스트 보간만 (v-html 없음), PDF 는 XML 이스케이프
- 영속 DB : 1521 은 localhost 에만, 비밀번호 기본값은 local 전용. 개인정보 이관 파일·실주보 PDF 캡처는 gitignore
- 업로드 요청은 CSRF(세션) 적용, 크기 제한 (파일 20MB / 요청 100MB, 사진 10MB)

## 추가 점검 (2026-11-07, 운영 배포 구성 - EC2 + ALB)

운영 구성(ALB TLS 종료 → EC2:80)을 로컬에서 그대로 재현(Caddy 가 ALB 역할)해 검증하며 도출. 상세 런북 [EC2_DEPLOY_RUNBOOK](../operations/EC2_DEPLOY_RUNBOOK.md) 9장

### 30. TLS 종료 LB 뒤에서 Gateway 의 `Forwarded` 헤더 (🟠 중간)
- **현상**: Gateway 가 RFC 7239 `Forwarded: proto=http` 를 백엔드에 보내고, 백엔드(ForwardedHeaderFilter)는 이를 `X-Forwarded-Proto: https` 보다 우선 →
  ALB 뒤에서 로그인 콜백이 `http://` 로 만들어져 로그인 불가. 또 신뢰 프록시(LB) 경로에서 클라이언트가 보낸 `Forwarded` 가 그대로 전달되면 host·proto 위조 가능
- **조치**: Gateway `forwarded.enabled: false` + `ForwardedHeaderSanitizerGlobalFilter` 가 `Forwarded` 를 출처와 무관하게 항상 제거 (X-Forwarded-* 만 사용)
- **검증**: `GatewayRoutingTest.forwardedHeaders_tlsTerminatingLoadBalancer`, `forwardedHeaders_spoofedFromTrustedProxy`, 운영 구성 E2E 70/70

### 31. 운영 기본 하드닝 (🟡 미비 → 조치)
- **조치**: EC2 - IMDSv2 필수·홉 제한 1(컨테이너의 역할 자격 증명 탈취 차단), SSH 미개방(Session Manager), EBS 암호화, 보안 패치 자동(dnf-automatic).
  컨테이너 - 비루트, `no-new-privileges`(데몬·compose), 로그 크기 제한, 내부망 전용 포트. Keycloak - 운영 client secret·콜백 교체, 테스트 계정 비활성화,
  **비밀번호 정책**(12자 이상·사용자명/이메일 금지·최근 3개 재사용 금지), admin 비밀번호는 첫 기동 때만 설정
- **검증**: 운영 구성 기동(no-new-privileges 상태에서 전 서비스 healthy), 정책 위반 비밀번호 거부 확인, E2E 70/70
- **남은 과제**: WAF·GuardDuty·CloudWatch 경보는 AWS 콘솔 작업 (런북 9-7)

---

## 남은 과제 요약

| 구분 | 내용 |
|---|---|
| 미구현 (설계 판단) | OIDC back-channel logout — BFF 는 refresh 시점(≤ Access Token 수명)에 Keycloak 세션 종료를 감지 |
| 미구현 (설계 판단) | SAML SLO — max-auth-age 1h + 요청마다 DB 재계산으로 완화 |
| 대체 | JSON 세션 직렬화 미채택 → AES-GCM + 허용목록 JDK 직렬화 |
| 운영 | 세션 키 교체 = 전체 재로그인, 인스턴스 간 동일 키 |
| 운영 | 운영 realm 구성, LB 헤더 정리, Redis ACL/TLS, 관리 포트 접근 제한, IDP_SUBJECT 매핑 절차 |
| local | E2E 무차별 대입 시나리오가 guest01 을 일시 잠금 |
| 미구현 | 첨부 악성 코드 검사(백신 연동) - 외부 공개 시 권장 (27) |
| 운영 | 게시물 소유 = CREATED_BY(로그인 ID) - IdP 사용자명 변경 시 소유 끊김 (24) |
