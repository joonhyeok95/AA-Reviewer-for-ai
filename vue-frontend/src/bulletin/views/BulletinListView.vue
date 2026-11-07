<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import type { PageResponse } from '@/global/api/types'
import EmptyState from '@/global/components/EmptyState.vue'
import UiPagination from '@/global/components/UiPagination.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { formatDate, formatDateTime } from '@/global/util/format'
import { fetchBulletins } from '@/bulletin/api'
import type { BulletinSummary } from '@/bulletin/types'

/** 주보 목록 (예배일 역순). 편집 권한이 있으면 작성 중 주보도 보인다. */
const auth = useAuthStore()
const code = useCodeStore()
const feedback = useApiFeedback()

const result = ref<PageResponse<BulletinSummary> | null>(null)
const page = ref(0)
const loading = ref(false)
const canAdd = computed(() => auth.can('bulletin', 'add'))

async function load(): Promise<void> {
  loading.value = true
  try {
    await code.getCodes('BULLETIN_STATUS')
    result.value = await fetchBulletins({ page: page.value, size: 20 })
  } catch (error) {
    feedback.failure(error)
  } finally {
    loading.value = false
  }
}

function onPage(next: number): void {
  page.value = next
  void load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>{{ $t('bulletin.title') }}</h1>
        <p class="lede">{{ $t('bulletin.lede') }}</p>
      </div>
      <div class="page-actions">
        <RouterLink v-if="canAdd" class="btn btn-primary" :to="{ name: 'bulletin-new' }" data-test="bulletin-create">
          + {{ $t('bulletin.create') }}</RouterLink>
      </div>
    </header>

    <div class="table-wrap" :aria-busy="loading">
      <table class="table">
        <thead>
          <tr>
            <th class="col-date">{{ $t('bulletin.worshipDt') }}</th>
            <th class="col-shrink">{{ $t('bulletin.issueShort') }}</th>
            <th>{{ $t('bulletin.sermon') }}</th>
            <th class="col-shrink">{{ $t('bulletin.status') }}</th>
            <th class="col-date">{{ $t('common.updatedAt') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in result?.content ?? []" :key="item.bulletinId" data-test="bulletin-row">
            <td class="col-date">
              <RouterLink :to="{ name: 'bulletin-detail', params: { id: item.bulletinId } }">
                {{ formatDate(item.worshipDt) }}</RouterLink>
              <span v-if="item.title" class="tag tag-info special">{{ item.title }}</span>
            </td>
            <td class="col-shrink num">{{ $t('bulletin.issue', { volume: item.volumeNo ?? '-', issue: item.issueNo ?? '-' }) }}</td>
            <td>{{ item.sermonTitle }} <span class="muted">{{ item.sermonScripture }}</span>
              <span v-if="item.preacherNm" class="muted"> · {{ item.preacherNm }}</span></td>
            <td class="col-shrink">
              <span class="tag" :class="item.statusCd === 'PUBLISHED' ? 'tag-accent' : 'tag-warn'">
                {{ code.codeName('BULLETIN_STATUS', item.statusCd) }}</span></td>
            <td class="col-date">{{ formatDateTime(item.updatedAt) }} <span class="by">{{ item.updatedBy }}</span></td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="result && !result.content.length" :title="$t('bulletin.empty')" description="" />
    </div>
    <div v-if="result" class="table-foot">
      <span class="num">{{ $t('common.total', { n: result.totalElements }) }}</span>
      <UiPagination :page="result.page" :total-pages="result.totalPages" @change="onPage" />
    </div>
  </div>
</template>

<style scoped>
.special {
  margin-left: var(--space-2);
}

.by {
  display: block;
  color: var(--ink-3);
  font-size: var(--text-xs);
}
</style>
