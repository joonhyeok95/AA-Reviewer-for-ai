package com.flowlog.backend.user.domain.service;

import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.user.domain.model.User;
import com.flowlog.backend.user.domain.repository.UserRepository;
import com.flowlog.backend.user.domain.service.dto.UserLangResponse;
import com.flowlog.backend.user.exception.UserErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 설정 서비스. (사용자 설정 언어)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Service("userSettingService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserSettingService {

  private final UserRepository userRepository;
  private final LangService langService;

  /**
   * 사용자 설정 언어를 변경한다. (화면 표시·공지 조회·메일 수신 언어)
   *
   * @param subject IdP 사용자 고유ID
   * @param langCd 언어코드 (공통코드 LANG, 사용 중)
   * @return 변경된 언어
   * @throws BusinessException 미등록 사용자 (B3001), 사용하지 않는 언어 (B3002)
   */
  @Transactional
  public UserLangResponse changeLang(String subject, String langCd) {
    if (!langService.isActive(langCd)) {
      throw new BusinessException(UserErrorCode.INVALID_LANG, langCd);
    }
    Optional<User> found = StringUtils.isBlank(subject) ? Optional.empty()
        : userRepository.findByIdpSubjectAndUseYn(subject, YesNo.Y);
    User user = found.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_REGISTERED));
    user.changeLang(langCd);
    return new UserLangResponse(user.getUserId(), langCd);
  }
}
