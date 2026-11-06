package com.flowlog.backend.user.domain.service;

import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.util.LikePatterns;
import com.flowlog.backend.user.domain.model.Dept;
import com.flowlog.backend.user.domain.model.User;
import com.flowlog.backend.user.domain.repository.DeptRepository;
import com.flowlog.backend.user.domain.repository.UserDeptRepository;
import com.flowlog.backend.user.domain.repository.UserRepository;
import com.flowlog.backend.user.domain.service.dto.UserSummaryResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 조회 서비스. (권한 부여 대상 검색)
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Logging
@Service("userQueryService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserQueryService {

  /** 검색 최대 건수. */
  public static final int MAX_RESULTS = 20;

  private final UserRepository userRepository;
  private final UserDeptRepository userDeptRepository;
  private final DeptRepository deptRepository;

  /**
   * 사용자 검색. (사용자ID·이름 부분 일치, 사용 중인 사용자, 최대 20건)
   *
   * @param keyword 검색어 (비우면 빈 목록)
   * @param langCd 표시 언어 (부서명)
   * @return 사용자 목록
   */
  public List<UserSummaryResponse> search(String keyword, String langCd) {
    String pattern = LikePatterns.contains(StringUtils.lowerCase(keyword, Locale.ROOT));
    if (pattern == null) {
      return List.of();
    }
    List<User> users = userRepository.search(pattern, PageRequest.of(0, MAX_RESULTS));
    Map<String, String> mainDepts = userDeptRepository
        .findByIdUserIdIn(users.stream().map(User::getUserId).toList()).stream()
        .filter(userDept -> YesNo.isYes(userDept.getMainDeptYn()))
        .collect(Collectors.toMap(userDept -> userDept.getId().getUserId(),
            userDept -> userDept.getId().getDeptCd(), (a, b) -> a));
    Map<String, Dept> depts = deptRepository.findAllById(mainDepts.values()).stream()
        .collect(Collectors.toMap(Dept::getDeptCd, Function.identity()));
    return users.stream()
        .map(user -> {
          Dept dept = depts.get(mainDepts.get(user.getUserId()));
          return new UserSummaryResponse(user.getUserId(), user.getUserNm(), user.getUserTypeCd(),
              dept == null ? null : dept.nameOf(langCd));
        })
        .toList();
  }
}
