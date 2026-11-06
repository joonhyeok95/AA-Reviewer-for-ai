package com.flowlog.backend.global.security;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API scope 작업명.
 *
 * <p>DB 업무 권한(TB_ROLE_MENU)의 작업은 LIST / ADD / EDIT / DEL 이고, 기본 scope 이름은
 * {@code {리소스}.list|add|edit|del} 이다. IdP 에서 다른 동사를 쓰는 리소스를 위해 동의어를 허용한다.
 *
 * <pre>
 * write  = add    (예: board.write  ↔ DB 등록 권한)
 * update = edit   (예: board.update ↔ DB 수정 권한)
 * delete = del    (예: board.delete ↔ DB 삭제 권한)
 * </pre>
 *
 * <p>교집합 계산에만 정규화하고, 권한 이름은 IdP scope 그대로 쓴다. ({@code SCOPE_board.write})
 *
 * @author flowlog
 * @since 2026-11-06
 */
public final class ScopeActions {

  /** API scope 형식 : {리소스}.{작업} (동의어 포함). */
  public static final Pattern API_SCOPE =
      Pattern.compile("^([a-z0-9-]+)\\.(list|add|edit|del|write|update|delete)$");

  private static final Map<String, String> ALIASES =
      Map.of("write", "add", "update", "edit", "delete", "del");

  private ScopeActions() {
  }

  /**
   * API scope 형식인지.
   *
   * @param scope scope
   * @return 형식이 맞으면 true
   */
  public static boolean isApiScope(String scope) {
    return scope != null && API_SCOPE.matcher(scope).matches();
  }

  /**
   * DB 업무 권한 이름으로 정규화. (동의어 → list/add/edit/del, 형식이 아니면 그대로)
   *
   * @param scope IdP scope
   * @return DB 권한 이름 (예: board.write → board.add)
   */
  public static String toPermission(String scope) {
    if (scope == null) {
      return null;
    }
    Matcher matcher = API_SCOPE.matcher(scope);
    if (!matcher.matches()) {
      return scope;
    }
    String action = matcher.group(2);
    return matcher.group(1) + "." + ALIASES.getOrDefault(action, action);
  }
}
