package com.flowlog.backend.catechism.web;

import com.flowlog.backend.catechism.domain.service.CatechismService;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.CatechismResponse;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.CatechismSaveRequest;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.UnitDetail;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.UnitSaveRequest;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 교리문답 API - 교리문답 → 단위(부·주일) → 문답 → 답 단락 → 근거 성구. (권한 catechism.*)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Logging
@Tag(name = "Catechism", description = "교리문답 (하이델베르크 등 - 단위 트리, 문답, 답 단락, 근거 성구)")
@RestController
@RequestMapping("/api/v1/catechisms")
@RequiredArgsConstructor
public class CatechismController {

  private final CatechismService catechismService;
  private final MessageHttpResponse messageHttpResponse;

  /**
   * 전체 교리문답 (단위 트리).
   *
   * @return 교리문답
   */
  @Operation(summary = "교리문답 목록 (단위 트리, 단위별 문답 수·문 번호 범위)")
  @GetMapping
  public ResponseEntity<MessageResponse<List<CatechismResponse>>> getCatechisms() {
    return messageHttpResponse.success(catechismService.getCatechisms());
  }

  /**
   * 교리문답 등록.
   *
   * @param request 등록 요청
   * @return 등록된 교리문답 (201)
   */
  @Operation(summary = "교리문답 등록")
  @PostMapping
  public ResponseEntity<MessageResponse<CatechismResponse>> createCatechism(
      @Valid @RequestBody CatechismSaveRequest request) {
    return messageHttpResponse.success(catechismService.createCatechism(request));
  }

  /**
   * 교리문답 수정.
   *
   * @param id 교리문답 ID
   * @param request 수정 요청
   * @return 수정된 교리문답
   */
  @Operation(summary = "교리문답 수정 (코드 제외)")
  @PutMapping("/{id}")
  public ResponseEntity<MessageResponse<CatechismResponse>> updateCatechism(
      @PathVariable("id") @Positive Long id, @Valid @RequestBody CatechismSaveRequest request) {
    return messageHttpResponse.success(catechismService.updateCatechism(id, request));
  }

  /**
   * 교리문답 삭제. (단위가 없을 때만)
   *
   * @param id 교리문답 ID
   * @return result 없음
   */
  @Operation(summary = "교리문답 삭제 (단위가 없을 때만)")
  @DeleteMapping("/{id}")
  public ResponseEntity<MessageResponse<Void>> deleteCatechism(
      @PathVariable("id") @Positive Long id) {
    catechismService.deleteCatechism(id);
    return messageHttpResponse.success();
  }

  /**
   * 단위 상세.
   *
   * @param unitId 단위 ID
   * @return 단위 (문답·답 단락·근거 성구)
   */
  @Operation(summary = "단위 상세 (문답·답 단락·근거 성구)")
  @GetMapping("/units/{unitId}")
  public ResponseEntity<MessageResponse<UnitDetail>> getUnit(
      @PathVariable("unitId") @Positive Long unitId) {
    return messageHttpResponse.success(catechismService.getUnit(unitId));
  }

  /**
   * 단위 등록.
   *
   * @param id 교리문답 ID
   * @param request 등록 요청 (문답 포함)
   * @return 등록된 단위 (201)
   */
  @Operation(summary = "단위 등록 (문답·답 단락·근거 성구 포함)")
  @PostMapping("/{id}/units")
  public ResponseEntity<MessageResponse<UnitDetail>> createUnit(
      @PathVariable("id") @Positive Long id, @Valid @RequestBody UnitSaveRequest request) {
    return messageHttpResponse.success(catechismService.createUnit(id, request));
  }

  /**
   * 단위 수정. (문답 이하 전체 교체)
   *
   * @param unitId 단위 ID
   * @param request 수정 요청
   * @return 수정된 단위
   */
  @Operation(summary = "단위 수정 (문답 이하 전체 교체)")
  @PutMapping("/units/{unitId}")
  public ResponseEntity<MessageResponse<UnitDetail>> updateUnit(
      @PathVariable("unitId") @Positive Long unitId,
      @Valid @RequestBody UnitSaveRequest request) {
    return messageHttpResponse.success(catechismService.updateUnit(unitId, request));
  }

  /**
   * 단위 삭제.
   *
   * @param unitId 단위 ID
   * @return result 없음
   */
  @Operation(summary = "단위 삭제 (하위 단위가 없고 주보에서 쓰지 않을 때)")
  @DeleteMapping("/units/{unitId}")
  public ResponseEntity<MessageResponse<Void>> deleteUnit(
      @PathVariable("unitId") @Positive Long unitId) {
    catechismService.deleteUnit(unitId);
    return messageHttpResponse.success();
  }
}
