import type { PathItem, Qa, UnitNode } from './types'

type Translate = (key: string, values?: Record<string, unknown>) => string

/** 단위 이름 : 제45주일 / Lord's Day 45 (+ 제목). 번호가 없으면 유형명 */
export function unitLabel(unit: Pick<PathItem, 'unitTypeCd' | 'unitNo' | 'title'>,
  typeName: (code: string) => string, t: Translate, withTitle = true): string {
  const type = typeName(unit.unitTypeCd)
  const head = unit.unitNo === null ? type : t('catechism.unitNo', { no: unit.unitNo, type })
  return withTitle && unit.title ? `${head} ${unit.title}` : head
}

/** 문 번호 범위 : 116~119문 */
export function qaRange(qas: Pick<Qa, 'questionNo'>[], t: Translate): string {
  const numbers = qas.map((qa) => qa.questionNo).filter((no): no is number => no !== null)
  if (!numbers.length) return ''
  const from = Math.min(...numbers)
  const to = Math.max(...numbers)
  return from === to ? t('catechism.qaOne', { no: from }) : t('catechism.qaRange', { from, to })
}

/** 트리를 들여쓰기 목록으로 (선택 상자·검색용) */
export function flattenUnits(units: UnitNode[], depth = 0)
  : { unit: UnitNode; depth: number }[] {
  return units.flatMap((unit) => [{ unit, depth }, ...flattenUnits(unit.children, depth + 1)])
}

/** 단위와 그 하위 ID (상위로 고를 수 없는 단위) */
export function selfAndDescendants(unit: UnitNode): number[] {
  return [unit.unitId, ...unit.children.flatMap(selfAndDescendants)]
}
