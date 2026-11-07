<script setup lang="ts">
/**
 * 반복 입력 행 도구 : 위로 / 아래로 / 삭제. (예배순서·교회소식·문답·답 단락 등 n개 입력)
 * 목록 조작은 부모가 한다 (moveItem / removeItem - global/util/list.ts).
 */
defineProps<{ index: number; length: number; label?: string }>()
const emit = defineEmits<{ up: []; down: []; remove: [] }>()
</script>

<template>
  <div class="row-tools">
    <button type="button" class="tool" :disabled="index === 0"
      :aria-label="`${label ?? ''} ${$t('common.moveUp')}`" :title="$t('common.moveUp')"
      @click="emit('up')">&uarr;</button>
    <button type="button" class="tool" :disabled="index >= length - 1"
      :aria-label="`${label ?? ''} ${$t('common.moveDown')}`" :title="$t('common.moveDown')"
      @click="emit('down')">&darr;</button>
    <button type="button" class="tool danger" :aria-label="`${label ?? ''} ${$t('common.delete')}`"
      :title="$t('common.delete')" data-test="row-remove" @click="emit('remove')">&times;</button>
  </div>
</template>

<style scoped>
.row-tools {
  display: inline-flex;
  flex: none;
  gap: 2px;
}

.tool {
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--ink-3);
  font: inherit;
  font-size: var(--text-sm);
  line-height: 1;
  cursor: pointer;
}

.tool:hover:not(:disabled) {
  background: var(--surface-hover);
  color: var(--ink);
}

.tool:disabled {
  opacity: 0.4;
  cursor: default;
}

.tool.danger:hover {
  border-color: var(--danger);
  color: var(--danger);
}

.tool:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}
</style>
