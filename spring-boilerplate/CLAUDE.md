# CLAUDE.md

Spring Boot 4.1 / Java 17 / Maven API 서버 (`com.flowlog.backend`).
상세 규칙은 `docs/DEVELOPMENT_GUIDE.md` 가 기준이며, 아래는 작업 시 반드시 지킬 핵심 요약이다.

## Commands
- 빌드/테스트: `./mvnw test` (Windows: `mvnw.cmd test`)
- 실행 (local, H2): `./mvnw spring-boot:run` → :9091 (직접). 외부 진입점은 `../spring-gateway` (:9909, `/cm/**` → 9091, `/cm` 제거)
- Swagger: `http://localhost:9909/cm/swagger-ui.html` (Gateway) / `http://localhost:9091/swagger-ui.html` (직접), H2 `http://localhost:9091/h2-console` (Gateway 는 차단)
- Actuator 는 관리 포트 `127.0.0.1:9191` (health, info, prometheus). 서비스 포트·Gateway 로 노출 금지
- Gateway 뒤에서 동작: `TrustedForwardedHeaderFilter` 가 `app.security.trusted-proxies`(Gateway IP)에서 온 X-Forwarded-* 만 반영 (`server.forward-headers-strategy=none` 유지). 앱에서 절대 URL 을 하드코딩하지 말 것
- 작업 후 항상 `./mvnw test` 통과 확인. DDL/초기데이터/Entity 변경 시 `./mvnw test -Poracle-it` (실제 Oracle, Docker 필요)
- 노션: 저장소 docs 가 원본, `scripts/notion-sync` 로 동기화 (노션에서 직접 고치지 말 것, 새 문서는 pages.json 에 매핑)
- 전체 실행: `scripts/dev-up.ps1` (Windows) / `scripts/dev-up.sh` → 종료 `dev-down`. 포트 충돌 시 시작 안 함
- 인프라: `docker compose -f infra/redis/docker-compose.yml up -d` (:6379), `docker compose -f infra/keycloak/docker-compose.yml up -d` (:7000)
- 인증 E2E: `python infra/keycloak/e2e/auth_e2e.py` (Gateway·앱(9091)·두 번째 인스턴스(9092) 실행 상태에서, `--quick` 은 refresh 대기 생략)

## 구조
- 공통: `global/{aop,common,component,config,constant,exception,interceptor,security,util,web}`
- 도메인: `{domain}/web`, `{domain}/exception`, `{domain}/domain/{model,repository,service/dto}`
- 신규 도메인은 `sample` 패키지 구조/코드 스타일을 그대로 따른다.
- 도메인: `sample`, `auth`, `code`(공통코드·지원 언어 API, 공통코드 관리 `code-groups`), `user`(인사 마스터 조직도·사용자 언어·사용자 검색), `role`(권한 관리: 권한·메뉴 접근·부여), `menu`(메뉴 관리, 본문 메뉴), `notice`(공지 다국어·첨부·메일), `mail`(메일 템플릿·발송). 공지 설계 `docs/design/03_notice.md`

## 필수 규칙
- 응답은 항상 `MessageHttpResponse` 로 생성 → `ResponseEntity<MessageResponse<T>>`
  (locale, timestamp, httpStatus, messageId, messageTitle, messageContent, path, result)
- 목록 조회는 `PageResponse` + `messageHttpResponse.successPage()`
- 오류는 `throw new BusinessException({Domain}ErrorCode.XXX, args)`. 오류 코드는 도메인별 enum (`ErrorCode` 구현)
- 새 messageId 는 `messages.properties`, `messages_ko_KR.properties`, `messages_en_US.properties` 세 곳에 모두 추가
- Controller/Service 클래스에 `@Logging`, Service 는 `@Service("beanName")` + `@Transactional(readOnly = true)`
- 생성자 주입(`private final` + `@RequiredArgsConstructor`). 필드 주입 금지
- DTO 는 record (`XxxCreateRequest`, `XxxUpdateRequest`, `XxxSearchRequest`, `XxxResponse`), Validation 은 DTO 에
- Entity: `@Setter`/`@Data` 금지, 시퀀스 PK(`SQ_테이블`), 테이블 `TB_`, 컬럼 대문자 snake, FK 컬럼 직접 매핑
- Oracle 호환 유지 (H2 Oracle 모드). Oracle 예약어 금지
- LIKE 검색: `LikePatterns.contains(입력)` + 쿼리에 `ESCAPE '\'` 명시 (생략 시 H2 Oracle 모드에서 0건). 예: `SampleRepository.search`
- 영속성: 기본 JPA. DB 종속/복잡 조회(동적 조건, CLOB 검색, 통계)는 MyBatis (`@Mapper` 인터페이스 + `resources/mapper/{domain}/*.xml`, 결과 DTO record, 조회 전용). 예: `NoticeSearchMapper`
- 스키마 기준은 `src/main/resources/db/ddl/*.sql` (Oracle DDL). 테이블 추가 = DDL 파일 + `application-local.yml` schema/data-locations 등록 + `OracleSchemaTest` 의 `spring.sql.init.*-locations` 에도 등록 + Entity + 테스트. local 초기 데이터는 `db/data/*.sql`. 시퀀스 재설정은 Oracle 문법 `ALTER SEQUENCE ... RESTART START WITH n`
- 설계 문서는 `docs/design/` (ERD, 컬럼 정의). 자연키/복합키 Entity 는 `AssignedIdEntity` 상속 + `@EmbeddedId`
- 코드 스타일: Google Java Style, 2칸 들여쓰기, 100자, 와일드카드 import 금지, switch default 필수
- `java.time` 만 사용, null 체크는 Apache Commons Utils
- 민감정보(계정/시크릿)는 코드·설정·문서에 쓰지 않고 환경변수로. 기본값은 `application-local.yml` 에만 (공통 yml 에 시크릿 기본값 금지). prod 는 `app.security.require-https=true` 로 기동 검사(`SecurityStartupValidator`)
- 테스트: given-when-then, `@DisplayName` 한글. Service 단위 테스트 + API 통합 테스트
- 화면(`../vue-frontend`)은 Gateway `/**` 로 서비스 (API 와 같은 Origin). 로그인 `?returnTo=` (`LoginReturnUrlFilter`, 같은 호스트 절대 경로만), CSRF 쿠키 경로 `/` + 첫 요청 발급(`CsrfCookieFilter`), 요청 캐시 미사용(익명 요청이 세션을 만들지 않게). `/auth/me` 에 표시 언어(`langCd`)
- 인증: 브라우저는 **BFF**(oauth2Login, 토큰은 Redis 세션에만, 클라이언트는 SESSION 쿠키만) 또는 SAML2 세션. 서버 간은 Client Credentials Bearer. **토큰 값을 응답/로그/쿠키에 절대 노출하지 말 것**
- 인가: API 는 `SCOPE_{리소스}.{list|add|edit|del}` (+ `ROLE_SYS_ADMIN`). 동사 동의어 `write`=add, `update`=edit, `delete`=del 허용(`ScopeActions`, 예: 공지사항 API = board.list/write/update/delete) - 권한 이름은 scope 그대로. 경로·메서드별 규칙은 `ApiAuthorizationRules` 규칙표 (SecurityConfig 에 직접 나열하지 말 것). **SCOPE = IdP scope ∩ DB 권한** (`AuthorityResolver` / `UserAuthorityService`, 캐시 60s). DB 권한 = 유효 권한의 `TB_ROLE_MENU` 플래그 × `TB_MENU.PERM_CD`
- **기본 거부** (`anyRequest().denyAll()`). 새 API = `ApiAuthorizationRules` 규칙 + 메뉴 `PERM_CD`/`TB_ROLE_MENU` + Keycloak 같은 이름 역할/client scope(setup.sh) + BFF scope(application.yml). 누락은 `ScopeConsistencyTest` 가 잡는다. 상세 `docs/design/02_auth_keycloak.md`
- 사용자 매핑은 `sub`(Keycloak user id = `TB_USER.IDP_SUBJECT`) 기준. `preferred_username`/NameID 로 권한을 매핑하지 말 것. 서비스 계정은 `app.security.service-account-clients` 의 azp 만
- 세션: Spring Session Redis (`spring.session.data.redis.*` - Boot 4 속성명), flush-mode immediate. 값은 AES-256-GCM 암호화 + 허용목록 JDK 직렬화(`SessionStoreConfig`, 키 `SESSION_ENCRYPTION_KEY`). 세션에 넣는 객체는 Serializable 이고 허용목록 패키지(`java.lang/util/time`, `org.springframework.security/session`, `com.flowlog.backend`) 안에 있어야 함
- 인증 감사 로그는 logger `AUDIT` (`AuthAuditLogger`: 로그인 성공/실패, 로그아웃, 401/403). 토큰·비밀번호 기록 금지
- **다국어**: 언어는 공통코드 `LANG`(ko, en … USE_YN='Y', ATTR1 = 에디터 로케일 - 본문 lang 속성)만 기준. 언어 목록/코드를 하드코딩하지 말고 `LangService`(`activeLangs`, `isActive`, `defaultLang`, `resolveUserLang`)를 사용. 사용자 언어 = `TB_USER.LANG_CD` → `Accept-Language` → 기본 언어. 데이터 언어코드는 `ko`/`en`(응답 `locale` 필드는 메시지 Locale 로 `ko_KR` 형식 유지)
- 관리 API(`code.*`/`menu.*`/`role.*`, 설계 01 6-1): 언어별 이름은 `LangService.normalizeNames`, 권한·메뉴 변경은 `@CacheEvict(userAuthority, allEntries)`, 공통코드 변경은 `codeItems` 캐시 삭제 (커밋 후 Redis Pub/Sub 로 모든 인스턴스에 전파 - `BroadcastingCacheManager`). 권한 부여·수정·회수는 `TB_ROLE_GRANT_HIST` 에 이력. 언어별 행 저장은 기존 행 수정 + 빠진 사용 중 언어만 삭제(같은 PK 삭제→삽입은 flush 순서로 충돌). 시스템관리자 권한(레벨 1) 변경·부여는 시스템관리자만
- 인사 마스터(사업장/부서) 명칭은 업무 테이블에 저장하지 말고 코드만 저장, 조회 시 `OrgService` 로 (ko = 국문, 그 외 = 영문명)
- 사용자 입력 HTML(에디터 본문)은 반드시 `HtmlSanitizer.sanitize` 후 저장, 메일 템플릿 치환값은 `HtmlSanitizer.escape`. 첨부는 `NoticeFileService` 방식(`FileStorage` - local 디렉터리 / db BLOB, 확장자 허용목록, 저장소 밖 경로 거부). 다중 인스턴스는 `storage-type=db` 또는 공유 디렉터리
- 현재 시각은 `Clock` Bean 주입 (`LocalDateTime.now(clock)`)
- API 통합 테스트는 `springSecurity()` + `jwt()`/`authentication()` post-processor 로 인증을 붙인다 (SampleApiIntegrationTest 참고). 세션 기반 변경 요청은 `csrf()` 필요
