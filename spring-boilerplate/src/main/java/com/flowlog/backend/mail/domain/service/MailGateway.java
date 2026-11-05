package com.flowlog.backend.mail.domain.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 메일 발송 게이트웨이.
 *
 * <ul>
 *   <li>SMTP 설정({@code spring.mail.host})이 있으면 JavaMailSender 로 발송</li>
 *   <li>없으면 (local) 발송하지 않고 로그만 남긴다 - 수신 주소는 건수만 기록</li>
 * </ul>
 * 수신자는 서로의 주소가 보이지 않도록 BCC 로 넣는다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@Component
public class MailGateway {

  private final ObjectProvider<JavaMailSender> mailSender;

  /**
   * 생성자.
   *
   * @param mailSender JavaMailSender (SMTP 설정 시에만 존재)
   */
  public MailGateway(ObjectProvider<JavaMailSender> mailSender) {
    this.mailSender = mailSender;
  }

  /**
   * 메일 1통 발송. (수신자 BCC)
   *
   * @param envelope 메일
   * @throws MailSendException 발송 실패
   */
  public void send(Envelope envelope) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      log.info("[Mail] (SMTP 미설정 - 로그 대체) subject={} lang={} bcc={}건",
          envelope.subject(), envelope.langCd(), envelope.bcc().size());
      return;
    }
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false,
          StandardCharsets.UTF_8.name());
      helper.setFrom(envelope.from());
      helper.setTo(envelope.from());
      helper.setBcc(envelope.bcc().toArray(String[]::new));
      helper.setSubject(envelope.subject());
      helper.setText(envelope.html(), true);
      sender.send(message);
    } catch (MessagingException | MailException e) {
      throw new MailSendException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
    }
  }

  /**
   * 메일.
   *
   * @param from 발신 주소
   * @param bcc 수신 주소 (BCC)
   * @param subject 제목
   * @param html 본문 HTML
   * @param langCd 메일 언어 (로그용)
   */
  public record Envelope(String from, List<String> bcc, String subject, String html,
      String langCd) {
  }

  /** 메일 발송 실패. */
  public static class MailSendException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 생성자.
     *
     * @param message 메시지
     * @param cause 원인
     */
    public MailSendException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
