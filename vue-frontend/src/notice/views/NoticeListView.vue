<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import type { Code } from '@/code/types'
import type { PageResponse } from '@/global/api/types'
import EmptyState from '@/global/components/EmptyState.vue'
import LangBadges from '@/global/components/LangBadges.vue'
import UiPagination from '@/global/components/UiPagination.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { formatDateTime } from '@/global/util/format'
import { fetchNotices } from '@/notice/api'
import type { NoticeSummary } from '@/notice/types'

/**
 * 공지 목록. 제목은 사용자 언어 버전, 없으면 대표 언어 버전(원문 표시)으로 나온다.
 * 검색도 표시 중인 버전 기준. 검색 조건은 URL 에 둬서 상세에서 돌아와도 유지된다.
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const code = useCodeStore()
const feedback = useApiFeedback()

const categories = ref<Code[]>([])
const form = reactive({ keyword: '', categoryCd: '', includeScheduled: false })
const result = ref<PageResponse<NoticeSummary> | null>(null)
const loading = ref(false)

const canAdd = computed(() => auth.can('board', 'add'))
const isManager = computed(() => auth.can('board', 'edit'))
const page = computed(() => Number(route.query.page ?? 0) || 0)

function syncForm(): void {
  form.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  form.categoryCd = typeof route.query.category === 'string' ? route.query.category : ''
  form.includeScheduled = route.query.scheduled === '1'
}

async function load(): Promise<void> {
  syncForm()
  loading.value = true
  try {
    result.value = await fetchNotices(
      { keyword: form.keyword, categoryCd: form.categoryCd,
        includeScheduled: isManager.value && form.includeScheduled },
      { page: page.value, size: 15 })
  } catch (error) {
    feedback.failure(error)
  } finally {
    loading.value = false
  }
}

function pushQuery(nextPage = 0): void {
  const query: Record<string, string> = {}
  if (form.keyword.trim()) query.keyword = form.keyword.trim()
  if (form.categoryCd) query.category = form.categoryCd
  if (form.includeScheduled) query.scheduled = '1'
  if (nextPage) query.page = String(nextPage)
  void router.push({ query })
}

function reset(): void {
  form.keyword = ''
  form.categoryCd = ''
  form.includeScheduled = false
  pushQuery()
}

async function loadCategories(): Promise<void> {
  categories.value = await code.getCodes('NOTICE_CATEGORY')
}

watch(() => route.query, load)
// 언어를 바꾸면 목록 제목/코드명이 그 언어 버전으로 바뀐다
watch(() => auth.user?.langCd, () => {
  void loadCategories()
  void load()
})

onMounted(async () => {
  try {
    await loadCategories()
  } catch (error) {
    feedback.failure(error)
  }
  await load()
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>{{ $t('notice.title') }}</h1>
        <p class="lede">{{ $t('notice.description') }}</p>
      </div>
      <div class="page-actions">
        <RouterLink v-if="canAdd" to="/support/notices/new" class="btn btn-primary"
          data-test="notice-create">{{ $t('notice.create') }}</RouterLink>
      </div>
    </header>

    <form class="filter-bar" role="search" @submit.prevent="pushQuery()">
      <div class="field">
        <label class="field-label" for="n-category">{{ $t('notice.category') }}</label>
        <select id="n-category" v-model="form.categoryCd" class="select">
          <option value="">{{ $t('common.all') }}</option>
          <option v-for="item in categories" :key="item.code" :value="item.code">{{ item.codeNm }}</option>
        </select>
      </div>
      <div class="field grow">
        <label class="field-label" for="n-keyword">{{ $t('notice.keyword') }}</label>
        <input id="n-keyword" v-model="form.keyword" class="input" type="search" maxlength="100"
          :placeholder="$t('notice.keywordPlaceholder')" data-test="notice-keyword">
      </div>
      <label v-if="isManager" class="check scheduled">
        <input v-model="form.includeScheduled" type="checkbox">{{ $t('notice.includeScheduled') }}
      </label>
      <button type="submit" class="btn btn-primary" data-test="notice-search">{{ $t('common.search') }}</button>
      <button type="button" class="btn btn-quiet" @click="reset">{{ $t('common.reset') }}</button>
    </form>

    <div class="table-wrap" :aria-busy="loading">
      <table class="table">
        <thead>
          <tr>
            <th class="col-num">{{ $t('common.seq') }}</th>
            <th class="col-shrink">{{ $t('notice.category') }}</th>
            <th>{{ $t('notice.titleCol') }}</th>
            <th class="col-shrink">{{ $t('notice.langs') }}</th>
            <th class="col-date">{{ $t('notice.postDate') }}</th>
            <th class="col-num">{{ $t('notice.views') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="notice in result?.content ?? []" :key="notice.noticeId" class="clickable"
            data-test="notice-row"
            @click="router.push(`/support/notices/${notice.noticeId}`)">
            <td class="col-num muted">{{ notice.noticeId }}</td>
            <td class="col-shrink">{{ notice.categoryNm }}</td>
            <td>
              <div class="title-cell">
                <span v-if="notice.scheduled" class="tag tag-warn">{{ $t('notice.scheduled') }}</span>
                <span v-if="notice.tagNm" class="tag tag-danger">{{ notice.tagNm }}</span>
                <span v-if="notice.popupYn === 'Y'" class="tag tag-info">{{ $t('notice.popupTag') }}</span>
                <RouterLink :to="`/support/notices/${notice.noticeId}`" class="title" @click.stop>
                  {{ notice.title }}</RouterLink>
                <span v-if="notice.fallback" class="fallback"
                  :title="$t('notice.fallbackHint')">{{ $t('notice.original', { lang: notice.displayLangCd.toUpperCase() }) }}</span>
                <span v-if="notice.fileCnt" class="files num" :aria-label="$t('notice.fileCount', { n: notice.fileCnt })">
                  ⎘ {{ notice.fileCnt }}</span>
              </div>
            </td>
            <td class="col-shrink"><LangBadges :langs="notice.langs" :active="notice.displayLangCd" /></td>
            <td class="col-date">{{ formatDateTime(notice.postDate) }}</td>
            <td class="col-num">{{ notice.viewCnt }}</td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="result && !result.content.length" :title="$t('notice.empty')"
        :description="form.keyword ? $t('notice.emptySearch') : undefined" />
    </div>
    <div v-if="result" class="table-foot">
      <span class="num">{{ $t('common.total', { n: result.totalElements }) }}</span>
      <UiPagination :page="result.page" :total-pages="result.totalPages" @change="pushQuery" />
    </div>
  </div>
</template>

<style scoped>
.scheduled {
  height: var(--control-h);
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 280px;
}

.title {
  color: var(--ink);
  font-weight: 550;
}

.fallback {
  font-size: 11px;
  color: var(--ink-3);
  border-bottom: 1px dotted var(--ink-4);
  cursor: help;
}

.files {
  font-size: var(--text-xs);
  color: var(--ink-3);
}

.table-wrap[aria-busy='true'] {
  opacity: 0.6;
}
</style>
