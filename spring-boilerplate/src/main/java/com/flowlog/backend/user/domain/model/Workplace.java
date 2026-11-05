package com.flowlog.backend.user.domain.model;

import com.flowlog.backend.global.common.model.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * 사업장 (인사 마스터). (TB_WORKPLACE)
 *
 * <p>업무 테이블에는 사업장코드만 저장하고 명칭은 조회 시점에 이 테이블에서 가져온다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_WORKPLACE")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workplace extends AssignedIdEntity<String> {

  /** 국문 명칭을 사용하는 언어. */
  public static final String KOREAN = "ko";

  @Id
  @Column(name = "WORKPLACE_CD", length = 50)
  private String workplaceCd; // 사업장코드

  @Column(name = "WORKPLACE_NM", length = 200, nullable = false)
  private String workplaceNm; // 사업장명 (국문)

  @Column(name = "WORKPLACE_NM_EN", length = 200)
  private String workplaceNmEn; // 사업장명 (영문)

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder; // 정렬순서

  @Column(name = "USE_YN", length = 1, nullable = false)
  private String useYn; // 사용여부

  @Builder
  private Workplace(
      String workplaceCd, String workplaceNm, String workplaceNmEn, Integer sortOrder,
      String useYn) {
    this.workplaceCd = workplaceCd;
    this.workplaceNm = workplaceNm;
    this.workplaceNmEn = workplaceNmEn;
    this.sortOrder = sortOrder;
    this.useYn = useYn;
  }

  @Override
  public String getId() {
    return workplaceCd;
  }

  /**
   * 언어별 사업장명. (ko = 국문, 그 외 = 영문명, 영문명이 없으면 국문)
   *
   * @param langCd 언어코드
   * @return 사업장명
   */
  public String nameOf(String langCd) {
    return localizedName(workplaceNm, workplaceNmEn, langCd);
  }

  /**
   * 인사 마스터 명칭 선택 규칙. (인사 마스터는 국문/영문만 제공)
   *
   * @param korean 국문명
   * @param english 영문명
   * @param langCd 언어코드
   * @return 명칭
   */
  static String localizedName(String korean, String english, String langCd) {
    return KOREAN.equals(langCd) || StringUtils.isBlank(english) ? korean : english;
  }
}
