<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

/**
 * 모달 대화상자 (native <dialog> - 포커스 가두기, ESC 닫기는 브라우저 기본 동작).
 */
const props = withDefaults(defineProps<{
  open: boolean
  title: string
  width?: string
}>(), { width: '520px' })

const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement | null>(null)

watch(() => props.open, async (open) => {
  await nextTick()
  if (!dialog.value) return
  if (open && !dialog.value.open) {
    dialog.value.showModal()
  } else if (!open && dialog.value.open) {
    dialog.value.close()
  }
}, { immediate: true })

onBeforeUnmount(() => {
  if (dialog.value?.open) dialog.value.close()
})

function onCancel(event: Event): void {
  event.preventDefault()
  emit('close')
}

function onBackdrop(event: MouseEvent): void {
  if (event.target === dialog.value) {
    emit('close')
  }
}
</script>

<template>
  <dialog ref="dialog" class="dialog" :style="{ width }" autofocus @cancel="onCancel"
    @mousedown="onBackdrop">
    <div v-if="open" class="dialog-inner">
      <header class="dialog-head">
        <h2>{{ title }}</h2>
        <button type="button" class="btn btn-quiet btn-sm" :aria-label="$t('common.close')"
          @click="emit('close')">✕</button>
      </header>
      <div class="dialog-body">
        <slot />
      </div>
      <footer v-if="$slots.footer" class="dialog-foot">
        <slot name="footer" />
      </footer>
    </div>
  </dialog>
</template>

<style scoped>
.dialog {
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 64px);
  padding: 0;
  border: 1px solid var(--rule);
  border-radius: var(--radius-lg);
  background: var(--surface);
  color: var(--ink);
  box-shadow: var(--shadow-overlay);
}

.dialog:focus-visible {
  box-shadow: var(--shadow-overlay);
}

.dialog::backdrop {
  background: rgb(27 29 26 / 0.38);
}

.dialog-inner {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 66px);
}

.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 14px 22px;
  border-bottom: 1px solid var(--rule);
}

.dialog-head h2 {
  font-size: var(--text-lg);
}

.dialog-body {
  padding: var(--space-5) 22px;
  overflow-y: auto;
}

.dialog-foot {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  padding: 12px 22px;
  border-top: 1px solid var(--rule);
  background: var(--paper);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}
</style>
