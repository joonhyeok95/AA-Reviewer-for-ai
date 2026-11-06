package com.flowlog.backend.global.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/**
 * DB(BLOB) 파일 저장소. (TB_FILE_STORE)
 *
 * <p>추가 인프라 없이 모든 인스턴스가 같은 파일을 본다. 파일 내용을 메모리에 올려 저장·조회하므로
 * 업로드 크기 제한({@code spring.servlet.multipart.max-file-size})을 수십 MB 이내로 유지한다.
 * 업무 트랜잭션 안에서 호출하면 업무 데이터와 함께 커밋·롤백된다.
 *
 * @author flowlog
 * @since 2026-11-06
 */
public class DatabaseFileStorage implements FileStorage {

  private final StoredFileRepository repository;

  /**
   * 생성자.
   *
   * @param repository DB 저장 파일 Repository
   */
  public DatabaseFileStorage(StoredFileRepository repository) {
    this.repository = repository;
  }

  @Override
  public String store(InputStream content, long size) {
    String key = UUID.randomUUID().toString();
    try {
      repository.save(new StoredFile(key, content.readAllBytes()));
    } catch (IOException e) {
      throw new FileStorageException("파일 읽기 실패", false, e);
    }
    return key;
  }

  @Override
  public Resource load(String key) {
    return repository.findById(key)
        .map(file -> (Resource) new ByteArrayResource(file.getFileData()))
        .orElseThrow(() -> new FileStorageException("저장 파일 없음: " + key, true, null));
  }

  @Override
  public void delete(String key) {
    repository.deleteById(key);
  }
}
