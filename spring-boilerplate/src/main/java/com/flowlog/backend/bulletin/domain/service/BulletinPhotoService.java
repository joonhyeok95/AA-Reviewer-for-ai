package com.flowlog.backend.bulletin.domain.service;

import com.flowlog.backend.bulletin.domain.model.Bulletin;
import com.flowlog.backend.bulletin.domain.repository.BulletinRepository;
import com.flowlog.backend.bulletin.exception.BulletinErrorCode;
import com.flowlog.backend.global.aop.Logging;
import com.flowlog.backend.global.exception.BusinessException;
import com.flowlog.backend.global.storage.FileStorage;
import com.flowlog.backend.global.storage.FileStorageException;
import com.flowlog.backend.global.storage.FileSignatures;
import com.flowlog.backend.global.storage.ImageNormalizer;
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
 *   <li>형식은 파일명·Content-Type 이 아니라 <b>내용(시그니처)</b>으로 판별 - JPG, PNG, GIF, WEBP 만</li>
 *   <li><b>다시 그려 저장</b> ({@link ImageNormalizer}) : 촬영 위치(GPS) 등 EXIF 메타데이터 제거(회전은 반영),
 *       최대 {@value #MAX_PIXELS} 화소(압축 폭탄 차단), 긴 변 {@value #MAX_SIDE}px, 결과는 JPEG/PNG.
 *       이미지에 끼워 넣은 HTML·스크립트도 사라진다</li>
 *   <li>권한 : 수정 권한(bulletin.edit) 또는 <b>본인이 등록한 주보</b> (등록 권한만 있으면 남의 주보 사진을 못 바꿈)</li>
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

  /** 최대 화소 (4천만 - 휴대폰 원본 사진 여유 있게). */
  public static final long MAX_PIXELS = 40_000_000L;

  /** 저장할 때 긴 변 최대 (px). */
  public static final int MAX_SIDE = 2560;

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
   * @param canEdit 수정 권한 여부 (없으면 본인이 등록한 주보만)
   * @param user 요청자 (등록자와 비교 - 감사 컬럼 CREATED_BY 와 같은 값)
   * @throws BusinessException 주보 없음(B8001), 형식(B8009), 크기(B8010), 화소(B8014), 권한(B8015),
   *     저장 실패(B8012)
   */
  @Transactional
  public void upload(Long bulletinId, MultipartFile file, boolean canEdit, String user) {
    Bulletin bulletin = findBulletin(bulletinId);
    if (!canEdit && (user == null || !user.equals(bulletin.getCreatedBy()))) {
      throw new BusinessException(BulletinErrorCode.PHOTO_FORBIDDEN);
    }
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
    if (FileSignatures.imageKind(content) == null) {
      throw new BusinessException(BulletinErrorCode.PHOTO_INVALID);
    }
    ImageNormalizer.Result image;
    try {
      image = ImageNormalizer.normalize(content, MAX_PIXELS, MAX_SIDE);
    } catch (ImageNormalizer.Rejected e) {
      throw e.reason() == ImageNormalizer.Rejected.Reason.TOO_MANY_PIXELS
          ? new BusinessException(BulletinErrorCode.PHOTO_PIXELS, String.valueOf(MAX_PIXELS / 1_000_000))
          : new BusinessException(BulletinErrorCode.PHOTO_INVALID);
    }
    String type = image.contentType();
    String key;
    try {
      key = fileStorage.store(new ByteArrayInputStream(image.content()), image.content().length);
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
   * 이미 저장된 사진 정리 - 메타데이터(촬영 위치 등)가 남은 사진을 다시 그려 바꾼다. (정규화 도입 전 업로드분, 여러 번 실행해도 같음)
   *
   * @return 정리한 사진 수
   */
  @Transactional
  public int normalizeStoredPhotos() {
    int changed = 0;
    for (Bulletin bulletin : bulletinRepository.findByPhotoKeyIsNotNull()) {
      try {
        byte[] content;
        try (InputStream in = fileStorage.load(bulletin.getPhotoKey()).getInputStream()) {
          content = in.readAllBytes();
        }
        if (!ImageNormalizer.hasMetadata(content)) {
          continue;
        }
        ImageNormalizer.Result image = ImageNormalizer.normalize(content, MAX_PIXELS, MAX_SIDE);
        String key = fileStorage.store(new ByteArrayInputStream(image.content()),
            image.content().length);
        deleteAfterCommit(bulletin.changePhoto(key, image.contentType()));
        changed++;
      } catch (IOException | RuntimeException e) {
        log.warn("[BulletinPhoto] 저장 사진 정리 실패 bulletinId={} ({})", bulletin.getId(),
            e.getMessage());
      }
    }
    if (changed > 0) {
      log.info("[BulletinPhoto] 저장 사진 {}건 메타데이터 제거", changed);
    }
    return changed;
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
