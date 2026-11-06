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
 * 공지 메일 대상 (공지ID + 유형 + 코드) 복합키.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeMailTargetId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "NOTICE_ID")
  private Long noticeId; // 공지ID

  @Column(name = "TARGET_TYPE", length = 20)
  private String targetType; // 대상 유형 (WORKPLACE/DEPT)

  @Column(name = "TARGET_CD", length = 50)
  private String targetCd; // 사업장/부서 코드
}
