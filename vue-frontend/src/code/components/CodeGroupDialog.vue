<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { createCodeGroup, updateCodeGroup } from '@/code/api'
import type { CodeGroupAdmin, CodeGroupSaveRequest } from '@/code/types'
import { ApiError } from '@/global/api/ApiError'
import LangNamesInput from '@/global/components/LangNamesInput.vue'
import UiDialog from '@/global/components/UiDialog.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'

/** 코드 그룹 등록/수정 (그룹코드는 등록 시에만) */
const props = defineProps<{ open: boolean; group: CodeGroupAdmin | null; nextSort: number }>()
const emit = defineEmits<{ close: []; saved: [groupCd: string] }>()

const feedback = useApiFeedback()
const form = reactive<CodeGroupSaveRequest>({
  groupCd: '', names: {}, groupDesc: '', sortOrder: 0, useYn: 'Y',
})
const errors = ref<Record<string, string>>({})
const saving = ref(false)
const editing = computed(() => props.group !== null)

watch(() => props.open, (open) => {
  if (!open) return
  errors.value = {}
  form.groupCd = props.group?.groupCd ?? ''
  form.names = { ...(props.group?.names ?? {}) }
  form.groupDesc = props.group?.groupDesc ?? ''
  form.sortOrder = props.group?.sortOrder ?? props.nextSort
  form.useYn = props.group?.useYn ?? 'Y'
})

async function submit(): Promise<void> {
  errors.value = {}
  saving.value = true
  try {
    const body = { ...form, groupCd: form.groupCd.trim().toUpperCase() }
    const response = props.group ? await updateCodeGroup(props.group.groupCd, body)
      : await createCodeGroup(body)
    feedback.success(response)
    emit('saved', response.result.groupCd)
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
  <UiDialog :open="open" :title="editing ? $t('codeAdmin.editGroup') : $t('codeAdmin.addGroup')"
    width="560px" @close="emit('close')">
    <form id="code-group-form" class="form-stack" novalidate @submit.prevent="submit">
      <div class="field">
        <label class="field-label" for="g-cd">{{ $t('codeAdmin.groupCd') }}<span class="req">*</span></label>
        <input id="g-cd" v-model="form.groupCd" class="input mono" maxlength="50" :disabled="editing"
          :aria-invalid="!!errors.groupCd" autocomplete="off" spellcheck="false" data-test="group-cd">
        <p class="field-hint">{{ editing ? $t('admin.keyLocked') : $t('codeAdmin.groupCdHint') }}</p>
        <p v-if="errors.groupCd" class="field-error">{{ errors.groupCd }}</p>
      </div>
      <LangNamesInput v-model="form.names" :label="$t('codeAdmin.groupNm')" id-prefix="g-nm"
        :error="errors.names" />
      <div class="field">
        <label class="field-label" for="g-desc">{{ $t('admin.desc') }}</label>
        <textarea id="g-desc" v-model="form.groupDesc" class="textarea" maxlength="1000" rows="2" />
      </div>
      <div class="grid-2">
        <div class="field">
          <label class="field-label" for="g-sort">{{ $t('admin.sortOrder') }}</label>
          <input id="g-sort" v-model.number="form.sortOrder" type="number" min="0" max="99999"
            class="input num">
        </div>
        <fieldset class="field plain">
          <legend class="field-label">{{ $t('admin.useYn') }}</legend>
          <div class="checks">
            <label class="check"><input v-model="form.useYn" type="radio" value="Y">{{ $t('common.use') }}</label>
            <label class="check"><input v-model="form.useYn" type="radio" value="N">{{ $t('common.unused') }}</label>
          </div>
        </fieldset>
      </div>
    </form>
    <template #footer>
      <button type="button" class="btn" @click="emit('close')">{{ $t('common.cancel') }}</button>
      <button type="submit" form="code-group-form" class="btn btn-primary" :disabled="saving"
        data-test="group-save">{{ $t('common.save') }}</button>
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
</style>
