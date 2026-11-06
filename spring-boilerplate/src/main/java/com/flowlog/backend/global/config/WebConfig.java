package com.flowlog.backend.global.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Web MVC 설정.
 *
 * <p>Locale 은 {@code Accept-Language} Header 로 결정한다. (미지정/미지원 시 ko_KR)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  /** 지원 로케일. 메시지 파일(messages_xx_XX.properties)을 추가하면 여기에도 등록한다. */
  private static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.KOREA, Locale.US);

  /**
   * Accept-Language 기반 LocaleResolver.
   *
   * @return LocaleResolver
   */
  @Bean
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(Locale.KOREA);
    resolver.setSupportedLocales(SUPPORTED_LOCALES);
    return resolver;
  }
}
