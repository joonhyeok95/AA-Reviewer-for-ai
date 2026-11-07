package com.flowlog.backend.liturgy.web;

import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import com.flowlog.backend.liturgy.domain.service.LiturgyService;
import com.flowlog.backend.liturgy.domain.service.dto.LiturgyDtos.LiturgyTextResponse;
import com.flowlog.backend.liturgy.domain.service.dto.LiturgyDtos.LiturgyTextSaveRequest;
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
 * 전례문 API - 사도신경·주기도문·교독문. (권한 liturgy.list/add/edit/del)
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Logging
@Tag(name = "Liturgy", description = "전례문 (사도신경·주기도문·교독문, 화자별 줄)")
@RestController
@RequestMapping("/api/v1/liturgy-texts")
@RequiredArgsConstructor
public class LiturgyController {

  private final LiturgyService liturgyService;
  private final MessageHttpResponse messageHttpResponse;

  /**
   * 전체 전례문 (줄 포함).
   *
   * @return 전례문
   */
  @Operation(summary = "전례문 목록 (줄 포함)")
  @GetMapping
  public ResponseEntity<MessageResponse<List<LiturgyTextResponse>>> getTexts() {
    return messageHttpResponse.success(liturgyService.getTexts());
  }

  /**
   * 전례문 단건.
   *
   * @param id 전례문 ID
   * @return 전례문
   */
  @Operation(summary = "전례문 단건")
  @GetMapping("/{id}")
  public ResponseEntity<MessageResponse<LiturgyTextResponse>> getText(
      @PathVariable("id") @Positive Long id) {
    return messageHttpResponse.success(liturgyService.getText(id));
  }

  /**
   * 전례문 등록.
   *
   * @param request 등록 요청
   * @return 등록된 전례문 (201)
   */
  @Operation(summary = "전례문 등록")
  @PostMapping
  public ResponseEntity<MessageResponse<LiturgyTextResponse>> createText(
      @Valid @RequestBody LiturgyTextSaveRequest request) {
    return messageHttpResponse.success(liturgyService.createText(request));
  }

  /**
   * 전례문 수정. (줄 전체 교체)
   *
   * @param id 전례문 ID
   * @param request 수정 요청
   * @return 수정된 전례문
   */
  @Operation(summary = "전례문 수정 (줄 전체 교체)")
  @PutMapping("/{id}")
  public ResponseEntity<MessageResponse<LiturgyTextResponse>> updateText(
      @PathVariable("id") @Positive Long id, @Valid @RequestBody LiturgyTextSaveRequest request) {
    return messageHttpResponse.success(liturgyService.updateText(id, request));
  }

  /**
   * 전례문 삭제. (주보에서 사용 중이면 409)
   *
   * @param id 전례문 ID
   * @return result 없음
   */
  @Operation(summary = "전례문 삭제")
  @DeleteMapping("/{id}")
  public ResponseEntity<MessageResponse<Void>> deleteText(@PathVariable("id") @Positive Long id) {
    liturgyService.deleteText(id);
    return messageHttpResponse.success();
  }
}
