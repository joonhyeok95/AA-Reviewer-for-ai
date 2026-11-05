package com.flowlog.backend.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.flowlog.backend.mail.domain.service.MailGateway;
import com.flowlog.backend.mail.domain.service.MailGateway.Envelope;
import com.flowlog.backend.notice.domain.model.Notice;
import com.flowlog.backend.notice.domain.model.NoticeI18n;
import com.flowlog.backend.notice.domain.model.NoticeMailHist;
import com.flowlog.backend.notice.domain.model.NoticeMailTarget;
import com.flowlog.backend.notice.domain.repository.NoticeI18nRepository;
import com.flowlog.backend.notice.domain.repository.NoticeMailHistRepository;
import com.flowlog.backend.notice.domain.repository.NoticeMailTargetRepository;
import com.flowlog.backend.notice.domain.repository.NoticeRepository;
import com.flowlog.backend.notice.domain.service.NoticeMailService;
import com.flowlog.backend.user.domain.model.UserDept;
import com.flowlog.backend.user.domain.repository.UserDeptRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 메일 발송 테스트. (NT-47 ~ NT-4A, 발송 시점 인사 마스터 기준 수신자)
 *
 * <p>수원 사업장(SUWON) = TF_TEAM(tx.viewer, 언어 미설정 → ko) + TF_LAB(partner01, en)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@SpringBootTest
@Transactional
class NoticeMailServiceTest {

  @Autowired
  private NoticeMailService noticeMailService;

  @Autowired
  private NoticeRepository noticeRepository;

  @Autowired
  private NoticeI18nRepository noticeI18nRepository;

  @Autowired
  private NoticeMailTargetRepository noticeMailTargetRepository;

  @Autowired
  private NoticeMailHistRepository noticeMailHistRepository;

  @Autowired
  private UserDeptRepository userDeptRepository;

  @MockitoBean
  private MailGateway mailGateway;

  /** 한국어만 작성된 공지 (대표 ko), 수원 사업장 대상, 게시일시 도래. */
  private Long koreanOnlyNoticeToSuwon() {
    Notice notice = noticeRepository.saveAndFlush(Notice.builder().categoryCd("GENERAL")
        .baseLangCd("ko").postDate(LocalDateTime.now().minusMinutes(1)).popupYn("N")
        .mailSendTypeCd("TARGET").build());
    noticeI18nRepository.save(new NoticeI18n(notice.getId(), "ko", "점검 & 안내",
        "<p>본문</p>", "본문"));
    noticeMailTargetRepository.saveAndFlush(
        new NoticeMailTarget(notice.getId(), "WORKPLACE", "SUWON", "Y"));
    return notice.getId();
  }

  @Test
  @DisplayName("수신자 언어별 그룹 일괄 발송 - 템플릿은 수신자 언어, 내용은 해당 언어 없으면 대표 언어(ko)")
  void groupByRecipientLanguage() {
    Long id = koreanOnlyNoticeToSuwon();

    int processed = noticeMailService.sendDueMails();

    ArgumentCaptor<Envelope> captor = ArgumentCaptor.forClass(Envelope.class);
    verify(mailGateway, times(2)).send(captor.capture());
    Map<String, Envelope> byLang = captor.getAllValues().stream()
        .collect(Collectors.toMap(Envelope::langCd, e -> e));
    assertThat(processed).isEqualTo(1);
    // ko 그룹 : 한국어 템플릿 + 한국어 본문
    assertThat(byLang.get("ko").subject()).isEqualTo("[공지] 점검 & 안내");
    assertThat(byLang.get("ko").bcc()).containsExactly("tx.viewer@example.com");
    assertThat(byLang.get("ko").html()).contains("Flowlog 공지사항", "점검 &amp; 안내", "<p>본문</p>");
    // en 그룹 : 영어 템플릿(머리글/안내/바닥글) + 영어 버전이 없으므로 대표 언어(ko) 본문
    assertThat(byLang.get("en").subject()).isEqualTo("[Notice] 점검 & 안내");
    assertThat(byLang.get("en").bcc()).containsExactly("partner01@example.com");
    assertThat(byLang.get("en").html()).contains("Flowlog Notice", "send-only", "<p>본문</p>");

    List<NoticeMailHist> histories = noticeMailHistRepository.findByNoticeIdOrderByIdAsc(id);
    assertThat(histories).extracting(h -> h.getReceiverId() + ":" + h.getLangCd() + ":"
            + h.getStatusCd())
        .containsExactlyInAnyOrder("tx.viewer:ko:SUCCESS", "partner01:en:SUCCESS");
    assertThat(noticeRepository.findById(id).orElseThrow().getMailStatusCd()).isEqualTo("DONE");
  }

  @Test
  @DisplayName("다시 실행해도 중복 발송 없음 (상태 선점), 예약 공지(2099)는 발송 안 함")
  void noDuplicateAndScheduledSkipped() {
    koreanOnlyNoticeToSuwon();
    noticeMailService.sendDueMails();

    assertThat(noticeMailService.sendDueMails()).isZero();
    verify(mailGateway, times(2)).send(any());
    assertThat(noticeRepository.findById(4L).orElseThrow().getMailStatusCd()).isEqualTo("READY");
  }

  @Test
  @DisplayName("수신자는 발송 시점 인사 마스터 기준 - 등록 후 수원으로 이동한 사용자(emp01)도 포함")
  void recipientsAtSendTime() {
    Long id = koreanOnlyNoticeToSuwon();
    userDeptRepository.saveAndFlush(new UserDept("emp01", "TF_LAB", "N"));

    noticeMailService.sendDueMails();

    assertThat(noticeMailHistRepository.findByNoticeIdOrderByIdAsc(id))
        .extracting(NoticeMailHist::getReceiverId)
        .contains("emp01");
  }

  @Test
  @DisplayName("발송 실패 - 수신자별 FAIL 이력, 공지 상태 FAIL")
  void sendFailure() {
    Long id = koreanOnlyNoticeToSuwon();
    willThrow(new MailGateway.MailSendException("SMTP down", null))
        .given(mailGateway).send(any());

    noticeMailService.sendDueMails();

    assertThat(noticeMailHistRepository.findByNoticeIdOrderByIdAsc(id))
        .allMatch(h -> "FAIL".equals(h.getStatusCd()) && "SMTP down".equals(h.getErrorMsg()));
    assertThat(noticeRepository.findById(id).orElseThrow().getMailStatusCd()).isEqualTo("FAIL");
  }

  @Test
  @DisplayName("영어 버전이 있으면 영어 수신자에게 영어 본문")
  void englishVersionForEnglishRecipient() {
    Long id = koreanOnlyNoticeToSuwon();
    noticeI18nRepository.saveAndFlush(new NoticeI18n(id, "en", "Maintenance", "<p>Body</p>",
        "Body"));

    noticeMailService.sendDueMails();

    ArgumentCaptor<Envelope> captor = ArgumentCaptor.forClass(Envelope.class);
    verify(mailGateway, times(2)).send(captor.capture());
    Envelope english = captor.getAllValues().stream()
        .filter(e -> "en".equals(e.langCd())).min(Comparator.comparing(Envelope::subject))
        .orElseThrow();
    assertThat(english.subject()).isEqualTo("[Notice] Maintenance");
    assertThat(english.html()).contains("<p>Body</p>").doesNotContain("<p>본문</p>");
  }
}
