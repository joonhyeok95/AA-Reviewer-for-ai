package com.flowlog.backend.role.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권한 다국어 PK. (ROLE_ID + LANG_CD)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleLangId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "ROLE_ID")
  private Long roleId; // 권한ID

  @Column(name = "LANG_CD", length = 10)
  private String langCd; // 언어코드
}
