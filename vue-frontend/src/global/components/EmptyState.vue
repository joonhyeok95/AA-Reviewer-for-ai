<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
</script>

<template>
  <div class="empty">
    <p class="empty-title">{{ title }}</p>
    <p v-if="description" class="empty-desc">{{ description }}</p>
    <div v-if="$slots.default" class="empty-actions"><slot /></div>
  </div>
</template>

<style scoped>
.empty {
  padding: var(--space-10) var(--space-6);
  text-align: center;
}

.empty-title {
  font-weight: 600;
  color: var(--ink-2);
}

.empty-desc {
  margin-top: var(--space-1);
  font-size: var(--text-sm);
  color: var(--ink-3);
}

.empty-actions {
  margin-top: var(--space-4);
}
</style>
