import { useI18n } from 'vue-i18n'
import { ApiError } from '@/global/api/ApiError'
import type { MessageResponse } from '@/global/api/types'
import { useToastStore } from '@/global/stores/toast'

/**
 * API 결과 알림. 서버가 다국어 처리한 메시지(messageTitle / messageContent)를 우선 사용하고,
 * 네트워크 오류처럼 서버 메시지가 없을 때만 화면 메시지를 쓴다.
 */
export function useApiFeedback() {
  const toast = useToastStore()
  const { t } = useI18n()

  function success(response: MessageResponse<unknown>): void {
    toast.push('success', response.messageTitle, response.messageContent)
  }

  function failure(error: unknown): void {
    if (error instanceof ApiError) {
      if (error.status === 401) {
        return // 로그인 화면으로 이동 중
      }
      if (error.status === 0) {
        toast.push('error', t('error.network.title'), t('error.network.content'))
        return
      }
      toast.push('error', error.title || t('error.unknown.title'),
        error.content || t('error.unknown.content', { status: error.status }))
      return
    }
    toast.push('error', t('error.unknown.title'), String(error))
  }

  return { success, failure }
}
