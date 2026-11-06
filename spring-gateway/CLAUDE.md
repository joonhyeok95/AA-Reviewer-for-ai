# CLAUDE.md

Spring Cloud Gateway (Server **WebFlux**, Netty) / Spring Boot 4.1 / Java 17 / Maven. 포트 9909 (관리 9910, 127.0.0.1).
백엔드 `../spring-boilerplate` (9091) 앞단. 코드 스타일·규칙은 `../spring-boilerplate/CLAUDE.md` 와 동일 (Google Java Style, 2칸, 100자).

## Commands
- 테스트: `./mvnw test` / 실행: `./mvnw spring-boot:run`
- 통합 확인: Keycloak + spring-boilerplate + gateway 실행 후 `python ../spring-boilerplate/infra/keycloak/e2e/auth_e2e.py` (기본 Gateway 경유)
- 메트릭: `curl http://127.0.0.1:9910/actuator/prometheus`

## 규칙
- 리액티브 환경: 필터/핸들러에서 블로킹 호출 금지 (JDBC, Thread.sleep, block()). Redis 조회도 Reactive 템플릿으로.
- 라우트는 `application.yml` 의 `spring.cloud.gateway.server.webflux.routes` 에 정의. 신규 서비스 = 라우트 추가 (`Path=/{prefix}/**` + `StripPrefix=1`) + 그 서비스의 내부 전용 경로(H2 콘솔, actuator 등)를 `app.gateway.blocked-paths` 에 추가.
- `trusted-proxies` 가 없으면 X-Forwarded-* 가 추가되지 않는다 → 백엔드 SAML/Swagger URL 이 깨짐. 제거 금지.
- `x-forwarded.host-append / prefix-append: false` 유지 (true 면 신뢰 프록시 경로에서 위조 Host/Prefix 가 백엔드까지 전달됨). `ForwardedHeaderSanitizerGlobalFilter` 제거 금지.
- 화면 라우트 `web` (`/**`, order 100, `WEB_SERVICE_URI`) 는 맨 마지막. Rate Limit/서킷 미적용, `/actuator/**` 제외(predicate 정규식). 새 API 라우트는 `web` 보다 앞(기본 order 0)에 둔다. 화면·API 같은 Origin 유지 (CORS 추가 금지)
- Actuator 는 관리 포트(9910)에서만. `gateway` 엔드포인트(라우트 쓰기 기능)는 노출 금지.
- `PathSecurityWebFilter` 는 라우팅 전에 우회 형식 경로(400 G0006)와 차단 경로(404 G0001)를 거른다. 경로 정규화로 우회를 "허용"하도록 바꾸지 말 것 (거부가 원칙).
- Rate Limit 키는 Redis 에 실존하는 세션만 세션 버킷 (`SessionOrIpKeyResolver`). 쿠키 값만 보고 키를 만들지 말 것 (위조 쿠키로 우회됨).
- Gateway 자체 오류는 `GatewayErrorHandler`, 서킷 fallback 은 `FallbackController`, 한도 초과는 `RateLimitGatewayFilterFactory` 가 `GatewayResponseWriter` 로 표준 응답(G0001~G0006/E0001) 반환. 새 messageId 는 messages 3개 파일 모두에 추가.
- 라우트 필터 순서: StripPrefix → RateLimit(Redis Lua) → CircuitBreaker(args 형식으로 fallbackUri 지정. shortcut `CircuitBreaker=name,uri` 는 fallback 이 적용되지 않음).
- Redis 필요 (Rate Limit). 테스트는 Testcontainers(`RedisTestContainerConfig`)로 실제 Redis 사용 → Docker 필요.
- 테스트는 `GatewayRoutingTest` 처럼 실제 Gateway + 스텁 백엔드로 "백엔드가 받은 요청"을 검증. 라우트 속성은 리스트라 테스트에서 덮어쓸 때 라우트 전체를 정의할 것. 경로 우회 테스트는 `RawHttp`(소켓)로 원본 경로를 그대로 보낸다.
