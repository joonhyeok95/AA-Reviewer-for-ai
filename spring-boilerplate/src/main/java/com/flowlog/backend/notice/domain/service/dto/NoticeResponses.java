package com.flowlog.backend.notice.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 공지 응답 DTO 모음.
 *
 * @author flowlog
 * @since 2026-11-05
 */
public final class NoticeResponses {

  private NoticeResponses() {
  }

  /**
   * 제공 언어 배지. (NT-65)
   *
   * @param langCd 언어코드
   * @param badge 배지 (KO/EN)
   */
  @Schema(description = "제공 언어 배지")
  public record LangBadge(
      @Schema(description = "언어코드", example = "ko") String langCd,
      @Schema(description = "배지", example = "KO") String badge) {
  }

  /**
   * 공지 목록 항목.
   *
   * @param noticeId 공지ID
   * @param categoryCd 분류
   * @param categoryNm 분류명 (사용자 언어)
   * @param tagCd 태그
   * @param tagNm 태그명
   * @param title 제목 (표시 언어 버전)
   * @param displayLangCd 표시 언어 (사용자 언어 → 없으면 대표 언어)
   * @param fallback 대표 언어로 대체 표시 여부
   * @param langs 제공 언어
   * @param postDate 게시일시
   * @param scheduled 예약(게시 전) 여부
   * @param popupYn 팝업여부
   * @param viewCnt 조회수
   * @param fileCnt 첨부 수
   */
  @Schema(description = "공지 목록 항목")
  public record NoticeSummaryResponse(
      Long noticeId, String categoryCd, String categoryNm, String tagCd, String tagNm,
      String title, String displayLangCd, boolean fallback, List<LangBadge> langs,
      LocalDateTime postDate, boolean scheduled, String popupYn, Integer viewCnt, int fileCnt) {
  }

  /**
   * 첨부파일.
   *
   * @param fileId 파일ID
   * @param orgFileNm 원본 파일명
   * @param fileSize 크기
   * @param fileExt 확장자
   */
  @Schema(description = "첨부파일")
  public record NoticeFileResponse(Long fileId, String orgFileNm, Long fileSize, String fileExt) {
  }

  /**
   * 공지 상세.
   *
   * @param noticeId 공지ID
   * @param categoryCd 분류
   * @param categoryNm 분류명
   * @param tagCd 태그
   * @param tagNm 태그명
   * @param baseLangCd 대표 언어
   * @param displayLangCd 표시 언어
   * @param fallback 요청/사용자 언어 버전이 없어 대표 언어로 대체했는지
   * @param langs 제공 언어 (언어 전환용)
   * @param title 제목
   * @param contentHtml 본문 HTML (정화된 값)
   * @param postDate 게시일시
   * @param popupYn 팝업여부
   * @param popupStartDt 팝업 시작일
   * @param popupEndDt 팝업 종료일
   * @param viewCnt 조회수
   * @param files 첨부파일 (게시물 공통)
   * @param createdBy 작성자
   * @param createdAt 작성일시
   * @param updatedAt 수정일시
   */
  @Schema(description = "공지 상세")
  public record NoticeDetailResponse(
      Long noticeId, String categoryCd, String categoryNm, String tagCd, String tagNm,
      String baseLangCd, String displayLangCd, boolean fallback, List<LangBadge> langs,
      String title, String contentHtml, LocalDateTime postDate, String popupYn,
      LocalDate popupStartDt, LocalDate popupEndDt, Integer viewCnt,
      List<NoticeFileResponse> files, String createdBy, LocalDateTime createdAt,
      LocalDateTime updatedAt) {
  }

  /**
   * 언어별 내용 (수정 화면).
   *
   * @param langCd 언어
   * @param title 제목
   * @param contentHtml 본문 HTML
   */
  @Schema(description = "언어별 내용")
  public record NoticeI18nResponse(String langCd, String title, String contentHtml) {
  }

  /**
   * 메일 대상 (명칭은 조회 시점 인사 마스터).
   *
   * @param targetType 대상 유형
   * @param targetCd 코드
   * @param targetNm 명칭 (사용자 언어)
   * @param includeSubYn 하위부서 포함
   */
  @Schema(description = "메일 대상")
  public record MailTargetResponse(
      String targetType, String targetCd, String targetNm, String includeSubYn) {
  }

  /**
   * 공지 수정 화면 (전체 언어 버전 + 메일 설정).
   *
   * @param noticeId 공지ID
   * @param categoryCd 분류
   * @param tagCd 태그
   * @param baseLangCd 대표 언어
   * @param postDate 게시일시
   * @param popupYn 팝업여부
   * @param popupStartDt 팝업 시작일
   * @param popupEndDt 팝업 종료일
   * @param mailSendTypeCd 메일 발송 유형
   * @param mailStatusCd 메일 상태
   * @param mailSendDt 메일 발송일시
   * @param mailChangeable 메일 설정 변경 가능 여부 (발송 중/완료면 false)
   * @param i18n 언어별 내용 (작성한 언어만)
   * @param mailTargets 메일 대상
   * @param files 첨부파일
   */
  @Schema(description = "공지 수정 화면")
  public record NoticeEditResponse(
      Long noticeId, String categoryCd, String tagCd, String baseLangCd, LocalDateTime postDate,
      String popupYn, LocalDate popupStartDt, LocalDate popupEndDt, String mailSendTypeCd,
      String mailStatusCd, LocalDateTime mailSendDt, boolean mailChangeable,
      List<NoticeI18nResponse> i18n, List<MailTargetResponse> mailTargets,
      List<NoticeFileResponse> files) {
  }

  /**
   * 메일 발송 이력.
   *
   * @param histId 이력ID
   * @param receiverId 수신자
   * @param langCd 발송 언어
   * @param sendDt 발송일시
   * @param statusCd 결과
   * @param errorMsg 실패 사유
   */
  @Schema(description = "메일 발송 이력")
  public record NoticeMailHistResponse(
      Long histId, String receiverId, String langCd, LocalDateTime sendDt, String statusCd,
      String errorMsg) {
  }
}
