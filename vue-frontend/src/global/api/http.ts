import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ApiError } from './ApiError'
import type { MessageResponse } from './types'

/**
 * API 클라이언트.
 *
 *  - baseURL `/cm` : Gateway 의 API 라우트 (/cm 제거 후 spring-boilerplate 로 전달)
 *  - 인증 : 브라우저는 SESSION 쿠키만 가진다. 토큰은 서버 세션(Redis)에만 있다.
 *  - CSRF : 서버가 내려준 XSRF-TOKEN 쿠키를 X-XSRF-TOKEN 헤더로 보낸다. (axios 기본 동작, 같은 Origin)
 *  - 언어 : Accept-Language = 현재 언어의 에디터 언어팩(LANG.ATTR1, 예: ko-KR) → 서버 메시지 언어
 *  - 오류 : 표준 응답을 ApiError 로 변환. 401 은 등록된 핸들러(로그인 화면 이동)에 알린다.
 */
export const API_BASE = '/cm'

declare module 'axios' {
  interface AxiosRequestConfig {
    /** true 면 401 이어도 로그인 화면 이동 핸들러를 호출하지 않는다. (로그인 여부 확인용) */
    silent401?: boolean
  }
}

export const http = axios.create({
  baseURL: API_BASE,
  timeout: 30_000,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: { 'X-Requested-With': 'XMLHttpRequest' },
  // Spring 의 List 바인딩과 맞춘다 (a=1&a=2)
  paramsSerializer: { indexes: null },
})

let acceptLanguage = ''
let unauthorizedHandler: (() => void) | null = null

/** 요청 언어. (LANG.ATTR1 값) */
export function setAcceptLanguage(value: string): void {
  acceptLanguage = value
}

/** 세션 만료(401) 시 호출할 핸들러. */
export function onUnauthorized(handler: () => void): void {
  unauthorizedHandler = handler
}

http.interceptors.request.use((config) => {
  if (acceptLanguage) {
    config.headers.set('Accept-Language', acceptLanguage)
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    if (!error.response) {
      return Promise.reject(ApiError.network())
    }
    let body: unknown = error.response.data
    if (body instanceof Blob) {
      body = await readBlobJson(body)
    }
    const apiError = ApiError.fromResponse(error.response.status, body)
    if (apiError.status === 401 && !error.config?.silent401 && unauthorizedHandler) {
      unauthorizedHandler()
    }
    return Promise.reject(apiError)
  },
)

async function readBlobJson(blob: Blob): Promise<unknown> {
  try {
    return JSON.parse(await blob.text())
  } catch {
    return null
  }
}

/** 표준 응답의 result 를 꺼낸다. */
export async function getResult<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.get<MessageResponse<T>>(url, config)
  return response.data.result
}

export async function postResult<T>(url: string, body?: unknown,
  config?: AxiosRequestConfig): Promise<MessageResponse<T>> {
  const response = await http.post<MessageResponse<T>>(url, body, config)
  return response.data
}

export async function putResult<T>(url: string, body?: unknown,
  config?: AxiosRequestConfig): Promise<MessageResponse<T>> {
  const response = await http.put<MessageResponse<T>>(url, body, config)
  return response.data
}

export async function deleteResult(url: string): Promise<MessageResponse<null>> {
  const response = await http.delete<MessageResponse<null>>(url)
  return response.data
}
