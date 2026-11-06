<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ApiError } from '@/global/api/ApiError'
import UiDialog from '@/global/components/UiDialog.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { createSample, updateSample } from '@/sample/api'
import type { Sample, SampleSaveRequest } from '@/sample/types'

/** 샘플 등록/수정. 검증 오류(400)는 서버 FieldErrorDetail 을 필드 아래에 표시한다. */
const props = defineProps<{ open: boolean; sample: Sample | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const feedback = useApiFeedback()
const form = reactive<SampleSaveRequest>({ name: '', description: '', useYn: 'Y' })
const errors = ref<Record<string, string>>({})
const saving = ref(false)
const editing = computed(() => props.sample !== null)

watch(() => props.open, (open) => {
  if (!open) return
  errors.value = {}
  form.name = props.sample?.name ?? ''
  form.description = props.sample?.description ?? ''
  form.useYn = props.sample?.useYn ?? 'Y'
})

async function submit(): Promise<void> {
  errors.value = {}
  saving.value = true
  try {
    const body = { ...form, name: form.name.trim() }
    const response = props.sample ? await updateSample(props.sample.id, body)
      : await createSample(body)
    feedback.success(response)
    emit('saved')
  } catch (error) {
    if (error instanceof ApiError && error.fieldErrors.length) {
      errors.value = Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.reason]))
    }
    feedback.failure(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <UiDialog :open="open" :title="editing ? $t('sample.editTitle') : $t('sample.createTitle')"
    @close="emit('close')">
    <form id="sample-form" class="form" novalidate @submit.prevent="submit">
      <div class="field">
        <label class="field-label" for="sample-name">{{ $t('sample.name') }}<span class="req">*</span></label>
        <input id="sample-name" v-model="form.name" class="input" maxlength="100" required
          :aria-invalid="!!errors.name" data-test="sample-name">
        <p v-if="errors.name" class="field-error">{{ errors.name }}</p>
      </div>
      <div class="field">
        <label class="field-label" for="sample-desc">{{ $t('sample.description') }}</label>
        <textarea id="sample-desc" v-model="form.description" class="textarea" maxlength="500"
          :aria-invalid="!!errors.description" />
        <p class="field-hint num">{{ form.description.length }} / 500</p>
      </div>
      <fieldset class="field plain">
        <legend class="field-label">{{ $t('sample.useYn') }}</legend>
        <div class="radios">
          <label class="check"><input v-model="form.useYn" type="radio" value="Y">{{ $t('common.use') }}</label>
          <label class="check"><input v-model="form.useYn" type="radio" value="N">{{ $t('common.unused') }}</label>
        </div>
      </fieldset>
    </form>
    <template #footer>
      <button type="button" class="btn" @click="emit('close')">{{ $t('common.cancel') }}</button>
      <button type="submit" form="sample-form" class="btn btn-primary" :disabled="saving"
        data-test="sample-save">{{ $t('common.save') }}</button>
    </template>
  </UiDialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.plain {
  margin: 0;
  padding: 0;
  border: 0;
}

.radios {
  display: flex;
  gap: var(--space-5);
}
</style>
