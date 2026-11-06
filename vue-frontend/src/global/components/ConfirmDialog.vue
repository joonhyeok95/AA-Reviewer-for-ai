<script setup lang="ts">
import UiDialog from './UiDialog.vue'

defineProps<{
  open: boolean
  title: string
  message: string
  confirmLabel: string
  danger?: boolean
  busy?: boolean
}>()

const emit = defineEmits<{ confirm: []; close: [] }>()
</script>

<template>
  <UiDialog :open="open" :title="title" width="420px" @close="emit('close')">
    <p class="message">{{ message }}</p>
    <template #footer>
      <button type="button" class="btn" @click="emit('close')">{{ $t('common.cancel') }}</button>
      <button type="button" class="btn" :class="danger ? 'btn-danger-solid' : 'btn-primary'"
        :disabled="busy" data-test="confirm" @click="emit('confirm')">{{ confirmLabel }}</button>
    </template>
  </UiDialog>
</template>

<style scoped>
.message {
  color: var(--ink-2);
  white-space: pre-line;
}
</style>
