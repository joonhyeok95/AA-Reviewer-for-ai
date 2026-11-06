package com.flowlog.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowlog.backend.global.security.ApiAuthorizationRules;
import com.flowlog.backend.global.security.AppSecurityProperties;
import com.flowlog.backend.global.security.ScopeActions;
import com.flowlog.backend.menu.domain.model.Menu;
import com.flowlog.backend.menu.domain.repository.MenuRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * API 권한(scope) 설정 누락 검사.
 *
 * <p>새 API 의 권한은 5곳에 같은 이름으로 있어야 동작한다. 하나라도 빠지면 403 이거나 scope 가 발급되지
 * 않으므로, 서로 맞는지 여기서 확인한다. (화면·Keycloak 없이 CI 에서 실행)
 * <ol>
 *   <li>Keycloak realm 역할 ({@code infra/keycloak/realm/flowlog-realm.json})</li>
 *   <li>Keycloak client scope ({@code infra/keycloak/setup/setup.sh})</li>
 *   <li>BFF 요청 scope ({@code app.security.oauth2.scopes})</li>
 *   <li>인가 규칙표 ({@link ApiAuthorizationRules})</li>
 *   <li>메뉴 PERM_CD (DB 업무 권한 리소스)</li>
 * </ol>
 *
 * @author flowlog
 * @since 2026-11-06
 */
@SpringBootTest
class ScopeConsistencyTest {

  private static final Path REALM = Path.of("infra/keycloak/realm/flowlog-realm.json");
  private static final Path SETUP = Path.of("infra/keycloak/setup/setup.sh");
  private static final Pattern SETUP_SCOPE = Pattern.compile("\"([a-z0-9-]+\\.[a-z]+):");

  @Autowired
  private AppSecurityProperties properties;

  @Autowired
  private MenuRepository menuRepository;

  private Set<String> realmScopes;
  private Set<String> setupScopes;
  private Set<String> bffScopes;

  @BeforeEach
  void setUp() throws Exception {
    JsonNode realm = new ObjectMapper().readTree(Files.readString(REALM, StandardCharsets.UTF_8));
    realmScopes = new TreeSet<>();
    realm.path("roles").path("realm").forEach(role -> {
      String name = role.path("name").asText();
      if (ScopeActions.isApiScope(name)) {
        realmScopes.add(name);
      }
    });
    setupScopes = new TreeSet<>();
    Matcher matcher = SETUP_SCOPE.matcher(Files.readString(SETUP, StandardCharsets.UTF_8));
    while (matcher.find()) {
      setupScopes.add(matcher.group(1));
    }
    bffScopes = properties.oauth2().scopes().stream().map(String::trim)
        .filter(ScopeActions::isApiScope).collect(Collectors.toCollection(TreeSet::new));
  }

  @Test
  @DisplayName("Keycloak 역할 = setup.sh client scope = BFF 요청 scope (같은 이름, 빠짐 없음)")
  void keycloakAndBffScopesMatch() {
    assertThat(realmScopes).isNotEmpty();
    assertThat(setupScopes).as("setup.sh client scope").isEqualTo(realmScopes);
    assertThat(bffScopes).as("application.yml app.security.oauth2.scopes").isEqualTo(realmScopes);
  }

  @Test
  @DisplayName("Keycloak scope = 인가 규칙표 scope (규칙이 없으면 기본 거부, 규칙만 있으면 발급 안 됨)")
  void everyScopeHasSecurityRule() {
    assertThat(ApiAuthorizationRules.scopes()).as("ApiAuthorizationRules").isEqualTo(realmScopes);
  }

  @Test
  @DisplayName("규칙표 - 예외 규칙이 같은 메서드의 CRUD 규칙보다 앞에 있음 (뒤에 있으면 가려짐)")
  void specificRulesFirst() {
    var rules = ApiAuthorizationRules.RULES;
    for (int i = 0; i < rules.size(); i++) {
      for (int j = 0; j < i; j++) {
        var earlier = rules.get(j);
        var later = rules.get(i);
        boolean shadowed = earlier.method().equals(later.method())
            && earlier.patternList().stream().anyMatch(pattern -> pattern.endsWith("/**")
                && later.patternList().stream().allMatch(path ->
                    path.startsWith(pattern.substring(0, pattern.length() - 2))));
        assertThat(shadowed).as("%s 가 %s 에 가려짐", later, earlier).isFalse();
      }
    }
  }

  @Test
  @DisplayName("메뉴 PERM_CD 마다 Keycloak 에 조회(list) scope 가 있고, scope 리소스는 모두 메뉴에 연결")
  void menuResourcesMatchScopes() {
    Set<String> menuResources = menuRepository.findAll().stream().map(Menu::getPermCd)
        .filter(StringUtils::isNotBlank).collect(Collectors.toCollection(TreeSet::new));
    Set<String> scopeResources = realmScopes.stream()
        .map(scope -> scope.substring(0, scope.indexOf('.')))
        .collect(Collectors.toCollection(TreeSet::new));

    assertThat(menuResources).allSatisfy(resource ->
        assertThat(realmScopes).as("메뉴 PERM_CD %s", resource).contains(resource + ".list"));
    assertThat(scopeResources).as("scope 리소스(메뉴 PERM_CD 없으면 DB 권한이 없어 항상 거부)")
        .isEqualTo(menuResources);
  }
}
