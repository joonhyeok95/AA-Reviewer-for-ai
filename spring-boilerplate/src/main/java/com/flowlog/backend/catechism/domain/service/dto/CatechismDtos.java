package com.flowlog.backend.catechism.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 교리문답 DTO.
 *
 * <pre>
 * 교리문답 → 단위 트리(부 → 주일 / 장) → 문답(문 번호·질문) → 답 단락 → 근거 성구
 * </pre>
 *
 * @author flowlog
 * @since 2026-11-08
 */
public final class CatechismDtos {

  private CatechismDtos() {
  }

  /**
   * 교리문답 (단위 트리 포함, 문답 본문 제외).
   *
   * @param catechismId 교리문답 ID
   * @param catechismCd 코드
   * @param title 이름
   * @param description 설명
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   * @param units 최상위 단위 (하위는 children)
   */
  @Schema(description = "교리문답 (단위 트리)")
  public record CatechismResponse(
      Long catechismId, String catechismCd, String title, String description, Integer sortOrder,
      String useYn, List<UnitNode> units) {
  }

  /**
   * 단위 트리 노드.
   *
   * @param unitId 단위 ID
   * @param parentUnitId 상위 단위 ID
   * @param unitTypeCd 단위 유형 (공통코드 CATECHISM_UNIT_TYPE)
   * @param unitNo 번호
   * @param title 제목
   * @param sortOrder 정렬순서
   * @param qaCount 문답 수 (이 단위 직속)
   * @param firstQuestionNo 첫 문 번호
   * @param lastQuestionNo 마지막 문 번호
   * @param children 하위 단위
   */
  @Schema(description = "교리문답 단위 (트리)")
  public record UnitNode(
      Long unitId, Long parentUnitId, String unitTypeCd, Integer unitNo, String title,
      Integer sortOrder, long qaCount, Integer firstQuestionNo, Integer lastQuestionNo,
      List<UnitNode> children) {
  }

  /**
   * 교리문답 등록·수정 요청. (수정 시 코드는 바꾸지 않음)
   *
   * @param catechismCd 코드 (영문 대문자·숫자·_)
   * @param title 이름
   * @param description 설명
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   */
  @Schema(description = "교리문답 등록·수정 요청")
  public record CatechismSaveRequest(
      @Schema(example = "HEIDELBERG") @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z0-9_]+$")
      String catechismCd,
      @Schema(example = "하이델베르크 요리문답") @NotBlank @Size(max = 200) String title,
      @Size(max = 1000) String description,
      Integer sortOrder,
      @NotBlank @Pattern(regexp = "^[YN]$") String useYn) {
  }

  /**
   * 단위 상세 (문답·답 단락·근거 성구 포함).
   *
   * @param unitId 단위 ID
   * @param catechismId 교리문답 ID
   * @param catechismTitle 교리문답 이름
   * @param parentUnitId 상위 단위 ID
   * @param unitTypeCd 단위 유형
   * @param unitNo 번호
   * @param title 제목
   * @param sortOrder 정렬순서
   * @param path 최상위부터 이 단위까지 (예: 제3부 → 제45주일)
   * @param qas 문답
   */
  @Schema(description = "교리문답 단위 상세")
  public record UnitDetail(
      Long unitId, Long catechismId, String catechismTitle, Long parentUnitId, String unitTypeCd,
      Integer unitNo, String title, Integer sortOrder, List<PathItem> path, List<QaResponse> qas) {
  }

  /**
   * 단위 경로 항목.
   *
   * @param unitId 단위 ID
   * @param unitTypeCd 단위 유형
   * @param unitNo 번호
   * @param title 제목
   */
  @Schema(description = "단위 경로")
  public record PathItem(Long unitId, String unitTypeCd, Integer unitNo, String title) {
  }

  /**
   * 문답.
   *
   * @param qaId 문답 ID
   * @param questionNo 문 번호
   * @param question 질문
   * @param answers 답 단락
   */
  @Schema(description = "문답")
  public record QaResponse(Long qaId, Integer questionNo, String question,
      List<AnswerResponse> answers) {
  }

  /**
   * 답 단락.
   *
   * @param content 내용
   * @param proofs 근거 성구
   */
  @Schema(description = "답 단락")
  public record AnswerResponse(String content, List<ProofResponse> proofs) {
  }

  /**
   * 근거 성구.
   *
   * @param bibleRef 성경 구절
   * @param bibleText 구절 본문
   */
  @Schema(description = "근거 성구")
  public record ProofResponse(String bibleRef, String bibleText) {
  }

  /**
   * 단위 등록·수정 요청. (문답·답 단락·근거 성구는 순서대로 전체 교체)
   *
   * @param parentUnitId 상위 단위 ID (최상위 null)
   * @param unitTypeCd 단위 유형
   * @param unitNo 번호
   * @param title 제목
   * @param sortOrder 정렬순서
   * @param qas 문답 (부처럼 묶음 단위면 비움)
   */
  @Schema(description = "교리문답 단위 등록·수정 요청")
  public record UnitSaveRequest(
      Long parentUnitId,
      @Schema(example = "LORDS_DAY") @NotBlank @Size(max = 30) String unitTypeCd,
      @Schema(example = "45") @Positive Integer unitNo,
      @Schema(example = "기도에 관하여") @Size(max = 200) String title,
      Integer sortOrder,
      @NotNull @Size(max = 50) List<@Valid QaSaveRequest> qas) {
  }

  /**
   * 문답 요청.
   *
   * @param questionNo 문 번호
   * @param question 질문
   * @param answers 답 단락 (1개 이상)
   */
  @Schema(description = "문답 요청")
  public record QaSaveRequest(
      @Schema(example = "116") @NotNull @Positive Integer questionNo,
      @Schema(example = "왜 그리스도인들은 기도해야 합니까?") @NotBlank @Size(max = 2000) String question,
      @NotEmpty @Size(max = 20) List<@Valid AnswerSaveRequest> answers) {
  }

  /**
   * 답 단락 요청.
   *
   * @param content 내용
   * @param proofs 근거 성구 (선택)
   */
  @Schema(description = "답 단락 요청")
  public record AnswerSaveRequest(
      @NotBlank @Size(max = 4000) String content,
      @Size(max = 30) List<@Valid ProofSaveRequest> proofs) {
  }

  /**
   * 근거 성구 요청.
   *
   * @param bibleRef 성경 구절
   * @param bibleText 구절 본문 (선택)
   */
  @Schema(description = "근거 성구 요청")
  public record ProofSaveRequest(
      @Schema(example = "살전 5:17") @NotBlank @Size(max = 100) String bibleRef,
      @Size(max = 2000) String bibleText) {
  }
}
