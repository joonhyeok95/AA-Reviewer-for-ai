package com.flowlog.backend.code.domain.service;

import com.flowlog.backend.code.domain.service.dto.CodeItem;
import com.flowlog.backend.code.domain.service.dto.LangResponse;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.security.AuthenticationSubjects;
import com.flowlog.backend.user.domain.model.User;
import com.flowlog.backend.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지원 언어 서비스. (공통코드 LANG, USE_YN='Y' 기준 - 언어 목록을 하드코딩하지 않는다)
 *
 * <ul>
 *   <li>언어코드 : ISO 639-1 (ko, en). 기본 언어 = 정렬순서가 가장 앞선 언어</li>
 *   <li>ATTR1 : 에디터 언어팩 (ko → ko-KR, en → en-US)</li>
 *   <li>새 언어 = LANG 코드 + 코드 다국어 + 메시지 리소스 + 메일 템플릿 등록 (코드 수정 없음)</li>
 * </ul>
 *
 * <p>사용자 언어 결정 : 사용자 설정 언어(TB_USER.LANG_CD) → 요청 Accept-Language → 기본 언어.
 * (사용 중지된 언어는 건너뛴다)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Service("langService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LangService {

  /** 언어 공통코드 그룹. */
  public static final String LANG_GROUP = "LANG";

  /** LANG 코드가 하나도 없을 때의 기본값. */
  private static final String FALLBACK_LANG = "ko";

  private final CodeService codeService;
  private final UserRepository userRepository;

  /**
   * 사용 중인 언어 코드 목록. (정렬순서)
   *
   * @return 언어 코드 항목
   */
  public List<CodeItem> activeLangs() {
    return codeService.getCodeItems(LANG_GROUP);
  }

  /**
   * 기본 언어. (정렬순서 첫 번째)
   *
   * @return 언어코드
   */
  public String defaultLang() {
    List<CodeItem> langs = activeLangs();
    return langs.isEmpty() ? FALLBACK_LANG : langs.get(0).code();
  }

  /**
   * 사용 중인 언어 여부.
   *
   * @param langCd 언어코드
   * @return 사용 중이면 true
   */
  public boolean isActive(String langCd) {
    return StringUtils.isNotBlank(langCd)
        && activeLangs().stream().anyMatch(lang -> lang.code().equals(langCd));
  }

  /**
   * 화면용 언어 목록. (언어 탭, 언어 선택 목록, 에디터 언어팩 매핑)
   *
   * @param displayLang 언어명 표시 언어
   * @return 언어 목록
   */
  public List<LangResponse> getLangs(String displayLang) {
    String defaultLang = defaultLang();
    return activeLangs().stream()
        .map(lang -> new LangResponse(lang.code(), lang.nameOf(displayLang, defaultLang),
            lang.attr1(), badgeOf(lang.code()), lang.code().equals(defaultLang)))
        .toList();
  }

  /**
   * 요청 사용자의 언어.
   *
   * @param authentication 인증 객체 (없으면 Accept-Language → 기본 언어)
   * @param locale 요청 Locale (Accept-Language)
   * @return 언어코드 (항상 사용 중인 언어)
   */
  public String resolveUserLang(Authentication authentication, Locale locale) {
    Optional<String> userLang = findUser(authentication).map(User::getLangCd)
        .filter(this::isActive);
    return userLang.orElseGet(() -> {
      String requested = locale == null ? null : locale.getLanguage();
      return isActive(requested) ? requested : defaultLang();
    });
  }

  /**
   * 언어 배지. (KO, EN)
   *
   * @param langCd 언어코드
   * @return 대문자 언어코드
   */
  public static String badgeOf(String langCd) {
    return StringUtils.upperCase(langCd, Locale.ROOT);
  }

  private Optional<User> findUser(Authentication authentication) {
    String subject = authentication == null ? null
        : AuthenticationSubjects.subjectOf(authentication);
    return StringUtils.isBlank(subject) ? Optional.empty()
        : userRepository.findByIdpSubjectAndUseYn(subject, YesNo.Y);
  }
}
