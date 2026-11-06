package com.flowlog.gateway.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 경로 차단 설정. ({@code app.gateway.*})
 *
 * <p>{@code blockedPaths} : 외부에 노출하면 안 되는 백엔드 경로. (대소문자 무시, PathPattern 형식)
 * 기본값은 백엔드 H2 콘솔과 Actuator. Gateway 를 거치면 백엔드 입장에서 접속지가 Gateway(내부)가
 * 되므로, 백엔드의 "localhost 만 허용" 류 보호가 무력화된다. 그래서 Gateway 에서 막는다.
 *
 * @param blockedPaths 차단 경로 패턴 목록
 * @author flowlog
 * @since 2026-11-05
 */
@ConfigurationProperties(prefix = "app.gateway")
public record PathSecurityProperties(
    @DefaultValue({"/cm/h2-console/**", "/cm/actuator/**"}) List<String> blockedPaths) {
}
