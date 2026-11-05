package com.flowlog.backend.code.domain.service;

import com.flowlog.backend.code.domain.model.Code;
import com.flowlog.backend.code.domain.model.CodeLang;
import com.flowlog.backend.code.domain.repository.CodeLangRepository;
import com.flowlog.backend.code.domain.repository.CodeRepository;
import com.flowlog.backend.code.domain.service.dto.CodeItem;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.YesNo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공통코드 조회 서비스.
 *
 * <p>요청마다 사용되므로 그룹 단위로 캐시한다. ({@code app.security.authority-cache-ttl} 과 같은 TTL)
 * 코드 변경은 최대 TTL 뒤 반영된다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Service("codeService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CodeService {

  /** 캐시 이름. */
  public static final String CACHE_NAME = "codeItems";

  private final CodeRepository codeRepository;
  private final CodeLangRepository codeLangRepository;

  /**
   * 그룹의 사용 중인 코드 목록 (언어별 이름 포함).
   *
   * @param groupCd 그룹코드
   * @return 코드 목록 (정렬순서)
   */
  @Cacheable(cacheNames = CACHE_NAME, key = "#groupCd")
  public List<CodeItem> getCodeItems(String groupCd) {
    Map<String, Map<String, String>> names = new HashMap<>();
    for (CodeLang lang : codeLangRepository.findByIdGroupCd(groupCd)) {
      names.computeIfAbsent(lang.getId().getCode(), key -> new HashMap<>())
          .put(lang.getId().getLangCd(), lang.getCodeNm());
    }
    return codeRepository.findByIdGroupCdAndUseYnOrderBySortOrderAsc(groupCd, YesNo.Y).stream()
        .map(code -> toItem(code, names.getOrDefault(code.getId().getCode(), Map.of())))
        .toList();
  }

  /**
   * 코드 → 코드 항목 맵. (이름 조회용)
   *
   * @param items 코드 목록
   * @return 코드값 → 코드 항목
   */
  public static Map<String, CodeItem> byCode(List<CodeItem> items) {
    return items.stream().collect(Collectors.toMap(CodeItem::code, item -> item));
  }

  private static CodeItem toItem(Code code, Map<String, String> names) {
    return new CodeItem(code.getId().getGroupCd(), code.getId().getCode(), code.getSortOrder(),
        code.getAttr1(), code.getAttr2(), code.getAttr3(), Map.copyOf(names));
  }
}
