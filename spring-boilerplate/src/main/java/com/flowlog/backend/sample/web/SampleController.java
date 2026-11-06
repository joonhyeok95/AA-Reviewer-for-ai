package com.flowlog.backend.sample.web;

import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.domain.service.dto.PageResponse;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import com.flowlog.backend.sample.domain.service.SampleService;
import com.flowlog.backend.sample.domain.service.dto.SampleCreateRequest;
import com.flowlog.backend.sample.domain.service.dto.SampleResponse;
import com.flowlog.backend.sample.domain.service.dto.SampleSearchRequest;
import com.flowlog.backend.sample.domain.service.dto.SampleUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 샘플 API. (boilerplate 예시 - 신규 API 작성 시 참고)
 *
 * <ul>
 *   <li>Controller 는 요청 바인딩/검증과 응답 생성만 담당하고 비즈니스 로직은 Service 에 둔다.</li>
 *   <li>응답은 항상 {@link MessageHttpResponse} 로 생성한다.</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Tag(name = "Sample", description = "샘플 API (boilerplate 예시)")
@RestController
@RequestMapping("/api/v1/samples")
@RequiredArgsConstructor
public class SampleController {

  private final SampleService sampleService;
  private final MessageHttpResponse messageHttpResponse;

  /**
   * 샘플 목록 조회.
   *
   * @param condition 검색 조건
   * @param pageable 페이징 (page=0부터, size 기본 20, sort 기본 id,desc)
   * @return 페이징 결과
   */
  @Operation(summary = "샘플 목록 조회", description = "검색 조건과 페이징으로 샘플 목록을 조회합니다.")
  @GetMapping
  public ResponseEntity<MessageResponse<PageResponse<SampleResponse>>> getSamples(
      @ParameterObject @Valid @ModelAttribute SampleSearchRequest condition,
      @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
      Pageable pageable) {
    return messageHttpResponse.successPage(sampleService.getSamples(condition, pageable));
  }

  /**
   * 샘플 단건 조회.
   *
   * @param id 샘플 ID
   * @return 샘플
   */
  @Operation(summary = "샘플 단건 조회")
  @GetMapping("/{id}")
  public ResponseEntity<MessageResponse<SampleResponse>> getSample(
      @Parameter(description = "샘플 ID", example = "1") @PathVariable("id") @Positive Long id) {
    return messageHttpResponse.success(sampleService.getSample(id));
  }

  /**
   * 샘플 등록.
   *
   * @param request 등록 요청
   * @return 등록된 샘플 (201)
   */
  @Operation(summary = "샘플 등록")
  @PostMapping
  public ResponseEntity<MessageResponse<SampleResponse>> createSample(
      @Valid @RequestBody SampleCreateRequest request) {
    return messageHttpResponse.success(sampleService.createSample(request));
  }

  /**
   * 샘플 수정.
   *
   * @param id 샘플 ID
   * @param request 수정 요청
   * @return 수정된 샘플
   */
  @Operation(summary = "샘플 수정")
  @PutMapping("/{id}")
  public ResponseEntity<MessageResponse<SampleResponse>> updateSample(
      @Parameter(description = "샘플 ID", example = "1") @PathVariable("id") @Positive Long id,
      @Valid @RequestBody SampleUpdateRequest request) {
    return messageHttpResponse.success(sampleService.updateSample(id, request));
  }

  /**
   * 샘플 삭제.
   *
   * @param id 샘플 ID
   * @return result 없음
   */
  @Operation(summary = "샘플 삭제")
  @DeleteMapping("/{id}")
  public ResponseEntity<MessageResponse<Void>> deleteSample(
      @Parameter(description = "샘플 ID", example = "1") @PathVariable("id") @Positive Long id) {
    sampleService.deleteSample(id);
    return messageHttpResponse.success();
  }
}
