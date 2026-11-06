<script setup lang="ts">
import { useToastStore } from '@/global/stores/toast'

const toast = useToastStore()
</script>

<template>
  <div class="toast-host" role="status" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="item in toast.items" :key="item.id" class="toast" :class="`toast-${item.kind}`"
        :data-test="`toast-${item.kind}`">
        <div class="toast-body">
          <strong>{{ item.title }}</strong>
          <p v-if="item.content">{{ item.content }}</p>
        </div>
        <button type="button" class="toast-close" :aria-label="$t('common.close')"
          @click="toast.dismiss(item.id)">×</button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  right: var(--space-5);
  bottom: var(--space-5);
  z-index: 200;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: min(380px, calc(100vw - 32px));
}

.toast {
  display: flex;
  gap: var(--space-3);
  padding: 12px 12px 12px 16px;
  background: var(--ink);
  color: var(--accent-ink);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-overlay);
  border-left: 3px solid var(--ink-4);
}

.toast-success {
  border-left-color: #6fb397;
}

.toast-error {
  border-left-color: #e0705c;
}

.toast-body {
  flex: 1;
  font-size: var(--text-sm);
}

.toast-body p {
  margin-top: 2px;
  color: #c9c7bd;
  font-size: var(--text-xs);
}

.toast-close {
  align-self: flex-start;
  border: 0;
  background: none;
  color: #a9a79e;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.18s, transform 0.18s;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>
