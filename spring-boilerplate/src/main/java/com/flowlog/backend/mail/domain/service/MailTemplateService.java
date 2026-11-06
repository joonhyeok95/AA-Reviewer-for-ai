package com.flowlog.backend.mail.domain.service;

import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.util.HtmlSanitizer;
import com.flowlog.backend.mail.domain.model.MailTemplate;
import com.flowlog.backend.mail.domain.model.MailTemplateId;
import com.flowlog.backend.mail.domain.repository.MailTemplateRepository;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메일 템플릿 서비스. (언어별 머리글 / 안내 문구 / 바닥글)
 *
 * <ul>
 *   <li>수신자 언어 템플릿 → 없으면 기본 언어 템플릿 (NT-49)</li>
 *   <li>치환자 {@code {{name}}} : 값은 HTML 이스케이프 후 치환 (제목은 개행 제거)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Service("mailTemplateService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MailTemplateService {

  private final MailTemplateRepository mailTemplateRepository;
  private final LangService langService;

  /**
   * 템플릿 적용.
   *
   * @param templateCd 템플릿코드
   * @param langCd 수신자 언어
   * @param variables 치환 값 (원문 - 이 메서드가 이스케이프)
   * @return 적용 결과 (템플릿이 없으면 빈 머리글/바닥글)
   */
  public RenderedTemplate render(String templateCd, String langCd, Map<String, String> variables) {
    Optional<MailTemplate> template = find(templateCd, langCd)
        .or(() -> find(templateCd, langService.defaultLang()));
    String subject = template.map(MailTemplate::getSubject).orElse("{{title}}");
    return new RenderedTemplate(
        template.map(t -> t.getId().getLangCd()).orElse(langCd),
        StringUtils.replaceChars(apply(subject, variables, false), "\r\n", "  "),
        apply(template.map(MailTemplate::getHeaderHtml).orElse(""), variables, true),
        apply(template.map(MailTemplate::getGuideHtml).orElse(""), variables, true),
        apply(template.map(MailTemplate::getFooterHtml).orElse(""), variables, true));
  }

  private Optional<MailTemplate> find(String templateCd, String langCd) {
    return mailTemplateRepository.findById(new MailTemplateId(templateCd, langCd))
        .filter(t -> YesNo.isYes(t.getUseYn()));
  }

  private static String apply(String text, Map<String, String> variables, boolean html) {
    String result = StringUtils.defaultString(text);
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      String value = StringUtils.defaultString(entry.getValue());
      result = result.replace("{{" + entry.getKey() + "}}",
          html ? HtmlSanitizer.escape(value) : value);
    }
    return result;
  }

  /**
   * 템플릿 적용 결과.
   *
   * @param langCd 사용된 템플릿 언어
   * @param subject 제목
   * @param headerHtml 머리글
   * @param guideHtml 안내 문구
   * @param footerHtml 바닥글
   */
  public record RenderedTemplate(
      String langCd, String subject, String headerHtml, String guideHtml, String footerHtml) {
  }
}
