package com.flowlog.backend.notice.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지사항 언어별 내용. (TB_NOTICE_I18N - 작성한 언어만 존재)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_NOTICE_I18N")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeI18n extends AssignedIdEntity<NoticeI18nId> {

  @EmbeddedId
  private NoticeI18nId id; // 공지ID + 언어

  @Column(name = "TITLE", length = 500, nullable = false)
  private String title; // 제목

  @Lob
  @Column(name = "CONTENT_HTML", nullable = false)
  private String contentHtml; // 본문 HTML (XSS 정화 후 저장)

  @Lob
  @Column(name = "CONTENT_TEXT")
  private String contentText; // 본문 텍스트 (검색용)

  /**
   * 생성자.
   *
   * @param noticeId 공지ID
   * @param langCd 언어코드
   * @param title 제목
   * @param contentHtml 본문 HTML (정화된 값)
   * @param contentText 본문 텍스트
   */
  public NoticeI18n(
      Long noticeId, String langCd, String title, String contentHtml, String contentText) {
    this.id = new NoticeI18nId(noticeId, langCd);
    this.title = title;
    this.contentHtml = contentHtml;
    this.contentText = contentText;
  }

  /**
   * 내용을 수정한다.
   *
   * @param title 제목
   * @param contentHtml 본문 HTML (정화된 값)
   * @param contentText 본문 텍스트
   */
  public void update(String title, String contentHtml, String contentText) {
    this.title = title;
    this.contentHtml = contentHtml;
    this.contentText = contentText;
  }

  /**
   * 언어코드.
   *
   * @return 언어코드
   */
  public String getLangCd() {
    return id.getLangCd();
  }
}
