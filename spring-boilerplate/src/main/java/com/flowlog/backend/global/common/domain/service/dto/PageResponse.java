package com.flowlog.backend.global.common.domain.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 페이징 결과. 목록 조회 응답의 {@code result} 에 담긴다.
 *
 * <p>Spring Data {@link Page} 를 그대로 직렬화하지 않고 이 객체로 변환해 응답 형식을 고정한다.
 *
 * @param content 데이터 목록
 * @param page 현재 페이지 (0부터 시작)
 * @param size 페이지 크기
 * @param totalElements 전체 건수
 * @param totalPages 전체 페이지 수
 * @param first 첫 페이지 여부
 * @param last 마지막 페이지 여부
 * @param <T> 데이터 타입
 * @author flowlog
 * @since 2026-11-05
 */
@Schema(description = "페이징 결과")
public record PageResponse<T>(
    @Schema(description = "데이터 목록") List<T> content,
    @Schema(description = "현재 페이지(0부터)", example = "0") int page,
    @Schema(description = "페이지 크기", example = "20") int size,
    @Schema(description = "전체 건수", example = "3") long totalElements,
    @Schema(description = "전체 페이지 수", example = "1") int totalPages,
    @Schema(description = "첫 페이지 여부") boolean first,
    @Schema(description = "마지막 페이지 여부") boolean last) {

  /**
   * Spring Data Page 를 PageResponse 로 변환한다.
   *
   * @param page 조회 결과
   * @param <T> 데이터 타입
   * @return 페이징 결과
   */
  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }

  /**
   * Entity Page 를 DTO 로 변환하며 PageResponse 를 생성한다.
   *
   * @param page 조회 결과
   * @param mapper 변환 함수
   * @param <E> 원본 타입
   * @param <T> 변환 타입
   * @return 페이징 결과
   */
  public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
    return from(page.map(mapper));
  }
}
