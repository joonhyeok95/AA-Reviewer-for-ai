package com.flowlog.backend.global.security;

import com.flowlog.backend.role.domain.service.UserAuthorityService;
import com.flowlog.backend.role.domain.service.dto.UserAuthority;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * 인증 방식(Bearer / BFF 세션 / SAML) 공통 권한 계산기.
 *
 * <p><b>API 권한 = IdP 가 허용한 scope ∩ DB 업무 권한</b>
 * <ul>
 *   <li>IdP scope (Keycloak 역할 기반) : 이 클라이언트/사용자에게 허용된 상한</li>
 *   <li>DB 권한 ({@link UserAuthorityService}) : 기간·부서·커스텀 권한이 반영된 실제 업무 권한</li>
 *   <li>둘 다 있어야 {@code SCOPE_*} 가 부여된다. DB 미등록 사용자는 IdP 역할이 있어도 API 권한 없음.</li>
 *   <li>DB 유효 권한은 {@code ROLE_*} 로도 부여 (예: ROLE_SYS_ADMIN = 전체 허용)</li>
 *   <li>서비스 계정(Client Credentials)은 사용자가 아니므로 토큰 scope 만 사용</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Component
@RequiredArgsConstructor
public class AuthorityResolver {

  /** scope 권한 접두사. */
  public static final String SCOPE_PREFIX = "SCOPE_";

  /** 역할 권한 접두사. */
  public static final String ROLE_PREFIX = "ROLE_";

  private final UserAuthorityService userAuthorityService;

  /**
   * 사용자 권한.
   *
   * @param subject IdP 사용자 고유ID (토큰 sub / SAML sub 속성)
   * @param grantedScopes IdP 가 허용한 scope (예: sample.list)
   * @return SCOPE_* (교집합) + ROLE_*
   */
  public Set<GrantedAuthority> forUser(String subject, Collection<String> grantedScopes) {
    UserAuthority user = userAuthorityService.loadBySubject(subject);
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    grantedScopes.stream()
        .filter(user.permissions()::contains)
        .sorted()
        .forEach(scope -> authorities.add(new SimpleGrantedAuthority(SCOPE_PREFIX + scope)));
    user.roleCds().forEach(roleCd -> authorities.add(new SimpleGrantedAuthority(
        ROLE_PREFIX + roleCd)));
    return authorities;
  }

  /**
   * 서비스 계정 권한. (토큰 scope 만)
   *
   * @param grantedScopes 토큰 scope
   * @return SCOPE_*
   */
  public Set<GrantedAuthority> forServiceAccount(Collection<String> grantedScopes) {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    grantedScopes.stream().sorted()
        .forEach(scope -> authorities.add(new SimpleGrantedAuthority(SCOPE_PREFIX + scope)));
    return authorities;
  }
}
