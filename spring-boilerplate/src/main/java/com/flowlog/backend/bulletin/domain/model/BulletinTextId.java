package com.flowlog.backend.bulletin.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * TB_BULLETIN_TEXT PK.
 *
 * @author flowlog
 * @since 2026-11-08
 */
@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BulletinTextId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "BULLETIN_ID")
  private Long bulletinId; // 주보 ID

  @Column(name = "TEXT_ID")
  private Long textId; // 전례문 ID
}
