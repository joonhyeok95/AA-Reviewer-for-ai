package com.flowlog.backend.global.util;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

/**
 * 에디터 HTML 정화. (저장형 XSS 방지)
 *
 * <ul>
 *   <li>허용 태그/속성만 남긴다 (jsoup {@link Safelist#relaxed()} 기준: 서식, 표, 목록, 링크, 이미지)</li>
 *   <li>script / style / iframe / on* 이벤트 / javascript: URL 은 제거</li>
 *   <li>링크·이미지 URL 은 http/https/mailto 만 허용, 링크에는 rel="noopener noreferrer"</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
public final class HtmlSanitizer {

  private static final Safelist SAFELIST = Safelist.relaxed()
      .addAttributes("a", "target")
      .addEnforcedAttribute("a", "rel", "noopener noreferrer")
      .addProtocols("a", "href", "http", "https", "mailto")
      .addProtocols("img", "src", "http", "https")
      .preserveRelativeLinks(false);

  private static final Document.OutputSettings OUTPUT =
      new Document.OutputSettings().prettyPrint(false);

  private HtmlSanitizer() {
  }

  /**
   * 허용 목록 기반으로 HTML 을 정화한다.
   *
   * @param html 입력 HTML
   * @return 정화된 HTML (null 이면 빈 문자열)
   */
  public static String sanitize(String html) {
    return StringUtils.isBlank(html) ? "" : Jsoup.clean(html, "", SAFELIST, OUTPUT);
  }

  /**
   * HTML 의 텍스트만 추출한다. (검색용)
   *
   * @param html HTML
   * @return 텍스트 (공백 정리)
   */
  public static String toText(String html) {
    return StringUtils.isBlank(html) ? "" : Jsoup.parse(html).text();
  }

  /**
   * HTML 특수문자 이스케이프. (메일 템플릿 치환값 등)
   *
   * @param text 텍스트
   * @return 이스케이프된 문자열
   */
  public static String escape(String text) {
    return text == null ? "" : Entities.escape(text);
  }
}
