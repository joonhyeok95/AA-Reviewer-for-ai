/** 반복 입력 목록 조작 (제자리 변경 - reactive 배열에 그대로 사용) */

export function moveItem<T>(list: T[], index: number, delta: number): void {
  const target = index + delta
  if (target < 0 || target >= list.length) return
  const [item] = list.splice(index, 1)
  list.splice(target, 0, item)
}

export function removeItem<T>(list: T[], index: number): void {
  list.splice(index, 1)
}
