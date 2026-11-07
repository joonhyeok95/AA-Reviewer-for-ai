package com.flowlog.backend.bulletin.domain.service;

import com.flowlog.backend.bulletin.domain.model.Bulletin;
import com.flowlog.backend.bulletin.domain.repository.BulletinRepository;
import com.flowlog.backend.bulletin.exception.BulletinErrorCode;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.global.storage.FileStorage;
import com.flowlog.backend.global.storage.FileStorageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 주보 대표사진. (주보당 1장)
 *
 * <ul>
 *   <li>저장 : 공지 첨부와 같은 {@link FileStorage} (local 디렉터리 / db BLOB). 주보에는 저장 키·형식만</li>
 *   <li>형식은 파일명·Content-Type 이 아니라 <b>내용(시그니처)</b>으로 판별 - JPG, PNG, GIF, WEBP 만.
 *       응답 Content-Type 도 판별한 형식으로 내려 다른 내용(HTML·SVG 스크립트)이 이미지로 위장하지 못하게 한다</li>
 *   <li>최대 {@value #MAX_MB}MB. 바꾸거나 지우면 이전 파일은 커밋 후 저장소에서 지운다</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-07
 */
@Slf4j
@Logging
@Service("bulletinPhotoService")
@Transactional(readOnly = true)
public class BulletinPhotoService {

  /** 최대 크기 (MB). */
  public static final int MAX_MB = 10;

  private static final long MAX_BYTES = MAX_MB * 1024L * 1024L;

  private final BulletinRepository bulletinRepository;
  private final FileStorage fileStorage;

  /**
   * 생성자.
   *
   * @param bulletinRepository 주보 Repository
   * @param fileStorage 파일 저장소
   */
  public BulletinPhotoService(BulletinRepository bulletinRepository, FileStorage fileStorage) {
    this.bulletinRepository = bulletinRepository;
    this.fileStorage = fileStorage;
  }

  /**
   * 대표사진 올리기 (있으면 바꾸기).
   *
   * @param bulletinId 주보 ID
   * @param file 이미지
   * @throws BusinessException 주보 없음(B8001), 형식(B8009), 크기(B8010), 저장 실패(B8012)
   */
  @Transactional
  public void upload(Long bulletinId, MultipartFile file) {
    Bulletin bulletin = findBulletin(bulletinId);
    if (file == null || file.isEmpty()) {
      throw new BusinessException(BulletinErrorCode.PHOTO_INVALID);
    }
    if (file.getSize() > MAX_BYTES) {
      throw new BusinessException(BulletinErrorCode.PHOTO_TOO_LARGE, String.valueOf(MAX_MB));
    }
    byte[] content;
    try (InputStream in = file.getInputStream()) {
      content = in.readAllBytes();
    } catch (IOException e) {
      log.error("[BulletinPhoto] 읽기 실패 bulletinId={}", bulletinId, e);
      throw new BusinessException(BulletinErrorCode.PHOTO_IO_FAILED);
    }
    String type = imageType(content);
    if (type == null) {
      throw new BusinessException(BulletinErrorCode.PHOTO_INVALID);
    }
    String key;
    try {
      key = fileStorage.store(new ByteArrayInputStream(content), content.length);
    } catch (FileStorageException e) {
      log.error("[BulletinPhoto] 저장 실패 bulletinId={}", bulletinId, e);
      throw new BusinessException(BulletinErrorCode.PHOTO_IO_FAILED);
    }
    deleteAfterCommit(bulletin.changePhoto(key, type)); // 변경 감지로 수정일시도 바뀜 (화면 캐시 갱신용)
  }

  /**
   * 대표사진 읽기.
   *
   * @param bulletinId 주보 ID
   * @param includeDrafts 작성 중 주보 사진도 볼 수 있는지 (편집 권한)
   * @return 형식 + 리소스
   * @throws BusinessException 주보·사진 없음 (B8001, B8011), 읽기 실패 (B8012)
   */
  public Photo load(Long bulletinId, boolean includeDrafts) {
    Bulletin bulletin = findBulletin(bulletinId);
    if (!includeDrafts && !BulletinService.PUBLISHED.equals(bulletin.getStatusCd())) {
      throw new BusinessException(BulletinErrorCode.BULLETIN_NOT_FOUND, String.valueOf(bulletinId));
    }
    if (!bulletin.hasPhoto()) {
      throw new BusinessException(BulletinErrorCode.PHOTO_NOT_FOUND, String.valueOf(bulletinId));
    }
    try {
      return new Photo(bulletin.getPhotoType(), fileStorage.load(bulletin.getPhotoKey()));
    } catch (FileStorageException e) {
      if (e.isNotFound()) {
        log.error("[BulletinPhoto] 저장 파일 없음 bulletinId={}", bulletinId);
        throw new BusinessException(BulletinErrorCode.PHOTO_NOT_FOUND, String.valueOf(bulletinId));
      }
      log.error("[BulletinPhoto] 읽기 실패 bulletinId={}", bulletinId, e);
      throw new BusinessException(BulletinErrorCode.PHOTO_IO_FAILED);
    }
  }

  /**
   * 대표사진 지우기. (없으면 무시)
   *
   * @param bulletinId 주보 ID
   */
  @Transactional
  public void delete(Long bulletinId) {
    Bulletin bulletin = findBulletin(bulletinId);
    if (bulletin.hasPhoto()) {
      deleteAfterCommit(bulletin.changePhoto(null, null));
    }
  }

  /**
   * 저장 파일을 커밋 후 지운다. (롤백되면 그대로 둔다)
   *
   * @param key 저장 키 (null 이면 무시)
   */
  void deleteAfterCommit(String key) {
    if (key == null) {
      return;
    }
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      fileStorage.delete(key);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        fileStorage.delete(key);
      }
    });
  }

  /**
   * 내용으로 이미지 형식 판별.
   *
   * @param content 파일 내용
   * @return MIME 형식, 허용 형식이 아니면 null
   */
  static String imageType(byte[] content) {
    if (startsWith(content, 0, 0xFF, 0xD8, 0xFF)) {
      return "image/jpeg";
    }
    if (startsWith(content, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
      return "image/png";
    }
    if (startsWith(content, 0, 'G', 'I', 'F', '8')) {
      return "image/gif";
    }
    if (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P')) {
      return "image/webp";
    }
    return null;
  }

  private static boolean startsWith(byte[] content, int offset, int... signature) {
    if (content.length < offset + signature.length) {
      return false;
    }
    return matches(content, offset, signature);
  }

  private static boolean matches(byte[] content, int offset, int[] signature) {
    for (int i = 0; i < signature.length; i++) {
      if ((content[offset + i] & 0xFF) != signature[i]) {
        return false;
      }
    }
    return true;
  }

  private Bulletin findBulletin(Long id) {
    return bulletinRepository.findById(id)
        .orElseThrow(() -> new BusinessException(BulletinErrorCode.BULLETIN_NOT_FOUND,
            String.valueOf(id)));
  }

  /**
   * 대표사진.
   *
   * @param contentType 형식 (image/*)
   * @param resource 내용
   */
  public record Photo(String contentType, Resource resource) {
  }
}
