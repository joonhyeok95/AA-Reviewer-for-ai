package com.flowlog.backend.notice.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지사항 첨부파일. (TB_NOTICE_FILE - 게시물 공통, 언어별 분리 안 함)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_NOTICE_FILE")
@SequenceGenerator(name = "SQ_NOTICE_FILE_GEN", sequenceName = "SQ_NOTICE_FILE", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeFile extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_NOTICE_FILE_GEN")
  @Column(name = "FILE_ID")
  private Long id; // 파일ID

  @Column(name = "NOTICE_ID", nullable = false)
  private Long noticeId; // 공지ID

  @Column(name = "ORG_FILE_NM", length = 500, nullable = false)
  private String orgFileNm; // 원본 파일명

  @Column(name = "SAVE_PATH", length = 1000, nullable = false)
  private String savePath; // 저장 경로 (저장소 기준 상대경로)

  @Column(name = "FILE_SIZE", nullable = false)
  private Long fileSize; // 파일 크기

  @Column(name = "FILE_EXT", length = 20, nullable = false)
  private String fileExt; // 확장자 (소문자)

  @Builder
  private NoticeFile(
      Long noticeId, String orgFileNm, String savePath, Long fileSize, String fileExt) {
    this.noticeId = noticeId;
    this.orgFileNm = orgFileNm;
    this.savePath = savePath;
    this.fileSize = fileSize;
    this.fileExt = fileExt;
  }
}
