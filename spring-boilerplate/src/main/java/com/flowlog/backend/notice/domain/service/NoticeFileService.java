package com.flowlog.backend.notice.domain.service;

import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.notice.NoticeProperties;
import com.flowlog.backend.notice.domain.model.NoticeFile;
import com.flowlog.backend.notice.domain.repository.NoticeFileRepository;
import com.flowlog.backend.notice.domain.repository.NoticeRepository;
import com.flowlog.backend.notice.domain.service.dto.NoticeResponses.NoticeFileResponse;
import com.flowlog.backend.notice.exception.NoticeErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공지 첨부파일 서비스. (게시물 공통 - 언어별 분리 안 함, NT-63)
 *
 * <ul>
 *   <li>저장 : {@code app.notice.file.storage-path}/{yyyy}/{MM}/{UUID} (원본 파일명은 DB 에만)</li>
 *   <li>허용 확장자만, 공지당 최대 개수 제한, 빈 파일 거부. 크기는 multipart 설정으로 제한</li>
 *   <li>다운로드 시 저장 경로가 저장소 밖을 가리키면 거부 (경로 조작 방지)</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Slf4j
@Logging
@Service("noticeFileService")
@Transactional(readOnly = true)
public class NoticeFileService {

  private final NoticeRepository noticeRepository;
  private final NoticeFileRepository noticeFileRepository;
  private final NoticeProperties.File properties;
  private final Path root;
  private final Clock clock;

  /**
   * 생성자.
   *
   * @param noticeRepository 공지 Repository
   * @param noticeFileRepository 첨부 Repository
   * @param properties 공지 설정
   * @param clock 시계
   */
  public NoticeFileService(
      NoticeRepository noticeRepository, NoticeFileRepository noticeFileRepository,
      NoticeProperties properties, Clock clock) {
    this.noticeRepository = noticeRepository;
    this.noticeFileRepository = noticeFileRepository;
    this.properties = properties.file();
    this.root = Path.of(properties.file().storagePath()).toAbsolutePath().normalize();
    this.clock = clock;
  }

  /**
   * 첨부파일 등록.
   *
   * @param noticeId 공지ID
   * @param files 업로드 파일
   * @return 등록된 파일
   */
  @Transactional
  public List<NoticeFileResponse> upload(Long noticeId, List<MultipartFile> files) {
    requireNotice(noticeId);
    long current = noticeFileRepository.countByNoticeId(noticeId);
    if (files.isEmpty()) {
      throw new BusinessException(NoticeErrorCode.NOTICE_FILE_EMPTY);
    }
    if (current + files.size() > properties.maxFiles()) {
      throw new BusinessException(NoticeErrorCode.NOTICE_FILE_LIMIT_EXCEEDED,
          properties.maxFiles());
    }
    files.forEach(this::validate);

    List<NoticeFileResponse> saved = new ArrayList<>();
    for (MultipartFile file : files) {
      String orgName = originalName(file);
      String relative = store(file);
      NoticeFile entity = noticeFileRepository.save(NoticeFile.builder()
          .noticeId(noticeId).orgFileNm(orgName).savePath(relative)
          .fileSize(file.getSize()).fileExt(extensionOf(orgName)).build());
      saved.add(new NoticeFileResponse(entity.getId(), entity.getOrgFileNm(),
          entity.getFileSize(), entity.getFileExt()));
    }
    return saved;
  }

  /**
   * 첨부파일 다운로드 정보.
   *
   * @param noticeId 공지ID
   * @param fileId 파일ID
   * @return 파일 정보 + 리소스
   */
  public Download download(Long noticeId, Long fileId) {
    requireNotice(noticeId);
    NoticeFile file = findFile(noticeId, fileId);
    Path path = resolve(file.getSavePath());
    if (!Files.isRegularFile(path)) {
      log.error("[NoticeFile] 저장 파일 없음 fileId={}", fileId);
      throw new BusinessException(NoticeErrorCode.NOTICE_FILE_NOT_FOUND, fileId);
    }
    return new Download(file.getOrgFileNm(), file.getFileSize(), new PathResource(path));
  }

  /**
   * 첨부파일 삭제. (DB + 저장 파일)
   *
   * @param noticeId 공지ID
   * @param fileId 파일ID
   */
  @Transactional
  public void delete(Long noticeId, Long fileId) {
    requireNotice(noticeId);
    NoticeFile file = findFile(noticeId, fileId);
    noticeFileRepository.delete(file);
    try {
      Files.deleteIfExists(resolve(file.getSavePath()));
    } catch (IOException e) {
      log.warn("[NoticeFile] 저장 파일 삭제 실패 fileId={} ({})", fileId, e.getMessage());
    }
  }

  private void validate(MultipartFile file) {
    if (file.isEmpty()) {
      throw new BusinessException(NoticeErrorCode.NOTICE_FILE_EMPTY);
    }
    String ext = extensionOf(originalName(file));
    if (!properties.allowedExtensions().contains(ext)) {
      throw new BusinessException(NoticeErrorCode.NOTICE_FILE_EXT_NOT_ALLOWED,
          StringUtils.defaultIfBlank(ext, "-"));
    }
  }

  private String store(MultipartFile file) {
    LocalDate today = LocalDate.now(clock);
    String relative = String.format("%04d/%02d/%s", today.getYear(), today.getMonthValue(),
        UUID.randomUUID());
    Path target = resolve(relative);
    try (InputStream in = file.getInputStream()) {
      Files.createDirectories(target.getParent());
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      log.error("[NoticeFile] 저장 실패", e);
      throw new BusinessException(NoticeErrorCode.NOTICE_FILE_IO_FAILED);
    }
    return relative;
  }

  /** 저장소 기준 경로 해석. 저장소 밖이면 거부. */
  private Path resolve(String relative) {
    Path path = root.resolve(relative).normalize();
    if (!path.startsWith(root)) {
      throw new BusinessException(NoticeErrorCode.NOTICE_FILE_IO_FAILED);
    }
    return path;
  }

  private NoticeFile findFile(Long noticeId, Long fileId) {
    return noticeFileRepository.findByIdAndNoticeId(fileId, noticeId)
        .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_FILE_NOT_FOUND, fileId));
  }

  private void requireNotice(Long noticeId) {
    noticeRepository.findById(noticeId).filter(notice -> !notice.isDeleted())
        .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND, noticeId));
  }

  /** 원본 파일명 (경로 제거, 제어문자 제거, 길이 제한). */
  static String originalName(MultipartFile file) {
    String name = StringUtils.defaultString(file.getOriginalFilename());
    name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    name = name.replaceAll("[\\p{Cntrl}]", "").trim();
    return StringUtils.abbreviate(StringUtils.defaultIfBlank(name, "file"), 200);
  }

  /** 확장자 (소문자, 마지막 '.' 이후). */
  static String extensionOf(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  /**
   * 다운로드 정보.
   *
   * @param fileName 원본 파일명
   * @param size 크기
   * @param resource 파일 리소스
   */
  public record Download(String fileName, Long size, Resource resource) {
  }
}
