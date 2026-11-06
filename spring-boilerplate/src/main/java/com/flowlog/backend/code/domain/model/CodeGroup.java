package com.flowlog.backend.code.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통코드 그룹. (TB_CODE_GROUP)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_CODE_GROUP")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeGroup extends AssignedIdEntity<String> {

  @Id
  @Column(name = "GROUP_CD", length = 50)
  private String groupCd; // 그룹코드

  @Column(name = "GROUP_DESC", length = 1000)
  private String groupDesc; // 설명 (관리용)

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 정렬순서

  @Column(name = "SYSTEM_YN", length = 1, nullable = false)
  private String systemYn; // 시스템코드여부

  @Column(name = "USE_YN", length = 1, nullable = false)
  private String useYn; // 사용여부

  @Builder
  private CodeGroup(
      String groupCd, String groupDesc, Integer sortOrder, String systemYn, String useYn) {
    this.groupCd = groupCd;
    this.groupDesc = groupDesc;
    this.sortOrder = sortOrder;
    this.systemYn = systemYn;
    this.useYn = useYn;
  }

  @Override
  public String getId() {
    return groupCd;
  }

  /**
   * 그룹 정보를 수정한다. (그룹코드, 시스템여부는 변경 불가)
   *
   * @param groupDesc 설명
   * @param sortOrder 정렬순서
   * @param useYn 사용여부
   */
  public void update(String groupDesc, Integer sortOrder, String useYn) {
    this.groupDesc = groupDesc;
    this.sortOrder = sortOrder;
    this.useYn = useYn;
  }
}
