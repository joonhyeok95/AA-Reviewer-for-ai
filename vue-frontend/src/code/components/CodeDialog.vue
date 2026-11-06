<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { createCode, updateCode } from '@/code/api'
import type { CodeAdmin, CodeSaveRequest } from '@/code/types'
import { ApiError } from '@/global/api/ApiError'
import LangNamesInput from '@/global/components/LangNamesInput.vue'
import UiDialog from '@/global/components/UiDialog.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'

/** 코드 등록/수정 (코드값은 등록 시에만). 확장속성 의미는 그룹마다 다르다 (예: LANG.ATTR1 = 에디터 언어팩) */
const props = defineProps<{
  open: boolean
  groupCd: string
  item: CodeAdmin | null
  nextSort: number
}>()
const emit = defineEmits<{ close: []; saved: [] }>()

const feedback = useApiFeedback()
const form = reactive<CodeSaveRequest>({
  code: '', names: {}, codeDesc: '', sortOrder: 0, attr1: '', attr2: '', attr3: '', useYn: 'Y',
})
const errors = ref<Record<string, string>>({})
const saving = ref(false)
const editing = computed(() => props.item !== null)

watch(() => props.open, (open) => {
  if (!open) return
  errors.value = {}
  const item = props.item
  form.code = item?.code ?? ''
  form.names = { ...(item?.names ?? {}) }
  form.codeDesc = item?.codeDesc ?? ''
  form.sortOrder = item?.sortOrder ?? props.nextSort
  form.attr1 = item?.attr1 ?? ''
  form.attr2 = item?.attr2 ?? ''
  form.attr3 = item?.attr3 ?? ''
  form.useYn = item?.useYn ?? 'Y'
})

async function submit(): Promise<void> {
  errors.value = {}
  saving.value = true
  try {
    const body = { ...form, code: form.code.trim() }
    const response = props.item ? await updateCode(props.groupCd, props.item.code, body)
      : await createCode(props.groupCd, body)
    feedback.success(response)
    emit('saved')
  } catch (error) {
    if (error instanceof ApiError) {
      errors.value = Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.reason]))
      if (error.messageId === 'B4002' || error.messageId === 'B4001') errors.value.names = error.content
    }
    feedback.failure(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <UiDialog :open="open" :title="editing ? $t('codeAdmin.editCode') : $t('codeAdmin.addCode')"
    width="600px" @close="emit('close')">
    <form id="code-form" class="form-stack" novalidate @submit.prevent="submit">
      <div class="field">
        <label class="field-label" for="c-cd">{{ $t('code.code') }}<span class="req">*</span>
          <span class="mono muted">{{ groupCd }}</span></label>
        <input id="c-cd" v-model="form.code" class="input mono" maxlength="50" :disabled="editing"
          :aria-invalid="!!errors.code" autocomplete="off" spellcheck="false" data-test="code-cd">
        <p class="field-hint">{{ editing ? $t('admin.keyLocked') : $t('codeAdmin.codeHint') }}</p>
        <p v-if="errors.code" class="field-error">{{ errors.code }}</p>
      </div>
      <LangNamesInput v-model="form.names" :label="$t('code.codeNm')" id-prefix="c-nm"
        :error="errors.names" />
      <div class="grid-3">
        <div class="field">
          <label class="field-label" for="c-a1">{{ $t('code.attr1') }}</label>
          <input id="c-a1" v-model="form.attr1" class="input mono" maxlength="200">
        </div>
        <div class="field">
          <label class="field-label" for="c-a2">{{ $t('codeAdmin.attr2') }}</label>
          <input id="c-a2" v-model="form.attr2" class="input mono" maxlength="200">
        </div>
        <div class="field">
          <label class="field-label" for="c-a3">{{ $t('codeAdmin.attr3') }}</label>
          <input id="c-a3" v-model="form.attr3" class="input mono" maxlength="200">
        </div>
      </div>
      <div class="field">
        <label class="field-label" for="c-desc">{{ $t('admin.desc') }}</label>
        <textarea id="c-desc" v-model="form.codeDesc" class="textarea" maxlength="1000" rows="2" />
      </div>
      <div class="grid-2">
        <div class="field">
          <label class="field-label" for="c-sort">{{ $t('admin.sortOrder') }}</label>
          <input id="c-sort" v-model.number="form.sortOrder" type="number" min="0" max="99999"
            class="input num">
        </div>
        <fieldset class="field plain">
          <legend class="field-label">{{ $t('admin.useYn') }}</legend>
          <div class="checks">
            <label class="check"><input v-model="form.useYn" type="radio" value="Y"
              data-test="code-use-y">{{ $t('common.use') }}</label>
            <label class="check"><input v-model="form.useYn" type="radio" value="N"
              data-test="code-use-n">{{ $t('common.unused') }}</label>
          </div>
        </fieldset>
      </div>
    </form>
    <template #footer>
      <button type="button" class="btn" @click="emit('close')">{{ $t('common.cancel') }}</button>
      <button type="submit" form="code-form" class="btn btn-primary" :disabled="saving"
        data-test="code-save">{{ $t('common.save') }}</button>
    </template>
  </UiDialog>
</template>

<style scoped>
.plain {
  margin: 0;
  padding: 0;
  border: 0;
}

.plain .checks {
  height: var(--control-h);
  align-items: center;
}

.field-label .mono {
  margin-left: 6px;
  font-weight: 400;
}
</style>
