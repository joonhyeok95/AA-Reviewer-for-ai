package com.flowlog.backend.notice.domain.service;

import com.flowlog.backend.code.domain.service.CodeService;
import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.code.domain.service.dto.CodeItem;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.domain.service.dto.PageResponse;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.global.util.HtmlSanitizer;
import com.flowlog.backend.notice.domain.model.Notice;
import com.flowlog.backend.notice.domain.model.NoticeFile;
import com.flowlog.backend.notice.domain.model.NoticeI18n;
import com.flowlog.backend.notice.domain.model.NoticeMailTarget;
import com.flowlog.backend.notice.domain.model.NoticeViewLog;
import com.flowlog.backend.notice.domain.model.NoticeViewLogId;
import com.flowlog.backend.notice.domain.repository.NoticeFileRepository;
import com.flowlog.backend.notice.domain.repository.NoticeI18nRepository;
import com.flowlog.backend.notice.domain.repository.NoticeMailHistRepository;
import com.flowlog.backend.notice.domain.repository.NoticeMailTargetRepository;
import com.flowlog.backend.notice.domain.repository.NoticeRepository;
import com.flowlog.backend.notice.domain.repository.NoticeSearchMapper;
import com.flowlog.backend.notice.domain.repository.NoticeViewLogRepository;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.LangBadge;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.MailTargetResponse;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeDetailResponse;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeEditResponse;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeFileResponse;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeI18nResponse;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeMailHistResponse;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeSummaryResponse;
import com.flowlog.backend.notice.domain.service.dto.NoticeSaveRequest;
import com.flowlog.backend.notice.domain.service.dto.NoticeSaveRequest.MailTargetRequest;
import com.flowlog.backend.notice.domain.service.dto.NoticeSaveRequest.NoticeI18nRequest;
import com.flowlog.backend.notice.domain.service.dto.NoticeSearchRequest;
import com.flowlog.backend.notice.domain.service.dto.NoticeSearchRow;
import com.flowlog.backend.notice.domain.service.dto.NoticeViewer;
import com.flowlog.backend.notice.exception.NoticeErrorCode;
import com.flowlog.backend.user.domain.service.OrgService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지사항 서비스.
 *
 * <ul>
 *   <li>NT-60~62 : 언어는 LANG 코드 기준, 최소 1개 언어의 제목·본문 필수, 대표 언어 기본값 = 처음 입력 언어</li>
 *   <li>NT-63 : 첨부파일은 게시물 공통 ({@link NoticeFileService})</li>
 *   <li>NT-64 : 표시 언어 = 요청(전환) 언어 → 사용자 언어 → 대표 언어 순으로 존재하는 버전</li>
 *   <li>NT-65 : 목록/상세에 제공 언어 배지</li>
 *   <li>NT-66 : 검색은 사용자 언어로 표시되는 버전 기준 (대표 언어 대체 표시분 포함)
 *       - 목록 검색 SQL 은 MyBatis {@link NoticeSearchMapper}</li>
 *   <li>본문 HTML 은 저장 시 정화(XSS)하고, 검색용 텍스트를 함께 저장</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Service("noticeService")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NoticeService {

  /** 분류 공통코드 그룹. */
  public static final String CATEGORY_GROUP = "NOTICE_CATEGORY";

  /** 태그 공통코드 그룹. */
  public static final String TAG_GROUP = "NOTICE_TAG";

  /** 메일 발송 유형 공통코드 그룹. */
  public static final String MAIL_SEND_TYPE_GROUP = "MAIL_SEND_TYPE";

  private final NoticeRepository noticeRepository;
  private final NoticeI18nRepository noticeI18nRepository;
  private final NoticeSearchMapper noticeSearchMapper;
  private final NoticeFileRepository noticeFileRepository;
  private final NoticeMailTargetRepository noticeMailTargetRepository;
  private final NoticeMailHistRepository noticeMailHistRepository;
  private final NoticeViewLogRepository noticeViewLogRepository;
  private final CodeService codeService;
  private final LangService langService;
  private final OrgService orgService;
  private final Clock clock;

  // ================================================================== 조회

  /**
   * 공지 목록. (사용자 언어 기준 표시·검색, 게시일시 최신순)
   *
   * @param condition 검색 조건
   * @param pageable 페이징 (정렬은 게시일시 최신순 고정)
   * @param viewer 조회자
   * @return 목록
   */
  public PageResponse<NoticeSummaryResponse> getNotices(
      NoticeSearchRequest condition, Pageable pageable, NoticeViewer viewer) {
    boolean includeScheduled =
        viewer.manager() && Boolean.TRUE.equals(condition.includeScheduled());
    String categoryCd = StringUtils.trimToNull(condition.categoryCd());
    String keyword = likePattern(condition.keyword());
    LocalDateTime now = now();
    List<NoticeSearchRow> rows = noticeSearchMapper.searchDisplayed(viewer.langCd(), categoryCd,
        keyword, includeScheduled, now, pageable.getOffset(), pageable.getPageSize());
    long total = noticeSearchMapper.countDisplayed(viewer.langCd(), categoryCd, keyword,
        includeScheduled, now);
    Page<NoticeSearchRow> page = new PageImpl<>(rows, pageable, total);
    List<Long> ids = rows.stream().map(NoticeSearchRow::noticeId).toList();
    Map<Long, Notice> notices = noticeRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Notice::getId, Function.identity()));
    Map<Long, List<String>> langs = availableLangs(ids);
    Map<Long, Long> fileCounts = noticeFileRepository.findByNoticeIdIn(ids).stream()
        .collect(Collectors.groupingBy(NoticeFile::getNoticeId, Collectors.counting()));
    Labels labels = labels(viewer.langCd());
    return PageResponse.from(page, row -> toSummary(notices.get(row.noticeId()), row.title(),
        row.langCd(), langs.getOrDefault(row.noticeId(), List.of()), viewer, labels,
        fileCounts.getOrDefault(row.noticeId(), 0L).intValue()));
  }

  /**
   * 현재 노출 중인 팝업 공지.
   *
   * @param viewer 조회자
   * @return 팝업 공지 목록
   */
  public List<NoticeSummaryResponse> getPopups(NoticeViewer viewer) {
    LocalDateTime now = now();
    List<Notice> notices = noticeRepository.findActivePopups(now, now.toLocalDate());
    List<Long> ids = notices.stream().map(Notice::getId).toList();
    Map<Long, List<NoticeI18n>> versions = versions(ids);
    Labels labels = labels(viewer.langCd());
    List<NoticeSummaryResponse> result = new ArrayList<>();
    for (Notice notice : notices) {
      List<NoticeI18n> list = versions.getOrDefault(notice.getId(), List.of());
      if (!list.isEmpty()) {
        NoticeI18n shown = select(list, null, viewer.langCd(), notice.getBaseLangCd());
        result.add(toSummary(notice, shown.getTitle(), shown.getLangCd(), langCodes(list),
            viewer, labels, 0));
      }
    }
    return result;
  }

  /**
   * 공지 상세. 조회수는 사용자·일자별 1회만 증가한다.
   *
   * @param id 공지ID
   * @param requestedLang 언어 전환 요청 (선택, NT-65)
   * @param viewer 조회자
   * @return 상세
   * @throws BusinessException 없음/삭제/게시 전(관리 권한 없음) (B2001)
   */
  @Transactional
  public NoticeDetailResponse getNotice(Long id, String requestedLang, NoticeViewer viewer) {
    Notice notice = findVisible(id, viewer);
    List<NoticeI18n> list = noticeI18nRepository.findByIdNoticeId(id);
    NoticeI18n shown = select(list, requestedLang, viewer.langCd(), notice.getBaseLangCd());
    String wanted = langService.isActive(requestedLang) ? requestedLang : viewer.langCd();
    int viewCnt = notice.getViewCnt() + recordView(notice, viewer);
    Labels labels = labels(viewer.langCd());
    return new NoticeDetailResponse(notice.getId(), notice.getCategoryCd(),
        labels.category(notice.getCategoryCd()), notice.getTagCd(), labels.tag(notice.getTagCd()),
        notice.getBaseLangCd(), shown.getLangCd(), !shown.getLangCd().equals(wanted),
        badges(langCodes(list)), shown.getTitle(), shown.getContentHtml(), notice.getPostDate(),
        notice.getPopupYn(), notice.getPopupStartDt(), notice.getPopupEndDt(), viewCnt,
        files(id), notice.getCreatedBy(), notice.getCreatedAt(), notice.getUpdatedAt());
  }

  /**
   * 공지 수정 화면 데이터. (전체 언어 버전 + 메일 설정)
   *
   * @param id 공지ID
   * @param langCd 명칭 표시 언어
   * @return 수정 화면 데이터
   */
  public NoticeEditResponse getNoticeForEdit(Long id, String langCd) {
    Notice notice = findActive(id);
    Map<String, String> workplaceNames = orgService.workplaceNames(langCd);
    Map<String, String> deptNames = orgService.deptNames(langCd);
    List<MailTargetResponse> targets = noticeMailTargetRepository.findByIdNoticeId(id).stream()
        .map(target -> new MailTargetResponse(target.getId().getTargetType(),
            target.getId().getTargetCd(),
            (OrgService.TARGET_WORKPLACE.equals(target.getId().getTargetType())
                ? workplaceNames : deptNames).get(target.getId().getTargetCd()),
            target.getIncludeSubYn()))
        .toList();
    List<NoticeI18nResponse> i18n = sortByLang(noticeI18nRepository.findByIdNoticeId(id)).stream()
        .map(v -> new NoticeI18nResponse(v.getLangCd(), v.getTitle(), v.getContentHtml()))
        .toList();
    return new NoticeEditResponse(notice.getId(), notice.getCategoryCd(), notice.getTagCd(),
        notice.getBaseLangCd(), notice.getPostDate(), notice.getPopupYn(),
        notice.getPopupStartDt(), notice.getPopupEndDt(), notice.getMailSendTypeCd(),
        notice.getMailStatusCd(), notice.getMailSendDt(), notice.isMailChangeable(), i18n, targets,
        files(id));
  }

  /**
   * 메일 발송 이력.
   *
   * @param id 공지ID
   * @return 이력
   */
  public List<NoticeMailHistResponse> getMailHistories(Long id) {
    findActive(id);
    return noticeMailHistRepository.findByNoticeIdOrderByIdAsc(id).stream()
        .map(h -> new NoticeMailHistResponse(h.getId(), h.getReceiverId(), h.getLangCd(),
            h.getSendDt(), h.getStatusCd(), h.getErrorMsg()))
        .toList();
  }

  // ================================================================== 변경

  /**
   * 공지 등록.
   *
   * @param request 등록 요청
   * @param langCd 명칭 표시 언어 (응답)
   * @return 등록된 공지 (수정 화면 형식)
   */
  @Transactional
  public NoticeEditResponse createNotice(NoticeSaveRequest request, String langCd) {
    Validated valid = validate(request, true);
    Notice notice = noticeRepository.save(Notice.builder()
        .categoryCd(request.categoryCd()).tagCd(StringUtils.trimToNull(request.tagCd()))
        .baseLangCd(valid.baseLangCd()).postDate(valid.postDate())
        .popupYn(valid.popupYn()).popupStartDt(request.popupStartDt())
        .popupEndDt(request.popupEndDt()).mailSendTypeCd(valid.mailSendTypeCd())
        .build());
    saveVersions(notice.getId(), valid.versions());
    saveTargets(notice.getId(), valid.targets());
    return getNoticeForEdit(notice.getId(), langCd);
  }

  /**
   * 공지 수정. 언어 버전은 요청 기준으로 교체(추가/수정/삭제)된다.
   *
   * <p>메일이 발송 중/완료된 공지는 메일 설정(유형/대상)을 바꾸지 않는다.
   *
   * @param id 공지ID
   * @param request 수정 요청
   * @param langCd 명칭 표시 언어 (응답)
   * @return 수정된 공지 (수정 화면 형식)
   */
  @Transactional
  public NoticeEditResponse updateNotice(Long id, NoticeSaveRequest request, String langCd) {
    Notice notice = findActive(id);
    boolean mailChangeable = notice.isMailChangeable();
    Validated valid = validate(request, mailChangeable);
    notice.update(request.categoryCd(), StringUtils.trimToNull(request.tagCd()),
        valid.baseLangCd(), valid.postDate(), valid.popupYn(), request.popupStartDt(),
        request.popupEndDt(), valid.mailSendTypeCd());

    Map<String, NoticeI18n> existing = noticeI18nRepository.findByIdNoticeId(id).stream()
        .collect(Collectors.toMap(NoticeI18n::getLangCd, Function.identity()));
    for (Version version : valid.versions()) {
      NoticeI18n current = existing.remove(version.langCd());
      if (current == null) {
        noticeI18nRepository.save(new NoticeI18n(id, version.langCd(), version.title(),
            version.contentHtml(), version.contentText()));
      } else {
        current.update(version.title(), version.contentHtml(), version.contentText());
      }
    }
    noticeI18nRepository.deleteAll(existing.values());

    if (mailChangeable) {
      noticeMailTargetRepository.deleteAll(noticeMailTargetRepository.findByIdNoticeId(id));
      noticeMailTargetRepository.flush();
      saveTargets(id, valid.targets());
    }
    return getNoticeForEdit(id, langCd);
  }

  /**
   * 공지 삭제. (논리 삭제, 발송 대기 메일 취소)
   *
   * @param id 공지ID
   */
  @Transactional
  public void deleteNotice(Long id) {
    findActive(id).delete();
  }

  // ================================================================== 언어 선택 / 검증

  /**
   * 표시할 언어 버전 선택. (NT-64)
   *
   * <p>요청(전환) 언어 → 사용자 언어 → 대표 언어 → 첫 번째 버전 순으로 존재하는 것.
   *
   * @param versions 공지의 언어 버전 (1건 이상)
   * @param requestedLang 언어 전환 요청 (없으면 null)
   * @param userLang 사용자 언어
   * @param baseLang 대표 언어
   * @return 표시 버전
   */
  static NoticeI18n select(
      List<NoticeI18n> versions, String requestedLang, String userLang, String baseLang) {
    Map<String, NoticeI18n> byLang = versions.stream()
        .collect(Collectors.toMap(NoticeI18n::getLangCd, Function.identity()));
    NoticeI18n shown = null;
    for (String lang : new String[] {requestedLang, userLang, baseLang}) {
      if (shown == null && lang != null) {
        shown = byLang.get(lang);
      }
    }
    return shown != null ? shown : versions.get(0);
  }

  private Validated validate(NoticeSaveRequest request, boolean mailChangeable) {
    requireCode(CATEGORY_GROUP, request.categoryCd(), "categoryCd");
    if (StringUtils.isNotBlank(request.tagCd())) {
      requireCode(TAG_GROUP, request.tagCd(), "tagCd");
    }
    String mailSendTypeCd = StringUtils.defaultIfBlank(request.mailSendTypeCd(),
        Notice.MAIL_TYPE_NONE);
    requireCode(MAIL_SEND_TYPE_GROUP, mailSendTypeCd, "mailSendTypeCd");

    List<Version> versions = versionsOf(request.i18n());
    String baseLangCd = StringUtils.defaultIfBlank(request.baseLangCd(), versions.get(0).langCd());
    if (versions.stream().noneMatch(v -> v.langCd().equals(baseLangCd))) {
      throw new BusinessException(NoticeErrorCode.NOTICE_BASE_LANG_INVALID, baseLangCd);
    }

    String popupYn = StringUtils.defaultIfBlank(request.popupYn(), YesNo.N);
    LocalDate start = request.popupStartDt();
    LocalDate end = request.popupEndDt();
    if (YesNo.isYes(popupYn) && (start == null || end == null || end.isBefore(start))) {
      throw new BusinessException(NoticeErrorCode.NOTICE_POPUP_PERIOD_INVALID);
    }

    List<MailTargetRequest> targets = request.mailTargets() == null ? List.of()
        : request.mailTargets().stream().distinct().toList();
    if (mailChangeable && !Notice.MAIL_TYPE_NONE.equals(mailSendTypeCd)) {
      if (targets.isEmpty()) {
        throw new BusinessException(NoticeErrorCode.NOTICE_MAIL_TARGET_REQUIRED);
      }
      for (MailTargetRequest target : targets) {
        boolean exists = OrgService.TARGET_WORKPLACE.equals(target.targetType())
            ? orgService.existsWorkplace(target.targetCd())
            : orgService.existsDept(target.targetCd());
        if (!exists) {
          throw new BusinessException(NoticeErrorCode.NOTICE_MAIL_TARGET_INVALID,
              target.targetType(), target.targetCd());
        }
      }
    }
    LocalDateTime postDate = request.postDate() == null ? now() : request.postDate();
    return new Validated(versions, baseLangCd, postDate, popupYn, mailSendTypeCd,
        Notice.MAIL_TYPE_NONE.equals(mailSendTypeCd) ? List.of() : targets);
  }

  /** NT-60/61 : 입력된 언어 버전 (제목·본문 모두 빈 탭은 제외), 정화된 HTML. */
  private List<Version> versionsOf(List<NoticeI18nRequest> requests) {
    Map<String, Version> versions = new LinkedHashMap<>();
    for (NoticeI18nRequest request : requests) {
      String langCd = request.langCd();
      if (!langService.isActive(langCd)) {
        throw new BusinessException(NoticeErrorCode.NOTICE_LANG_INVALID, langCd);
      }
      String title = StringUtils.trimToEmpty(request.title());
      String html = HtmlSanitizer.sanitize(request.contentHtml());
      String text = HtmlSanitizer.toText(html);
      boolean hasContent = StringUtils.isNotBlank(text) || html.contains("<img");
      if (title.isEmpty() && !hasContent) {
        continue;
      }
      if (title.isEmpty() || !hasContent) {
        throw new BusinessException(NoticeErrorCode.NOTICE_LANG_INCOMPLETE, langCd);
      }
      if (versions.put(langCd, new Version(langCd, title, html, text)) != null) {
        throw new BusinessException(NoticeErrorCode.NOTICE_LANG_DUPLICATED, langCd);
      }
    }
    if (versions.isEmpty()) {
      throw new BusinessException(NoticeErrorCode.NOTICE_CONTENT_REQUIRED);
    }
    return List.copyOf(versions.values());
  }

  private void requireCode(String groupCd, String code, String field) {
    boolean exists = codeService.getCodeItems(groupCd).stream()
        .anyMatch(item -> item.code().equals(code));
    if (!exists) {
      throw new BusinessException(NoticeErrorCode.NOTICE_CODE_INVALID, field, code);
    }
  }

  // ================================================================== 내부

  private void saveVersions(Long noticeId, List<Version> versions) {
    versions.forEach(v -> noticeI18nRepository.save(
        new NoticeI18n(noticeId, v.langCd(), v.title(), v.contentHtml(), v.contentText())));
  }

  private void saveTargets(Long noticeId, List<MailTargetRequest> targets) {
    Set<String> seen = new LinkedHashSet<>();
    for (MailTargetRequest target : targets) {
      if (seen.add(target.targetType() + ":" + target.targetCd())) {
        noticeMailTargetRepository.save(new NoticeMailTarget(noticeId, target.targetType(),
            target.targetCd(), StringUtils.defaultIfBlank(target.includeSubYn(), YesNo.Y)));
      }
    }
  }

  /** 조회수 : 사용자·일자별 첫 조회만 +1. */
  private int recordView(Notice notice, NoticeViewer viewer) {
    int increased = 0;
    if (StringUtils.isNotBlank(viewer.userId())) {
      NoticeViewLogId logId = new NoticeViewLogId(notice.getId(), viewer.userId(),
          now().toLocalDate());
      if (!noticeViewLogRepository.existsById(logId)) {
        noticeViewLogRepository.save(new NoticeViewLog(notice.getId(), viewer.userId(),
            logId.getViewDate()));
        noticeRepository.increaseViewCnt(notice.getId());
        increased = 1;
      }
    }
    return increased;
  }

  private Notice findActive(Long id) {
    return noticeRepository.findById(id).filter(notice -> !notice.isDeleted())
        .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND, id));
  }

  private Notice findVisible(Long id, NoticeViewer viewer) {
    Notice notice = findActive(id);
    if (!viewer.manager() && !notice.isPosted(now())) {
      throw new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND, id);
    }
    return notice;
  }

  private List<NoticeFileResponse> files(Long noticeId) {
    return noticeFileRepository.findByNoticeIdOrderByIdAsc(noticeId).stream()
        .map(f -> new NoticeFileResponse(f.getId(), f.getOrgFileNm(), f.getFileSize(),
            f.getFileExt()))
        .toList();
  }

  private Map<Long, List<NoticeI18n>> versions(List<Long> ids) {
    return ids.isEmpty() ? Map.of() : noticeI18nRepository.findByIdNoticeIdIn(ids).stream()
        .collect(Collectors.groupingBy(i -> i.getId().getNoticeId()));
  }

  private Map<Long, List<String>> availableLangs(List<Long> ids) {
    Map<Long, List<String>> result = new HashMap<>();
    versions(ids).forEach((id, list) -> result.put(id, langCodes(list)));
    return result;
  }

  /** 언어 버전 → 언어코드 (LANG 정렬순서). */
  private List<String> langCodes(List<NoticeI18n> versions) {
    return sortByLang(versions).stream().map(NoticeI18n::getLangCd).toList();
  }

  private List<NoticeI18n> sortByLang(List<NoticeI18n> versions) {
    Map<String, Integer> order = new HashMap<>();
    List<CodeItem> langs = langService.activeLangs();
    for (int i = 0; i < langs.size(); i++) {
      order.put(langs.get(i).code(), i);
    }
    return versions.stream()
        .sorted(Comparator.comparing((NoticeI18n v) -> order.getOrDefault(v.getLangCd(), 999))
            .thenComparing(NoticeI18n::getLangCd))
        .toList();
  }

  private static List<LangBadge> badges(List<String> langCodes) {
    return langCodes.stream().map(lang -> new LangBadge(lang, LangService.badgeOf(lang)))
        .toList();
  }

  private NoticeSummaryResponse toSummary(
      Notice notice, String title, String displayLangCd, List<String> langs,
      NoticeViewer viewer, Labels labels, int fileCnt) {
    return new NoticeSummaryResponse(notice.getId(), notice.getCategoryCd(),
        labels.category(notice.getCategoryCd()), notice.getTagCd(), labels.tag(notice.getTagCd()),
        title, displayLangCd, !displayLangCd.equals(viewer.langCd()),
        badges(langs), notice.getPostDate(), notice.getPostDate().isAfter(now()),
        notice.getPopupYn(), notice.getViewCnt(), fileCnt);
  }

  private Labels labels(String langCd) {
    String fallback = langService.defaultLang();
    return new Labels(CodeService.byCode(codeService.getCodeItems(CATEGORY_GROUP)),
        CodeService.byCode(codeService.getCodeItems(TAG_GROUP)), langCd, fallback);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  /** 검색어 → LIKE 패턴 (소문자, 와일드카드 이스케이프). */
  static String likePattern(String keyword) {
    String trimmed = StringUtils.trimToNull(keyword);
    String pattern = null;
    if (trimmed != null) {
      String escaped = trimmed.toLowerCase(Locale.ROOT)
          .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      pattern = "%" + escaped + "%";
    }
    return pattern;
  }

  /** 정화된 언어 버전. */
  private record Version(String langCd, String title, String contentHtml, String contentText) {
  }

  /** 검증 결과. */
  private record Validated(
      List<Version> versions, String baseLangCd, LocalDateTime postDate, String popupYn,
      String mailSendTypeCd, List<MailTargetRequest> targets) {
  }

  /** 분류/태그 명칭 (사용자 언어). */
  private record Labels(
      Map<String, CodeItem> categories, Map<String, CodeItem> tags, String langCd,
      String fallback) {

    String category(String code) {
      CodeItem item = code == null ? null : categories.get(code);
      return item == null ? code : item.nameOf(langCd, fallback);
    }

    String tag(String code) {
      CodeItem item = code == null ? null : tags.get(code);
      return item == null ? code : item.nameOf(langCd, fallback);
    }
  }
}
