<script setup lang="ts">
/**
 * 제공 언어 배지 (KO EN). 현재 표시 중인 언어는 채워서 표시한다.
 * selectable 이면 버튼으로 동작해 언어 전환에 쓴다.
 */
defineProps<{
  langs: { langCd: string; badge: string }[]
  active?: string
  selectable?: boolean
}>()

const emit = defineEmits<{ select: [langCd: string] }>()
</script>

<template>
  <span class="badges">
    <template v-for="lang in langs" :key="lang.langCd">
      <button v-if="selectable" type="button" class="badge"
        :class="{ on: lang.langCd === active }" :aria-pressed="lang.langCd === active"
        :data-test="`lang-${lang.langCd}`" @click="emit('select', lang.langCd)">{{ lang.badge }}</button>
      <span v-else class="badge" :class="{ on: lang.langCd === active }"
        :title="lang.langCd">{{ lang.badge }}</span>
    </template>
  </span>
</template>

<style scoped>
.badges {
  display: inline-flex;
  gap: 3px;
  vertical-align: middle;
}

.badge {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 5px;
  border: 1px solid var(--rule-strong);
  border-radius: 3px;
  background: transparent;
  color: var(--ink-3);
  font-family: var(--font-mono);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

button.badge {
  height: 26px;
  padding: 0 9px;
  font-size: 11.5px;
  cursor: pointer;
}

button.badge:hover {
  border-color: var(--ink-3);
  color: var(--ink);
}

.badge.on {
  border-color: var(--ink-2);
  background: var(--ink-2);
  color: var(--surface);
}
</style>
