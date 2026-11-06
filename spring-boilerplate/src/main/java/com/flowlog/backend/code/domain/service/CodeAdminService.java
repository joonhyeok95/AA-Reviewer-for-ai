package com.flowlog.backend.code.domain.service;

import com.flowlog.backend.code.domain.model.Code;
import com.flowlog.backend.code.domain.model.CodeGroup;
import com.flowlog.backend.code.domain.model.CodeGroupLang;
import com.flowlog.backend.code.domain.model.CodeId;
import com.flowlog.backend.code.domain.model.CodeLang;
import com.flowlog.backend.code.domain.repository.CodeGroupLangRepository;
import com.flowlog.backend.code.domain.repository.CodeGroupRepository;
import com.flowlog.backend.code.domain.repository.CodeLangRepository;
import com.flowlog.backend.code.domain.repository.CodeRepository;
import com.flowlog.backend.code.domain.service.dto.CodeAdminDtos.CodeAdminResponse;
import com.flowlog.backend.code.domain.service.dto.CodeAdminDtos.CodeGroupAdminResponse;
import com.flowlog.backend.code.domain.service.dto.CodeAdminDtos.CodeGroupSaveRequest;
import com.flowlog.backend.code.domain.service.dto.CodeAdminDtos.CodeSaveRequest;
import com.flowlog.backend.code.exception.CodeErrorCode;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.exception.BusinessException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공통코드 관리 서비스. (그룹·코드 등록/수정/삭제, 언어별 이름)
 *
 * <ul>
 *   <li>시스템 그룹(SYSTEM_YN=Y)은 삭제 불가, 그 그룹의 코드도 삭제 불가 (사용 여부로 관리)</li>
 *   <li>언어(LANG) 그룹은 사용 중인 코드가 최소 1개 남아야 한다</li>
 *   <li>변경 시 공통코드 캐시({@link CodeService#CACHE_NAME}) 전체 삭제 - 지원 언어 등 즉시 반영</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Logging
@Service("codeAdminService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CodeAdminService {

  private final CodeGroupRepository codeGroupRepository;
  private final CodeGroupLangRepository codeGroupLangRepository;
  private final CodeRepository codeRepository;
  private final CodeLangRepository codeLangRepository;
  private final LangService langService;

  /**
   * 전체 그룹 (미사용 포함).
   *
   * @param langCd 표시 언어
   * @return 그룹 목록
   */
  public List<CodeGroupAdminResponse> getGroups(String langCd) {
    Map<String, Map<String, String>> names = new HashMap<>();
    for (CodeGroupLang lang : codeGroupLangRepository.findAll()) {
      names.computeIfAbsent(lang.getId().getGroupCd(), key -> new LinkedHashMap<>())
          .put(lang.getId().getLangCd(), lang.getGroupNm());
    }
    Map<String, Long> counts = codeRepository.countByGroup().stream()
        .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    return codeGroupRepository.findAllByOrderBySortOrderAscGroupCdAsc().stream()
        .map(group -> toResponse(group, names.getOrDefault(group.getGroupCd(), Map.of()),
            counts.getOrDefault(group.getGroupCd(), 0L), langCd))
        .toList();
  }

  /**
   * 그룹 등록.
   *
   * @param request 요청
   * @param langCd 표시 언어
   * @return 등록된 그룹
   */
  @Transactional
  @CacheEvict(cacheNames = CodeService.CACHE_NAME, allEntries = true)
  public CodeGroupAdminResponse createGroup(CodeGroupSaveRequest request, String langCd) {
    if (codeGroupRepository.existsById(request.groupCd())) {
      throw new BusinessException(CodeErrorCode.GROUP_DUPLICATED, request.groupCd());
    }
    Map<String, String> names = langService.normalizeNames(request.names());
    CodeGroup group = codeGroupRepository.save(CodeGroup.builder()
        .groupCd(request.groupCd())
        .groupDesc(StringUtils.trimToNull(request.groupDesc()))
        .sortOrder(request.sortOrder())
        .systemYn(YesNo.N)
        .useYn(request.useYn())
        .build());
    saveGroupNames(group.getGroupCd(), names);
    return toResponse(group, names, 0L, langCd);
  }

  /**
   * 그룹 수정. (그룹코드·시스템 여부는 바꾸지 않음)
   *
   * @param groupCd 그룹코드
   * @param request 요청
   * @param langCd 표시 언어
   * @return 수정된 그룹
   */
  @Transactional
  @CacheEvict(cacheNames = CodeService.CACHE_NAME, allEntries = true)
  public CodeGroupAdminResponse updateGroup(
      String groupCd, CodeGroupSaveRequest request, String langCd) {
    CodeGroup group = findGroup(groupCd);
    Map<String, String> names = langService.normalizeNames(request.names());
    group.update(StringUtils.trimToNull(request.groupDesc()), request.sortOrder(),
        request.useYn());
    saveGroupNames(groupCd, names);
    long codeCnt = codeRepository.findByIdGroupCdOrderBySortOrderAscIdCodeAsc(groupCd).size();
    return toResponse(group, names, codeCnt, langCd);
  }

  /**
   * 그룹 삭제. (코드·언어별 이름 포함, 시스템 그룹 불가)
   *
   * @param groupCd 그룹코드
   */
  @Transactional
  @CacheEvict(cacheNames = CodeService.CACHE_NAME, allEntries = true)
  public void deleteGroup(String groupCd) {
    CodeGroup group = findGroup(groupCd);
    if (YesNo.isYes(group.getSystemYn())) {
      throw new BusinessException(CodeErrorCode.SYSTEM_GROUP_PROTECTED, groupCd);
    }
    codeLangRepository.deleteAll(codeLangRepository.findByIdGroupCd(groupCd));
    codeRepository.deleteAll(codeRepository.findByIdGroupCdOrderBySortOrderAscIdCodeAsc(groupCd));
    codeGroupLangRepository.deleteAll(codeGroupLangRepository.findByIdGroupCd(groupCd));
    codeGroupRepository.delete(group);
  }

  /**
   * 그룹의 전체 코드 (미사용 포함).
   *
   * @param groupCd 그룹코드
   * @param langCd 표시 언어
   * @return 코드 목록
   */
  public List<CodeAdminResponse> getCodes(String groupCd, String langCd) {
    findGroup(groupCd);
    Map<String, Map<String, String>> names = codeNames(groupCd);
    return codeRepository.findByIdGroupCdOrderBySortOrderAscIdCodeAsc(groupCd).stream()
        .map(code -> toResponse(code, names.getOrDefault(code.getId().getCode(), Map.of()),
            langCd))
        .toList();
  }

  /**
   * 코드 등록.
   *
   * @param groupCd 그룹코드
   * @param request 요청
   * @param langCd 표시 언어
   * @return 등록된 코드
   */
  @Transactional
  @CacheEvict(cacheNames = CodeService.CACHE_NAME, allEntries = true)
  public CodeAdminResponse createCode(String groupCd, CodeSaveRequest request, String langCd) {
    findGroup(groupCd);
    if (codeRepository.existsById(new CodeId(groupCd, request.code()))) {
      throw new BusinessException(CodeErrorCode.CODE_DUPLICATED, groupCd, request.code());
    }
    Map<String, String> names = langService.normalizeNames(request.names());
    Code code = codeRepository.save(Code.builder()
        .groupCd(groupCd)
        .code(request.code())
        .codeDesc(StringUtils.trimToNull(request.codeDesc()))
        .sortOrder(request.sortOrder())
        .attr1(StringUtils.trimToNull(request.attr1()))
        .attr2(StringUtils.trimToNull(request.attr2()))
        .attr3(StringUtils.trimToNull(request.attr3()))
        .useYn(request.useYn())
        .build());
    saveCodeNames(groupCd, request.code(), names);
    return toResponse(code, names, langCd);
  }

  /**
   * 코드 수정. (코드값은 바꾸지 않음)
   *
   * @param groupCd 그룹코드
   * @param codeValue 코드
   * @param request 요청
   * @param langCd 표시 언어
   * @return 수정된 코드
   */
  @Transactional
  @CacheEvict(cacheNames = CodeService.CACHE_NAME, allEntries = true)
  public CodeAdminResponse updateCode(
      String groupCd, String codeValue, CodeSaveRequest request, String langCd) {
    Code code = findCode(groupCd, codeValue);
    Map<String, String> names = langService.normalizeNames(request.names());
    if (YesNo.isYes(code.getUseYn()) && !YesNo.isYes(request.useYn())) {
      requireOtherActiveLang(code);
    }
    code.update(StringUtils.trimToNull(request.codeDesc()), request.sortOrder(),
        StringUtils.trimToNull(request.attr1()), StringUtils.trimToNull(request.attr2()),
        StringUtils.trimToNull(request.attr3()), request.useYn());
    saveCodeNames(groupCd, codeValue, names);
    return toResponse(code, names, langCd);
  }

  /**
   * 코드 삭제. (시스템 그룹의 코드는 불가 - 사용 여부로 관리)
   *
   * @param groupCd 그룹코드
   * @param codeValue 코드
   */
  @Transactional
  @CacheEvict(cacheNames = CodeService.CACHE_NAME, allEntries = true)
  public void deleteCode(String groupCd, String codeValue) {
    CodeGroup group = findGroup(groupCd);
    Code code = findCode(groupCd, codeValue);
    if (YesNo.isYes(group.getSystemYn())) {
      throw new BusinessException(CodeErrorCode.SYSTEM_CODE_PROTECTED, groupCd, codeValue);
    }
    codeLangRepository.deleteAll(codeLangRepository.findByIdGroupCdAndIdCode(groupCd, codeValue));
    codeRepository.delete(code);
  }

  private void requireOtherActiveLang(Code code) {
    if (LangService.LANG_GROUP.equals(code.getId().getGroupCd())) {
      boolean otherActive = codeRepository
          .findByIdGroupCdAndUseYnOrderBySortOrderAsc(LangService.LANG_GROUP, YesNo.Y).stream()
          .anyMatch(lang -> !lang.getId().getCode().equals(code.getId().getCode()));
      if (!otherActive) {
        throw new BusinessException(CodeErrorCode.LAST_LANG_REQUIRED);
      }
    }
  }

  /**
   * 언어별 이름 저장 - 기존 행은 이름만 바꾸고(같은 키 삭제→삽입 시 flush 순서로 PK 충돌),
   * 요청에서 빠진 사용 중 언어는 삭제.
   */
  private void saveGroupNames(String groupCd, Map<String, String> names) {
    Map<String, CodeGroupLang> existing = codeGroupLangRepository.findByIdGroupCd(groupCd).stream()
        .collect(Collectors.toMap(lang -> lang.getId().getLangCd(), lang -> lang));
    names.forEach((langCd, name) -> {
      CodeGroupLang lang = existing.remove(langCd);
      if (lang == null) {
        codeGroupLangRepository.save(new CodeGroupLang(groupCd, langCd, name));
      } else {
        lang.changeName(name);
      }
    });
    codeGroupLangRepository.deleteAll(activeOnly(existing));
  }

  private void saveCodeNames(String groupCd, String code, Map<String, String> names) {
    Map<String, CodeLang> existing = codeLangRepository.findByIdGroupCdAndIdCode(groupCd, code)
        .stream().collect(Collectors.toMap(lang -> lang.getId().getLangCd(), lang -> lang));
    names.forEach((langCd, name) -> {
      CodeLang lang = existing.remove(langCd);
      if (lang == null) {
        codeLangRepository.save(new CodeLang(groupCd, code, langCd, name));
      } else {
        lang.changeName(name);
      }
    });
    codeLangRepository.deleteAll(activeOnly(existing));
  }

  /** 삭제 대상 = 요청에서 빠진 "사용 중" 언어만 (미사용 언어 이름은 보존). */
  private <T> List<T> activeOnly(Map<String, T> existing) {
    return existing.entrySet().stream()
        .filter(entry -> langService.isActive(entry.getKey()))
        .map(Map.Entry::getValue)
        .toList();
  }

  private Map<String, Map<String, String>> codeNames(String groupCd) {
    Map<String, Map<String, String>> names = new HashMap<>();
    for (CodeLang lang : codeLangRepository.findByIdGroupCd(groupCd)) {
      names.computeIfAbsent(lang.getId().getCode(), key -> new LinkedHashMap<>())
          .put(lang.getId().getLangCd(), lang.getCodeNm());
    }
    return names;
  }

  private CodeGroup findGroup(String groupCd) {
    return codeGroupRepository.findById(groupCd)
        .orElseThrow(() -> new BusinessException(CodeErrorCode.GROUP_NOT_FOUND, groupCd));
  }

  private Code findCode(String groupCd, String code) {
    return codeRepository.findById(new CodeId(groupCd, code))
        .orElseThrow(() -> new BusinessException(CodeErrorCode.CODE_NOT_FOUND, groupCd, code));
  }

  private CodeGroupAdminResponse toResponse(
      CodeGroup group, Map<String, String> names, long codeCnt, String langCd) {
    return new CodeGroupAdminResponse(group.getGroupCd(), langService.nameOf(names, langCd),
        names, group.getGroupDesc(), group.getSortOrder(), group.getSystemYn(), group.getUseYn(),
        codeCnt);
  }

  private CodeAdminResponse toResponse(Code code, Map<String, String> names, String langCd) {
    return new CodeAdminResponse(code.getId().getGroupCd(), code.getId().getCode(),
        langService.nameOf(names, langCd), names, code.getCodeDesc(), code.getSortOrder(),
        code.getAttr1(), code.getAttr2(), code.getAttr3(), code.getUseYn());
  }
}
