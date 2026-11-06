import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchCodes, fetchLangs } from './api'
import type { Code, Lang } from './types'

/**
 * 지원 언어 / 공통코드 캐시.
 *
 *  - 언어 목록은 하드코딩하지 않는다. 공통코드 LANG (GET /api/v1/langs) 이 기준
 *  - 공통코드는 응답 언어(사용자 언어)로 이름이 오므로 언어가 바뀌면 clearCodes() 로 비운다
 */
export const useCodeStore = defineStore('code', () => {
  const langs = ref<Lang[]>([])
  const codes = ref<Record<string, Code[]>>({})
  const pending = new Map<string, Promise<Code[]>>()

  const defaultLang = computed(() => langs.value.find((lang) => lang.defaultLang)
    ?? langs.value[0])

  async function loadLangs(): Promise<Lang[]> {
    langs.value = await fetchLangs()
    return langs.value
  }

  function langOf(langCd: string | null | undefined): Lang | undefined {
    return langs.value.find((lang) => lang.langCd === langCd)
  }

  async function getCodes(groupCd: string): Promise<Code[]> {
    const cached = codes.value[groupCd]
    if (cached) {
      return cached
    }
    let request = pending.get(groupCd)
    if (!request) {
      request = fetchCodes(groupCd).finally(() => pending.delete(groupCd))
      pending.set(groupCd, request)
    }
    const result = await request
    codes.value = { ...codes.value, [groupCd]: result }
    return result
  }

  function codeName(groupCd: string, code: string | null | undefined): string {
    if (!code) {
      return ''
    }
    return codes.value[groupCd]?.find((item) => item.code === code)?.codeNm ?? code
  }

  function clearCodes(): void {
    codes.value = {}
  }

  return { langs, codes, defaultLang, loadLangs, langOf, getCodes, codeName, clearCodes }
})
