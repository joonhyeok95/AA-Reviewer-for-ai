package com.flowlog.backend.bulletin.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주보 교회소식 Entity. (TB_BULLETIN_NEWS - 섹션 → 항목 → 하위 항목)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_BULLETIN_NEWS")
@SequenceGenerator(
    name = "SQ_BULLETIN_NEWS_GEN", sequenceName = "SQ_BULLETIN_NEWS", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BulletinNews extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_BULLETIN_NEWS_GEN")
  @Column(name = "NEWS_ID")
  private Long id; // ID

  @Column(name = "BULLETIN_ID", nullable = false)
  private Long bulletinId; // 주보 ID

  @Column(name = "PARENT_NEWS_ID")
  private Long parentNewsId; // 상위 소식 ID (섹션 null)

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 같은 상위 안의 순서

  @Column(name = "TITLE", length = 200)
  private String title; // 제목

  @Column(name = "CONTENT", length = 4000)
  private String content; // 내용

  @Column(name = "SCHEDULE_TEXT", length = 100)
  private String scheduleText; // 일정 표시

  @Column(name = "HIGHLIGHT_YN", length = 1, nullable = false)
  private String highlightYn; // 강조 여부

  /**
   * 생성자.
   *
   * @param bulletinId 주보 ID
   * @param parentNewsId 상위 소식 ID (섹션 null)
   * @param sortOrder 같은 상위 안의 순서
   * @param title 제목
   * @param content 내용
   * @param scheduleText 일정 표시
   * @param highlightYn 강조 여부
   */
  public BulletinNews(
      Long bulletinId, Long parentNewsId, Integer sortOrder, String title, String content,
      String scheduleText, String highlightYn) {
    this.bulletinId = bulletinId;
    this.parentNewsId = parentNewsId;
    this.sortOrder = sortOrder;
    this.title = title;
    this.content = content;
    this.scheduleText = scheduleText;
    this.highlightYn = highlightYn;
  }
}
