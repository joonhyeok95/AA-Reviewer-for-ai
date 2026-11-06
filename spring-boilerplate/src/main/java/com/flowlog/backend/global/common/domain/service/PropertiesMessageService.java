package com.flowlog.backend.global.common.domain.service;

import com.flowlog.backend.global.constant.MessageCode;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

/**
 * properties 파일({@code messages/messages_*.properties}) 기반 메시지 서비스.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Service("propertiesMessageService")
@RequiredArgsConstructor
public class PropertiesMessageService implements MessageService {

  private final MessageSource messageSource;

  @Override
  public String getTitle(String messageId, Locale locale, Object... args) {
    return resolve(messageId + MessageCode.TITLE_SUFFIX, messageId, locale, args);
  }

  @Override
  public String getContent(String messageId, Locale locale, Object... args) {
    return resolve(messageId + MessageCode.CONTENT_SUFFIX, messageId, locale, args);
  }

  private String resolve(String key, String defaultMessage, Locale locale, Object... args) {
    return messageSource.getMessage(key, args, defaultMessage, locale);
  }
}
