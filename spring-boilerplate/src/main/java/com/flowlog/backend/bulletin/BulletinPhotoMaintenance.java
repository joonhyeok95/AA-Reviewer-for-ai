package com.flowlog.backend.bulletin;

import com.flowlog.backend.bulletin.domain.service.BulletinPhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 시 저장된 주보 사진 정리 - 메타데이터(촬영 위치 GPS 등)가 남은 사진을 다시 그려 저장한다.
 * (사진 정규화 도입 전에 올린 사진용. 정리된 사진은 건너뛰므로 매번 실행해도 비용이 작다)
 *
 * @author flowlog
 * @since 2026-11-07
 */
@Component
@RequiredArgsConstructor
public class BulletinPhotoMaintenance implements ApplicationRunner {

  private final BulletinPhotoService photoService;

  @Override
  public void run(ApplicationArguments args) {
    photoService.normalizeStoredPhotos();
  }
}
