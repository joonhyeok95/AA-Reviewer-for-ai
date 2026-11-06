<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/auth/store'
import UiDialog from '@/global/components/UiDialog.vue'
import { formatDate, today } from '@/global/util/format'
import { sanitizeHtml } from '@/global/util/html'
import { fetchNotice, fetchPopups } from '@/notice/api'
import type { NoticeDetail } from '@/notice/types'

/**
 * 팝업 공지 (GET /notices/popups). 로그인 후 한 번 표시한다.
 * "오늘 하루 보지 않기" 는 브라우저(localStorage)에 날짜로 기록한다.
 */
const HIDE_KEY = 'flowlog.notice-popup.hidden'
const SHOWN_KEY = 'flowlog.notice-popup.shown'

const auth = useAuthStore()
const queue = ref<NoticeDetail[]>([])
const current = computed(() => queue.value[0])

function hiddenToday(): Record<string, string> {
  try {
    const stored = JSON.parse(localStorage.getItem(HIDE_KEY) ?? '{}') as Record<string, string>
    return Object.fromEntries(Object.entries(stored).filter(([, date]) => date === today()))
  } catch {
    return {}
  }
}

function next(): void {
  queue.value = queue.value.slice(1)
}

function hideToday(): void {
  if (!current.value) return
  const hidden = hiddenToday()
  hidden[String(current.value.noticeId)] = today()
  localStorage.setItem(HIDE_KEY, JSON.stringify(hidden))
  next()
}

onMounted(async () => {
  if (!auth.can('notice', 'list') || sessionStorage.getItem(SHOWN_KEY) === auth.user?.userId) {
    return
  }
  sessionStorage.setItem(SHOWN_KEY, auth.user?.userId ?? '')
  try {
    const hidden = hiddenToday()
    const popups = (await fetchPopups()).filter((p) => !hidden[String(p.noticeId)]).slice(0, 3)
    queue.value = await Promise.all(popups.map((p) => fetchNotice(p.noticeId)))
  } catch {
    // 팝업은 부가 기능 - 실패해도 화면 사용에 영향 없음
  }
})
</script>

<template>
  <UiDialog v-if="current" :open="true" :title="current.title" width="560px" @close="next">
    <p class="meta num">
      <span>{{ current.categoryNm }}</span>
      <span>{{ formatDate(current.postDate) }}</span>
    </p>
    <div class="content notice-content" v-html="sanitizeHtml(current.contentHtml)" />
    <template #footer>
      <button type="button" class="btn btn-quiet hide" data-test="popup-hide-today"
        @click="hideToday">{{ $t('notice.popup.hideToday') }}</button>
      <RouterLink :to="`/support/notices/${current.noticeId}`" class="btn" @click="next">
        {{ $t('notice.popup.detail') }}</RouterLink>
      <button type="button" class="btn btn-primary" data-test="popup-close" @click="next">
        {{ queue.length > 1 ? $t('notice.popup.next', { n: queue.length - 1 }) : $t('common.close') }}</button>
    </template>
  </UiDialog>
</template>

<style scoped>
.meta {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  font-size: var(--text-xs);
  color: var(--ink-3);
}

.content {
  max-height: 50vh;
  overflow-y: auto;
}

.hide {
  margin-right: auto;
}
</style>
