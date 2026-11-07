package com.flowlog.backend.global.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * API 인가 규칙표. (경로·메서드 → 필요한 scope)
 *
 * <p>SecurityConfig 에 리소스마다 4~8줄씩 쌓이던 규칙을 한 곳의 표로 둔다.
 * <ul>
 *   <li>{@link #crud} : 메서드별 작업 - GET=조회, POST=등록, PUT·PATCH=수정, DELETE=삭제</li>
 *   <li>{@link #rule} : 예외 규칙 (예: 조회 경로지만 수정 권한이 필요한 수정 화면)</li>
 *   <li><b>순서가 중요하다</b> - 앞의 규칙이 먼저 일치한다. 구체적인 예외 규칙을 crud 보다 앞에 둔다</li>
 *   <li>모든 규칙은 시스템관리자({@code ROLE_SYS_ADMIN})도 허용한다</li>
 *   <li>여기 없는 경로는 SecurityConfig 의 기본 거부(denyAll) 대상</li>
 * </ul>
 *
 * <p>새 API : 이 표에 규칙 추가 + 메뉴 PERM_CD + Keycloak 역할/client scope + BFF scope.
 * 누락은 {@code ScopeConsistencyTest} 가 잡는다.
 *
 * @author flowlog
 * @since 2026-11-06
 */
public final class ApiAuthorizationRules {

  /** 시스템관리자 (모든 API 허용). */
  public static final String SYS_ADMIN = "ROLE_SYS_ADMIN";

  /** 작업 동사 - 기본 (DB 권한과 같은 이름). */
  public static final Verbs DEFAULT_VERBS = new Verbs("list", "add", "edit", "del");

  /** 작업 동사 - 게시판(공지사항). ({@link ScopeActions} 동의어 : write=add, update=edit, delete=del) */
  public static final Verbs BOARD_VERBS = new Verbs("list", "write", "update", "delete");

  /** 규칙표. (위에서부터 일치) */
  public static final List<Rule> RULES = Collections.unmodifiableList(build());

  private ApiAuthorizationRules() {
  }

  private static List<Rule> build() {
    List<Rule> rules = new ArrayList<>();
    // 공통코드 관리
    rules.addAll(crud("code", DEFAULT_VERBS, "/api/v1/code-groups/**"));
    // 메뉴 관리 (/api/v1/menus/me, /menus/*/content 는 SecurityConfig 의 로그인 사용자 경로)
    rules.addAll(crud("menu", DEFAULT_VERBS, "/api/v1/menus", "/api/v1/menus/*"));
    // 권한 관리 : 사용자 검색은 조회, 부여·회수는 수정 (권한 자체 삭제만 role.del)
    rules.add(rule(HttpMethod.GET, List.of("role.list"), "/api/v1/users"));
    rules.add(rule(HttpMethod.POST, List.of("role.edit"), "/api/v1/roles/*/grants"));
    rules.add(rule(HttpMethod.DELETE, List.of("role.edit"), "/api/v1/roles/*/grants/**"));
    rules.addAll(crud("role", DEFAULT_VERBS, "/api/v1/roles/**"));
    // 공지사항(게시판) : 수정 화면·메일 이력·첨부 삭제 = update, 첨부 업로드 = write 또는 update
    rules.add(rule(HttpMethod.GET, List.of("board.update"),
        "/api/v1/notices/*/edit", "/api/v1/notices/*/mail-histories"));
    rules.add(rule(HttpMethod.POST, List.of("board.write", "board.update"),
        "/api/v1/notices/*/files"));
    rules.add(rule(HttpMethod.DELETE, List.of("board.update"), "/api/v1/notices/*/files/*"));
    rules.addAll(crud("board", BOARD_VERBS, "/api/v1/notices/**"));
    // 캘린더 (생일·결혼기념일·휴일)
    rules.addAll(crud("calendar", DEFAULT_VERBS, "/api/v1/calendar/**"));
    // 샘플
    rules.addAll(crud("sample", DEFAULT_VERBS, "/api/v1/samples/**"));
    return rules;
  }

  /**
   * 규칙을 Spring Security 에 등록한다. (규칙표 순서대로)
   *
   * @param registry authorizeHttpRequests 레지스트리
   */
  public static void apply(
      AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry registry) {
    for (Rule rule : RULES) {
      registry.requestMatchers(rule.method(), rule.patterns()).hasAnyAuthority(rule.authorities());
    }
  }

  /**
   * 규칙표에 쓰인 모든 scope. (예: board.write)
   *
   * @return scope 이름
   */
  public static Set<String> scopes() {
    Set<String> scopes = new TreeSet<>();
    RULES.forEach(rule -> scopes.addAll(rule.scopes()));
    return scopes;
  }

  /**
   * 리소스 CRUD 규칙. GET=조회, POST=등록, PUT·PATCH=수정, DELETE=삭제.
   *
   * @param resource 리소스 (메뉴 PERM_CD)
   * @param verbs 작업 동사
   * @param patterns 경로
   * @return 규칙 (메서드별)
   */
  public static List<Rule> crud(String resource, Verbs verbs, String... patterns) {
    return List.of(
        rule(HttpMethod.GET, List.of(resource + "." + verbs.list()), patterns),
        rule(HttpMethod.POST, List.of(resource + "." + verbs.add()), patterns),
        rule(HttpMethod.PUT, List.of(resource + "." + verbs.edit()), patterns),
        rule(HttpMethod.PATCH, List.of(resource + "." + verbs.edit()), patterns),
        rule(HttpMethod.DELETE, List.of(resource + "." + verbs.del()), patterns));
  }

  /**
   * 단일 규칙.
   *
   * @param method HTTP 메서드
   * @param scopes 허용 scope (하나라도 있으면 허용)
   * @param patterns 경로
   * @return 규칙
   */
  public static Rule rule(HttpMethod method, List<String> scopes, String... patterns) {
    return new Rule(method, List.of(patterns), List.copyOf(scopes));
  }

  /**
   * 작업 동사. (조회/등록/수정/삭제 → scope 의 작업 이름)
   *
   * @param list 조회
   * @param add 등록
   * @param edit 수정
   * @param del 삭제
   */
  public record Verbs(String list, String add, String edit, String del) {
  }

  /**
   * 인가 규칙.
   *
   * @param method HTTP 메서드
   * @param patternList 경로 패턴
   * @param scopes 허용 scope
   */
  public record Rule(HttpMethod method, List<String> patternList, List<String> scopes) {

    /**
     * 경로 패턴 배열.
     *
     * @return 경로 패턴
     */
    public String[] patterns() {
      return patternList.toArray(String[]::new);
    }

    /**
     * 허용 권한. (SCOPE_{scope} + 시스템관리자)
     *
     * @return 권한 이름
     */
    public String[] authorities() {
      List<String> authorities = new ArrayList<>(scopes.stream()
          .map(scope -> AuthorityResolver.SCOPE_PREFIX + scope).toList());
      authorities.add(SYS_ADMIN);
      return authorities.toArray(String[]::new);
    }
  }
}
