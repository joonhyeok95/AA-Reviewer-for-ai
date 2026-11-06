package com.flowlog.backend.global.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드 진입/종료/소요시간 로깅 대상 표시 어노테이션.
 *
 * <p>Controller, Service 클래스에는 반드시 선언한다. ({@link LoggingAspect} 참고)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Logging {
}
