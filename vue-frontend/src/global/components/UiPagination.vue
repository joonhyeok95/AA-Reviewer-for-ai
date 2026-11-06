<script setup lang="ts">
import { computed } from 'vue'

/** 페이지 이동. page 는 서버와 같이 0부터. */
const props = defineProps<{ page: number; totalPages: number }>()
const emit = defineEmits<{ change: [page: number] }>()

const pages = computed(() => {
  const size = 5
  let start = Math.max(0, props.page - Math.floor(size / 2))
  const end = Math.min(props.totalPages, start + size)
  start = Math.max(0, end - size)
  return Array.from({ length: end - start }, (_, index) => start + index)
})
</script>

<template>
  <nav v-if="totalPages > 1" class="pager" :aria-label="$t('common.pagination')">
    <button type="button" class="pager-btn" :disabled="page === 0"
      :aria-label="$t('common.prevPage')" @click="emit('change', page - 1)">‹</button>
    <button v-for="p in pages" :key="p" type="button" class="pager-btn num"
      :aria-current="p === page ? 'page' : undefined" @click="emit('change', p)">{{ p + 1 }}</button>
    <button type="button" class="pager-btn" :disabled="page >= totalPages - 1"
      :aria-label="$t('common.nextPage')" @click="emit('change', page + 1)">›</button>
  </nav>
</template>

<style scoped>
.pager {
  display: flex;
  gap: 2px;
}

.pager-btn {
  min-width: 30px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--ink-2);
  font-size: var(--text-sm);
  cursor: pointer;
}

.pager-btn:hover:not(:disabled) {
  background: var(--surface-sunken);
}

.pager-btn:disabled {
  color: var(--ink-4);
  cursor: default;
}

.pager-btn[aria-current='page'] {
  border-color: var(--rule-strong);
  background: var(--surface);
  color: var(--ink);
  font-weight: 650;
}
</style>
