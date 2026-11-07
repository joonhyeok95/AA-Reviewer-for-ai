<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/global/api/ApiError'
import ConfirmDialog from '@/global/components/ConfirmDialog.vue'
import UiDialog from '@/global/components/UiDialog.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { formatDateTime } from '@/global/util/format'
import type { Code } from '@/code/types'
import { createEvent, deleteEvent, fetchEvent, updateEvent } from '@/calendar/api'
import type { CalendarEvent, CalendarEventSaveRequest } from '@/calendar/types'

/**
 * 일정 등록 / 수정 / 보기.
 *  - eventId 가 있으면 원본을 불러온다 (매년 반복 일정은 최초 일자 기준)
 *  - 수정 권한이 없으면 읽기 전용
 *  - 검증 오류(400)는 서버 FieldErrorDetail 을 필드 아래에 표시
 */
const props = defineProps<{
  open: boolean
  eventId: number | null
  /** 새 일정 기본 날짜 (YYYY-MM-DD) */
  initialDate: string
  types: Code[]
  canEdit: boolean
  canDelete: boolean
}>()
const emit = defineEmits<{ close: []; saved: [] }>()

const { t } = useI18n()
const feedback = useApiFeedback()
const form = reactive<CalendarEventSaveRequest>({
  eventTypeCd: '', title: '', startDt: '', endDt: null, repeatYearlyYn: 'N', yearKnownYn: 'Y', memo: '',
})
const original = ref<CalendarEvent | null>(null)
const errors = ref<Record<string, string>>({})
const saving = ref(false)
const loading = ref(false)
const confirmDelete = ref(false)
const deleting = ref(false)

const editing = computed(() => props.eventId !== null)
const readonly = computed(() => editing.value && !props.canEdit)
const dialogTitle = computed(() => {
  if (!editing.value) return t('calendar.createTitle')
  return readonly.value ? t('calendar.viewTitle') : t('calendar.editTitle')
})

watch(() => props.open, async (open) => {
  if (!open) return
  errors.value = {}
  original.value = null
  confirmDelete.value = false
  Object.assign(form, {
    eventTypeCd: props.types[0]?.code ?? '', title: '', startDt: props.initialDate, endDt: null,
    repeatYearlyYn: 'N', yearKnownYn: 'Y', memo: '',
  })
  if (props.eventId === null) return
  loading.value = true
  try {
    const event = await fetchEvent(props.eventId)
    original.value = event
    Object.assign(form, {
      eventTypeCd: event.eventTypeCd, title: event.title, startDt: event.startDt,
      endDt: event.endDt === event.startDt ? null : event.endDt,
      repeatYearlyYn: event.repeatYearlyYn, yearKnownYn: event.yearKnownYn, memo: event.memo ?? '',
    })
  } catch (error) {
    feedback.failure(error)
    emit('close')
  } finally {
    loading.value = false
  }
})

async function submit(): Promise<void> {
  if (readonly.value) return
  errors.value = {}
  saving.value = true
  try {
    const body: CalendarEventSaveRequest = {
      ...form, title: form.title.trim(), endDt: form.endDt || null,
    }
    const response = props.eventId === null ? await createEvent(body)
      : await updateEvent(props.eventId, body)
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

async function remove(): Promise<void> {
  if (props.eventId === null) return
  deleting.value = true
  try {
    feedback.success(await deleteEvent(props.eventId))
    confirmDelete.value = false
    emit('saved')
  } catch (error) {
    feedback.failure(error)
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <UiDialog :open="open" :title="dialogTitle" @close="emit('close')">
    <form id="calendar-event-form" class="form" novalidate :aria-busy="loading"
      @submit.prevent="submit">
      <p v-if="editing && form.repeatYearlyYn === 'Y'" class="field-hint">
        {{ $t('calendar.repeatOriginHint') }}</p>
      <div class="grid-2">
        <div class="field">
          <label class="field-label" for="cal-type">{{ $t('calendar.type') }}<span class="req">*</span></label>
          <select id="cal-type" v-model="form.eventTypeCd" class="select" :disabled="readonly"
            :aria-invalid="!!errors.eventTypeCd" data-test="calendar-type">
            <option v-for="type in types" :key="type.code" :value="type.code">{{ type.codeNm }}</option>
          </select>
          <p v-if="errors.eventTypeCd" class="field-error">{{ errors.eventTypeCd }}</p>
        </div>
        <div class="field">
          <span class="field-label">{{ $t('calendar.repeat') }}</span>
          <label class="check repeat">
            <input v-model="form.repeatYearlyYn" type="checkbox" true-value="Y" false-value="N"
              :disabled="readonly" data-test="calendar-repeat">{{ $t('calendar.repeatYearly') }}
          </label>
          <label v-if="form.repeatYearlyYn === 'Y'" class="check">
            <input v-model="form.yearKnownYn" type="checkbox" true-value="N" false-value="Y"
              :disabled="readonly" data-test="calendar-year-unknown">{{ $t('calendar.yearUnknown') }}
          </label>
        </div>
      </div>
      <div class="field">
        <label class="field-label" for="cal-title">{{ $t('calendar.titleField') }}<span class="req">*</span></label>
        <input id="cal-title" v-model="form.title" class="input" maxlength="200" required
          :readonly="readonly" :aria-invalid="!!errors.title" data-test="calendar-title">
        <p v-if="errors.title" class="field-error">{{ errors.title }}</p>
      </div>
      <div class="grid-2">
        <div class="field">
          <label class="field-label" for="cal-start">{{ $t('calendar.startDt') }}<span class="req">*</span></label>
          <input id="cal-start" v-model="form.startDt" class="input" type="date" required
            :readonly="readonly" :aria-invalid="!!errors.startDt" data-test="calendar-start">
          <p v-if="errors.startDt" class="field-error">{{ errors.startDt }}</p>
        </div>
        <div class="field">
          <label class="field-label" for="cal-end">{{ $t('calendar.endDt') }}</label>
          <input id="cal-end" v-model="form.endDt" class="input" type="date" :min="form.startDt"
            :readonly="readonly" :aria-invalid="!!errors.endDt" data-test="calendar-end">
          <p v-if="errors.endDt" class="field-error">{{ errors.endDt }}</p>
          <p v-else class="field-hint">{{ $t('calendar.endHint') }}</p>
        </div>
      </div>
      <p v-if="!readonly" class="field-hint">{{ $t('calendar.repeatHint') }}</p>
      <div class="field">
        <label class="field-label" for="cal-memo">{{ $t('calendar.memo') }}</label>
        <textarea id="cal-memo" v-model="form.memo" class="textarea" maxlength="1000"
          :readonly="readonly" data-test="calendar-memo" />
      </div>
      <p v-if="original" class="field-hint num">
        {{ $t('common.updatedAt') }} {{ formatDateTime(original.updatedAt) }} · {{ original.updatedBy }}</p>
    </form>
    <template #footer>
      <button v-if="editing && canDelete" type="button" class="btn btn-danger push-left"
        data-test="calendar-delete" @click="confirmDelete = true">{{ $t('common.delete') }}</button>
      <button type="button" class="btn" @click="emit('close')">
        {{ readonly ? $t('common.close') : $t('common.cancel') }}</button>
      <button v-if="!readonly" type="submit" form="calendar-event-form" class="btn btn-primary"
        :disabled="saving || loading" data-test="calendar-save">{{ $t('common.save') }}</button>
    </template>
  </UiDialog>
  <ConfirmDialog :open="confirmDelete" :title="$t('calendar.deleteTitle')"
    :message="form.repeatYearlyYn === 'Y' ? t('calendar.deleteRepeatConfirm', { name: form.title })
      : t('calendar.deleteConfirm', { name: form.title })"
    :confirm-label="$t('common.delete')" danger :busy="deleting"
    @close="confirmDelete = false" @confirm="remove" />
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.repeat {
  min-height: var(--control-h);
}

.push-left {
  margin-right: auto;
}
</style>
