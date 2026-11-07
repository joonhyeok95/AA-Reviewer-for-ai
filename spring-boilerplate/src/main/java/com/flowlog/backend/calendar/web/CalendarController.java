package com.flowlog.backend.calendar.web;

import com.flowlog.backend.calendar.domain.service.CalendarService;
import com.flowlog.backend.calendar.domain.service.dto.CalendarEventResponse;
import com.flowlog.backend.calendar.domain.service.dto.CalendarEventSaveRequest;
import com.flowlog.backend.calendar.domain.service.dto.CalendarOccurrenceResponse;
import com.flowlog.backend.calendar.domain.service.dto.CalendarRangeRequest;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
 * 캘린더 API - 생일·결혼기념일·휴일 등 일정. (권한 calendar.list/add/edit/del)
 *
 * @author flowlog
 * @since 2026-11-07
 */
@Logging
@Tag(name = "Calendar", description = "캘린더 (생일·결혼기념일·휴일 등 일정, 매년 반복)")
@RestController
@RequestMapping("/api/v1/calendar/events")
@RequiredArgsConstructor
public class CalendarController {

  private final CalendarService calendarService;
  private final MessageHttpResponse messageHttpResponse;

  /**
   * 기간 안의 일정 (매년 반복은 해마다 1건씩 펼쳐서).
   *
   * @param range 조회 기간 (from ~ to, 최대 400일)
   * @return 발생 목록
   */
  @Operation(summary = "기간 일정 조회",
      description = "from~to(포함) 안의 일정을 날짜순으로 조회합니다. 매년 반복 일정은 해마다 1건씩 펼쳐서 응답합니다.")
  @GetMapping
  public ResponseEntity<MessageResponse<List<CalendarOccurrenceResponse>>> getOccurrences(
      @ParameterObject @Valid @ModelAttribute CalendarRangeRequest range) {
    return messageHttpResponse.success(calendarService.getOccurrences(range));
  }

  /**
   * 일정 단건 (원본).
   *
   * @param id 일정 ID
   * @return 일정
   */
  @Operation(summary = "일정 단건 조회")
  @GetMapping("/{id}")
  public ResponseEntity<MessageResponse<CalendarEventResponse>> getEvent(
      @Parameter(description = "일정 ID", example = "1") @PathVariable("id") @Positive Long id) {
    return messageHttpResponse.success(calendarService.getEvent(id));
  }

  /**
   * 일정 등록.
   *
   * @param request 등록 요청
   * @return 등록된 일정 (201)
   */
  @Operation(summary = "일정 등록")
  @PostMapping
  public ResponseEntity<MessageResponse<CalendarEventResponse>> createEvent(
      @Valid @RequestBody CalendarEventSaveRequest request) {
    return messageHttpResponse.success(calendarService.createEvent(request));
  }

  /**
   * 일정 수정.
   *
   * @param id 일정 ID
   * @param request 수정 요청
   * @return 수정된 일정
   */
  @Operation(summary = "일정 수정")
  @PutMapping("/{id}")
  public ResponseEntity<MessageResponse<CalendarEventResponse>> updateEvent(
      @Parameter(description = "일정 ID", example = "1") @PathVariable("id") @Positive Long id,
      @Valid @RequestBody CalendarEventSaveRequest request) {
    return messageHttpResponse.success(calendarService.updateEvent(id, request));
  }

  /**
   * 일정 삭제. (매년 반복 일정은 모든 해 삭제)
   *
   * @param id 일정 ID
   * @return result 없음
   */
  @Operation(summary = "일정 삭제")
  @DeleteMapping("/{id}")
  public ResponseEntity<MessageResponse<Void>> deleteEvent(
      @Parameter(description = "일정 ID", example = "1") @PathVariable("id") @Positive Long id) {
    calendarService.deleteEvent(id);
    return messageHttpResponse.success();
  }
}
