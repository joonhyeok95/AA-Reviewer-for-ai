package com.flowlog.backend.liturgy.domain.service.dto;

import com.flowlog.backend.liturgy.domain.model.LiturgyLine;
import com.flowlog.backend.liturgy.domain.model.LiturgyText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 전례문 DTO. (사도신경·주기도문·교독문 - 줄마다 화자)
 *
 * @author flowlog
 * @since 2026-11-08
 */
public final class LiturgyDtos {

  private LiturgyDtos() {
  }

  /**
   * 전례문 (줄 포함).
   *
   * @param textId 전례문 ID
   * @param textTypeCd 유형 (공통코드 LITURGY_TYPE)
   * @param title 제목
   * @param versionNm 번역·판
   * @param intro 머리말
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   * @param lines 줄 (순서대로)
   * @param updatedBy 수정자
   * @param updatedAt 수정일시
   */
  @Schema(description = "전례문")
  public record LiturgyTextResponse(
      Long textId, String textTypeCd, String title, String versionNm, String intro,
      Integer sortOrder, String useYn, List<LineResponse> lines, String updatedBy,
      LocalDateTime updatedAt) {

    /**
     * Entity → 응답.
     *
     * @param text 전례문
     * @param lines 줄 (순서대로)
     * @return 응답
     */
    public static LiturgyTextResponse of(LiturgyText text, List<LiturgyLine> lines) {
      return new LiturgyTextResponse(text.getId(), text.getTextTypeCd(), text.getTitle(),
          text.getVersionNm(), text.getIntro(), text.getSortOrder(), text.getUseYn(),
          lines.stream().map(line -> new LineResponse(line.getSpeakerCd(), line.getContent()))
              .toList(),
          text.getUpdatedBy(), text.getUpdatedAt());
    }
  }

  /**
   * 전례문 줄.
   *
   * @param speakerCd 화자 (공통코드 LITURGY_SPEAKER, null = 표시 안 함)
   * @param content 내용
   */
  @Schema(description = "전례문 줄")
  public record LineResponse(String speakerCd, String content) {
  }

  /**
   * 전례문 등록·수정 요청. (줄은 순서대로 전체 교체)
   *
   * @param textTypeCd 유형
   * @param title 제목
   * @param versionNm 번역·판
   * @param intro 머리말
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   * @param lines 줄 (1개 이상)
   */
  @Schema(description = "전례문 등록·수정 요청")
  public record LiturgyTextSaveRequest(
      @Schema(example = "CREED") @NotBlank @Size(max = 30) String textTypeCd,
      @Schema(example = "사도신경") @NotBlank @Size(max = 200) String title,
      @Schema(example = "새번역") @Size(max = 100) String versionNm,
      @Size(max = 1000) String intro,
      Integer sortOrder,
      @NotBlank @Pattern(regexp = "^[YN]$") String useYn,
      @NotEmpty @Size(max = 200) List<@Valid LineSaveRequest> lines) {
  }

  /**
   * 전례문 줄 요청.
   *
   * @param speakerCd 화자 (생략 = 표시 안 함)
   * @param content 내용
   */
  @Schema(description = "전례문 줄 요청")
  public record LineSaveRequest(
      @Schema(example = "LEADER") @Size(max = 30) String speakerCd,
      @Schema(example = "당신은 성부 하나님을 믿습니까?") @NotBlank @Size(max = 2000) String content) {
  }
}
