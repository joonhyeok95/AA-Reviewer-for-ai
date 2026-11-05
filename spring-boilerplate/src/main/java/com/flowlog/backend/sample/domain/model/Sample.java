package com.flowlog.backend.sample.domain.model;

import com.flowlog.backend.global.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 샘플 Entity. (boilerplate 예시 - 신규 도메인 작성 시 참고)
 *
 * <ul>
 *   <li>{@code @Setter} 금지 : 상태 변경은 의미 있는 메서드({@link #update})로만 한다.</li>
 *   <li>PK 는 Oracle 호환을 위해 시퀀스(SQ_테이블명)를 사용한다.</li>
 *   <li>연관관계 매핑(@ManyToOne 등) 대신 FK 컬럼을 직접 매핑하는 것을 기본으로 한다.</li>
 * </ul>
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(
    name = "TB_SAMPLE",
    uniqueConstraints = @UniqueConstraint(name = "UK_SAMPLE_NAME", columnNames = "NAME"))
@SequenceGenerator(name = "SQ_SAMPLE_GEN", sequenceName = "SQ_SAMPLE", allocationSize = 1)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sample extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SQ_SAMPLE_GEN")
  @Column(name = "SAMPLE_ID")
  private Long id; // 샘플 ID

  @Column(name = "NAME", length = 100, nullable = false)
  private String name; // 샘플명

  @Column(name = "DESCRIPTION", length = 500)
  private String description; // 설명

  @Column(name = "USE_YN", length = 1, nullable = false)
  private String useYn; // 사용여부 (Y/N)

  @Builder
  private Sample(String name, String description, String useYn) {
    this.name = name;
    this.description = description;
    this.useYn = useYn;
  }

  /**
   * 샘플 정보를 수정한다.
   *
   * @param name 샘플명
   * @param description 설명
   * @param useYn 사용여부
   */
  public void update(String name, String description, String useYn) {
    this.name = name;
    this.description = description;
    this.useYn = useYn;
  }
}
