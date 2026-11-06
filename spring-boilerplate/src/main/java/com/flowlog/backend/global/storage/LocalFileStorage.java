package com.flowlog.backend.global.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

/**
 * 디렉터리 파일 저장소. 키 = {yyyy}/{MM}/{UUID} (원본 파일명은 업무 테이블에만)
 *
 * <p>저장 경로는 웹 경로 밖이어야 한다. 키가 저장소 밖을 가리키면 거부한다 (경로 조작 방지).
 * 여러 인스턴스로 운영하면 이 경로는 모든 인스턴스가 공유하는 디렉터리(NFS 등)여야 한다.
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Slf4j
public class LocalFileStorage implements FileStorage {

  private final Path root;
  private final Clock clock;

  /**
   * 생성자.
   *
   * @param root 저장 경로
   * @param clock 시계
   */
  public LocalFileStorage(String root, Clock clock) {
    this.root = Path.of(root).toAbsolutePath().normalize();
    this.clock = clock;
  }

  @Override
  public String store(InputStream content, long size) {
    LocalDate today = LocalDate.now(clock);
    String key = String.format("%04d/%02d/%s", today.getYear(), today.getMonthValue(),
        UUID.randomUUID());
    Path target = resolve(key);
    try {
      Files.createDirectories(target.getParent());
      Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new FileStorageException("파일 저장 실패", false, e);
    }
    return key;
  }

  @Override
  public Resource load(String key) {
    Path path = resolve(key);
    if (!Files.isRegularFile(path)) {
      throw new FileStorageException("저장 파일 없음: " + key, true, null);
    }
    return new PathResource(path);
  }

  @Override
  public void delete(String key) {
    try {
      Files.deleteIfExists(resolve(key));
    } catch (IOException e) {
      log.warn("[FileStorage] 저장 파일 삭제 실패 key={} ({})", key, e.getMessage());
    }
  }

  private Path resolve(String key) {
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root)) {
      throw new FileStorageException("저장소 밖 경로", false, null);
    }
    return path;
  }
}
