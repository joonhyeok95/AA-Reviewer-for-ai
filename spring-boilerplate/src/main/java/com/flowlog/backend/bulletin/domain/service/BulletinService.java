package com.flowlog.backend.bulletin.domain.service;

import com.flowlog.backend.bulletin.domain.model.Bulletin;
import com.flowlog.backend.bulletin.domain.model.BulletinCatechism;
import com.flowlog.backend.bulletin.domain.model.BulletinDuty;
import com.flowlog.backend.bulletin.domain.model.BulletinNews;
import com.flowlog.backend.bulletin.domain.model.BulletinOrder;
import com.flowlog.backend.bulletin.domain.model.BulletinText;
import com.flowlog.backend.bulletin.domain.repository.BulletinCatechismRepository;
import com.flowlog.backend.bulletin.domain.repository.BulletinDutyRepository;
import com.flowlog.backend.bulletin.domain.repository.BulletinNewsRepository;
import com.flowlog.backend.bulletin.domain.repository.BulletinOrderRepository;
import com.flowlog.backend.bulletin.domain.repository.BulletinRepository;
import com.flowlog.backend.bulletin.domain.repository.BulletinTextRepository;
import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.BulletinDetail;
import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.BulletinSaveRequest;
import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.BulletinSummary;
import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.DutyItem;
import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.NewsNode;
import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.OrderItem;
import com.flowlog.backend.bulletin.exception.BulletinErrorCode;
import com.flowlog.backend.catechism.domain.service.CatechismService;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.UnitDetail;
import com.flowlog.backend.code.domain.service.CodeService;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.domain.service.dto.PageResponse;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.liturgy.domain.service.LiturgyService;
import com.flowlog.backend.liturgy.domain.service.dto.LiturgyDtos.LiturgyTextResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주보 서비스.
 *
 * <ul>
 *   <li>주보 1건 = 예배일 1일. 하위(예배순서·교회소식·섬김위원·교리문답·전례문)는 저장 시 전체 교체</li>
 *   <li>작성 중(DRAFT) 주보는 편집 권한이 있는 사용자만 본다 (목록·상세). 이번 주 주보는 게시된 것만</li>
 *   <li>이번 주 주보 = 오늘 ~ 6일 뒤 가장 이른 게시 주보, 없으면 오늘 이전 가장 최근 게시 주보</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Logging
@Service("bulletinService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BulletinService {

  /** 상태 공통코드 그룹. */
  public static final String STATUS_GROUP = "BULLETIN_STATUS";

  /** 게시 상태. */
  public static final String PUBLISHED = "PUBLISHED";

  private final BulletinRepository bulletinRepository;
  private final BulletinOrderRepository orderRepository;
  private final BulletinNewsRepository newsRepository;
  private final BulletinDutyRepository dutyRepository;
  private final BulletinCatechismRepository bulletinCatechismRepository;
  private final BulletinTextRepository bulletinTextRepository;
  private final CatechismService catechismService;
  private final LiturgyService liturgyService;
  private final CodeService codeService;
  private final Clock clock;

  /**
   * 주보 목록 (예배일 역순).
   *
   * @param includeDrafts 작성 중 포함 여부 (편집 권한)
   * @param pageable 페이징
   * @return 주보 목록
   */
  public PageResponse<BulletinSummary> getBulletins(boolean includeDrafts, Pageable pageable) {
    return PageResponse.from(
        bulletinRepository.search(includeDrafts ? null : PUBLISHED, pageable),
        BulletinSummary::from);
  }

  /**
   * 이번 주 주보 (게시된 것).
   *
   * @return 주보 상세
   * @throws BusinessException 게시된 주보가 없는 경우 (B8008)
   */
  public BulletinDetail getCurrent() {
    LocalDate today = LocalDate.now(clock);
    Bulletin bulletin = bulletinRepository
        .findFirstByStatusCdAndWorshipDtBetweenOrderByWorshipDtAsc(PUBLISHED, today,
            today.plusDays(6))
        .or(() -> bulletinRepository
            .findFirstByStatusCdAndWorshipDtLessThanEqualOrderByWorshipDtDesc(PUBLISHED, today))
        .orElseThrow(() -> new BusinessException(BulletinErrorCode.CURRENT_NOT_FOUND));
    return toDetail(bulletin);
  }

  /**
   * 주보 상세.
   *
   * @param id 주보 ID
   * @param includeDrafts 작성 중 조회 가능 여부 (편집 권한)
   * @return 주보 상세
   * @throws BusinessException 없거나 권한 없이 작성 중 주보를 조회한 경우 (B8001)
   */
  public BulletinDetail getBulletin(Long id, boolean includeDrafts) {
    Bulletin bulletin = findBulletin(id);
    if (!includeDrafts && !PUBLISHED.equals(bulletin.getStatusCd())) {
      throw new BusinessException(BulletinErrorCode.BULLETIN_NOT_FOUND, String.valueOf(id));
    }
    return toDetail(bulletin);
  }

  /**
   * 주보 등록.
   *
   * @param request 등록 요청
   * @return 등록된 주보
   */
  @Transactional
  public BulletinDetail createBulletin(BulletinSaveRequest request) {
    if (bulletinRepository.existsByWorshipDt(request.worshipDt())) {
      throw new BusinessException(BulletinErrorCode.WORSHIP_DATE_DUPLICATED,
          request.worshipDt().toString());
    }
    validate(request);
    Bulletin bulletin = bulletinRepository.save(new Bulletin(request.worshipDt(),
        request.volumeNo(), request.issueNo(), trim(request.title()), trim(request.themeVerse()),
        trim(request.themeVerseRef()), trim(request.sermonTitle()),
        trim(request.sermonScripture()), trim(request.preacherNm()), trim(request.sermonMemo()),
        request.statusCd()));
    saveChildren(bulletin.getId(), request);
    return toDetail(bulletin);
  }

  /**
   * 주보 수정. (하위 전체 교체)
   *
   * @param id 주보 ID
   * @param request 수정 요청
   * @return 수정된 주보
   */
  @Transactional
  public BulletinDetail updateBulletin(Long id, BulletinSaveRequest request) {
    Bulletin bulletin = findBulletin(id);
    if (bulletinRepository.existsByWorshipDtAndIdNot(request.worshipDt(), id)) {
      throw new BusinessException(BulletinErrorCode.WORSHIP_DATE_DUPLICATED,
          request.worshipDt().toString());
    }
    validate(request);
    bulletin.update(request.worshipDt(), request.volumeNo(), request.issueNo(),
        trim(request.title()), trim(request.themeVerse()), trim(request.themeVerseRef()),
        trim(request.sermonTitle()), trim(request.sermonScripture()), trim(request.preacherNm()),
        trim(request.sermonMemo()), request.statusCd());
    bulletinRepository.saveAndFlush(bulletin);
    deleteChildren(id);
    saveChildren(id, request);
    return toDetail(findBulletin(id));
  }

  /**
   * 주보 삭제. (하위 함께 삭제)
   *
   * @param id 주보 ID
   */
  @Transactional
  public void deleteBulletin(Long id) {
    Bulletin bulletin = findBulletin(id);
    deleteChildren(id);
    bulletinRepository.delete(bulletin);
  }

  // ================================================================== 검증

  private void validate(BulletinSaveRequest request) {
    boolean statusExists = codeService.getCodeItems(STATUS_GROUP).stream()
        .anyMatch(item -> item.code().equals(request.statusCd()));
    if (!statusExists) {
      throw new BusinessException(BulletinErrorCode.STATUS_INVALID, request.statusCd());
    }
    List<Long> unitIds = distinct(request.catechismUnitIds());
    List<UnitDetail> units = catechismService.getUnitsInOrder(unitIds);
    if (units.size() != unitIds.size()) {
      throw new BusinessException(BulletinErrorCode.CATECHISM_UNIT_INVALID,
          String.valueOf(firstMissing(unitIds, units.stream().map(UnitDetail::unitId).toList())));
    }
    List<Long> textIds = distinct(request.liturgyTextIds());
    List<LiturgyTextResponse> texts = liturgyService.getTextsInOrder(textIds);
    if (texts.size() != textIds.size()) {
      throw new BusinessException(BulletinErrorCode.LITURGY_TEXT_INVALID,
          String.valueOf(firstMissing(textIds,
              texts.stream().map(LiturgyTextResponse::textId).toList())));
    }
    validateNews(request.news(), 1);
    Set<String> cells = new HashSet<>();
    for (DutyItem duty : request.duties()) {
      if (!cells.add(duty.roleNm().strip() + "|" + duty.dutyDt())) {
        throw new BusinessException(BulletinErrorCode.DUTY_DUPLICATED, duty.roleNm(),
            duty.dutyDt().toString());
      }
    }
  }

  private void validateNews(List<NewsNode> nodes, int depth) {
    for (NewsNode node : nodes) {
      List<NewsNode> children = childrenOf(node);
      if (depth > NewsNode.MAX_DEPTH
          || (StringUtils.isAllBlank(node.title(), node.content()) && children.isEmpty())) {
        throw new BusinessException(BulletinErrorCode.NEWS_INVALID,
            String.valueOf(NewsNode.MAX_DEPTH));
      }
      validateNews(children, depth + 1);
    }
  }

  // ================================================================== 하위 저장·삭제

  private void deleteChildren(Long bulletinId) {
    orderRepository.deleteByBulletinId(bulletinId);
    newsRepository.detachParents(bulletinId);
    newsRepository.deleteByBulletinId(bulletinId);
    dutyRepository.deleteByBulletinId(bulletinId);
    bulletinCatechismRepository.deleteByBulletinId(bulletinId);
    bulletinTextRepository.deleteByBulletinId(bulletinId);
  }

  private void saveChildren(Long bulletinId, BulletinSaveRequest request) {
    List<OrderItem> orders = request.orders();
    for (int i = 0; i < orders.size(); i++) {
      orderRepository.save(new BulletinOrder(bulletinId, i + 1, orders.get(i).itemNm().strip(),
          trim(orders.get(i).detail())));
    }
    saveNews(bulletinId, null, request.news());
    Map<String, Integer> roleOrder = new LinkedHashMap<>();
    for (DutyItem duty : request.duties()) {
      String role = duty.roleNm().strip();
      roleOrder.putIfAbsent(role, roleOrder.size() + 1);
      dutyRepository.save(new BulletinDuty(bulletinId, role, roleOrder.get(role), duty.dutyDt(),
          trim(duty.members())));
    }
    List<Long> unitIds = distinct(request.catechismUnitIds());
    for (int i = 0; i < unitIds.size(); i++) {
      bulletinCatechismRepository.save(new BulletinCatechism(bulletinId, unitIds.get(i), i + 1));
    }
    List<Long> textIds = distinct(request.liturgyTextIds());
    for (int i = 0; i < textIds.size(); i++) {
      bulletinTextRepository.save(new BulletinText(bulletinId, textIds.get(i), i + 1));
    }
  }

  private void saveNews(Long bulletinId, Long parentId, List<NewsNode> nodes) {
    for (int i = 0; i < nodes.size(); i++) {
      NewsNode node = nodes.get(i);
      BulletinNews saved = newsRepository.save(new BulletinNews(bulletinId, parentId, i + 1,
          trim(node.title()), trim(node.content()), trim(node.scheduleText()),
          YesNo.Y.equals(node.highlightYn()) ? YesNo.Y : YesNo.N));
      saveNews(bulletinId, saved.getId(), childrenOf(node));
    }
  }

  // ================================================================== 조회 조립

  private BulletinDetail toDetail(Bulletin bulletin) {
    Long id = bulletin.getId();
    List<OrderItem> orders = orderRepository.findByBulletinIdOrderBySortOrderAsc(id).stream()
        .map(order -> new OrderItem(order.getItemNm(), order.getDetail())).toList();
    List<DutyItem> duties = dutyRepository.findByBulletinIdOrderByRoleOrderAscDutyDtAsc(id)
        .stream().map(duty -> new DutyItem(duty.getRoleNm(), duty.getDutyDt(), duty.getMembers()))
        .toList();
    List<Long> unitIds = bulletinCatechismRepository.findByIdBulletinIdOrderBySortOrderAsc(id)
        .stream().map(link -> link.getId().getUnitId()).toList();
    List<Long> textIds = bulletinTextRepository.findByIdBulletinIdOrderBySortOrderAsc(id)
        .stream().map(link -> link.getId().getTextId()).toList();
    return new BulletinDetail(id, bulletin.getWorshipDt(), bulletin.getVolumeNo(),
        bulletin.getIssueNo(), bulletin.getTitle(), bulletin.getThemeVerse(),
        bulletin.getThemeVerseRef(), bulletin.getSermonTitle(), bulletin.getSermonScripture(),
        bulletin.getPreacherNm(), bulletin.getSermonMemo(), bulletin.getStatusCd(), orders,
        newsTree(newsRepository.findByBulletinIdOrderBySortOrderAscIdAsc(id)), duties,
        catechismService.getUnitsInOrder(unitIds), liturgyService.getTextsInOrder(textIds),
        bulletin.getUpdatedBy(), bulletin.getUpdatedAt());
  }

  private static List<NewsNode> newsTree(List<BulletinNews> all) {
    Map<Long, List<BulletinNews>> byParent = all.stream().collect(Collectors.groupingBy(
        news -> Objects.requireNonNullElse(news.getParentNewsId(), 0L), LinkedHashMap::new,
        Collectors.toList()));
    return nodes(0L, byParent, new HashSet<>());
  }

  private static List<NewsNode> nodes(Long parentId, Map<Long, List<BulletinNews>> byParent,
      Set<Long> visited) {
    List<NewsNode> result = new ArrayList<>();
    for (BulletinNews news : byParent.getOrDefault(parentId, List.of())) {
      if (visited.add(news.getId())) {
        result.add(new NewsNode(news.getTitle(), news.getContent(), news.getScheduleText(),
            news.getHighlightYn(), nodes(news.getId(), byParent, visited)));
      }
    }
    return result;
  }

  // ================================================================== 공통

  private static List<NewsNode> childrenOf(NewsNode node) {
    return node.children() == null ? List.of() : node.children();
  }

  private static List<Long> distinct(List<Long> ids) {
    return ids.stream().distinct().toList();
  }

  private static Long firstMissing(List<Long> requested, List<Long> found) {
    return requested.stream().filter(id -> !found.contains(id)).findFirst().orElse(null);
  }

  private static String trim(String value) {
    return StringUtils.trimToNull(value);
  }

  private Bulletin findBulletin(Long id) {
    return bulletinRepository.findById(id)
        .orElseThrow(() -> new BusinessException(BulletinErrorCode.BULLETIN_NOT_FOUND,
            String.valueOf(id)));
  }
}
