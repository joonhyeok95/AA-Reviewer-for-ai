package com.flowlog.backend.notice.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지 다국어 (공지ID + 언어) 복합키.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeI18nId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "NOTICE_ID")
  private Long noticeId; // 공지ID

  @Column(name = "LANG_CD", length = 10)
  private String langCd; // 언어코드
}
