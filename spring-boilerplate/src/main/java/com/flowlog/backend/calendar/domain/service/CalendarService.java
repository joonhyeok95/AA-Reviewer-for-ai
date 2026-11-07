package com.flowlog.backend.calendar.domain.service;

import com.flowlog.backend.calendar.domain.model.CalendarEvent;
import com.flowlog.backend.calendar.domain.repository.CalendarEventRepository;
import com.flowlog.backend.calendar.domain.service.dto.CalendarEventResponse;
import com.flowlog.backend.calendar.domain.service.dto.CalendarEventSaveRequest;
import com.flowlog.backend.calendar.domain.service.dto.CalendarOccurrenceResponse;
import com.flowlog.backend.calendar.domain.service.dto.CalendarRangeRequest;
import com.flowlog.backend.calendar.exception.CalendarErrorCode;
import com.flowlog.backend.code.domain.service.CodeService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캘린더(생일·결혼기념일·휴일 등) 서비스.
 *
 * <ul>
 *   <li>기간 조회는 매년 반복 일정을 해마다 펼친 "발생" 목록으로 응답 ({@link CalendarOccurrences})</li>
 *   <li>일정 유형은 공통코드 {@value #EVENT_TYPE_GROUP} 의 사용 중인 코드만 허용</li>
 *   <li>매년 반복 일정은 최대 {@value #MAX_REPEAT_DAYS}일, 그 외는 최대 {@value #MAX_EVENT_DAYS}일</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-07
 */
@Logging
@Service("calendarService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CalendarService {

  /** 일정 유형 공통코드 그룹. */
  public static final String EVENT_TYPE_GROUP = "CAL_EVENT_TYPE";

  /** 매년 반복 일정의 최대 일수. (해마다 겹치지 않도록) */
  public static final int MAX_REPEAT_DAYS = 31;

  /** 반복하지 않는 일정의 최대 일수. */
  public static final int MAX_EVENT_DAYS = 366;

  private final CalendarEventRepository calendarEventRepository;
  private final CodeService codeService;

  /**
   * 기간 안의 일정 발생 목록.
   *
   * @param range 조회 기간
   * @return 발생 목록 (시작일, 유형 정렬순서 무관 - 일정 ID 순)
   * @throws BusinessException 기간이 잘못되었거나 너무 긴 경우 (B7005)
   */
  public List<CalendarOccurrenceResponse> getOccurrences(CalendarRangeRequest range) {
    LocalDate from = range.from();
    LocalDate to = range.to();
    if (to.isBefore(from)
        || ChronoUnit.DAYS.between(from, to) + 1 > CalendarRangeRequest.MAX_DAYS) {
      throw new BusinessException(CalendarErrorCode.RANGE_INVALID,
          String.valueOf(CalendarRangeRequest.MAX_DAYS));
    }
    return calendarEventRepository.findCandidates(from, to).stream()
        .flatMap(event -> CalendarOccurrences.expand(event.getStartDt(), event.getEndDt(),
                YesNo.Y.equals(event.getRepeatYearlyYn()), from, to).stream()
            .map(occurrence -> CalendarOccurrenceResponse.of(event, occurrence)))
        .sorted(Comparator.comparing(CalendarOccurrenceResponse::startDt)
            .thenComparing(CalendarOccurrenceResponse::eventId))
        .toList();
  }

  /**
   * 일정 단건 (원본).
   *
   * @param id 일정 ID
   * @return 일정
   * @throws BusinessException 일정이 없는 경우 (B7001)
   */
  public CalendarEventResponse getEvent(Long id) {
    return CalendarEventResponse.from(findEvent(id));
  }

  /**
   * 일정 등록.
   *
   * @param request 등록 요청
   * @return 등록된 일정
   * @throws BusinessException 유형 코드(B7002)·기간(B7003, B7004) 오류
   */
  @Transactional
  public CalendarEventResponse createEvent(CalendarEventSaveRequest request) {
    validate(request);
    CalendarEvent saved = calendarEventRepository.save(CalendarEvent.builder()
        .eventTypeCd(request.eventTypeCd())
        .title(request.title().strip())
        .startDt(request.startDt())
        .endDt(request.endOrStart())
        .repeatYearlyYn(request.repeatYearlyYn())
        .memo(StringUtils.trimToNull(request.memo()))
        .build());
    return CalendarEventResponse.from(saved);
  }

  /**
   * 일정 수정.
   *
   * @param id 일정 ID
   * @param request 수정 요청
   * @return 수정된 일정
   * @throws BusinessException 일정 없음(B7001), 유형 코드(B7002)·기간(B7003, B7004) 오류
   */
  @Transactional
  public CalendarEventResponse updateEvent(Long id, CalendarEventSaveRequest request) {
    CalendarEvent event = findEvent(id);
    validate(request);
    event.update(request.eventTypeCd(), request.title().strip(), request.startDt(),
        request.endOrStart(), request.repeatYearlyYn(), StringUtils.trimToNull(request.memo()));
    calendarEventRepository.flush();
    return CalendarEventResponse.from(event);
  }

  /**
   * 일정 삭제. (매년 반복 일정은 모든 해의 일정이 함께 삭제된다)
   *
   * @param id 일정 ID
   * @throws BusinessException 일정이 없는 경우 (B7001)
   */
  @Transactional
  public void deleteEvent(Long id) {
    calendarEventRepository.delete(findEvent(id));
  }

  // ================================================================== 내부

  private void validate(CalendarEventSaveRequest request) {
    boolean typeExists = codeService.getCodeItems(EVENT_TYPE_GROUP).stream()
        .anyMatch(item -> item.code().equals(request.eventTypeCd()));
    if (!typeExists) {
      throw new BusinessException(CalendarErrorCode.EVENT_TYPE_INVALID, request.eventTypeCd());
    }
    LocalDate end = request.endOrStart();
    if (end.isBefore(request.startDt())) {
      throw new BusinessException(CalendarErrorCode.EVENT_PERIOD_INVALID);
    }
    int maxDays = YesNo.Y.equals(request.repeatYearlyYn()) ? MAX_REPEAT_DAYS : MAX_EVENT_DAYS;
    if (ChronoUnit.DAYS.between(request.startDt(), end) + 1 > maxDays) {
      throw new BusinessException(CalendarErrorCode.EVENT_PERIOD_TOO_LONG,
          String.valueOf(maxDays));
    }
  }

  private CalendarEvent findEvent(Long id) {
    return calendarEventRepository.findById(id)
        .orElseThrow(() -> new BusinessException(
            CalendarErrorCode.EVENT_NOT_FOUND, String.valueOf(id)));
  }
}
