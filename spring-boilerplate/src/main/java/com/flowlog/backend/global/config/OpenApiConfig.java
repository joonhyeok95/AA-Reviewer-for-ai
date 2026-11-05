package com.flowlog.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 설정.
 *
 * <ul>
 *   <li>Swagger UI : /swagger-ui.html</li>
 *   <li>API Docs   : /v3/api-docs</li>
 * </ul>
 *
 * <p>인증 방법
 * <ul>
 *   <li>브라우저(세션) : 같은 브라우저에서 {@code /oauth2/authorization/keycloak} 로 로그인한 뒤 Swagger 를
 *       사용하면 SESSION 쿠키로 호출된다. 변경 요청의 CSRF 헤더는 Swagger 가 XSRF-TOKEN 쿠키에서 자동으로 넣는다.
 *       (토큰을 브라우저에 노출하지 않기 위해 Swagger 의 OAuth2 로그인은 사용하지 않음)</li>
 *   <li>bearerAuth : 서버 간 호출용 Client Credentials 토큰 직접 입력</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME = "bearerAuth";

  /**
   * OpenAPI 문서 기본 정보 + 보안 스키마.
   *
   * @return OpenAPI
   */
  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .info(new Info()
            .title("Flowlog API")
            .description("Flowlog Backend API 문서<br>"
                + "브라우저 로그인: <code>/oauth2/authorization/keycloak</code> (세션) 후 사용")
            .version("v1"))
        .components(new Components()
            .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("서버 간 호출용 Client Credentials Access Token")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
  }
}
