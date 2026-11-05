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

/**
 * 사용자. (TB_USER)
 *
 * <p>인사 시스템/Keycloak 연동 대상. 권한 매핑에 필요한 최소 정보만 보관한다.
 *
 * @author flowlog
 * @since 2026-11-05
 */
@Getter
@Entity
@Table(name = "TB_USER")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends AssignedIdEntity<String> {

  @Id
  @Column(name = "USER_ID", length = 50)
  private String userId; // 사용자ID (로그인ID = Keycloak username)

  @Column(name = "IDP_SUBJECT", length = 64, unique = true)
  private String idpSubject; // IdP 사용자 고유ID (Keycloak user id = 토큰 sub, 권한 매핑 기준)

  @Column(name = "USER_NM", length = 100, nullable = false)
  private String userNm; // 사용자명

  @Column(name = "EMAIL", length = 200)
  private String email; // 이메일

  @Column(name = "USER_TYPE_CD", length = 50, nullable = false)
  private String userTypeCd; // 사용자유형 (공통코드 USER_TYPE)

  @Column(name = "COMPANY_NM", length = 200)
  private String companyNm; // 소속회사명

  @Column(name = "USE_YN", length = 1, nullable = false)
  private String useYn; // 사용여부

  @Builder
  private User(
      String userId, String idpSubject, String userNm, String email, String userTypeCd,
      String companyNm, String useYn) {
    this.userId = userId;
    this.idpSubject = idpSubject;
    this.userNm = userNm;
    this.email = email;
    this.userTypeCd = userTypeCd;
    this.companyNm = companyNm;
    this.useYn = useYn;
  }

  @Override
  public String getId() {
    return userId;
  }

  /**
   * 사용자 정보를 수정한다. (인사 동기화 등)
   *
   * @param userNm 사용자명
   * @param email 이메일
   * @param userTypeCd 사용자유형
   * @param companyNm 소속회사명
   * @param useYn 사용여부
   */
  public void update(
      String userNm, String email, String userTypeCd, String companyNm, String useYn) {
    this.userNm = userNm;
    this.email = email;
    this.userTypeCd = userTypeCd;
    this.companyNm = companyNm;
    this.useYn = useYn;
  }

  /**
   * IdP 사용자 고유ID 를 연결한다. (최초 로그인 시 또는 관리자가 매핑)
   *
   * @param idpSubject Keycloak user id (토큰 sub)
   */
  public void linkIdpSubject(String idpSubject) {
    this.idpSubject = idpSubject;
  }
}
