<script setup lang="ts">
import { computed } from 'vue'
import { useCodeStore } from '@/code/store'
import type { LangNames } from '@/code/types'

/**
 * 언어별 이름 입력. 지원 언어(LANG)마다 입력칸 하나 - 언어 목록은 하드코딩하지 않는다.
 * 기본 언어는 필수, 나머지 언어는 비우면 기본 언어 이름으로 표시된다.
 */
const props = defineProps<{
  modelValue: LangNames
  label: string
  idPrefix: string
  maxlength?: number
  error?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [value: LangNames] }>()
const code = useCodeStore()

const langs = computed(() => code.langs)

function update(langCd: string, value: string): void {
  emit('update:modelValue', { ...props.modelValue, [langCd]: value })
}
</script>

<template>
  <fieldset class="names">
    <legend class="field-label">{{ label }}</legend>
    <div v-for="lang in langs" :key="lang.langCd" class="name-row">
      <label :for="`${idPrefix}-${lang.langCd}`" class="lang">
        <span class="mono">{{ lang.badge }}</span>
        <span class="lang-nm">{{ lang.langNm }}</span>
        <span v-if="lang.defaultLang" class="req" :title="$t('admin.requiredDefault')">*</span>
      </label>
      <input :id="`${idPrefix}-${lang.langCd}`" class="input" :value="modelValue[lang.langCd] ?? ''"
        :maxlength="maxlength ?? 200" :lang="lang.langCd" :required="lang.defaultLang"
        :aria-invalid="lang.defaultLang && !!error"
        :placeholder="lang.defaultLang ? '' : $t('admin.fallbackPlaceholder')"
        :data-test="`${idPrefix}-${lang.langCd}`"
        @input="update(lang.langCd, ($event.target as HTMLInputElement).value)">
    </div>
    <p v-if="error" class="field-error">{{ error }}</p>
  </fieldset>
</template>

<style scoped>
.names {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  border: 0;
}

.name-row {
  display: grid;
  grid-template-columns: 110px 1fr;
  align-items: center;
  gap: var(--space-2);
}

.lang {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-sm);
  color: var(--ink-2);
}

.lang .mono {
  font-size: 10.5px;
  padding: 1px 4px;
  border: 1px solid var(--rule-strong);
  border-radius: 3px;
  color: var(--ink-3);
}

.lang-nm {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.req {
  color: var(--danger);
}
</style>
