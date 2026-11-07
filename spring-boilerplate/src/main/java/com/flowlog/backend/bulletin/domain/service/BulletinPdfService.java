package com.flowlog.backend.bulletin.domain.service;

import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.BulletinDetail;
import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.DutyItem;
import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.NewsNode;
import com.flowlog.backend.bulletin.domain.service.dto.BulletinDtos.OrderItem;
import com.flowlog.backend.bulletin.exception.BulletinErrorCode;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.AnswerResponse;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.QaResponse;
import com.flowlog.backend.catechism.domain.service.dto.CatechismDtos.UnitDetail;
import com.flowlog.backend.code.domain.service.CodeService;
import com.flowlog.backend.code.domain.service.LangService;
import com.flowlog.backend.code.domain.service.dto.CodeItem;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.liturgy.domain.service.dto.LiturgyDtos.LineResponse;
import com.flowlog.backend.liturgy.domain.service.dto.LiturgyDtos.LiturgyTextResponse;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주보 PDF - 원본 주보와 같은 <b>A4 가로 3단 접지 2장</b>. (기기·브라우저와 관계없이 같은 결과)
 *
 * <pre>
 * 1장 (바깥면) : [교회소식 · 섬김위원] [예배순서 · 말씀메모] [표지 : 날짜·호, 교회 이름, 섬기는 이, 대표사진, 표지 말씀]
 * 2장 (안쪽면) : [전례문 1 (사도신경)] [전례문 2 (주기도문)] [교리문답]   - 4개 이상이면 셋째 단에 이어서
 * </pre>
 *
 * <ul>
 *   <li>HTML/CSS → PDF (openhtmltopdf, PDFBox). 글꼴은 나눔고딕 (resources/fonts, SIL OFL - 문서에 포함)</li>
 *   <li><b>단마다 맞춤</b> : 단 하나를 단 크기 쪽 PDF 로 먼저 찍어 1쪽을 넘으면 글자 배율을 이분 탐색으로 줄인다
 *       (최소 {@value #MIN_SCALE}). 그래서 내용이 많아도 항상 2장</li>
 *   <li>사용자 입력은 모두 XML 이스케이프 (본문은 문자열만 - HTML 을 받지 않는다)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-07
 */
@Slf4j
@Logging
@Service("bulletinPdfService")
@Transactional(readOnly = true)
public class BulletinPdfService {

  static final String FONT = "NanumGothic";
  static final double MIN_SCALE = 0.45;

  /** 단 크기 (mm) : A4 가로 297 × 210, 바깥 여백 8, 단 사이 10. */
  static final double PAGE_MARGIN = 8;
  static final double GUTTER = 10;
  static final double PANEL_W = (297 - PAGE_MARGIN * 2 - GUTTER * 2) / 3;
  static final double PANEL_H = 210 - PAGE_MARGIN * 2 - 1;

  private static final Map<Integer, byte[]> FONTS = new LinkedHashMap<>();

  private final BulletinService bulletinService;
  private final BulletinPhotoService photoService;
  private final CodeService codeService;
  private final LangService langService;
  private final MessageSource messageSource;

  /**
   * 생성자.
   *
   * @param bulletinService 주보
   * @param photoService 대표사진
   * @param codeService 공통코드
   * @param langService 언어
   * @param messageSource 문구
   */
  public BulletinPdfService(BulletinService bulletinService, BulletinPhotoService photoService,
      CodeService codeService, LangService langService, MessageSource messageSource) {
    this.bulletinService = bulletinService;
    this.photoService = photoService;
    this.codeService = codeService;
    this.langService = langService;
    this.messageSource = messageSource;
  }

  /**
   * 주보 PDF 파일. (작성 중 주보는 편집 권한자만 - 상세 조회와 같음)
   *
   * @param bulletinId 주보 ID
   * @param includeDrafts 작성 중 포함 여부
   * @param langCd 데이터 언어
   * @param locale 문구 Locale
   * @return 파일 이름 + 내용
   */
  public Pdf create(Long bulletinId, boolean includeDrafts, String langCd, Locale locale) {
    BulletinDetail bulletin = bulletinService.getBulletin(bulletinId, includeDrafts);
    Photo photo = null;
    if (bulletin.hasPhoto()) {
      try {
        BulletinPhotoService.Photo stored = photoService.load(bulletinId, includeDrafts);
        try (InputStream in = stored.resource().getInputStream()) {
          photo = new Photo(stored.contentType(), in.readAllBytes());
        }
      } catch (IOException | BusinessException e) {
        log.warn("[BulletinPdf] 대표사진 없이 생성 bulletinId={} ({})", bulletinId, e.getMessage());
      }
    }
    String fileName = messageSource.getMessage("bulletin.pdf.fileName",
        new Object[] {bulletin.worshipDt().toString()}, "bulletin.pdf", locale);
    return new Pdf(fileName, render(bulletin, photo, langCd, locale));
  }

  /**
   * 주보 PDF 만들기.
   *
   * @param bulletin 주보 상세
   * @param photo 대표사진 (형식, 내용) - 없으면 null
   * @param langCd 데이터 언어 (공통코드 이름)
   * @param locale 문구 Locale
   * @return PDF
   */
  public byte[] render(BulletinDetail bulletin, Photo photo, String langCd, Locale locale) {
    Context ctx = new Context(bulletin, photo, langCd, locale);
    List<String> page1 = List.of(newsPanel(ctx), ordersPanel(ctx), coverPanel(ctx));
    List<String> page2 = innerPanels(ctx);
    StringBuilder body = new StringBuilder();
    List<List<String>> pages = List.of(page1, page2);
    for (int p = 0; p < pages.size(); p++) {
      List<String> page = pages.get(p);
      // 다음 장은 앞에서 나눈다 (뒤에서 나누면 꽉 찬 장 다음에 빈 장이 생김)
      body.append(p == 0 ? "<div class=\"sheet\">" : "<div class=\"sheet next\">");
      for (int i = 0; i < page.size(); i++) {
        String panel = page.get(i);
        double scale = fitScale(panel);
        body.append("<div class=\"panel\" style=\"left:")
            .append(fmt(i * (PANEL_W + GUTTER))).append("mm;\"><div class=\"flow\" style=\"font-size:")
            .append(fmt(9.2 * scale)).append("pt;\">").append(panel).append("</div></div>");
      }
      body.append("</div>");
    }
    return toPdf(document("@page { size: 297mm 210mm; margin: " + fmt(PAGE_MARGIN) + "mm; }",
        body.toString()));
  }

  // ================================================================== 단 맞춤

  /** 단 하나만 단 크기 쪽에 찍어 1쪽에 들어가는 가장 큰 배율. */
  double fitScale(String panel) {
    if (pageCount(toPdf(measureDoc(panel, 1))) <= 1) {
      return 1;
    }
    double low = MIN_SCALE;
    double high = 1;
    for (int i = 0; i < 6; i++) {
      double mid = (low + high) / 2;
      if (pageCount(toPdf(measureDoc(panel, mid))) <= 1) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return low;
  }

  private String measureDoc(String panel, double scale) {
    return document("@page { size: " + fmt(PANEL_W) + "mm " + fmt(PANEL_H) + "mm; margin: 0; }",
        "<div class=\"panel measure\"><div class=\"flow\" style=\"font-size:" + fmt(9.2 * scale)
            + "pt;\">" + panel + "</div></div>");
  }

  private static int pageCount(byte[] pdf) {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      return doc.getNumberOfPages();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ================================================================== 단 (패널)

  private String newsPanel(Context ctx) {
    StringBuilder html = new StringBuilder(badge(ctx.msg("bulletin.pdf.news")));
    html.append("<div class=\"box news\">");
    List<NewsNode> news = ctx.bulletin.news();
    int no = 0;
    for (NewsNode section : news) {
      no++;
      html.append("<div class=\"section\"><span class=\"no\">").append(no).append(".</span> ");
      if (StringUtils.isNotBlank(section.title())) {
        html.append("<span class=\"section-title\">").append(esc(section.title())).append("</span> ");
      }
      html.append(span(section.content(), section.highlightYn())).append("</div>");
      for (NewsNode item : children(section)) {
        html.append("<div class=\"item").append(hl(item.highlightYn())).append("\">♣ ");
        if (StringUtils.isNotBlank(item.title())) {
          html.append("<b>").append(esc(item.title())).append("</b> ");
        }
        html.append(lines(item.content()));
        if (StringUtils.isNotBlank(item.scheduleText())) {
          html.append(" <span class=\"schedule\">– ").append(esc(item.scheduleText())).append("</span>");
        }
        html.append("</div>");
        for (NewsNode sub : children(item)) {
          html.append("<div class=\"sub").append(hl(sub.highlightYn())).append("\">* ");
          if (StringUtils.isNotBlank(sub.title())) {
            html.append("<b>").append(esc(sub.title())).append("</b> ");
          }
          html.append(lines(sub.content()));
          if (StringUtils.isNotBlank(sub.scheduleText())) {
            html.append(" <span class=\"schedule\">– ").append(esc(sub.scheduleText())).append("</span>");
          }
          html.append("</div>");
        }
      }
    }
    if (!ctx.bulletin.duties().isEmpty()) {
      html.append("<div class=\"section\"><span class=\"no\">").append(no + 1).append(".</span> ")
          .append("<span class=\"section-title\">").append(esc(ctx.msg("bulletin.pdf.duty")))
          .append("</span></div>").append(dutyTable(ctx));
    }
    return html.append("</div>").toString();
  }

  private String dutyTable(Context ctx) {
    List<DutyItem> duties = ctx.bulletin.duties();
    List<LocalDate> dates = duties.stream().map(DutyItem::dutyDt).distinct().sorted().toList();
    Map<String, Map<LocalDate, String>> roles = new LinkedHashMap<>();
    for (DutyItem duty : duties) {
      roles.computeIfAbsent(duty.roleNm(), key -> new LinkedHashMap<>())
          .put(duty.dutyDt(), StringUtils.defaultString(duty.members()));
    }
    DateTimeFormatter md = DateTimeFormatter.ofPattern("M/d");
    StringBuilder html = new StringBuilder("<table class=\"duty\"><tr><th></th>");
    for (LocalDate day : dates) {
      html.append("<th").append(current(ctx, day)).append('>').append(day.format(md)).append("</th>");
    }
    html.append("</tr>");
    roles.forEach((role, members) -> {
      html.append("<tr><th>").append(esc(role)).append("</th>");
      for (LocalDate day : dates) {
        html.append("<td").append(current(ctx, day)).append('>')
            .append(esc(members.getOrDefault(day, ""))).append("</td>");
      }
      html.append("</tr>");
    });
    return html.append("</table>").toString();
  }

  private String ordersPanel(Context ctx) {
    StringBuilder html = new StringBuilder(badge(ctx.msg("bulletin.pdf.orders")));
    html.append("<div class=\"box\"><table class=\"orders\">");
    for (OrderItem order : ctx.bulletin.orders()) {
      html.append("<tr><th>").append(esc(order.itemNm())).append("</th><td>")
          .append(esc(order.detail())).append("</td></tr>");
    }
    html.append("</table>");
    BulletinDetail b = ctx.bulletin;
    if (StringUtils.isNotBlank(b.sermonTitle())) {
      html.append("<div class=\"sermon\"><b>").append(esc(b.sermonTitle())).append("</b>");
      if (StringUtils.isNotBlank(b.sermonScripture())) {
        html.append(" (").append(esc(b.sermonScripture())).append(')');
      }
      html.append("</div>");
    }
    html.append("</div>").append(badge(ctx.msg("bulletin.pdf.memo")))
        .append("<div class=\"box memo\">").append(lines(b.sermonMemo())).append("</div>");
    return html.toString();
  }

  private String coverPanel(Context ctx) {
    BulletinDetail b = ctx.bulletin;
    String date = b.worshipDt().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        .withLocale(ctx.locale));
    String issue = b.volumeNo() != null || b.issueNo() != null
        ? " " + ctx.msg("bulletin.pdf.issue", Objects.toString(b.volumeNo(), "-"),
            Objects.toString(b.issueNo(), "-")) : "";
    StringBuilder html = new StringBuilder("<div class=\"cover\">");
    html.append("<div class=\"issue\">").append(esc(date + issue)).append("</div>")
        .append("<div class=\"church\">").append(esc(ctx.code("CHURCH_INFO", "NAME"))).append("</div>")
        .append("<div class=\"served\">").append(esc(ctx.code("CHURCH_INFO", "SERVED_BY")))
        .append("</div><div class=\"cover-rule\"></div>");
    if (StringUtils.isNotBlank(b.title())) {
      html.append("<div class=\"special\">").append(esc(b.title())).append("</div>");
    }
    if (ctx.photo != null) {
      html.append("<div class=\"photo\"><img src=\"data:").append(ctx.photo.contentType())
          .append(";base64,").append(Base64.getEncoder().encodeToString(ctx.photo.content()))
          .append("\"/></div>");
    }
    if (StringUtils.isNotBlank(b.themeVerse())) {
      html.append("<div class=\"verse\">").append(lines(b.themeVerse())).append("</div>");
      if (StringUtils.isNotBlank(b.themeVerseRef())) {
        html.append("<div class=\"verse\">(").append(esc(b.themeVerseRef())).append(")</div>");
      }
    }
    return html.append("</div>").toString();
  }

  /** 2장 : 전례문·교리문답을 차례로 세 단에 (넘치면 셋째 단에 이어서). */
  private List<String> innerPanels(Context ctx) {
    List<String> items = new ArrayList<>();
    ctx.bulletin.liturgyTexts().forEach(text -> items.add(liturgyPanel(ctx, text)));
    ctx.bulletin.catechismUnits().forEach(unit -> items.add(catechismPanel(ctx, unit)));
    List<String> panels = new ArrayList<>(items.subList(0, Math.min(2, items.size())));
    if (items.size() > 2) {
      panels.add(String.join("<div class=\"gap\"></div>", items.subList(2, items.size())));
    }
    while (panels.size() < 3) {
      panels.add("");
    }
    return panels;
  }

  private String liturgyPanel(Context ctx, LiturgyTextResponse text) {
    String title = text.title() + (StringUtils.isNotBlank(text.versionNm())
        ? "(" + text.versionNm() + ")" : "");
    StringBuilder html = new StringBuilder(badge(title)).append("<div class=\"box liturgy\">");
    if (StringUtils.isNotBlank(text.intro())) {
      html.append("<div class=\"intro\">").append(lines(text.intro())).append("</div>");
    }
    for (LineResponse line : text.lines()) {
      String speaker = line.speakerCd() == null ? null : ctx.code("LITURGY_SPEAKER", line.speakerCd());
      if ("LEADER".equals(line.speakerCd())) {
        html.append("<div class=\"leader\">").append(esc(speaker)).append(" : ")
            .append(lines(line.content())).append("</div>");
      } else if (speaker != null) {
        html.append("<div class=\"people\">").append(esc(speaker)).append(" : ")
            .append(lines(line.content())).append("</div>");
      } else {
        html.append("<div class=\"plain\">").append(lines(line.content())).append("</div>");
      }
    }
    return html.append("</div>").toString();
  }

  private String catechismPanel(Context ctx, UnitDetail unit) {
    StringBuilder html = new StringBuilder(badge(unit.catechismTitle()))
        .append("<div class=\"box catechism\"><div class=\"unit-title\">");
    String range = qaRange(unit.qas());
    String unitName = ctx.code("CATECHISM_UNIT_TYPE", unit.unitTypeCd());
    String label = ctx.msg("bulletin.pdf.unit", Objects.toString(unit.unitNo(), ""), unitName,
        range);
    if (StringUtils.isNotBlank(unit.title())) {
      html.append(esc(unit.title())).append(' ');
    }
    html.append('(').append(esc(label)).append(")</div>");
    String leader = ctx.code("LITURGY_SPEAKER", "LEADER");
    String people = ctx.code("LITURGY_SPEAKER", "PEOPLE");
    for (QaResponse qa : unit.qas()) {
      html.append("<div class=\"qa\"><span class=\"qno\">")
          .append(esc(ctx.msg("bulletin.pdf.qa", Objects.toString(qa.questionNo(), ""))))
          .append("</span></div><div class=\"leader\">").append(esc(leader)).append(" : ")
          .append(lines(qa.question())).append("</div>");
      boolean first = true;
      for (AnswerResponse answer : qa.answers()) {
        boolean note = answer.content().startsWith("※");
        html.append("<div class=\"").append(note ? "note" : "answer").append("\">");
        if (first && !note) {
          html.append(esc(people)).append(" : ");
          first = false;
        }
        html.append(lines(answer.content())).append("</div>");
      }
    }
    return html.append("</div>").toString();
  }

  private static String qaRange(List<QaResponse> qas) {
    List<Integer> nos = qas.stream().map(QaResponse::questionNo).filter(Objects::nonNull).toList();
    if (nos.isEmpty()) {
      return "";
    }
    int from = nos.stream().min(Integer::compare).orElseThrow();
    int to = nos.stream().max(Integer::compare).orElseThrow();
    return from == to ? String.valueOf(from) : from + "-" + to;
  }

  // ================================================================== HTML 조각

  private static String badge(String title) {
    return "<div class=\"badge\">" + esc(title) + "</div><div class=\"rule\"></div>";
  }

  private static List<NewsNode> children(NewsNode node) {
    return node.children() == null ? List.of() : node.children();
  }

  private static String span(String content, String highlightYn) {
    return StringUtils.isBlank(content) ? ""
        : "<span class=\"" + ("Y".equals(highlightYn) ? "hl" : "") + "\">" + lines(content) + "</span>";
  }

  private static String hl(String highlightYn) {
    return "Y".equals(highlightYn) ? " hl" : "";
  }

  private static String current(Context ctx, LocalDate day) {
    return day.equals(ctx.bulletin.worshipDt()) ? " class=\"current\"" : "";
  }

  /** 줄바꿈 → br (이스케이프 후). */
  static String lines(String text) {
    if (StringUtils.isBlank(text)) {
      return "";
    }
    return text.strip().lines().map(BulletinPdfService::esc).collect(Collectors.joining("<br/>"));
  }

  /** XML 이스케이프 + XML 에 쓸 수 없는 제어문자 제거. */
  static String esc(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder out = new StringBuilder(text.length());
    text.codePoints().forEach(cp -> {
      switch (cp) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> {
          if (cp >= 0x20 || cp == '\t' || cp == '\n' || cp == '\r') {
            out.appendCodePoint(cp);
          }
        }
      }
    });
    return out.toString();
  }

  private static String fmt(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  // ================================================================== 문서 · 렌더링

  private static String document(String pageRule, String body) {
    return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>" + pageRule + CSS
        + "</style></head><body>" + body + "</body></html>";
  }

  private static byte[] toPdf(String html) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      FONTS.forEach((weight, bytes) -> builder.useFont(() -> new ByteArrayInputStream(bytes), FONT,
          weight, FontStyle.NORMAL, true));
      builder.withHtmlContent(html, null);
      builder.toStream(out);
      builder.run();
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (RuntimeException e) {
      log.error("[BulletinPdf] PDF 생성 실패", e);
      throw new BusinessException(BulletinErrorCode.PDF_FAILED);
    }
  }

  static {
    FONTS.put(400, font("NanumGothic-Regular.ttf"));
    FONTS.put(700, font("NanumGothic-Bold.ttf"));
    FONTS.put(800, font("NanumGothic-ExtraBold.ttf"));
  }

  private static byte[] font(String name) {
    try (InputStream in = new ClassPathResource("fonts/" + name).getInputStream()) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("글꼴을 읽지 못했습니다: " + name, e);
    }
  }

  /** 원본 주보 색 : 베이지 단, 갈색 알약 제목, 흰 상자. */
  private static final String CSS = """
      body { margin: 0; font-family: 'NanumGothic'; color: #222; }
      .sheet { position: relative; width: 100%; height: %Hmm; }
      .sheet.next { page-break-before: always; }
      .panel { position: absolute; top: 0; width: %Wmm; height: %Hmm; overflow: hidden;
        background: #EEE7DC; box-sizing: border-box; padding: 4mm 4mm 5mm; }
      .panel.measure { position: static; height: auto; overflow: visible; }
      .flow { line-height: 1.5; }
      .badge { display: inline-block; background: #7A6450; color: #fff; font-weight: 700;
        font-size: 1.12em; padding: 1.4mm 5mm; border-radius: 4mm; }
      .rule { border-bottom: 0.35mm solid #B4A594; margin: 1.6mm 0 2.4mm; }
      .box { background: #fff; padding: 2.6mm 3mm; margin-bottom: 3.5mm; }
      .gap { height: 3mm; }
      .section { margin-top: 1.4mm; }
      .section-title { background: #DDD7CF; padding: 0 0.8mm; font-weight: 700; }
      .no { font-weight: 700; }
      .item { padding-left: 3.5mm; }
      .sub { padding-left: 6.5mm; font-size: 0.94em; }
      .hl, .hl b { color: #1565C0; font-weight: 700; }
      .schedule { color: #555; }
      .hl .schedule { color: #1565C0; }
      table { border-collapse: collapse; width: 100%; }
      table.duty { margin-top: 1mm; font-size: 0.82em; text-align: center; }
      table.duty th, table.duty td { border: 0.25mm solid #333; padding: 0.6mm 0.4mm; font-weight: 400; }
      table.duty .current { background: #BFE3EE; }
      table.orders { border: 0.3mm solid #222; }
      table.orders th, table.orders td { padding: 1.5mm 1.5mm; }
      table.orders tr:nth-child(odd) th, table.orders tr:nth-child(odd) td { background: #F0F0F0; }
      table.orders th { width: 42%; text-align: center; font-weight: 700; border-right: 0.3mm solid #222; }
      table.orders td { font-weight: 700; padding-left: 3mm; }
      .sermon { margin-top: 2mm; text-align: center; }
      .memo { min-height: 48mm; }
      .cover { text-align: center; }
      .issue { text-align: right; font-weight: 700; font-size: 1.05em; }
      .church { font-size: 2.6em; font-weight: 800; letter-spacing: 3mm; margin-top: 5mm; }
      .served { font-size: 1.15em; margin-top: 1mm; }
      .cover-rule { border-bottom: 0.6mm solid #A69685; margin: 3mm 0 6mm; }
      .special { font-weight: 700; font-size: 1.2em; margin-bottom: 4mm; }
      .photo { margin: 2mm 0 8mm; }
      .photo img { max-width: 72mm; max-height: 60mm; }
      .verse { font-size: 1.25em; line-height: 1.9; }
      .liturgy .intro { font-size: 0.92em; margin-bottom: 2mm; }
      .leader { background: #E3E3E3; margin-top: 2.2mm; font-size: 0.92em; }
      .people { font-weight: 700; margin-top: 0.8mm; }
      .plain { font-weight: 700; color: #444; margin-top: 1.6mm; }
      .catechism { font-size: 0.95em; }
      .unit-title { text-align: center; font-weight: 700; margin: 1.5mm 0 2.5mm; }
      .qa { margin-top: 2.6mm; }
      .qno { background: #FFF200; font-weight: 800; padding: 0 0.6mm; }
      .catechism .leader { margin-top: 0.3mm; }
      .answer { margin-top: 0.3mm; }
      .note { margin-top: 0.8mm; font-size: 0.88em; color: #555; }
      """.replace("%W", fmt(PANEL_W)).replace("%H", fmt(PANEL_H));

  /**
   * 대표사진 (형식 + 내용).
   *
   * @param contentType image/*
   * @param content 내용
   */
  public record Photo(String contentType, byte[] content) {
  }

  /**
   * PDF 파일.
   *
   * @param fileName 내려받을 파일 이름
   * @param content 내용
   */
  public record Pdf(String fileName, byte[] content) {
  }

  /** 렌더링 문맥 (언어·공통코드 이름). */
  private final class Context {

    private final BulletinDetail bulletin;
    private final Photo photo;
    private final String langCd;
    private final Locale locale;
    private final Map<String, Map<String, CodeItem>> codes = new LinkedHashMap<>();

    Context(BulletinDetail bulletin, Photo photo, String langCd, Locale locale) {
      this.bulletin = bulletin;
      this.photo = photo;
      this.langCd = langCd;
      this.locale = locale;
    }

    String msg(String key, Object... args) {
      return messageSource.getMessage(key, args, key, locale);
    }

    String code(String group, String code) {
      CodeItem item = codes.computeIfAbsent(group,
          g -> CodeService.byCode(codeService.getCodeItems(g))).get(code);
      return item == null ? code : item.nameOf(langCd, langService.defaultLang());
    }
  }
}
