package com.flowlog.backend.global.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 생성/수정 일시 공통 Entity.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

  @CreatedDate
  @Column(name = "CREATED_AT", nullable = false, updatable = false)
  private LocalDateTime createdAt; // 생성일시

  @LastModifiedDate
  @Column(name = "UPDATED_AT", nullable = false)
  private LocalDateTime updatedAt; // 수정일시
}
