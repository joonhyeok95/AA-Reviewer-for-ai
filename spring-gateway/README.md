# spring-gateway

Flowlog API Gateway — Spring Cloud Gateway **Server WebFlux** (Netty, 비동기 논블로킹)

| 항목 | 내용 |
|---|---|
| 버전 | Java 17 / Spring Boot 4.1.2 / Spring Cloud 2025.1.4 (Gateway 5.0.4, CircuitBreaker Resilience4j) |
| 포트 | **9909** (서비스) / **9910** (관리: actuator, 기본 127.0.0.1 바인딩) |
| 패키지 | `com.flowlog.gateway` |
| 상태 | 테스트 51/51 (실제 Redis 컨테이너) · 실연동 E2E Rate Limit / 서킷브레이커 확인 · 화면 E2E 35/35 |

## 구성

```
 Client ──▶ spring-gateway :9909 ───────────────(/cm 제거)──────────────▶ spring-boilerplate :9091
            /cm/**
            ⓪ PathSecurityWebFilter  우회 형식 경로 400 G0006 / 차단 경로(H2 콘솔·Actuator) 404 G0001
            ① TraceIdGlobalFilter    X-Trace-Id 생성/전파 + 접근 로그
            ② ForwardedHeaderSanitizer  클라이언트가 보낸 Forwarded/X-Forwarded-* 정리
            ③ StripPrefix=1
            ④ RateLimit              Redis 토큰 버킷 (Lua 스크립트, 원자적) - 실존 세션/IP 단위
            ⑤ CircuitBreaker         Resilience4j - 장애 시 OPEN → 즉시 fallback
                     │
                     ▼
               Redis :6379  request_rate_limiter.{route.key}.tokens / .timestamp

 Client ──▶ spring-gateway :9909 ── 그 외 /** (경로 그대로, ⓪①② 만) ──▶ 화면 vue-frontend (local Vite :5173 / 운영 nginx)
```

| 라우트 | Predicate | Filters | 대상 |
|---|---|---|---|
| `cm` | `Path=/cm/**` | `StripPrefix=1`, `RateLimit`, `CircuitBreaker(cm → forward:/fallback/cm)` | `${CM_SERVICE_URI:http://localhost:9091}` |
| `web` (order 100) | `Path=/`, `/{first:(?!(?i:actuator)$).+}/**` | 없음 (경로 그대로, Rate Limit·서킷 미적용) | `${WEB_SERVICE_URI:http://localhost:5173}` — 화면 [vue-frontend](../vue-frontend) (local Vite / 운영 nginx) |

> 화면과 API 가 **같은 Origin(9909)** 이므로 SESSION·XSRF-TOKEN 쿠키를 그대로 쓰고 CORS 가 없다. 화면 라우트는 `/actuator/**` 를 받지 않는다 (→ 404 G0001). Vite HMR 웹소켓도 이 라우트로 전달된다.

> RateLimit 을 CircuitBreaker 앞에 둔다. 한도 초과(429) 요청이 서킷 실패율에 섞이지 않도록 하기 위함.

## 보안

| 항목 | 내용 |
|---|---|
| Actuator 분리 | 관리 엔드포인트는 **별도 포트 9910, 127.0.0.1** 에서만. 노출: `health, info, prometheus`. `/actuator/gateway`(라우트 추가·삭제·refresh 쓰기 기능)는 **노출하지 않음** → 서비스 포트에서 404 |
| 경로 차단 | `app.gateway.blocked-paths` (기본 `/cm/h2-console/**`, `/cm/actuator/**`, 대소문자 무시) → 백엔드 호출 없이 404 G0001. Gateway 를 거치면 백엔드 입장의 접속지가 내부가 되어 H2 콘솔의 "localhost 전용" 보호가 무력화되기 때문 |
| 경로 우회 차단 | 원본 경로에 `;` `//` `/./` `/../` `%2e` `%2f` `%5c` `%3b` `%25` → 400 G0006 (Gateway 는 정규화하지 않고 백엔드(Tomcat)는 정규화하는 차이를 이용한 우회 방지). 역슬래시는 Netty 가 400 으로 거부 |
| Forwarded 헤더 | 신뢰 프록시(`trusted-proxies`)가 아닌 곳에서 온 `Forwarded`/`X-Forwarded-*`/`X-Real-IP` 제거. `X-Forwarded-Prefix` 는 항상 제거 후 Gateway 가 `/cm` 설정. Host/Prefix 는 append 하지 않고 덮어씀 (`host-append`, `prefix-append: false`) |
| Rate Limit 우회 방지 | SESSION 쿠키는 Redis 에 **실제 존재하는 세션일 때만** 세션 버킷 사용. 위조·만료 쿠키는 IP 버킷 → 쿠키를 바꿔 가며 한도를 피하거나 Redis 키를 무한 생성하는 공격 차단 |

### Forwarded 헤더 점검 결과 (클라이언트가 `X-Forwarded-Host: evil.example`, `X-Forwarded-Prefix: /evil`, `Forwarded: host=evil.example` 전송)

| 경로 | 수정 전 (백엔드가 받은 값) | 수정 후 |
|---|---|---|
| 비신뢰 클라이언트 | Gateway 기본 동작으로 이미 제거됨 (Gateway 값만) | 동일 + `X-Real-IP` 도 제거 |
| 신뢰 프록시 (local 기본: loopback) | `X-Forwarded-Host: evil.example,localhost:9909`, `X-Forwarded-Prefix: /evil,/cm`, `X-Forwarded-Proto/Port` 도 append → 백엔드 `ForwardedHeaderFilter` 는 prefix 를 이어 붙여 **`/evil/cm`** 기준으로 URL 생성 (`Forwarded` 는 Gateway 값으로 교체되어 Host 는 영향 적음) | `X-Forwarded-Host: localhost:9909`, `X-Forwarded-Prefix: /cm` 단일 값 |

신뢰 프록시가 보낸 Proto/Port/For 는 유지(append)한다 — TLS 를 종료하는 LB 의 `https/443` 을 백엔드가 써야 하므로. 따라서 **LB 가 클라이언트의 X-Forwarded-* 를 덮어쓰도록** 설정해야 한다 (운영 체크리스트).

## Rate Limit (Redis + Lua)

| 항목 | 값 |
|---|---|
| 방식 | `RedisRateLimiter` (토큰 버킷). Redis 에서 `request_rate_limiter.lua` 를 **EVALSHA 로 실행** → 토큰 조회·판단·차감·저장이 **하나의 원자적 연산**. Gateway 가 여러 대여도 경합 없이 정확 |
| 키 | Redis 에 존재하는 로그인 세션(`SESSION` 쿠키 Base64 디코딩 → `flowlog:session:sessions:{id}` EXISTS) 단위 (`session:<SHA-256>`), 그 외 클라이언트 IP (`ip:<addr>`) |
| 클라이언트 IP | `trusted-proxy-hops` 0 = TCP 접속 IP (기본), 1 이상 = X-Forwarded-For 오른쪽 n 번째 (LB 가 추가한 값). LB 뒤에서 0 이면 모든 사용자가 LB IP 버킷 하나를 공유 |
| 기본값 | 초당 20개 충전(`RATE_LIMIT_REPLENISH_RATE`), 최대 40개(`RATE_LIMIT_BURST_CAPACITY`) |
| 초과 시 | **429 G0005** 표준 응답 + `X-RateLimit-Remaining` 등 헤더 + 메트릭 `gateway_ratelimit_rejected_total{route}` |
| Redis 장애 시 | 요청 허용 (fail-open) — 서비스 지속. 세션 확인 실패 시 IP 키 사용 |

## Circuit Breaker (Resilience4j)

| 항목 | 값 |
|---|---|
| 집계 | 최근 10건(COUNT_BASED), 최소 5건 |
| OPEN 조건 | 실패율 50% 이상 — 실패: 연결 실패, 타임아웃, 백엔드 502/503/504 |
| OPEN 동작 | **10초간 백엔드 호출 없이 즉시 503 G0004** (장애 서비스로 요청이 몰려 Gateway 자원이 고갈되는 것을 방지) |
| HALF_OPEN | 10초 후 자동 전환, 3건 시험 호출 → 성공 시 CLOSED 복구 |
| TimeLimiter | 15초 초과 → 504 G0003 |
| 4xx | 실패로 집계하지 않음 (401/403 등 정상 응답) |
| 모니터링 | `http://127.0.0.1:9910/actuator/prometheus` → `resilience4j_circuitbreaker_state{name="cm",state=...}` 등 |

실연동 확인 (spring-boilerplate 9091 중지 → 재기동):

```
정상          401 C0009 (백엔드 응답 그대로)
9091 중지     #1~#5  503 G0002 (연결 실패, 실패 집계)
              #6~#8  503 G0004 (서킷 OPEN, 백엔드 호출 없음, ~7ms)
재기동 후     401 C0009 (HALF_OPEN 시험 호출 성공 → CLOSED)
```

## 오류 응답 (spring-boilerplate 표준 응답과 동일 형식)

| 상황 | Status | messageId |
|---|---|---|
| 매칭 라우트 없음 / 차단 경로 | 404 | G0001 |
| 백엔드 이용 불가 (연결 실패/5xx) | 503 | G0002 |
| 백엔드 응답 시간 초과 | 504 | G0003 |
| 서킷 OPEN (호출 차단) | 503 | G0004 |
| 요청 한도 초과 | 429 | G0005 |
| 우회 형식 요청 경로 | 400 | G0006 |
| 기타 | 500 | E0001 |

백엔드가 반환한 4xx(예: 401 C0009)는 변경 없이 그대로 전달한다. `Accept-Language` 로 ko_KR / en_US.

## 실행

```bash
docker compose -f ../spring-boilerplate/infra/redis/docker-compose.yml up -d   # Redis
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
./mvnw test                     # Docker 필요 (Testcontainers 로 Redis 기동)
```

| 환경변수 | 기본값 |
|---|---|
| `CM_SERVICE_URI` | `http://localhost:9091` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / (없음) |
| `RATE_LIMIT_REPLENISH_RATE` / `RATE_LIMIT_BURST_CAPACITY` | 20 / 40 |
| `RATE_LIMIT_SESSION_KEY_PREFIX` | `flowlog:session:sessions:` (spring-boilerplate `spring.session.data.redis.namespace` + `:sessions:`) |
| `RATE_LIMIT_TRUSTED_PROXY_HOPS` | 0 (운영 LB 1단 = 1) |
| `GATEWAY_TRUSTED_PROXIES` | loopback (X-Forwarded 헤더 신뢰/추가 조건) |
| `MANAGEMENT_PORT` / `MANAGEMENT_ADDRESS` | 9910 / 127.0.0.1 |

CI: 저장소 루트 `.github/workflows/gateway-ci.yml` (temurin 17, `./mvnw -B verify`, Testcontainers).

## 테스트 (54/54)

| 클래스 | 테스트 | 검증 |
|---|---|---|
| `GatewayRoutingTest` (10) | stripPrefixRouting, forwardedHeaders, forwardedHeaders_spoofedFromTrustedProxy, traceIdPropagation, forwardsAuthorizationAndBody, backendErrorPassThrough, noRoute, noRoute_english, backendDown, backendTimeout | 라우팅·헤더 전달·위조 Host/Prefix 덮어쓰기·오류 표준 응답 (운영과 같은 필터 구성) |
| `GatewayUntrustedProxyTest` (1) | spoofedHeadersRemoved | 비신뢰 클라이언트의 X-Forwarded-*/Forwarded/X-Real-IP 제거 |
| `GatewaySecurityTest` (23) | normalPathRouted, blockedPaths ×7, bypassAttempts ×11, backslashRejected | 차단 경로 404 G0001 / 우회 형식 400 G0006, **백엔드 호출 0건** |
| | gatewayActuatorNotExposed, managementPortEndpoints | 서비스 포트에서 `/actuator/gateway` 조회·라우트 추가·refresh 불가, 관리 포트 분리 |
| | prometheusMetrics | 관리 포트 `/actuator/prometheus` 에 서킷 상태 메트릭 |
| `GatewayResilienceTest` (5) | rateLimit_exceeded | 버킷(3) 소진 → 429 G0005, Redis 토큰 버킷 키, 세션ID 원문 미저장, 거부 메트릭 증가 |
| | rateLimit_perSession | 실존 세션별 독립 버킷 |
| | rateLimit_forgedSessionCookies | 위조 쿠키를 매번 바꿔도 IP 버킷 하나 → 429 |
| | circuit_opensWhenBackendDown | 3건 실패(G0002) → OPEN → 즉시 G0004 (<1초) |
| | circuit_recovers | OPEN 중 **백엔드 호출 0건**, 복구 후 HALF_OPEN → CLOSED |
| `SessionOrIpKeyResolverTest` (8) | ip_directConnection, ip_behindLoadBalancer(_noHeader), session_exists/notExists/invalidCookie/redisError, sessionId_decoding | 키 결정 단위 테스트 (hops 0/1, 위조 XFF 무시, Redis 오류 시 IP) |
| `GatewayWebRouteTest` (3) | routesByPrefix, webRoute_noRateLimit, blockedPaths_notServedByWeb | 운영 yml 라우트 그대로: `/cm/**` → API, 그 외 → 화면(경로 유지), 화면은 Rate Limit 미적용, 차단 경로·`/actuator/**` 는 화면으로도 안 감 |
| `SpringGatewayApplicationTests` (1) | contextLoads | 기동 |

## 운영 전환 체크리스트

- [ ] `trusted-proxies` 를 실제 LB/Ingress 대역으로 한정, `RATE_LIMIT_TRUSTED_PROXY_HOPS` 를 LB 단계 수로 설정
- [ ] LB 가 클라이언트가 보낸 `X-Forwarded-*` 를 덮어쓰도록 설정 (append 금지)
- [ ] 백엔드는 Gateway 에서만 접근 가능하도록 네트워크 제한
- [ ] Redis 인증(`REDIS_PASSWORD`/ACL)/TLS/HA, Rate Limit 값을 실제 트래픽 기준으로 조정 (라우트/사용자 등급별 분리 검토)
- [ ] 서킷 임계치·대기시간을 서비스 특성에 맞게 조정, Prometheus 수집 + 알림 (`resilience4j_circuitbreaker_state`, `gateway_ratelimit_rejected_total`)
- [ ] 관리 포트(9910)는 내부망/수집기만 접근 (`MANAGEMENT_ADDRESS` 변경 시 네트워크 정책 필수)
