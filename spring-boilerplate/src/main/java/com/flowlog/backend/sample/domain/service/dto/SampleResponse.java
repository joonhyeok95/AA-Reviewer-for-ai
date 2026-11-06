package com.flowlog.backend.sample.domain.service.dto;

import com.flowlog.backend.sample.domain.model.Sample;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 샘플 응답.
 *
 * @param id 샘플 ID
 * @param name 샘플명
 * @param description 설명
 * @param useYn 사용여부
 * @param createdBy 생성자
 * @param createdAt 생성일시
 * @param updatedBy 수정자
 * @param updatedAt 수정일시
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "샘플 응답")
public record SampleResponse(
    @Schema(description = "샘플 ID", example = "1") Long id,
    @Schema(description = "샘플명", example = "샘플1") String name,
    @Schema(description = "설명", example = "첫 번째 샘플 데이터") String description,
    @Schema(description = "사용여부(Y/N)", example = "Y") String useYn,
    @Schema(description = "생성자", example = "system") String createdBy,
    @Schema(description = "생성일시") LocalDateTime createdAt,
    @Schema(description = "수정자", example = "system") String updatedBy,
    @Schema(description = "수정일시") LocalDateTime updatedAt) {

  /**
   * Entity 를 응답 DTO 로 변환한다.
   *
   * @param sample 샘플 Entity
   * @return 샘플 응답
   */
  public static SampleResponse from(Sample sample) {
    return new SampleResponse(
        sample.getId(),
        sample.getName(),
        sample.getDescription(),
        sample.getUseYn(),
        sample.getCreatedBy(),
        sample.getCreatedAt(),
        sample.getUpdatedBy(),
        sample.getUpdatedAt());
  }
}
