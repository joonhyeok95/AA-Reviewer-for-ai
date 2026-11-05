package com.flowlog.backend.global.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * 생성/수정 일시 + 생성/수정자 공통 Entity.
 *
 * <p>생성/수정자는 {@code JpaConfig#auditorAware()} 에서 주입된다. (인증 연동 전에는 "system")
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

  @CreatedBy
  @Column(name = "CREATED_BY", length = 50, nullable = false, updatable = false)
  private String createdBy; // 생성자

  @LastModifiedBy
  @Column(name = "UPDATED_BY", length = 50, nullable = false)
  private String updatedBy; // 수정자
}
