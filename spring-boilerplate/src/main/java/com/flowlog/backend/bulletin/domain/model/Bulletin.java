package com.flowlog.backend.bulletin.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주보 Entity. (TB_BULLETIN - 예배일 1건, 하위 : 예배순서·교회소식·섬김위원·교리문답·전례문)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Entity
@Table(name = "TB_BULLETIN")
@SequenceGenerator(name = "SQ_BULLETIN_GEN", sequenceName = "SQ_BULLETIN", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bulletin extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_BULLETIN_GEN")
  @Column(name = "BULLETIN_ID")
  private Long id; // ID

  @Column(name = "WORSHIP_DT", nullable = false)
  private LocalDate worshipDt; // 예배일

  @Column(name = "VOLUME_NO")
  private Integer volumeNo; // 통권 권

  @Column(name = "ISSUE_NO")
  private Integer issueNo; // 호

  @Column(name = "TITLE", length = 200)
  private String title; // 특별 주일 이름

  @Column(name = "THEME_VERSE", length = 2000)
  private String themeVerse; // 표지 말씀

  @Column(name = "THEME_VERSE_REF", length = 100)
  private String themeVerseRef; // 표지 말씀 출처

  @Column(name = "SERMON_TITLE", length = 200)
  private String sermonTitle; // 설교 제목

  @Column(name = "SERMON_SCRIPTURE", length = 100)
  private String sermonScripture; // 설교 본문

  @Column(name = "PREACHER_NM", length = 100)
  private String preacherNm; // 설교자

  @Column(name = "SERMON_MEMO", length = 4000)
  private String sermonMemo; // 말씀메모

  @Column(name = "STATUS_CD", length = 30, nullable = false)
  private String statusCd; // 상태 (공통코드 BULLETIN_STATUS)

  /**
   * 생성자.
   *
   * @param worshipDt 예배일
   * @param volumeNo 통권 권
   * @param issueNo 호
   * @param title 특별 주일 이름
   * @param themeVerse 표지 말씀
   * @param themeVerseRef 표지 말씀 출처
   * @param sermonTitle 설교 제목
   * @param sermonScripture 설교 본문
   * @param preacherNm 설교자
   * @param sermonMemo 말씀메모
   * @param statusCd 상태 (공통코드 BULLETIN_STATUS)
   */
  public Bulletin(
      LocalDate worshipDt, Integer volumeNo, Integer issueNo, String title, String themeVerse,
      String themeVerseRef, String sermonTitle, String sermonScripture, String preacherNm,
      String sermonMemo, String statusCd) {
    update(
        worshipDt, volumeNo, issueNo, title, themeVerse, themeVerseRef, sermonTitle,
        sermonScripture, preacherNm, sermonMemo, statusCd);
  }

  /**
   * 기본 정보를 수정한다.
   *
   * @param worshipDt 예배일
   * @param volumeNo 통권 권
   * @param issueNo 호
   * @param title 특별 주일 이름
   * @param themeVerse 표지 말씀
   * @param themeVerseRef 표지 말씀 출처
   * @param sermonTitle 설교 제목
   * @param sermonScripture 설교 본문
   * @param preacherNm 설교자
   * @param sermonMemo 말씀메모
   * @param statusCd 상태 (공통코드 BULLETIN_STATUS)
   */
  public void update(
      LocalDate worshipDt, Integer volumeNo, Integer issueNo, String title, String themeVerse,
      String themeVerseRef, String sermonTitle, String sermonScripture, String preacherNm,
      String sermonMemo, String statusCd) {
    this.worshipDt = worshipDt;
    this.volumeNo = volumeNo;
    this.issueNo = issueNo;
    this.title = title;
    this.themeVerse = themeVerse;
    this.themeVerseRef = themeVerseRef;
    this.sermonTitle = sermonTitle;
    this.sermonScripture = sermonScripture;
    this.preacherNm = preacherNm;
    this.sermonMemo = sermonMemo;
    this.statusCd = statusCd;
  }
}
