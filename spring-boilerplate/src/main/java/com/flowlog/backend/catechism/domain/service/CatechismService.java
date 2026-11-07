package com.flowlog.backend.catechism.domain.service;

import com.flowlog.backend.bulletin.domain.repository.BulletinCatechismRepository;
import com.flowlog.backend.catechism.domain.model.Catechism;
import com.flowlog.backend.catechism.domain.model.CatechismAnswer;
import com.flowlog.backend.catechism.domain.model.CatechismProof;
import com.flowlog.backend.catechism.domain.model.CatechismQa;
import com.flowlog.backend.catechism.domain.model.CatechismUnit;
import com.flowlog.backend.catechism.domain.repository.CatechismAnswerRepository;
import com.flowlog.backend.catechism.domain.repository.CatechismProofRepository;
import com.flowlog.backend.catechism.domain.repository.CatechismQaRepository;
import com.flowlog.backend.catechism.domain.repository.CatechismRepository;
import com.flowlog.backend.catechism.domain.repository.CatechismUnitRepository;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.AnswerResponse;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.AnswerSaveRequest;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.CatechismResponse;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.CatechismSaveRequest;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.PathItem;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.ProofResponse;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.ProofSaveRequest;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.QaResponse;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.QaSaveRequest;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.UnitDetail;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.UnitNode;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.UnitSaveRequest;
import com.flowlog.backend.catechism.exception.CatechismErrorCode;
import com.flowlog.backend.code.domain.service.CodeService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교리문답 서비스.
 *
 * <ul>
 *   <li>교리문답 → 단위 트리 → 문답 → 답 단락 → 근거 성구</li>
 *   <li>단위 저장 시 문답·답 단락·근거 성구를 순서대로 전체 교체 (근거 성구 → 답 → 문답 순서로 삭제)</li>
 *   <li>문 번호는 교리문답 안에서 유일 (요청 안·다른 단위 모두 검사)</li>
 *   <li>하위 단위가 있거나 주보에서 쓰는 단위는 삭제할 수 없다</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Logging
@Service("catechismService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CatechismService {

  /** 단위 유형 공통코드 그룹. */
  public static final String UNIT_TYPE_GROUP = "CATECHISM_UNIT_TYPE";

  private final CatechismRepository catechismRepository;
  private final CatechismUnitRepository unitRepository;
  private final CatechismQaRepository qaRepository;
  private final CatechismAnswerRepository answerRepository;
  private final CatechismProofRepository proofRepository;
  private final BulletinCatechismRepository bulletinCatechismRepository;
  private final CodeService codeService;

  // ================================================================== 교리문답

  /**
   * 전체 교리문답 (단위 트리 포함).
   *
   * @return 교리문답
   */
  public List<CatechismResponse> getCatechisms() {
    return catechismRepository.findAllByOrderBySortOrderAscIdAsc().stream()
        .map(this::toResponse).toList();
  }

  /**
   * 교리문답 등록.
   *
   * @param request 등록 요청
   * @return 등록된 교리문답
   * @throws BusinessException 코드 중복 (B9002)
   */
  @Transactional
  public CatechismResponse createCatechism(CatechismSaveRequest request) {
    if (catechismRepository.existsByCatechismCd(request.catechismCd())) {
      throw new BusinessException(CatechismErrorCode.CATECHISM_CD_DUPLICATED,
          request.catechismCd());
    }
    Catechism saved = catechismRepository.save(new Catechism(request.catechismCd(),
        request.title().strip(), StringUtils.trimToNull(request.description()),
        orderOf(request.sortOrder()), request.useYn()));
    return toResponse(saved);
  }

  /**
   * 교리문답 수정. (코드는 바꾸지 않음)
   *
   * @param id 교리문답 ID
   * @param request 수정 요청
   * @return 수정된 교리문답
   */
  @Transactional
  public CatechismResponse updateCatechism(Long id, CatechismSaveRequest request) {
    Catechism catechism = findCatechism(id);
    catechism.update(request.title().strip(), StringUtils.trimToNull(request.description()),
        orderOf(request.sortOrder()), request.useYn());
    catechismRepository.flush();
    return toResponse(catechism);
  }

  /**
   * 교리문답 삭제. (단위가 없을 때만)
   *
   * @param id 교리문답 ID
   * @throws BusinessException 없거나(B9001) 단위가 있는 경우(B9009)
   */
  @Transactional
  public void deleteCatechism(Long id) {
    Catechism catechism = findCatechism(id);
    if (unitRepository.existsByCatechismId(id)) {
      throw new BusinessException(CatechismErrorCode.CATECHISM_HAS_UNITS, catechism.getTitle());
    }
    catechismRepository.delete(catechism);
  }

  // ================================================================== 단위

  /**
   * 단위 상세 (문답·답 단락·근거 성구 포함).
   *
   * @param unitId 단위 ID
   * @return 단위 상세
   * @throws BusinessException 없는 경우 (B9003)
   */
  public UnitDetail getUnit(Long unitId) {
    return getUnitsInOrder(List.of(findUnit(unitId).getId())).get(0);
  }

  /**
   * 여러 단위 상세 (요청 순서대로, 없는 ID 는 빠짐). 주보 표시용.
   *
   * @param unitIds 단위 ID
   * @return 단위 상세
   */
  public List<UnitDetail> getUnitsInOrder(List<Long> unitIds) {
    if (unitIds.isEmpty()) {
      return List.of();
    }
    Map<Long, CatechismUnit> allUnits = new HashMap<>();
    Map<Long, Catechism> catechisms = new HashMap<>();
    List<CatechismUnit> targets = unitRepository.findAllById(unitIds);
    for (CatechismUnit unit : targets) {
      catechisms.computeIfAbsent(unit.getCatechismId(), this::findCatechism);
      if (!allUnits.containsKey(unit.getId())) {
        unitRepository.findByCatechismIdOrderBySortOrderAscIdAsc(unit.getCatechismId())
            .forEach(each -> allUnits.put(each.getId(), each));
      }
    }
    Map<Long, List<QaResponse>> qas = loadQas(targets.stream().map(CatechismUnit::getId).toList());
    Map<Long, UnitDetail> byId = new HashMap<>();
    for (CatechismUnit unit : targets) {
      byId.put(unit.getId(), new UnitDetail(unit.getId(), unit.getCatechismId(),
          catechisms.get(unit.getCatechismId()).getTitle(), unit.getParentUnitId(),
          unit.getUnitTypeCd(), unit.getUnitNo(), unit.getTitle(), unit.getSortOrder(),
          pathOf(unit, allUnits), qas.getOrDefault(unit.getId(), List.of())));
    }
    return unitIds.stream().filter(byId::containsKey).map(byId::get).toList();
  }

  /**
   * 단위 등록.
   *
   * @param catechismId 교리문답 ID
   * @param request 등록 요청
   * @return 등록된 단위
   */
  @Transactional
  public UnitDetail createUnit(Long catechismId, UnitSaveRequest request) {
    findCatechism(catechismId);
    validateUnit(catechismId, null, request);
    CatechismUnit unit = unitRepository.save(new CatechismUnit(catechismId,
        request.parentUnitId(), request.unitTypeCd(), request.unitNo(),
        StringUtils.trimToNull(request.title()), orderOf(request.sortOrder())));
    saveQas(catechismId, unit.getId(), request.qas());
    return getUnit(unit.getId());
  }

  /**
   * 단위 수정. (문답 이하 전체 교체)
   *
   * @param unitId 단위 ID
   * @param request 수정 요청
   * @return 수정된 단위
   */
  @Transactional
  public UnitDetail updateUnit(Long unitId, UnitSaveRequest request) {
    CatechismUnit unit = findUnit(unitId);
    validateUnit(unit.getCatechismId(), unitId, request);
    unit.update(request.parentUnitId(), request.unitTypeCd(), request.unitNo(),
        StringUtils.trimToNull(request.title()), orderOf(request.sortOrder()));
    deleteQas(unitId);
    saveQas(unit.getCatechismId(), unitId, request.qas());
    return getUnit(unitId);
  }

  /**
   * 단위 삭제. (문답 이하 함께 삭제)
   *
   * @param unitId 단위 ID
   * @throws BusinessException 없거나(B9003) 하위 단위가 있거나(B9007) 주보에서 사용 중(B9008)
   */
  @Transactional
  public void deleteUnit(Long unitId) {
    CatechismUnit unit = findUnit(unitId);
    if (unitRepository.existsByParentUnitId(unitId)) {
      throw new BusinessException(CatechismErrorCode.UNIT_HAS_CHILDREN, String.valueOf(unitId));
    }
    long used = bulletinCatechismRepository.countByIdUnitId(unitId);
    if (used > 0) {
      throw new BusinessException(CatechismErrorCode.UNIT_IN_USE, String.valueOf(used));
    }
    deleteQas(unitId);
    unitRepository.delete(unit);
  }

  // ================================================================== 내부

  private void validateUnit(Long catechismId, Long unitId, UnitSaveRequest request) {
    boolean typeExists = codeService.getCodeItems(UNIT_TYPE_GROUP).stream()
        .anyMatch(item -> item.code().equals(request.unitTypeCd()));
    if (!typeExists) {
      throw new BusinessException(CatechismErrorCode.CODE_INVALID, UNIT_TYPE_GROUP,
          request.unitTypeCd());
    }
    if (request.parentUnitId() != null) {
      CatechismUnit parent = unitRepository.findById(request.parentUnitId())
          .filter(found -> found.getCatechismId().equals(catechismId))
          .orElseThrow(() -> new BusinessException(CatechismErrorCode.PARENT_INVALID,
              String.valueOf(request.parentUnitId())));
      if (unitId != null && isSelfOrDescendant(parent, unitId)) {
        throw new BusinessException(CatechismErrorCode.PARENT_INVALID,
            String.valueOf(request.parentUnitId()));
      }
    }
    Set<Integer> numbers = new HashSet<>();
    for (QaSaveRequest qa : request.qas()) {
      if (!numbers.add(qa.questionNo())) {
        throw new BusinessException(CatechismErrorCode.QUESTION_NO_DUPLICATED,
            String.valueOf(qa.questionNo()));
      }
    }
    if (!numbers.isEmpty()) {
      List<CatechismQa> duplicates = qaRepository.findDuplicates(catechismId, numbers,
          unitId == null ? -1L : unitId);
      if (!duplicates.isEmpty()) {
        throw new BusinessException(CatechismErrorCode.QUESTION_NO_DUPLICATED,
            String.valueOf(duplicates.get(0).getQuestionNo()));
      }
    }
  }

  /** 새 상위(parent)가 자기 자신이거나 자기 하위인지. (상위로 거슬러 올라가며 확인) */
  private boolean isSelfOrDescendant(CatechismUnit parent, Long unitId) {
    CatechismUnit current = parent;
    Set<Long> visited = new HashSet<>();
    while (current != null && visited.add(current.getId())) {
      if (current.getId().equals(unitId)) {
        return true;
      }
      current = current.getParentUnitId() == null ? null
          : unitRepository.findById(current.getParentUnitId()).orElse(null);
    }
    return false;
  }

  private void deleteQas(Long unitId) {
    qaRepository.deleteProofsOfUnit(unitId);
    qaRepository.deleteAnswersOfUnit(unitId);
    qaRepository.deleteQasOfUnit(unitId);
  }

  private void saveQas(Long catechismId, Long unitId, List<QaSaveRequest> qas) {
    for (int i = 0; i < qas.size(); i++) {
      QaSaveRequest qa = qas.get(i);
      CatechismQa savedQa = qaRepository.save(new CatechismQa(catechismId, unitId,
          qa.questionNo(), qa.question().strip(), i + 1));
      for (int j = 0; j < qa.answers().size(); j++) {
        AnswerSaveRequest answer = qa.answers().get(j);
        CatechismAnswer savedAnswer = answerRepository.save(
            new CatechismAnswer(savedQa.getId(), j + 1, answer.content().strip()));
        List<ProofSaveRequest> proofs = answer.proofs() == null ? List.of() : answer.proofs();
        for (int k = 0; k < proofs.size(); k++) {
          proofRepository.save(new CatechismProof(savedAnswer.getId(), k + 1,
              proofs.get(k).bibleRef().strip(), StringUtils.trimToNull(proofs.get(k).bibleText())));
        }
      }
    }
  }

  private Map<Long, List<QaResponse>> loadQas(Collection<Long> unitIds) {
    List<CatechismQa> qas = qaRepository
        .findByUnitIdInOrderByUnitIdAscSortOrderAscQuestionNoAsc(unitIds);
    if (qas.isEmpty()) {
      return Map.of();
    }
    List<CatechismAnswer> answers = answerRepository
        .findByQaIdInOrderByQaIdAscSortOrderAsc(qas.stream().map(CatechismQa::getId).toList());
    Map<Long, List<ProofResponse>> proofs = answers.isEmpty() ? Map.of()
        : proofRepository.findByAnswerIdInOrderByAnswerIdAscSortOrderAsc(
                answers.stream().map(CatechismAnswer::getId).toList()).stream()
            .collect(Collectors.groupingBy(CatechismProof::getAnswerId,
                Collectors.mapping(p -> new ProofResponse(p.getBibleRef(), p.getBibleText()),
                    Collectors.toList())));
    Map<Long, List<AnswerResponse>> answersByQa = answers.stream()
        .collect(Collectors.groupingBy(CatechismAnswer::getQaId,
            Collectors.mapping(a -> new AnswerResponse(a.getContent(),
                proofs.getOrDefault(a.getId(), List.of())), Collectors.toList())));
    return qas.stream().collect(Collectors.groupingBy(CatechismQa::getUnitId,
        Collectors.mapping(q -> new QaResponse(q.getId(), q.getQuestionNo(), q.getQuestion(),
            answersByQa.getOrDefault(q.getId(), List.of())), Collectors.toList())));
  }

  private List<PathItem> pathOf(CatechismUnit unit, Map<Long, CatechismUnit> allUnits) {
    List<PathItem> path = new ArrayList<>();
    Set<Long> visited = new HashSet<>();
    CatechismUnit current = unit;
    while (current != null && visited.add(current.getId())) {
      path.add(0, new PathItem(current.getId(), current.getUnitTypeCd(), current.getUnitNo(),
          current.getTitle()));
      current = current.getParentUnitId() == null ? null : allUnits.get(current.getParentUnitId());
    }
    return path;
  }

  private CatechismResponse toResponse(Catechism catechism) {
    List<CatechismUnit> units = unitRepository
        .findByCatechismIdOrderBySortOrderAscIdAsc(catechism.getId());
    Map<Long, Object[]> stats = new HashMap<>();
    for (Object[] row : qaRepository.statsByUnit(catechism.getId())) {
      stats.put((Long) row[0], row);
    }
    Map<Long, List<CatechismUnit>> byParent = new HashMap<>();
    units.forEach(unit -> byParent.computeIfAbsent(
        Objects.requireNonNullElse(unit.getParentUnitId(), 0L), key -> new ArrayList<>())
        .add(unit));
    return new CatechismResponse(catechism.getId(), catechism.getCatechismCd(),
        catechism.getTitle(), catechism.getDescription(), catechism.getSortOrder(),
        catechism.getUseYn(), nodes(0L, byParent, stats, new HashSet<>()));
  }

  private List<UnitNode> nodes(Long parentId, Map<Long, List<CatechismUnit>> byParent,
      Map<Long, Object[]> stats, Set<Long> visited) {
    List<UnitNode> result = new ArrayList<>();
    for (CatechismUnit unit : byParent.getOrDefault(parentId, List.of())) {
      if (!visited.add(unit.getId())) {
        continue;
      }
      Object[] stat = stats.get(unit.getId());
      result.add(new UnitNode(unit.getId(), unit.getParentUnitId(), unit.getUnitTypeCd(),
          unit.getUnitNo(), unit.getTitle(), unit.getSortOrder(),
          stat == null ? 0 : ((Number) stat[1]).longValue(),
          stat == null ? null : ((Number) stat[2]).intValue(),
          stat == null ? null : ((Number) stat[3]).intValue(),
          nodes(unit.getId(), byParent, stats, visited)));
    }
    return result;
  }

  private static int orderOf(Integer sortOrder) {
    return sortOrder == null ? 0 : sortOrder;
  }

  private Catechism findCatechism(Long id) {
    return catechismRepository.findById(id)
        .orElseThrow(() -> new BusinessException(CatechismErrorCode.CATECHISM_NOT_FOUND,
            String.valueOf(id)));
  }

  private CatechismUnit findUnit(Long unitId) {
    return unitRepository.findById(unitId)
        .orElseThrow(() -> new BusinessException(CatechismErrorCode.UNIT_NOT_FOUND,
            String.valueOf(unitId)));
  }
}
