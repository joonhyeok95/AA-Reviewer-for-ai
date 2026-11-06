package com.flowlog.backend.notice.web;

import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.security.AuthenticationSubjects;
import com.flowlog.backend.notice.domain.service.dto.NoticeViewer;
import com.flowlog.backend.user.domain.model.User;
import com.flowlog.backend.user.domain.repository.UserRepository;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * 인증 정보 → 공지 조회자. (사용자ID, 사용자 언어, 공지 관리 권한)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Component
@RequiredArgsConstructor
public class NoticeViewerResolver {

  /** 예약 공지까지 볼 수 있는 권한. */
  static final Set<String> MANAGER_AUTHORITIES = Set.of("SCOPE_board.update", "ROLE_SYS_ADMIN");

  private final LangService langService;
  private final UserRepository userRepository;

  /**
   * 조회자 정보.
   *
   * @param authentication 인증 객체
   * @param locale 요청 Locale (사용자 설정 언어가 없을 때)
   * @return 조회자
   */
  public NoticeViewer resolve(Authentication authentication, Locale locale) {
    String subject = AuthenticationSubjects.subjectOf(authentication);
    String userId = StringUtils.isBlank(subject) ? authentication.getName()
        : userRepository.findByIdpSubjectAndUseYn(subject, YesNo.Y).map(User::getUserId)
            .orElse(authentication.getName());
    boolean manager = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority).anyMatch(MANAGER_AUTHORITIES::contains);
    return new NoticeViewer(userId, langService.resolveUserLang(authentication, locale), manager);
  }
}
