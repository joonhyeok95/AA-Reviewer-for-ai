package com.flowlog.backend.bulletin.domain.service.dto;

import com.flowlog.backend.bulletin.domain.model.Bulletin;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.UnitDetail;
import com.flowlog.backend.liturgy.domain.service.dto.LiturgyDtos.LiturgyTextResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주보 DTO.
 *
 * <p>예배순서·교회소식·섬김위원은 응답과 저장 요청이 같은 구조 (저장 시 순서대로 전체 교체).
 *
 * @author flowlog
 * @since 2026-11-08
 */
public final class BulletinDtos {

  private BulletinDtos() {
  }

  /**
   * 주보 목록 항목.
   *
   * @param bulletinId 주보 ID
   * @param worshipDt 예배일
   * @param volumeNo 권
   * @param issueNo 호
   * @param title 특별 주일 이름
   * @param sermonTitle 설교 제목
   * @param sermonScripture 설교 본문
   * @param preacherNm 설교자
   * @param statusCd 상태
   * @param hasPhoto 대표사진 여부 ({@code GET /api/v1/bulletins/{id}/photo})
   * @param updatedBy 수정자
   * @param updatedAt 수정일시
   */
  @Schema(description = "주보 목록 항목")
  public record BulletinSummary(
      Long bulletinId, LocalDate worshipDt, Integer volumeNo, Integer issueNo, String title,
      String sermonTitle, String sermonScripture, String preacherNm, String statusCd,
      boolean hasPhoto, String updatedBy, LocalDateTime updatedAt) {

    /**
     * Entity → 목록 항목.
     *
     * @param bulletin 주보
     * @return 목록 항목
     */
    public static BulletinSummary from(Bulletin bulletin) {
      return new BulletinSummary(bulletin.getId(), bulletin.getWorshipDt(),
          bulletin.getVolumeNo(), bulletin.getIssueNo(), bulletin.getTitle(),
          bulletin.getSermonTitle(), bulletin.getSermonScripture(), bulletin.getPreacherNm(),
          bulletin.getStatusCd(), bulletin.hasPhoto(), bulletin.getUpdatedBy(),
          bulletin.getUpdatedAt());
    }
  }

  /**
   * 주보 상세 (표시용 - 교리문답·전례문 본문 포함).
   *
   * @param bulletinId 주보 ID
   * @param worshipDt 예배일
   * @param volumeNo 권
   * @param issueNo 호
   * @param title 특별 주일 이름
   * @param themeVerse 표지 말씀
   * @param themeVerseRef 표지 말씀 출처
   * @param sermonTitle 설교 제목
   * @param sermonScripture 설교 본문
   * @param preacherNm 설교자
   * @param sermonMemo 말씀메모
   * @param statusCd 상태
   * @param hasPhoto 대표사진 여부 ({@code GET /api/v1/bulletins/{id}/photo})
   * @param orders 예배순서
   * @param news 교회소식 (섹션 → 항목 → 하위 항목)
   * @param duties 섬김위원 (역할 순서, 날짜 순)
   * @param catechismUnits 교리문답 단위 (문답 포함)
   * @param liturgyTexts 전례문 (줄 포함)
   * @param updatedBy 수정자
   * @param updatedAt 수정일시
   */
  @Schema(description = "주보 상세")
  public record BulletinDetail(
      Long bulletinId, LocalDate worshipDt, Integer volumeNo, Integer issueNo, String title,
      String themeVerse, String themeVerseRef, String sermonTitle, String sermonScripture,
      String preacherNm, String sermonMemo, String statusCd, boolean hasPhoto,
      List<OrderItem> orders,
      List<NewsNode> news, List<DutyItem> duties, List<UnitDetail> catechismUnits,
      List<LiturgyTextResponse> liturgyTexts, String updatedBy, LocalDateTime updatedAt) {
  }

  /**
   * 예배순서 항목.
   *
   * @param itemNm 항목 (예: 찬양)
   * @param detail 담당·내용 (예: 8장)
   */
  @Schema(description = "예배순서 항목")
  public record OrderItem(
      @Schema(example = "성경봉독") @NotBlank @Size(max = 100) String itemNm,
      @Schema(example = "야고보서 5:12-20") @Size(max = 500) String detail) {
  }

  /**
   * 교회소식 노드. (섹션 → 항목 → 하위 항목, 최대 {@value #MAX_DEPTH}단계)
   *
   * @param title 제목 (섹션 : 기도제목 / 항목 : 금요기도회)
   * @param content 내용 (여러 줄)
   * @param scheduleText 일정 표시 (예: 매주, 11/14)
   * @param highlightYn 강조 여부
   * @param children 하위 항목
   */
  @Schema(description = "교회소식 (섹션 → 항목 → 하위 항목)")
  public record NewsNode(
      @Schema(example = "행사 및 일정") @Size(max = 200) String title,
      @Size(max = 4000) String content,
      @Schema(example = "매주") @Size(max = 100) String scheduleText,
      @Pattern(regexp = "^[YN]$") String highlightYn,
      @Size(max = 100) List<@Valid NewsNode> children) {

    /** 최대 단계 (섹션 1 → 항목 2 → 하위 항목 3). */
    public static final int MAX_DEPTH = 3;
  }

  /**
   * 섬김위원 칸. (역할 × 날짜 = 담당자)
   *
   * @param roleNm 역할 (예: 식사)
   * @param dutyDt 날짜
   * @param members 담당자 (예: 가람, 나래)
   */
  @Schema(description = "섬김위원 칸")
  public record DutyItem(
      @Schema(example = "식사") @NotBlank @Size(max = 50) String roleNm,
      @Schema(example = "2026-11-08") @NotNull LocalDate dutyDt,
      @Schema(example = "김OO, 이OO") @Size(max = 200) String members) {
  }

  /**
   * 주보 등록·수정 요청. (하위는 순서대로 전체 교체)
   *
   * @param worshipDt 예배일
   * @param volumeNo 권
   * @param issueNo 호
   * @param title 특별 주일 이름
   * @param themeVerse 표지 말씀
   * @param themeVerseRef 표지 말씀 출처
   * @param sermonTitle 설교 제목
   * @param sermonScripture 설교 본문
   * @param preacherNm 설교자
   * @param sermonMemo 말씀메모
   * @param statusCd 상태 (DRAFT / PUBLISHED)
   * @param orders 예배순서
   * @param news 교회소식
   * @param duties 섬김위원 (역할 순서 = 처음 나온 순서)
   * @param catechismUnitIds 교리문답 단위 ID (순서대로)
   * @param liturgyTextIds 전례문 ID (순서대로)
   */
  @Schema(description = "주보 등록·수정 요청")
  public record BulletinSaveRequest(
      @Schema(example = "2026-11-08") @NotNull LocalDate worshipDt,
      @Schema(example = "9") @Positive Integer volumeNo,
      @Schema(example = "45") @Positive Integer issueNo,
      @Size(max = 200) String title,
      @Size(max = 2000) String themeVerse,
      @Schema(example = "엡 2:15-16") @Size(max = 100) String themeVerseRef,
      @Size(max = 200) String sermonTitle,
      @Schema(example = "야고보서 5:12-20") @Size(max = 100) String sermonScripture,
      @Size(max = 100) String preacherNm,
      @Size(max = 4000) String sermonMemo,
      @Schema(example = "PUBLISHED") @NotBlank @Size(max = 30) String statusCd,
      @NotNull @Size(max = 50) List<@Valid OrderItem> orders,
      @NotNull @Size(max = 50) List<@Valid NewsNode> news,
      @NotNull @Size(max = 200) List<@Valid DutyItem> duties,
      @NotNull @Size(max = 10) List<@NotNull Long> catechismUnitIds,
      @NotNull @Size(max = 10) List<@NotNull Long> liturgyTextIds) {
  }
}
