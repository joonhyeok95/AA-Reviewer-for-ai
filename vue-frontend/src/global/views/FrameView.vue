<script setup lang="ts">
import { computed } from 'vue'
import EmptyState from '@/global/components/EmptyState.vue'
import { isSafeExternalUrl } from '@/menu/link'
import { useMenuStore } from '@/menu/store'

/** IFRAME 유형 메뉴 - 외부 화면을 본문 영역에 표시 (http/https 만) */
const props = defineProps<{ menuId: number }>()
const menu = useMenuStore()

const target = computed(() => menu.findById(props.menuId))
const src = computed(() => {
  const url = target.value?.menuUrl
  return target.value?.viewTypeCd === 'IFRAME' && url && isSafeExternalUrl(url) ? url : ''
})
</script>

<template>
  <div v-if="src" class="frame-page">
    <header class="frame-head">
      <strong>{{ target?.menuNm }}</strong>
      <a :href="src" target="_blank" rel="noopener noreferrer" class="mono">{{ src }} ↗</a>
    </header>
    <iframe :src="src" :title="target?.menuNm" sandbox="allow-scripts allow-same-origin allow-forms"
      referrerpolicy="no-referrer" />
  </div>
  <div v-else class="page">
    <EmptyState :title="$t('error.notFound.title')" :description="$t('error.notFound.content')" />
  </div>
</template>

<style scoped>
.frame-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--topbar-h));
}

.frame-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  padding: 10px var(--space-8);
  border-bottom: 1px solid var(--rule);
  font-size: var(--text-sm);
}

.frame-head a {
  font-size: var(--text-xs);
}

iframe {
  flex: 1;
  width: 100%;
  border: 0;
  background: var(--surface);
}
</style>
