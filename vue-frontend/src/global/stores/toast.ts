import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastKind = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  kind: ToastKind
  title: string
  content?: string
}

/** 화면 우측 하단 알림. 서버 표준 응답의 messageTitle / messageContent 를 그대로 보여준다. */
export const useToastStore = defineStore('toast', () => {
  const items = ref<Toast[]>([])
  let seq = 0

  function push(kind: ToastKind, title: string, content?: string, timeout = 4500): void {
    const id = ++seq
    items.value.push({ id, kind, title, content })
    if (items.value.length > 4) {
      items.value.shift()
    }
    window.setTimeout(() => dismiss(id), kind === 'error' ? timeout + 2500 : timeout)
  }

  function dismiss(id: number): void {
    items.value = items.value.filter((item) => item.id !== id)
  }

  return { items, push, dismiss }
})
