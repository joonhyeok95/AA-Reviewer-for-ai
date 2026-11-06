package com.flowlog.backend.notice.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지 메일 대상. (TB_NOTICE_MAIL_TARGET - 코드만 저장, 수신자는 발송 시점 인사 마스터로 추출)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_NOTICE_MAIL_TARGET")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeMailTarget extends AssignedIdEntity<NoticeMailTargetId> {

  @EmbeddedId
  private NoticeMailTargetId id; // 공지ID + 대상유형 + 대상코드

  @Column(name = "INCLUDE_SUB_YN", length = 1, nullable = false)
  private String includeSubYn; // 하위부서 포함여부

  /**
   * 생성자.
   *
   * @param noticeId 공지ID
   * @param targetType 대상 유형 (WORKPLACE/DEPT)
   * @param targetCd 사업장/부서 코드
   * @param includeSubYn 하위부서 포함여부
   */
  public NoticeMailTarget(
      Long noticeId, String targetType, String targetCd, String includeSubYn) {
    this.id = new NoticeMailTargetId(noticeId, targetType, targetCd);
    this.includeSubYn = includeSubYn;
  }
}
