package com.flowlog.backend.global.storage;

/**
 * 파일 저장소 오류.
 *
 * @author flowlog
 * @since 2026-11-06
 */
public class FileStorageException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final boolean notFound;

  /**
   * 생성자.
   *
   * @param message 메시지
   * @param notFound 파일이 없는 경우 true
   * @param cause 원인
   */
  public FileStorageException(String message, boolean notFound, Throwable cause) {
    super(message, cause);
    this.notFound = notFound;
  }

  /**
   * 파일이 없는 경우인지.
   *
   * @return 없으면 true
   */
  public boolean isNotFound() {
    return notFound;
  }
}
