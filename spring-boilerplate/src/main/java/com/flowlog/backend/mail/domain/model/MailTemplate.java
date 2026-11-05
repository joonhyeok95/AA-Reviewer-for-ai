package com.flowlog.backend.mail.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메일 템플릿. (TB_MAIL_TEMPLATE - 언어별 머리글 / 안내 문구 / 바닥글)
 *
 * <p>새 언어 추가 시 LANG 코드와 함께 이 테이블에 템플릿을 등록하면 코드 수정 없이 확장된다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_MAIL_TEMPLATE")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailTemplate extends AssignedIdEntity<MailTemplateId> {

  @EmbeddedId
  private MailTemplateId id; // 템플릿코드 + 언어

  @Column(name = "SUBJECT", length = 500, nullable = false)
  private String subject; // 제목 (치환자 사용)

  @Column(name = "HEADER_HTML", length = 4000)
  private String headerHtml; // 머리글

  @Column(name = "GUIDE_HTML", length = 4000)
  private String guideHtml; // 안내 문구

  @Column(name = "FOOTER_HTML", length = 4000)
  private String footerHtml; // 바닥글

  @Column(name = "USE_YN", length = 1, nullable = false)
  private String useYn; // 사용여부

  /**
   * 생성자.
   *
   * @param templateCd 템플릿코드
   * @param langCd 언어코드
   * @param subject 제목
   * @param headerHtml 머리글
   * @param guideHtml 안내 문구
   * @param footerHtml 바닥글
   */
  public MailTemplate(
      String templateCd, String langCd, String subject, String headerHtml, String guideHtml,
      String footerHtml) {
    this.id = new MailTemplateId(templateCd, langCd);
    this.subject = subject;
    this.headerHtml = headerHtml;
    this.guideHtml = guideHtml;
    this.footerHtml = footerHtml;
    this.useYn = "Y";
  }
}
