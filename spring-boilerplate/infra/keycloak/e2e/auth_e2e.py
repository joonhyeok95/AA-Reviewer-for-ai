"""
Keycloak · Redis · Gateway · spring-boilerplate 인증 E2E 점검 (Python 표준 라이브러리만 사용)

사전 조건
  docker compose -f infra/redis/docker-compose.yml up -d
  docker compose -f infra/keycloak/docker-compose.yml up -d
  spring-boilerplate 실행 (:9091)  +  (세션 공유 점검용) 두 번째 인스턴스 (:9092)
  spring-gateway 실행 (:9909)

실행
  python infra/keycloak/e2e/auth_e2e.py                         # Gateway 경유 (http://localhost:9909/cm)
  python infra/keycloak/e2e/auth_e2e.py http://localhost:9091   # 애플리케이션 직접
  옵션: --second http://localhost:9092  (세션 공유 점검 대상, 기본값)
        --quick                          (토큰 갱신 대기(55초) 시나리오 생략)

점검 시나리오
  [Bearer]  Client Credentials (flowlog-batch) - 서버 간 호출, 역할 제한 scope 로 인가
  [BFF]     OAuth2 로그인 (flowlog-bff) - 클라이언트는 SESSION 쿠키만, 토큰은 Redis 세션에만,
            요청마다 세션 토큰 scope 로 인가, 다른 인스턴스와 세션 공유, 토큰 자동 갱신, 로그아웃
  [SAML2]   SP-initiated SSO - sub/Role 속성 → scope ∩ DB 권한, Redis 세션 공유
  [보안]    Redis 세션 암호화, 사용자 메뉴, 관리 포트 분리, 로그인 무차별 대입 잠금
  [Gateway] Rate Limit (위조 세션 쿠키 우회 불가), 차단 경로, actuator 비노출 (Gateway 경유 시)
"""
import base64
import html
import http.cookiejar
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ARGS = [a for a in sys.argv[1:] if not a.startswith("--")]
if "--second" in sys.argv:
    ARGS = [a for a in ARGS if a != sys.argv[sys.argv.index("--second") + 1]]
APP = (ARGS[0] if ARGS else "http://localhost:9909/cm").rstrip("/")
SECOND = "http://localhost:9092"
if "--second" in sys.argv:
    SECOND = sys.argv[sys.argv.index("--second") + 1].rstrip("/")
QUICK = "--quick" in sys.argv
VIA_GATEWAY = ":9909" in APP

ISSUER = "http://localhost:7000/realms/flowlog"
TOKEN_URL = ISSUER + "/protocol/openid-connect/token"
PASSWORD = "Passw0rd!"
REDIS_CONTAINER = "flowlog-redis"
SESSION_KEY_PREFIX = "flowlog:session:sessions:"
TOKENS_FIELD = ("sessionAttr:org.springframework.security.oauth2.client.web."
                "HttpSessionOAuth2AuthorizedClientRepository.AUTHORIZED_CLIENTS")
GATEWAY = "http://localhost:9909"
MANAGEMENT_PORTS = {"spring-boilerplate": 9191, "spring-gateway": 9910}
JWT_PATTERN = re.compile(r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+")

results = []
app_responses = []  # 토큰 노출 검사용 (애플리케이션/Gateway 응답 전체)


def check(name, ok, detail=""):
    results.append((name, bool(ok), detail))
    print(f"  {'PASS' if ok else 'FAIL'}  {name}" + (f"  ({detail})" if detail else ""))


class LocalhostSecurePolicy(http.cookiejar.DefaultCookiePolicy):
    """브라우저처럼 localhost 를 보안 컨텍스트로 취급 (Keycloak 26 은 http 에서도 Secure 쿠키 사용)."""

    def return_ok_secure(self, cookie, request):
        host = urllib.parse.urlparse(request.get_full_url()).hostname
        return host in ("localhost", "127.0.0.1") or super().return_ok_secure(cookie, request)


class RecordingHandler(urllib.request.BaseHandler):
    """애플리케이션 응답(리다이렉트 중간 응답 포함)의 헤더를 기록한다."""
    handler_order = 1000

    def http_response(self, request, response):
        url = request.get_full_url()
        if url.startswith(APP) or url.startswith(SECOND):
            app_responses.append((url, str(response.headers), None))
        return response


def new_session():
    jar = http.cookiejar.CookieJar(policy=LocalhostSecurePolicy())
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(jar), RecordingHandler())
    return opener, jar


def call(opener, method, url, data=None, headers=None):
    body = None
    hdrs = dict(headers or {})
    if isinstance(data, dict):
        body = urllib.parse.urlencode(data).encode()
        hdrs.setdefault("Content-Type", "application/x-www-form-urlencoded")
    elif isinstance(data, str):
        body = data.encode()
        hdrs.setdefault("Content-Type", "application/json")
    req = urllib.request.Request(url, data=body, method=method, headers=hdrs)
    try:
        with opener.open(req) as resp:
            status, text, final = resp.status, resp.read().decode("utf-8", "replace"), resp.geturl()
    except urllib.error.HTTPError as e:
        status, text, final = e.code, e.read().decode("utf-8", "replace"), url
    if final.startswith(APP) or final.startswith(SECOND):
        app_responses.append((final, "", text))
    return status, text, final


def as_json(text):
    try:
        return json.loads(text)
    except ValueError:
        return {}


def form_of(page, form_id=None):
    pattern = r'<form[^>]*id="%s"[^>]*>' % form_id if form_id else r"<form[^>]*>"
    m = re.search(pattern, page, re.S)
    if not m:
        return None, {}
    action = html.unescape(re.search(r'action="([^"]+)"', m.group(0)).group(1))
    inputs = dict((html.unescape(n), html.unescape(v))
                  for n, v in re.findall(r'<input[^>]*name="([^"]+)"[^>]*value="([^"]*)"', page))
    return action, inputs


def keycloak_login(opener, login_page, username):
    action, _ = form_of(login_page, "kc-form-login")
    if not action:
        raise RuntimeError("Keycloak 로그인 폼을 찾지 못했습니다.")
    return call(opener, "POST", action, {"username": username, "password": PASSWORD})


def cookie(jar, name):
    return next((c for c in jar if c.name == name), None)


def xsrf(jar):
    c = cookie(jar, "XSRF-TOKEN")
    return {"X-XSRF-TOKEN": c.value if c else ""}


def session_id(jar):
    c = cookie(jar, "SESSION")
    return base64.b64decode(c.value + "=" * (-len(c.value) % 4)).decode() if c else None


def redis(*args):
    out = subprocess.run(["docker", "exec", REDIS_CONTAINER, "redis-cli", "--raw", *args],
                         capture_output=True)
    return out.stdout.decode("utf-8", "replace")


def stored_token(sid):
    """Redis 세션의 토큰 속성 값(암호문) 지문 - 갱신 여부 비교용. 값 자체는 AES-GCM 암호문."""
    out = subprocess.run(["docker", "exec", REDIS_CONTAINER, "redis-cli", "--raw", "HGET",
                          SESSION_KEY_PREFIX + sid, TOKENS_FIELD], capture_output=True)
    raw = out.stdout.strip()
    return raw[-24:].hex() if raw else None


def session_plaintext_has_jwt(sid):
    """Redis 세션 원문(전체 필드)에 JWT 형태 문자열이 보이는지."""
    out = subprocess.run(["docker", "exec", REDIS_CONTAINER, "redis-cli", "HGETALL",
                          SESSION_KEY_PREFIX + sid], capture_output=True)
    return bool(JWT_PATTERN.search(out.stdout.decode("latin-1")))


def me(opener, base=APP, headers=None):
    status, text, _ = call(opener, "GET", base + "/api/v1/auth/me", headers=headers)
    return status, as_json(text).get("result", {})


def scopes_of(result):
    return sorted(a for a in result.get("authorities", []) if a.startswith("SCOPE_sample"))


def sample_body(name):
    return json.dumps({"name": f"{name}-{int(time.time() * 1000) % 100000}", "useYn": "Y"},
                      ensure_ascii=False)


# --------------------------------------------------------------------------- scenarios
def scenario_anonymous():
    print("\n[공통] 미인증")
    opener, _ = new_session()
    status, text, _ = call(opener, "GET", APP + "/api/v1/auth/me")
    check("인증 없이 /api/v1/auth/me → 401 C0009",
          status == 401 and as_json(text).get("messageId") == "C0009", f"status={status}")


def scenario_bearer():
    print("\n[Bearer] Client Credentials (flowlog-batch, 서비스계정 역할: sample.list, sample.del)")
    opener, _ = new_session()

    def token(scope):
        _, text, _ = call(opener, "POST", TOKEN_URL, {
            "grant_type": "client_credentials", "client_id": "flowlog-batch",
            "client_secret": "flowlog-batch-local-secret", "scope": scope})
        return json.loads(text)

    tok = token("sample.list sample.add sample.edit sample.del")
    check("4개 scope 요청 → 역할에 매핑된 list, del 만 발급 (Keycloak 역할 제한)",
          set(tok["scope"].split()) >= {"sample.list", "sample.del"}
          and "sample.add" not in tok["scope"], f"scope={tok['scope']}")
    auth = {"Authorization": "Bearer " + tok["access_token"]}
    status, r = me(opener, headers=auth)
    check("/me → OAUTH2_BEARER, 서비스계정, SCOPE_sample.del/list",
          status == 200 and r.get("authType") == "OAUTH2_BEARER" and r.get("serviceAccount")
          and scopes_of(r) == ["SCOPE_sample.del", "SCOPE_sample.list"], f"scopes={scopes_of(r)}")
    status, _, _ = call(opener, "GET", APP + "/api/v1/samples", headers=auth)
    check("GET /samples (sample.list) → 200", status == 200, f"status={status}")
    status, _, _ = call(opener, "POST", APP + "/api/v1/samples", sample_body("bearer"), auth)
    check("POST /samples (sample.add 없음) → 403", status == 403, f"status={status}")
    status, _, _ = call(opener, "DELETE", APP + "/api/v1/samples/3", headers=auth)
    check("DELETE /samples/3 (sample.del) → 200", status == 200, f"status={status}")


def bff_login(username):
    opener, jar = new_session()
    status, page, _ = call(opener, "GET", APP + "/oauth2/authorization/keycloak")
    if "kc-form-login" not in page:
        raise RuntimeError(f"Keycloak 로그인 화면이 아님 (status={status})")
    status, text, final = keycloak_login(opener, page, username)
    return opener, jar, status, as_json(text).get("result", {}), final


def scenario_bff():
    print("\n[BFF] OAuth2 로그인 (flowlog-bff) - 토큰은 서버 세션(Redis)에만")
    start = len(app_responses)
    opener, jar, status, r, final = bff_login("tx.viewer")
    check("로그인 → 콜백 → 성공 URL(/api/v1/auth/me), authType OAUTH2_SESSION, client flowlog-bff",
          status == 200 and r.get("authType") == "OAUTH2_SESSION"
          and r.get("userId") == "tx.viewer" and r.get("clientId") == "flowlog-bff",
          f"final={final}")
    check("scope 4개를 요청했지만 tx.viewer 역할(sample.list)만 발급 → 권한 SCOPE_sample.list",
          scopes_of(r) == ["SCOPE_sample.list"], f"scopes={scopes_of(r)}")

    app_host_cookies = sorted({c.name for c in jar if c.name in ("SESSION", "XSRF-TOKEN", "JSESSIONID")
                               or "token" in c.name.lower()} - {"XSRF-TOKEN"} | (
        {"XSRF-TOKEN"} if cookie(jar, "XSRF-TOKEN") else set()))
    session = cookie(jar, "SESSION")
    check("클라이언트 쿠키 = SESSION(HttpOnly) + CSRF 쿠키뿐 (토큰 쿠키 없음)",
          session is not None and session.has_nonstandard_attr("HttpOnly")
          and set(app_host_cookies) <= {"SESSION", "XSRF-TOKEN"},
          f"cookies={app_host_cookies}, SESSION path={session.path if session else None}")
    leaked = [u for u, h, b in app_responses[start:] if JWT_PATTERN.search(h + (b or ""))]
    check("로그인 과정의 모든 애플리케이션 응답(헤더/본문/쿠키)에 토큰(JWT) 없음",
          not leaked, f"검사 {len(app_responses) - start}건, 노출 {len(leaked)}건")

    sid = session_id(jar)
    exists = redis("EXISTS", SESSION_KEY_PREFIX + sid).strip()
    token_before = stored_token(sid)
    check("Redis 에 세션 저장 + 세션 안에 토큰 보관 (서버 전용)",
          exists == "1" and token_before is not None,
          f"key={SESSION_KEY_PREFIX}{sid[:8]}...")
    check("Redis 세션 값은 암호화 - Redis 를 직접 읽어도 토큰(JWT) 원문이 보이지 않음",
          not session_plaintext_has_jwt(sid))

    status, text, _ = call(opener, "GET", APP + "/api/v1/menus/me")
    menus = as_json(text).get("result", [])
    tx = next((m for m in menus if m.get("menuId") == 1), {})
    trade_list = ((tx.get("children") or [{}])[0].get("children") or [{}])[0]
    check("내 메뉴 API → 유효 권한 메뉴만 (시스템관리 없음), 거래목록은 조회 전용",
          status == 200 and 11 not in [m.get("menuId") for m in menus]
          and trade_list.get("menuId") == 3
          and trade_list.get("permission", {}).get("add") is False,
          f"roots={[m.get('menuId') for m in menus]}")

    status, _, _ = call(opener, "GET", APP + "/api/v1/samples")
    check("GET /samples (세션 토큰 scope sample.list) → 200", status == 200, f"status={status}")
    status, text, _ = call(opener, "POST", APP + "/api/v1/samples", sample_body("viewer"), xsrf(jar))
    check("POST /samples (세션 토큰에 sample.add 없음) → 403 C0010",
          status == 403 and '"C0010"' in text, f"status={status}")

    second_opener, _ = new_session()
    status, r2 = me(second_opener, SECOND, {"Cookie": f"SESSION={session.value}"})
    check(f"다른 인스턴스({SECOND})에 같은 SESSION 쿠키 → 200 (Redis 세션 공유)",
          status == 200 and r2.get("userId") == "tx.viewer"
          and r2.get("authType") == "OAUTH2_SESSION", f"status={status}")

    if QUICK:
        print("  SKIP  토큰 자동 갱신 (--quick)")
    else:
        print("  ...   Access Token 수명(60초) 경과 대기 (55초)")
        time.sleep(55)
        status, r = me(opener)
        token_after = stored_token(sid)
        check("토큰 만료 임박 후 요청 → refresh token 으로 자동 갱신, 세션(Redis) 토큰(암호문) 교체, 200",
              status == 200 and token_after is not None and token_after != token_before,
              f"status={status}, token 변경={token_after != token_before}")

    status, _, _ = call(opener, "POST", APP + "/logout", {}, xsrf(jar))
    gone = redis("EXISTS", SESSION_KEY_PREFIX + sid).strip()
    after, _ = me(opener)
    check("POST /logout → 200, Redis 세션 삭제, 이후 /me 401",
          status == 200 and gone == "0" and after == 401,
          f"logout={status}, redis={gone}, me={after}")
    _, page, _ = call(opener, "GET", APP + "/oauth2/authorization/keycloak")
    check("로그아웃 시 Keycloak SSO 세션도 종료 → 재로그인 시 로그인 화면 표시",
          "kc-form-login" in page)


def scenario_bff_roles():
    print("\n[BFF] 사용자별 scope 인가 (tx.manager: list/add/edit, admin: 전체)")
    opener, jar, _, r, _ = bff_login("tx.manager")
    check("tx.manager 세션 scope = list, add, edit",
          scopes_of(r) == ["SCOPE_sample.add", "SCOPE_sample.edit", "SCOPE_sample.list"],
          f"scopes={scopes_of(r)}")
    status, text, _ = call(opener, "POST", APP + "/api/v1/samples", sample_body("manager"), xsrf(jar))
    created = as_json(text).get("result", {})
    check("POST /samples → 201, 생성자 = tx.manager",
          status == 201 and created.get("createdBy") == "tx.manager", f"status={status}")
    status, _, _ = call(opener, "DELETE", APP + f"/api/v1/samples/{created.get('id')}",
                        headers=xsrf(jar))
    check("DELETE (sample.del 없음) → 403", status == 403, f"status={status}")

    opener, jar, _, r, _ = bff_login("admin")
    status, _, _ = call(opener, "DELETE", APP + f"/api/v1/samples/{created.get('id')}",
                        headers=xsrf(jar))
    check("admin DELETE → 200", status == 200, f"status={status}, scopes={scopes_of(r)}")


def scenario_saml():
    print("\n[SAML2] SP-initiated SSO (tx.manager) - sub/Role 속성 → scope ∩ DB 권한, Redis 세션")
    opener, jar = new_session()
    status, page, _ = call(opener, "GET", APP + "/saml2/authenticate/keycloak")
    if "SAMLRequest" in page and "kc-form-login" not in page:
        action, inputs = form_of(page)
        status, page, _ = call(opener, "POST", action, inputs)
    status, page, _ = keycloak_login(opener, page, "tx.manager")
    action, inputs = form_of(page)
    status, text, final = call(opener, "POST", action, inputs)
    r = as_json(text).get("result", {})
    check("SAML 로그인 → authType SAML2, sub 로 사용자 매핑, scope = Role ∩ DB(list, add, edit)",
          status == 200 and r.get("authType") == "SAML2" and r.get("userId") == "tx.manager"
          and scopes_of(r) == ["SCOPE_sample.add", "SCOPE_sample.edit", "SCOPE_sample.list"],
          f"ACS={action}, scopes={scopes_of(r)}")
    status, text, _ = call(opener, "POST", APP + "/api/v1/samples", sample_body("saml"), xsrf(jar))
    check("POST /samples (SCOPE_sample.add) → 201", status == 201, f"status={status}")
    status, _, _ = call(opener, "DELETE", APP + "/api/v1/samples/1", headers=xsrf(jar))
    check("DELETE (sample.del 없음) → 403", status == 403, f"status={status}")

    sid = session_id(jar)
    check("Redis 에 SAML 세션 저장", redis("EXISTS", SESSION_KEY_PREFIX + sid).strip() == "1")
    second_opener, _ = new_session()
    status, r2 = me(second_opener, SECOND, {"Cookie": f"SESSION={cookie(jar, 'SESSION').value}"})
    check(f"다른 인스턴스({SECOND})에 같은 SESSION 쿠키 → SAML2 세션 공유",
          status == 200 and r2.get("authType") == "SAML2", f"status={status}")


def scenario_gateway_rate_limit():
    print("\n[Gateway] Rate Limit (Redis Lua 토큰 버킷, IP 키: 초당 20 / 최대 40)")
    opener, _ = new_session()
    statuses = []
    body429 = None
    for _ in range(60):
        status, text, _ = call(opener, "GET", APP + "/api/v1/auth/me")
        statuses.append(status)
        if status == 429 and body429 is None:
            body429 = as_json(text)
    limited = statuses.count(429)
    check("60건 연속 요청 → 버킷 소진 후 429 G0005 표준 응답",
          limited > 0 and body429 and body429.get("messageId") == "G0005",
          f"401(통과)={statuses.count(401)}, 429={limited}")
    keys = redis("KEYS", "request_rate_limiter.{cm.ip:*").split()
    check("Redis 에 토큰 버킷 키 (request_rate_limiter.{cm.ip:...}.tokens/.timestamp)",
          any(k.endswith(".tokens") for k in keys), ", ".join(keys[:2]))
    time.sleep(3)


def scenario_gateway_security():
    print("\n[Gateway] 보안 - 위조 세션 쿠키 Rate Limit 우회 불가, 차단 경로, actuator 비노출")
    opener, _ = new_session()
    statuses = []
    for i in range(60):
        forged = base64.b64encode(f"forged-{i}-{time.time_ns()}".encode()).decode()
        status, _, _ = call(opener, "GET", APP + "/api/v1/auth/me",
                            headers={"Cookie": f"SESSION={forged}"})
        statuses.append(status)
    check("요청마다 다른 위조 SESSION 쿠키 60건 → IP 버킷으로 제한되어 429 발생 (우회 불가)",
          statuses.count(429) > 0, f"429={statuses.count(429)}")
    time.sleep(3)
    for path in ("/h2-console/", "/actuator/health", "/./h2-console", "//actuator/env"):
        status, text, _ = call(opener, "GET", APP + path)
        check(f"Gateway 차단 경로 {APP.replace(GATEWAY, '')}{path} → {status}",
              status in (400, 404) and as_json(text).get("messageId") in ("G0001", "G0006"),
              f"messageId={as_json(text).get('messageId')}")
    status, _, _ = call(opener, "GET", GATEWAY + "/actuator/gateway/routes")
    check("Gateway 서비스 포트(9909)에 /actuator/gateway 없음 (라우트 조작 불가)", status == 404,
          f"status={status}")


def scenario_management_ports():
    print("\n[보안] Actuator 관리 포트 분리 (localhost 전용) + Prometheus 메트릭")
    opener, _ = new_session()
    for app, port in MANAGEMENT_PORTS.items():
        status, text, _ = call(opener, "GET", f"http://127.0.0.1:{port}/actuator/prometheus")
        expected = "resilience4j_circuitbreaker" if app == "spring-gateway" else "jvm_memory"
        check(f"{app} 관리 포트 {port} /actuator/prometheus → 200",
              status == 200 and expected in text, f"status={status}")
    status, _, _ = call(opener, "GET", "http://localhost:9091/actuator/health")
    check("spring-boilerplate 서비스 포트(9091)에는 actuator 없음", status in (401, 403, 404),
          f"status={status}")


def scenario_brute_force():
    print("\n[보안] 로그인 무차별 대입 잠금 (Keycloak brute force detection, guest01)")
    for _ in range(5):
        opener, _ = new_session()
        _, page, _ = call(opener, "GET", APP + "/oauth2/authorization/keycloak")
        action, _ = form_of(page, "kc-form-login")
        call(opener, "POST", action, {"username": "guest01", "password": "wrong-password"})
    opener, _ = new_session()
    _, page, _ = call(opener, "GET", APP + "/oauth2/authorization/keycloak")
    _, _, final = keycloak_login(opener, page, "guest01")
    check("비밀번호 5회 실패 후에는 올바른 비밀번호로도 로그인 불가 (일시 잠금)",
          "/api/v1/auth/me" not in final, f"final={final[:70]}")


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    print(f"APP={APP}  SECOND={SECOND}  gateway={VIA_GATEWAY}  quick={QUICK}")
    scenarios = [scenario_anonymous, scenario_bearer, scenario_bff, scenario_bff_roles,
                 scenario_saml]
    if VIA_GATEWAY:
        scenarios += [scenario_gateway_rate_limit, scenario_gateway_security]
    scenarios += [scenario_management_ports, scenario_brute_force]
    for scenario in scenarios:
        try:
            scenario()
        except Exception as e:  # noqa: BLE001 - 시나리오 단위로 실패를 기록하고 계속 진행
            check(f"{scenario.__name__} 실행 오류", False, repr(e))
    failed = [r for r in results if not r[1]]
    print(f"\n결과: {len(results) - len(failed)}/{len(results)} PASS")
    sys.exit(1 if failed else 0)
