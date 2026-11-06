package com.flowlog.backend.notice.web;

import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.common.domain.service.dto.PageResponse;
import com.flowlog.backend.global.common.model.MessageResponse;
import com.flowlog.backend.global.component.MessageHttpResponse;
import com.flowlog.backend.notice.domain.service.NoticeFileService;
import com.flowlog.backend.notice.domain.service.NoticeService;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeDetailResponse;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeEditResponse;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeFileResponse;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeMailHistResponse;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeSummaryResponse;
import com.flowlog.backend.notice.domain.service.dto.NoticeSaveRequest;
import com.flowlog.backend.notice.domain.service.dto.NoticeSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공지사항 API.
 *
 * <p>권한 (게시판 scope) : 조회 {@code board.list}, 등록·첨부 {@code board.write}, 수정·첨부 삭제·메일 이력
 * {@code board.update}, 삭제 {@code board.delete} (SecurityConfig)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Logging
@Validated
@Tag(name = "Notice", description = "공지사항 (다국어)")
@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

  private final NoticeService noticeService;
  private final NoticeFileService noticeFileService;
  private final NoticeViewerResolver viewerResolver;
  private final MessageHttpResponse messageHttpResponse;

  /**
   * 공지 목록.
   *
   * @param condition 검색 조건
   * @param pageable 페이징
   * @param authentication 인증 객체
   * @param locale 요청 언어
   * @return 목록
   */
  @Operation(summary = "공지 목록",
      description = "사용자 언어 버전(없으면 대표 언어 버전)으로 표시·검색합니다. 제공 언어 배지 포함.")
  @GetMapping
  public ResponseEntity<MessageResponse<PageResponse<NoticeSummaryResponse>>> getNotices(
      @ParameterObject @Valid @ModelAttribute NoticeSearchRequest condition,
      @ParameterObject @PageableDefault(size = 20) Pageable pageable,
      Authentication authentication, Locale locale) {
    return messageHttpResponse.successPage(noticeService.getNotices(condition, pageable,
        viewerResolver.resolve(authentication, locale)));
  }

  /**
   * 노출 중인 팝업 공지.
   *
   * @param authentication 인증 객체
   * @param locale 요청 언어
   * @return 팝업 공지
   */
  @Operation(summary = "팝업 공지 목록")
  @GetMapping("/popups")
  public ResponseEntity<MessageResponse<List<NoticeSummaryResponse>>> getPopups(
      Authentication authentication, Locale locale) {
    return messageHttpResponse.success(
        noticeService.getPopups(viewerResolver.resolve(authentication, locale)));
  }

  /**
   * 공지 상세.
   *
   * @param id 공지ID
   * @param lang 언어 전환 (선택)
   * @param authentication 인증 객체
   * @param locale 요청 언어
   * @return 상세
   */
  @Operation(summary = "공지 상세",
      description = "표시 언어 = lang(전환) → 사용자 언어 → 대표 언어 순으로 존재하는 버전. 조회수는 사용자·일자별 1회.")
  @GetMapping("/{id}")
  public ResponseEntity<MessageResponse<NoticeDetailResponse>> getNotice(
      @Parameter(description = "공지ID", example = "1") @PathVariable("id") @Positive Long id,
      @Parameter(description = "언어 전환", example = "en") @RequestParam(required = false)
      @Size(max = 10) String lang,
      Authentication authentication, Locale locale) {
    return messageHttpResponse.success(
        noticeService.getNotice(id, lang, viewerResolver.resolve(authentication, locale)));
  }

  /**
   * 공지 수정 화면 데이터.
   *
   * @param id 공지ID
   * @param authentication 인증 객체
   * @param locale 요청 언어
   * @return 전체 언어 버전 + 메일 설정
   */
  @Operation(summary = "공지 수정 화면 데이터", description = "작성된 전체 언어 버전과 메일 설정을 반환합니다.")
  @GetMapping("/{id}/edit")
  public ResponseEntity<MessageResponse<NoticeEditResponse>> getNoticeForEdit(
      @PathVariable("id") @Positive Long id, Authentication authentication, Locale locale) {
    return messageHttpResponse.success(noticeService.getNoticeForEdit(id,
        viewerResolver.resolve(authentication, locale).langCd()));
  }

  /**
   * 공지 등록.
   *
   * @param request 등록 요청
   * @param authentication 인증 객체
   * @param locale 요청 언어
   * @return 등록된 공지 (201)
   */
  @Operation(summary = "공지 등록",
      description = "최소 1개 언어의 제목·본문 필수. 대표 언어 미지정 시 처음 입력한 언어. 본문 HTML 은 정화 후 저장.")
  @PostMapping
  public ResponseEntity<MessageResponse<NoticeEditResponse>> createNotice(
      @Valid @RequestBody NoticeSaveRequest request, Authentication authentication,
      Locale locale) {
    return messageHttpResponse.success(noticeService.createNotice(request,
        viewerResolver.resolve(authentication, locale).langCd()));
  }

  /**
   * 공지 수정.
   *
   * @param id 공지ID
   * @param request 수정 요청
   * @param authentication 인증 객체
   * @param locale 요청 언어
   * @return 수정된 공지
   */
  @Operation(summary = "공지 수정", description = "언어 버전은 요청 기준으로 교체됩니다. 메일 발송 후에는 메일 설정이 바뀌지 않습니다.")
  @PutMapping("/{id}")
  public ResponseEntity<MessageResponse<NoticeEditResponse>> updateNotice(
      @PathVariable("id") @Positive Long id, @Valid @RequestBody NoticeSaveRequest request,
      Authentication authentication, Locale locale) {
    return messageHttpResponse.success(noticeService.updateNotice(id, request,
        viewerResolver.resolve(authentication, locale).langCd()));
  }

  /**
   * 공지 삭제.
   *
   * @param id 공지ID
   * @return result 없음
   */
  @Operation(summary = "공지 삭제", description = "논리 삭제. 발송 대기 중인 메일은 취소됩니다.")
  @DeleteMapping("/{id}")
  public ResponseEntity<MessageResponse<Void>> deleteNotice(@PathVariable("id") @Positive Long id) {
    noticeService.deleteNotice(id);
    return messageHttpResponse.success();
  }

  /**
   * 첨부파일 등록. (게시물 공통)
   *
   * @param id 공지ID
   * @param files 파일 (multipart, 필드명 files)
   * @return 등록된 파일 (201)
   */
  @Operation(summary = "첨부파일 등록", description = "허용 확장자, 공지당 최대 개수 제한")
  @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<MessageResponse<List<NoticeFileResponse>>> uploadFiles(
      @PathVariable("id") @Positive Long id, @RequestPart("files") List<MultipartFile> files) {
    return messageHttpResponse.success(noticeFileService.upload(id, files));
  }

  /**
   * 첨부파일 다운로드.
   *
   * @param id 공지ID
   * @param fileId 파일ID
   * @return 파일 (attachment)
   */
  @Operation(summary = "첨부파일 다운로드")
  @GetMapping("/{id}/files/{fileId}")
  public ResponseEntity<Resource> downloadFile(
      @PathVariable("id") @Positive Long id, @PathVariable("fileId") @Positive Long fileId) {
    NoticeFileService.Download download = noticeFileService.download(id, fileId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(download.size())
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(download.resource());
  }

  /**
   * 첨부파일 삭제.
   *
   * @param id 공지ID
   * @param fileId 파일ID
   * @return result 없음
   */
  @Operation(summary = "첨부파일 삭제")
  @DeleteMapping("/{id}/files/{fileId}")
  public ResponseEntity<MessageResponse<Void>> deleteFile(
      @PathVariable("id") @Positive Long id, @PathVariable("fileId") @Positive Long fileId) {
    noticeFileService.delete(id, fileId);
    return messageHttpResponse.success();
  }

  /**
   * 메일 발송 이력.
   *
   * @param id 공지ID
   * @return 수신자별 이력
   */
  @Operation(summary = "메일 발송 이력")
  @GetMapping("/{id}/mail-histories")
  public ResponseEntity<MessageResponse<List<NoticeMailHistResponse>>> getMailHistories(
      @PathVariable("id") @Positive Long id) {
    return messageHttpResponse.success(noticeService.getMailHistories(id));
  }
}
