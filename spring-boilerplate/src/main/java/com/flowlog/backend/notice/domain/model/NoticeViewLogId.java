package com.flowlog.backend.notice.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지 조회 로그 (공지ID + 사용자 + 일자) 복합키.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeViewLogId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "NOTICE_ID")
  private Long noticeId; // 공지ID

  @Column(name = "USER_ID", length = 50)
  private String userId; // 사용자ID

  @Column(name = "VIEW_DATE")
  private LocalDate viewDate; // 조회일자
}
