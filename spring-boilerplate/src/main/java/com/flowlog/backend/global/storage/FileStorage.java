package com.flowlog.backend.global.storage;

import java.io.InputStream;
import org.springframework.core.io.Resource;

/**
 * 첨부파일 저장소. (업무 메타데이터는 각 도메인 테이블, 파일 내용만 여기에)
 *
 * <p>여러 인스턴스로 운영할 때는 모든 인스턴스가 같은 파일을 볼 수 있어야 한다.
 * <ul>
 *   <li>{@link LocalFileStorage} : 디렉터리 저장. 다중 인스턴스면 공유 디렉터리(NFS 등)를 마운트해야 한다</li>
 *   <li>{@link DatabaseFileStorage} : DB(BLOB) 저장. 추가 인프라 없이 모든 인스턴스가 공유</li>
 * </ul>
 * 저장 방식을 바꾸면 기존 파일은 이전 저장소에 남으므로 이관이 필요하다.
 *
 * @author flowlog
 * @since 2026-11-06
 */
public interface FileStorage {

  /**
   * 파일을 저장한다.
   *
   * @param content 파일 내용
   * @param size 크기 (바이트)
   * @return 저장 키 (업무 테이블에 저장해 조회·삭제에 사용)
   * @throws FileStorageException 저장 실패
   */
  String store(InputStream content, long size);

  /**
   * 파일을 읽는다.
   *
   * @param key 저장 키
   * @return 파일 리소스
   * @throws FileStorageException 파일이 없거나 읽기 실패 ({@link FileStorageException#isNotFound()})
   */
  Resource load(String key);

  /**
   * 파일을 삭제한다. (없으면 무시)
   *
   * @param key 저장 키
   */
  void delete(String key);
}
