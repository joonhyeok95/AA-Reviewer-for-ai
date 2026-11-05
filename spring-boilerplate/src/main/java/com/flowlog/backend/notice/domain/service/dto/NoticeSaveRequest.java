package com.flowlog.backend.notice.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 공지 등록/수정 요청.
 *
 * <ul>
 *   <li>{@code i18n} : 언어 탭 순서대로 전달. 제목·본문이 모두 빈 언어는 입력하지 않은 것으로 본다.
 *       최소 1개 언어는 제목·본문 필수 (NT-61)</li>
 *   <li>{@code baseLangCd} : 대표 언어. 없으면 처음 입력한 언어 (NT-62)</li>
 *   <li>{@code postDate} : 게시일시. 없으면 즉시, 미래면 예약 게시</li>
 * </ul>
 *
 * @param categoryCd 분류 (NOTICE_CATEGORY)
 * @param tagCd 태그 (NOTICE_TAG, 선택)
 * @param baseLangCd 대표 언어 (선택)
 * @param postDate 게시일시 (선택)
 * @param popupYn 팝업여부
 * @param popupStartDt 팝업 시작일
 * @param popupEndDt 팝업 종료일
 * @param mailSendTypeCd 메일 발송 유형 (MAIL_SEND_TYPE, 기본 NONE)
 * @param i18n 언어별 제목/본문
 * @param mailTargets 메일 대상 (사업장/부서 코드)
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "공지 등록/수정 요청")
public record NoticeSaveRequest(
    @Schema(description = "분류", example = "GENERAL") @NotBlank @Size(max = 50) String categoryCd,
    @Schema(description = "태그", example = "IMPORTANT") @Size(max = 50) String tagCd,
    @Schema(description = "대표 언어 (없으면 처음 입력한 언어)", example = "ko") @Size(max = 10)
    String baseLangCd,
    @Schema(description = "게시일시 (없으면 즉시)", example = "2026-11-05T09:00:00")
    LocalDateTime postDate,
    @Schema(description = "팝업여부", example = "N") @Pattern(regexp = "^[YN]$") String popupYn,
    @Schema(description = "팝업 시작일", example = "2026-11-05") LocalDate popupStartDt,
    @Schema(description = "팝업 종료일", example = "2026-11-30") LocalDate popupEndDt,
    @Schema(description = "메일 발송 유형", example = "NONE") @Size(max = 50) String mailSendTypeCd,
    @Schema(description = "언어별 제목/본문 (탭 순서)") @NotEmpty @Size(max = 20)
    List<@Valid NoticeI18nRequest> i18n,
    @Schema(description = "메일 대상") @Size(max = 200) List<@Valid MailTargetRequest> mailTargets) {

  /**
   * 언어별 제목/본문.
   *
   * @param langCd 언어코드 (LANG)
   * @param title 제목
   * @param contentHtml 본문 HTML (저장 시 정화)
   */
  @Schema(description = "언어별 제목/본문")
  public record NoticeI18nRequest(
      @Schema(description = "언어", example = "ko") @NotBlank @Size(max = 10) String langCd,
      @Schema(description = "제목", example = "시스템 점검 안내") @Size(max = 500) String title,
      @Schema(description = "본문 HTML", example = "<p>점검이 진행됩니다.</p>")
      @Size(max = 1_000_000) String contentHtml) {
  }

  /**
   * 메일 대상.
   *
   * @param targetType 대상 유형 (WORKPLACE / DEPT)
   * @param targetCd 사업장/부서 코드 (인사 마스터)
   * @param includeSubYn 하위부서 포함 (DEPT, 기본 Y)
   */
  @Schema(description = "메일 대상")
  public record MailTargetRequest(
      @Schema(description = "대상 유형", example = "DEPT") @NotBlank
      @Pattern(regexp = "^(WORKPLACE|DEPT)$") String targetType,
      @Schema(description = "사업장/부서 코드", example = "TF_TEAM") @NotBlank @Size(max = 50)
      String targetCd,
      @Schema(description = "하위부서 포함", example = "Y") @Pattern(regexp = "^[YN]$")
      String includeSubYn) {
  }
}
