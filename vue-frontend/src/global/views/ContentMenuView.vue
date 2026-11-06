<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useAuthStore } from '@/auth/store'
import { ApiError } from '@/global/api/ApiError'
import EmptyState from '@/global/components/EmptyState.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { sanitizeHtml } from '@/global/util/html'
import { fetchMenuContent } from '@/menu/api'
import type { MenuContent } from '@/menu/types'

/**
 * 본문형(CONTENT) 메뉴. 메뉴 본문(언어별 HTML)을 보여준다. (GET /menus/{id}/content)
 * 사용자 언어 본문이 없으면 기본 언어 본문과 안내 문구. 내 메뉴에 없는 메뉴는 404.
 */
const props = defineProps<{ menuId: number }>()
const auth = useAuthStore()
const feedback = useApiFeedback()

const content = ref<MenuContent | null>(null)
const notFound = ref(false)
const html = computed(() => content.value?.contentHtml ? sanitizeHtml(content.value.contentHtml) : '')

async function load(): Promise<void> {
  try {
    content.value = await fetchMenuContent(props.menuId)
    notFound.value = false
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound.value = true
      content.value = null
      return
    }
    feedback.failure(error)
  }
}

watch([() => props.menuId, () => auth.user?.langCd], load, { immediate: true })
</script>

<template>
  <div class="page">
    <EmptyState v-if="notFound" :title="$t('error.notFound.title')" :description="$t('error.notFound.content')" />
    <article v-else-if="content" class="article">
      <header class="page-head">
        <h1 data-test="content-title">{{ content.menuNm }}</h1>
      </header>
      <p v-if="content.fallback" class="notice-line warn">
        {{ $t('pending.contentFallback', { lang: content.langCd.toUpperCase() }) }}</p>
      <div v-if="html" class="notice-content body" data-test="content-body" v-html="html" />
      <EmptyState v-else :title="$t('pending.contentEmpty')" />
    </article>
  </div>
</template>

<style scoped>
.article {
  max-width: 820px;
}

.body {
  padding-top: var(--space-2);
}

.notice-line {
  margin-bottom: var(--space-4);
}
</style>
