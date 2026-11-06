package com.flowlog.backend.notice.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지 조회 로그. (TB_NOTICE_VIEW_LOG - 사용자·일자별 1건, 중복 조회 판정)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_NOTICE_VIEW_LOG")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeViewLog extends AssignedIdEntity<NoticeViewLogId> {

  @EmbeddedId
  private NoticeViewLogId id; // 공지ID + 사용자ID + 조회일자

  /**
   * 생성자.
   *
   * @param noticeId 공지ID
   * @param userId 사용자ID
   * @param viewDate 조회일자
   */
  public NoticeViewLog(Long noticeId, String userId, LocalDate viewDate) {
    this.id = new NoticeViewLogId(noticeId, userId, viewDate);
  }
}
