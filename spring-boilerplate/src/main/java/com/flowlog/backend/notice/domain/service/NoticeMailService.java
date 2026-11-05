package com.flowlog.backend.notice.domain.service;

import com.flowlog.backend.code.domain.service.CodeService;
import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.code.domain.service.dto.CodeItem;
import com.flowlog.backend.global.constant.YesNo;
import com.flowlog.backend.global.util.HtmlSanitizer;
import com.flowlog.backend.mail.domain.service.MailGateway;
import com.flowlog.backend.mail.domain.service.MailGateway.Envelope;
import com.flowlog.backend.mail.domain.service.MailTemplateService;
import com.flowlog.backend.mail.domain.service.MailTemplateService.RenderedTemplate;
import com.flowlog.backend.notice.NoticeProperties;
import com.flowlog.backend.notice.domain.model.Notice;
import com.flowlog.backend.notice.domain.model.NoticeI18n;
import com.flowlog.backend.notice.domain.model.NoticeMailHist;
import com.flowlog.backend.notice.domain.model.NoticeMailTarget;
import com.flowlog.backend.notice.domain.repository.NoticeI18nRepository;
import com.flowlog.backend.notice.domain.repository.NoticeMailHistRepository;
import com.flowlog.backend.notice.domain.repository.NoticeMailTargetRepository;
import com.flowlog.backend.notice.domain.repository.NoticeRepository;
import com.flowlog.backend.user.domain.model.User;
import com.flowlog.backend.user.domain.service.OrgService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공지 메일 발송 서비스.
 *
 * <ol>
 *   <li>게시일시가 도래한 발송 대기(READY) 공지를 찾고, 상태를 SENDING 으로 원자적으로 바꿔 선점
 *       (여러 인스턴스가 동시에 돌아도 1대만 발송)</li>
 *   <li>수신자 = <b>발송 시점</b>의 인사 마스터 기준 (메일 대상 사업장/부서 → 소속 사용자, 겸직 포함)</li>
 *   <li>수신자를 사용자 설정 언어별로 그룹핑 (NT-47, NT-4A)</li>
 *   <li>언어 그룹마다 : 템플릿(머리글/안내/바닥글) = 수신자 언어 (NT-49),
 *       공지 내용 = 해당 언어 버전, 없으면 대표 언어 버전 (NT-48)</li>
 *   <li>BCC 로 batch-size 단위 일괄 발송, 수신자별 이력 저장, 공지 상태 DONE/FAIL</li>
 * </ol>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@Service("noticeMailService")
public class NoticeMailService {

  /** 메일 템플릿 코드. */
  public static final String TEMPLATE_CD = "NOTICE";

  private static final DateTimeFormatter POST_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final NoticeRepository noticeRepository;
  private final NoticeI18nRepository noticeI18nRepository;
  private final NoticeMailTargetRepository noticeMailTargetRepository;
  private final NoticeMailHistRepository noticeMailHistRepository;
  private final OrgService orgService;
  private final LangService langService;
  private final CodeService codeService;
  private final MailTemplateService mailTemplateService;
  private final MailGateway mailGateway;
  private final NoticeProperties.Mail properties;
  private final TransactionTemplate transaction;
  private final Clock clock;

  /**
   * 생성자.
   *
   * @param noticeRepository 공지
   * @param noticeI18nRepository 공지 언어별 내용
   * @param noticeMailTargetRepository 메일 대상
   * @param noticeMailHistRepository 발송 이력
   * @param orgService 인사 마스터
   * @param langService 언어
   * @param codeService 공통코드
   * @param mailTemplateService 메일 템플릿
   * @param mailGateway 메일 발송
   * @param properties 공지 설정
   * @param transactionManager 트랜잭션
   * @param clock 시계
   */
  public NoticeMailService(
      NoticeRepository noticeRepository, NoticeI18nRepository noticeI18nRepository,
      NoticeMailTargetRepository noticeMailTargetRepository,
      NoticeMailHistRepository noticeMailHistRepository, OrgService orgService,
      LangService langService, CodeService codeService, MailTemplateService mailTemplateService,
      MailGateway mailGateway, NoticeProperties properties,
      PlatformTransactionManager transactionManager, Clock clock) {
    this.noticeRepository = noticeRepository;
    this.noticeI18nRepository = noticeI18nRepository;
    this.noticeMailTargetRepository = noticeMailTargetRepository;
    this.noticeMailHistRepository = noticeMailHistRepository;
    this.orgService = orgService;
    this.langService = langService;
    this.codeService = codeService;
    this.mailTemplateService = mailTemplateService;
    this.mailGateway = mailGateway;
    this.properties = properties.mail();
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  /**
   * 게시일시가 도래한 공지 메일을 발송한다. (스케줄러)
   *
   * @return 처리한 공지 수
   */
  public int sendDueMails() {
    LocalDateTime now = LocalDateTime.now(clock);
    int processed = 0;
    for (Long noticeId : noticeRepository.findMailDueIds(now)) {
      Boolean claimed = transaction.execute(status -> noticeRepository.changeMailStatus(
          noticeId, Notice.MAIL_READY, Notice.MAIL_SENDING) == 1);
      if (Boolean.TRUE.equals(claimed)) {
        send(noticeId);
        processed++;
      }
    }
    return processed;
  }

  /** 선점한 공지 1건 발송. 메일 발송은 DB 트랜잭션 밖에서 수행한다. */
  private void send(Long noticeId) {
    List<NoticeMailHist> histories = new ArrayList<>();
    boolean success;
    try {
      List<LangGroup> groups = transaction.execute(status -> plan(noticeId));
      success = groups != null;
      for (LangGroup group : groups == null ? List.<LangGroup>of() : groups) {
        success &= sendGroup(noticeId, group, histories);
      }
    } catch (RuntimeException e) {
      log.error("[NoticeMail] 발송 처리 오류 noticeId={}", noticeId, e);
      success = false;
    }
    boolean result = success;
    transaction.executeWithoutResult(status -> {
      noticeMailHistRepository.saveAll(histories);
      noticeRepository.findById(noticeId)
          .ifPresent(notice -> notice.completeMail(result, LocalDateTime.now(clock)));
    });
    log.info("[NoticeMail] noticeId={} 결과={} 수신자={}명", noticeId, result ? "DONE" : "FAIL",
        histories.size());
  }

  /** 수신자 추출(발송 시점 인사 마스터) + 언어별 그룹 + 언어별 메일 내용. */
  List<LangGroup> plan(Long noticeId) {
    Notice notice = noticeRepository.findById(noticeId).orElseThrow();
    Map<String, NoticeI18n> versions = new LinkedHashMap<>();
    noticeI18nRepository.findByIdNoticeId(noticeId).forEach(v -> versions.put(v.getLangCd(), v));

    Set<String> deptCds = new LinkedHashSet<>();
    for (NoticeMailTarget target : noticeMailTargetRepository.findByIdNoticeId(noticeId)) {
      deptCds.addAll(orgService.expandDepts(target.getId().getTargetType(),
          target.getId().getTargetCd(), YesNo.isYes(target.getIncludeSubYn())));
    }
    Map<String, List<User>> byLang = new LinkedHashMap<>();
    for (User user : orgService.usersInDepts(deptCds)) {
      String lang = langService.isActive(user.getLangCd()) ? user.getLangCd()
          : langService.defaultLang();
      byLang.computeIfAbsent(lang, key -> new ArrayList<>()).add(user);
    }

    List<LangGroup> groups = new ArrayList<>();
    byLang.forEach((lang, users) -> groups.add(new LangGroup(lang, users,
        compose(notice, lang, versions))));
    return groups;
  }

  /** 수신자 언어의 템플릿 + (수신자 언어 버전 → 없으면 대표 언어 버전) 공지 내용. */
  private Envelope compose(Notice notice, String lang, Map<String, NoticeI18n> versions) {
    NoticeI18n content = versions.containsKey(lang) ? versions.get(lang)
        : versions.getOrDefault(notice.getBaseLangCd(), versions.values().iterator().next());
    CodeItem category = CodeService.byCode(codeService.getCodeItems(NoticeService.CATEGORY_GROUP))
        .get(notice.getCategoryCd());
    String link = StringUtils.replace(StringUtils.defaultString(properties.linkTemplate()),
        "{id}", String.valueOf(notice.getId()));
    RenderedTemplate template = mailTemplateService.render(TEMPLATE_CD, lang, Map.of(
        "title", content.getTitle(),
        "postDate", notice.getPostDate().format(POST_DATE_FORMAT),
        "link", link,
        "categoryNm", category == null ? notice.getCategoryCd()
            : category.nameOf(lang, langService.defaultLang())));
    String html = "<div>" + template.headerHtml()
        + "<h3>" + HtmlSanitizer.escape(content.getTitle()) + "</h3>"
        + template.guideHtml()
        + "<div>" + content.getContentHtml() + "</div>"
        + template.footerHtml() + "</div>";
    return new Envelope(properties.from(), List.of(), template.subject(), html,
        content.getLangCd());
  }

  /** 언어 그룹 발송 (BCC batch-size 단위). */
  private boolean sendGroup(Long noticeId, LangGroup group, List<NoticeMailHist> histories) {
    boolean success = true;
    List<User> withEmail = new ArrayList<>();
    for (User user : group.users()) {
      if (StringUtils.isBlank(user.getEmail())) {
        histories.add(history(noticeId, user, group.langCd(), NoticeMailHist.FAIL, "이메일 주소 없음"));
      } else {
        withEmail.add(user);
      }
    }
    int size = Math.max(1, properties.batchSize());
    for (int from = 0; from < withEmail.size(); from += size) {
      List<User> chunk = withEmail.subList(from, Math.min(from + size, withEmail.size()));
      Envelope template = group.envelope();
      String status = NoticeMailHist.SUCCESS;
      String error = null;
      try {
        mailGateway.send(new Envelope(template.from(),
            chunk.stream().map(User::getEmail).toList(), template.subject(), template.html(),
            group.langCd()));
      } catch (MailGateway.MailSendException e) {
        log.warn("[NoticeMail] 발송 실패 noticeId={} lang={} ({})", noticeId, group.langCd(),
            e.getMessage());
        status = NoticeMailHist.FAIL;
        error = StringUtils.abbreviate(e.getMessage(), 1000);
        success = false;
      }
      for (User user : chunk) {
        histories.add(history(noticeId, user, group.langCd(), status, error));
      }
    }
    return success;
  }

  private NoticeMailHist history(
      Long noticeId, User user, String lang, String status, String error) {
    return NoticeMailHist.builder().noticeId(noticeId).receiverId(user.getUserId())
        .langCd(lang).sendDt(LocalDateTime.now(clock)).statusCd(status).errorMsg(error).build();
  }

  /**
   * 수신 언어 그룹.
   *
   * @param langCd 수신자 언어
   * @param users 수신자
   * @param envelope 메일 내용 (수신자 제외)
   */
  record LangGroup(String langCd, List<User> users, Envelope envelope) {
  }
}
