package com.flowlog.backend.liturgy.domain.service;

import com.flowlog.backend.bulletin.domain.repository.BulletinTextRepository;
import com.flowlog.backend.code.domain.service.CodeService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.liturgy.domain.model.LiturgyLine;
import com.flowlog.backend.liturgy.domain.model.LiturgyText;
import com.flowlog.backend.liturgy.domain.repository.LiturgyLineRepository;
import com.flowlog.backend.liturgy.domain.repository.LiturgyTextRepository;
import com.flowlog.backend.liturgy.domain.service.dto.LiturgyDtos.LineSaveRequest;
import com.flowlog.backend.liturgy.domain.service.dto.LiturgyDtos.LiturgyTextResponse;
import com.flowlog.backend.liturgy.domain.service.dto.LiturgyDtos.LiturgyTextSaveRequest;
import com.flowlog.backend.liturgy.exception.LiturgyErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전례문 서비스. (사도신경·주기도문·교독문)
 *
 * <ul>
 *   <li>유형은 공통코드 {@value #TYPE_GROUP}, 화자는 {@value #SPEAKER_GROUP}</li>
 *   <li>줄은 저장할 때 순서대로 전체 교체</li>
 *   <li>주보에서 쓰는 전례문은 삭제할 수 없다 (사용 안 함으로 바꿔 숨긴다)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Logging
@Service("liturgyService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LiturgyService {

  /** 전례문 유형 공통코드 그룹. */
  public static final String TYPE_GROUP = "LITURGY_TYPE";

  /** 화자 공통코드 그룹. */
  public static final String SPEAKER_GROUP = "LITURGY_SPEAKER";

  private final LiturgyTextRepository liturgyTextRepository;
  private final LiturgyLineRepository liturgyLineRepository;
  private final BulletinTextRepository bulletinTextRepository;
  private final CodeService codeService;

  /**
   * 전체 전례문 (줄 포함, 정렬순서).
   *
   * @return 전례문
   */
  public List<LiturgyTextResponse> getTexts() {
    return withLines(liturgyTextRepository.findAllByOrderBySortOrderAscIdAsc());
  }

  /**
   * 전례문 단건.
   *
   * @param id 전례문 ID
   * @return 전례문
   * @throws BusinessException 없는 경우 (B9501)
   */
  public LiturgyTextResponse getText(Long id) {
    return withLines(List.of(findText(id))).get(0);
  }

  /**
   * 여러 전례문 (요청 순서대로, 없는 ID 는 빠짐). 주보 표시용.
   *
   * @param ids 전례문 ID
   * @return 전례문
   */
  public List<LiturgyTextResponse> getTextsInOrder(List<Long> ids) {
    Map<Long, LiturgyTextResponse> byId = withLines(liturgyTextRepository.findAllById(ids))
        .stream().collect(Collectors.toMap(LiturgyTextResponse::textId, text -> text));
    return ids.stream().filter(byId::containsKey).map(byId::get).toList();
  }

  /**
   * 전례문 등록.
   *
   * @param request 등록 요청
   * @return 등록된 전례문
   */
  @Transactional
  public LiturgyTextResponse createText(LiturgyTextSaveRequest request) {
    validate(request);
    LiturgyText text = liturgyTextRepository.save(LiturgyText.builder()
        .textTypeCd(request.textTypeCd()).title(request.title().strip())
        .versionNm(StringUtils.trimToNull(request.versionNm()))
        .intro(StringUtils.trimToNull(request.intro()))
        .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
        .useYn(request.useYn()).build());
    saveLines(text.getId(), request.lines());
    return getText(text.getId());
  }

  /**
   * 전례문 수정. (줄 전체 교체)
   *
   * @param id 전례문 ID
   * @param request 수정 요청
   * @return 수정된 전례문
   */
  @Transactional
  public LiturgyTextResponse updateText(Long id, LiturgyTextSaveRequest request) {
    LiturgyText text = findText(id);
    validate(request);
    text.update(request.textTypeCd(), request.title().strip(),
        StringUtils.trimToNull(request.versionNm()), StringUtils.trimToNull(request.intro()),
        request.sortOrder() == null ? 0 : request.sortOrder(), request.useYn());
    liturgyLineRepository.deleteByTextId(id);
    saveLines(id, request.lines());
    return getText(id);
  }

  /**
   * 전례문 삭제.
   *
   * @param id 전례문 ID
   * @throws BusinessException 없거나(B9501) 주보에서 사용 중(B9503)
   */
  @Transactional
  public void deleteText(Long id) {
    LiturgyText text = findText(id);
    long used = bulletinTextRepository.countByIdTextId(id);
    if (used > 0) {
      throw new BusinessException(LiturgyErrorCode.TEXT_IN_USE, String.valueOf(used));
    }
    liturgyLineRepository.deleteByTextId(id);
    liturgyTextRepository.delete(text);
  }

  // ================================================================== 내부

  private void validate(LiturgyTextSaveRequest request) {
    requireCode(TYPE_GROUP, request.textTypeCd());
    request.lines().stream().map(LineSaveRequest::speakerCd).filter(StringUtils::isNotBlank)
        .distinct().forEach(speaker -> requireCode(SPEAKER_GROUP, speaker));
  }

  private void requireCode(String groupCd, String code) {
    boolean exists = codeService.getCodeItems(groupCd).stream()
        .anyMatch(item -> item.code().equals(code));
    if (!exists) {
      throw new BusinessException(LiturgyErrorCode.CODE_INVALID, groupCd, code);
    }
  }

  private void saveLines(Long textId, List<LineSaveRequest> lines) {
    for (int i = 0; i < lines.size(); i++) {
      LineSaveRequest line = lines.get(i);
      liturgyLineRepository.save(new LiturgyLine(textId, i + 1,
          StringUtils.trimToNull(line.speakerCd()), line.content().strip()));
    }
  }

  private List<LiturgyTextResponse> withLines(Collection<LiturgyText> texts) {
    if (texts.isEmpty()) {
      return List.of();
    }
    Map<Long, List<LiturgyLine>> lines = liturgyLineRepository
        .findByTextIdInOrderByTextIdAscSortOrderAsc(
            texts.stream().map(LiturgyText::getId).toList())
        .stream().collect(Collectors.groupingBy(LiturgyLine::getTextId));
    return texts.stream()
        .map(text -> LiturgyTextResponse.of(text, lines.getOrDefault(text.getId(), List.of())))
        .toList();
  }

  private LiturgyText findText(Long id) {
    return liturgyTextRepository.findById(id)
        .orElseThrow(() -> new BusinessException(LiturgyErrorCode.TEXT_NOT_FOUND,
            String.valueOf(id)));
  }
}
