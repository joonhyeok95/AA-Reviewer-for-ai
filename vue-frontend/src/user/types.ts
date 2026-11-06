/** GET /api/v1/org/tree - 사업장 */
export interface OrgWorkplace {
  workplaceCd: string
  workplaceNm: string
  depts: OrgDept[]
}

/** 부서 (하위 부서 트리) */
export interface OrgDept {
  deptCd: string
  deptNm: string
  workplaceCd: string
  children: OrgDept[]
}

/** PUT /api/v1/users/me/lang 결과 */
export interface UserLang {
  userId: string
  langCd: string
}
