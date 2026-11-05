package com.flowlog.backend.global.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * {@link Logging} 이 선언된 클래스/메서드의 실행 정보를 로깅한다.
 *
 * <p>파라미터 값은 개인정보 노출 위험이 있으므로 기록하지 않는다. (DEBUG 레벨에서 메서드명/소요시간만)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

  /**
   * 대상 메서드 실행 전후로 진입/종료/소요시간을 기록한다.
   *
   * @param joinPoint 실행 대상
   * @return 대상 메서드 반환값
   * @throws Throwable 대상 메서드에서 발생한 예외 (그대로 전파)
   */
  @Around("@within(com.flowlog.backend.global.aop.Logging)"
      + " || @annotation(com.flowlog.backend.global.aop.Logging)")
  public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
    String signature = joinPoint.getSignature().toShortString();
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    log.debug("[START] {}", signature);
    try {
      return joinPoint.proceed();
    } catch (Throwable throwable) {
      log.debug("[ERROR] {} - {}", signature, throwable.getClass().getSimpleName());
      throw throwable;
    } finally {
      stopWatch.stop();
      log.debug("[END] {} ({} ms)", signature, stopWatch.getTotalTimeMillis());
    }
  }
}
