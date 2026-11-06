package com.flowlog.backend.mail.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메일 템플릿 (템플릿코드 + 언어) 복합키.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailTemplateId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "TEMPLATE_CD", length = 50)
  private String templateCd; // 템플릿코드

  @Column(name = "LANG_CD", length = 10)
  private String langCd; // 언어코드
}
