package com.flowlog.backend.notice.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지 메일 발송 이력. (TB_NOTICE_MAIL_HIST - 수신자별)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_NOTICE_MAIL_HIST")
@SequenceGenerator(
    name = "SQ_NOTICE_MAIL_HIST_GEN", sequenceName = "SQ_NOTICE_MAIL_HIST", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeMailHist extends BaseEntity {

  /** 발송 결과 : 성공. */
  public static final String SUCCESS = "SUCCESS";

  /** 발송 결과 : 실패. */
  public static final String FAIL = "FAIL";

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_NOTICE_MAIL_HIST_GEN")
  @Column(name = "HIST_ID")
  private Long id; // 이력ID

  @Column(name = "NOTICE_ID", nullable = false)
  private Long noticeId; // 공지ID

  @Column(name = "RECEIVER_ID", length = 50, nullable = false)
  private String receiverId; // 수신자 사용자ID

  @Column(name = "LANG_CD", length = 10, nullable = false)
  private String langCd; // 발송 언어

  @Column(name = "SEND_DT", nullable = false)
  private LocalDateTime sendDt; // 발송일시

  @Column(name = "STATUS_CD", length = 20, nullable = false)
  private String statusCd; // 발송 결과 (MAIL_STATUS)

  @Column(name = "ERROR_MSG", length = 1000)
  private String errorMsg; // 실패 사유

  @Builder
  private NoticeMailHist(
      Long noticeId, String receiverId, String langCd, LocalDateTime sendDt, String statusCd,
      String errorMsg) {
    this.noticeId = noticeId;
    this.receiverId = receiverId;
    this.langCd = langCd;
    this.sendDt = sendDt;
    this.statusCd = statusCd;
    this.errorMsg = errorMsg;
  }
}
