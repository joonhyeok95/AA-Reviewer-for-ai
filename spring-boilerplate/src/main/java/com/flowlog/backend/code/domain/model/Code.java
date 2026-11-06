package com.flowlog.backend.code.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통코드. (TB_CODE)
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_CODE")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Code extends AssignedIdEntity<CodeId> {

  @EmbeddedId
  private CodeId id; // 그룹코드 + 코드

  @Column(name = "CODE_DESC", length = 1000)
  private String codeDesc; // 설명 (관리용)

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 정렬순서

  @Column(name = "ATTR1", length = 200)
  private String attr1; // 확장속성1

  @Column(name = "ATTR2", length = 200)
  private String attr2; // 확장속성2

  @Column(name = "ATTR3", length = 200)
  private String attr3; // 확장속성3

  @Column(name = "USE_YN", length = 1, nullable = false)
  private String useYn; // 사용여부

  @Builder
  private Code(
      String groupCd, String code, String codeDesc, Integer sortOrder,
      String attr1, String attr2, String attr3, String useYn) {
    this.id = new CodeId(groupCd, code);
    this.codeDesc = codeDesc;
    this.sortOrder = sortOrder;
    this.attr1 = attr1;
    this.attr2 = attr2;
    this.attr3 = attr3;
    this.useYn = useYn;
  }

  /**
   * 코드 정보를 수정한다. (그룹코드, 코드는 변경 불가)
   *
   * @param codeDesc 설명
   * @param sortOrder 정렬순서
   * @param attr1 확장속성1
   * @param attr2 확장속성2
   * @param attr3 확장속성3
   * @param useYn 사용여부
   */
  public void update(
      String codeDesc, Integer sortOrder, String attr1, String attr2, String attr3,
      String useYn) {
    this.codeDesc = codeDesc;
    this.sortOrder = sortOrder;
    this.attr1 = attr1;
    this.attr2 = attr2;
    this.attr3 = attr3;
    this.useYn = useYn;
  }
}
