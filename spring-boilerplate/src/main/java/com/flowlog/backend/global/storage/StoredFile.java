package com.flowlog.backend.global.storage;

import com.flowlog.backend.global.common.model.BaseEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DB 저장 파일 내용. (TB_FILE_STORE - {@link DatabaseFileStorage})
 *
 * @author flowlog
 * @since 2026-11-06
 */
@Getter
@Entity
@Table(name = "TB_FILE_STORE")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile extends BaseEntity {

  @Id
  @Column(name = "FILE_KEY", length = 64)
  private String fileKey; // 저장 키 (UUID)

  @Column(name = "FILE_SIZE", nullable = false, updatable = false)
  private Long fileSize; // 크기 (바이트)

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @Column(name = "FILE_DATA", nullable = false, updatable = false)
  private byte[] fileData; // 내용

  /**
   * 생성자.
   *
   * @param fileKey 저장 키
   * @param fileData 내용
   */
  public StoredFile(String fileKey, byte[] fileData) {
    this.fileKey = fileKey;
    this.fileData = fileData;
    this.fileSize = (long) fileData.length;
  }
}
