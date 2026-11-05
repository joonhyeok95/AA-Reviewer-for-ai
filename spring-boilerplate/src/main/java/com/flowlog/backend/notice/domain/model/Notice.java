package com.flowlog.backend.notice.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import com.flowlog.backend.global.constant.YesNo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지사항. (TB_NOTICE)
 *
 * <p>언어별 제목/본문은 {@link NoticeI18n} (작성한 언어만 존재). 대표 언어 = {@link #baseLangCd}.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_NOTICE")
@SequenceGenerator(name = "SQ_NOTICE_GEN", sequenceName = "SQ_NOTICE", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseEntity {

  /** 메일 발송 유형 : 발송 안 함. */
  public static final String MAIL_TYPE_NONE = "NONE";

  /** 메일 상태 : 발송 대상 아님. */
  public static final String MAIL_NONE = "NONE";

  /** 메일 상태 : 게시일시 도래 시 발송. */
  public static final String MAIL_READY = "READY";

  /** 메일 상태 : 발송 중 (인스턴스 1대가 선점). */
  public static final String MAIL_SENDING = "SENDING";

  /** 메일 상태 : 발송 완료. */
  public static final String MAIL_DONE = "DONE";

  /** 메일 상태 : 일부/전체 실패. */
  public static final String MAIL_FAIL = "FAIL";

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_NOTICE_GEN")
  @Column(name = "NOTICE_ID")
  private Long id; // 공지ID

  @Column(name = "CATEGORY_CD", length = 50, nullable = false)
  private String categoryCd; // 분류 (NOTICE_CATEGORY)

  @Column(name = "TAG_CD", length = 50)
  private String tagCd; // 태그 (NOTICE_TAG)

  @Column(name = "BASE_LANG_CD", length = 10, nullable = false)
  private String baseLangCd; // 대표 언어

  @Column(name = "POST_DATE", nullable = false)
  private LocalDateTime postDate; // 게시일시

  @Column(name = "POPUP_YN", length = 1, nullable = false)
  private String popupYn; // 팝업여부

  @Column(name = "POPUP_START_DT")
  private LocalDate popupStartDt; // 팝업 시작일

  @Column(name = "POPUP_END_DT")
  private LocalDate popupEndDt; // 팝업 종료일

  @Column(name = "MAIL_SEND_TYPE_CD", length = 50, nullable = false)
  private String mailSendTypeCd; // 메일 발송 유형 (MAIL_SEND_TYPE)

  @Column(name = "MAIL_STATUS_CD", length = 20, nullable = false)
  private String mailStatusCd; // 메일 발송 상태

  @Column(name = "MAIL_SEND_DT")
  private LocalDateTime mailSendDt; // 메일 발송 완료일시

  @Column(name = "VIEW_CNT", nullable = false)
  private Integer viewCnt; // 조회수

  @Column(name = "DEL_YN", length = 1, nullable = false)
  private String delYn; // 삭제여부

  @Builder
  private Notice(
      String categoryCd, String tagCd, String baseLangCd, LocalDateTime postDate, String popupYn,
      LocalDate popupStartDt, LocalDate popupEndDt, String mailSendTypeCd) {
    this.categoryCd = categoryCd;
    this.tagCd = tagCd;
    this.baseLangCd = baseLangCd;
    this.postDate = postDate;
    this.popupYn = popupYn;
    this.popupStartDt = popupStartDt;
    this.popupEndDt = popupEndDt;
    this.mailSendTypeCd = mailSendTypeCd;
    this.mailStatusCd = MAIL_TYPE_NONE.equals(mailSendTypeCd) ? MAIL_NONE : MAIL_READY;
    this.viewCnt = 0;
    this.delYn = YesNo.N;
  }

  /**
   * 공지 내용을 수정한다.
   *
   * <p>메일이 이미 발송(중/완료)되었으면 메일 설정은 바꾸지 않는다. 그 외에는 유형에 따라
   * 발송 대기(READY) 또는 미발송(NONE)으로 다시 설정한다. (실패 건은 수정 시 재발송 대기)
   *
   * @param categoryCd 분류
   * @param tagCd 태그
   * @param baseLangCd 대표 언어
   * @param postDate 게시일시
   * @param popupYn 팝업여부
   * @param popupStartDt 팝업 시작일
   * @param popupEndDt 팝업 종료일
   * @param mailSendTypeCd 메일 발송 유형
   */
  public void update(
      String categoryCd, String tagCd, String baseLangCd, LocalDateTime postDate, String popupYn,
      LocalDate popupStartDt, LocalDate popupEndDt, String mailSendTypeCd) {
    this.categoryCd = categoryCd;
    this.tagCd = tagCd;
    this.baseLangCd = baseLangCd;
    this.postDate = postDate;
    this.popupYn = popupYn;
    this.popupStartDt = popupStartDt;
    this.popupEndDt = popupEndDt;
    if (isMailChangeable()) {
      this.mailSendTypeCd = mailSendTypeCd;
      this.mailStatusCd = MAIL_TYPE_NONE.equals(mailSendTypeCd) ? MAIL_NONE : MAIL_READY;
    }
  }

  /**
   * 메일 설정 변경 가능 여부. (발송 중/완료 후에는 불가)
   *
   * @return 변경 가능하면 true
   */
  public boolean isMailChangeable() {
    return !MAIL_SENDING.equals(mailStatusCd) && !MAIL_DONE.equals(mailStatusCd);
  }

  /** 논리 삭제. 발송 대기 중인 메일은 취소한다. */
  public void delete() {
    this.delYn = YesNo.Y;
    if (MAIL_READY.equals(mailStatusCd)) {
      this.mailStatusCd = MAIL_NONE;
    }
  }

  /**
   * 메일 발송 결과를 기록한다.
   *
   * @param success 전체 성공 여부
   * @param sentAt 발송 일시
   */
  public void completeMail(boolean success, LocalDateTime sentAt) {
    this.mailStatusCd = success ? MAIL_DONE : MAIL_FAIL;
    this.mailSendDt = sentAt;
  }

  /**
   * 게시 여부. (게시일시 도래, 삭제 안 됨)
   *
   * @param now 기준 시각
   * @return 게시 중이면 true
   */
  public boolean isPosted(LocalDateTime now) {
    return YesNo.N.equals(delYn) && !postDate.isAfter(now);
  }

  /**
   * 삭제 여부.
   *
   * @return 삭제되었으면 true
   */
  public boolean isDeleted() {
    return YesNo.isYes(delYn);
  }
}
