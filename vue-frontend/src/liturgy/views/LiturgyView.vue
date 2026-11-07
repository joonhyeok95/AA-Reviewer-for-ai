<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import type { Code } from '@/code/types'
import { ApiError } from '@/global/api/ApiError'
import ConfirmDialog from '@/global/components/ConfirmDialog.vue'
import EmptyState from '@/global/components/EmptyState.vue'
import RowTools from '@/global/components/RowTools.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { moveItem, removeItem } from '@/global/util/list'
import { createLiturgyText, deleteLiturgyText, fetchLiturgyTexts, updateLiturgyText } from '@/liturgy/api'
import LiturgyTextView from '@/liturgy/components/LiturgyTextView.vue'
import type { LiturgyText, LiturgyTextSaveRequest } from '@/liturgy/types'

/**
 * 전례문 (사도신경·주기도문·교독문) - 줄마다 화자(인도자/성도/다함께, 없으면 표시 안 함).
 * 주보에서 쓰는 전례문은 삭제할 수 없다 (사용 안 함으로 숨김).
 */
const auth = useAuthStore()
const code = useCodeStore()
const feedback = useApiFeedback()
const { t } = useI18n()

const texts = ref<LiturgyText[]>([])
const types = ref<Code[]>([])
const speakers = ref<Code[]>([])
const selectedId = ref<number | null>(null)
const form = ref<LiturgyTextSaveRequest | null>(null)
const creating = ref(false)
const errors = ref<Record<string, string>>({})
const saving = ref(false)
const deleting = ref<LiturgyText | null>(null)
const busy = ref(false)

const canAdd = computed(() => auth.can('liturgy', 'add'))
const canEdit = computed(() => auth.can('liturgy', 'edit'))
const canDel = computed(() => auth.can('liturgy', 'del'))
const selected = computed(() => texts.value.find((text) => text.textId === selectedId.value) ?? null)

async function load(): Promise<void> {
  try {
    const [list, typeCodes, speakerCodes] = await Promise.all([
      fetchLiturgyTexts(), code.getCodes('LITURGY_TYPE'), code.getCodes('LITURGY_SPEAKER')])
    texts.value = list
    types.value = typeCodes
    speakers.value = speakerCodes
    if (selectedId.value === null && list.length) selectedId.value = list[0].textId
  } catch (error) {
    feedback.failure(error)
  }
}

function pick(id: number): void {
  selectedId.value = id
  form.value = null
  creating.value = false
}

function startEdit(text: LiturgyText): void {
  errors.value = {}
  creating.value = false
  form.value = {
    textTypeCd: text.textTypeCd, title: text.title, versionNm: text.versionNm ?? '',
    intro: text.intro ?? '', sortOrder: text.sortOrder, useYn: text.useYn,
    lines: text.lines.map((line) => ({ ...line })),
  }
}

function startCreate(): void {
  errors.value = {}
  creating.value = true
  selectedId.value = null
  form.value = {
    textTypeCd: types.value[0]?.code ?? '', title: '', versionNm: '', intro: '',
    sortOrder: texts.value.length + 1, useYn: 'Y', lines: [{ speakerCd: null, content: '' }],
  }
}

async function save(): Promise<void> {
  if (!form.value) return
  errors.value = {}
  saving.value = true
  try {
    const body = { ...form.value, lines: form.value.lines.filter((line) => line.content.trim()) }
    const response = creating.value || selectedId.value === null ? await createLiturgyText(body)
      : await updateLiturgyText(selectedId.value, body)
    feedback.success(response)
    form.value = null
    creating.value = false
    await load()
    selectedId.value = response.result.textId
  } catch (error) {
    if (error instanceof ApiError && error.fieldErrors.length) {
      errors.value = Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.reason]))
    }
    feedback.failure(error)
  } finally {
    saving.value = false
  }
}

async function confirmDelete(): Promise<void> {
  if (!deleting.value) return
  busy.value = true
  try {
    feedback.success(await deleteLiturgyText(deleting.value.textId))
    deleting.value = null
    selectedId.value = null
    await load()
  } catch (error) {
    feedback.failure(error)
  } finally {
    busy.value = false
  }
}

watch(() => auth.user?.langCd, () => {
  code.clearCodes()
  void load()
})
onMounted(load)
</script>

<template>
  <div class="page wide">
    <header class="page-head">
      <div>
        <h1>{{ $t('liturgy.title') }}</h1>
        <p class="lede">{{ $t('liturgy.lede') }}</p>
      </div>
      <div class="page-actions">
        <button v-if="canAdd" type="button" class="btn btn-primary" data-test="liturgy-add" @click="startCreate">
          + {{ $t('liturgy.add') }}</button>
      </div>
    </header>

    <div class="split">
      <aside class="split-list" :aria-label="$t('liturgy.title')">
        <ul class="split-list-body">
          <li v-for="text in texts" :key="text.textId">
            <button type="button" class="pick" :aria-current="text.textId === selectedId"
              :data-test="`liturgy-${text.textId}`" @click="pick(text.textId)">
              <span class="pick-main">
                <span>{{ text.title }}</span>
                <span class="pick-sub">{{ code.codeName('LITURGY_TYPE', text.textTypeCd) }}
                  <template v-if="text.versionNm"> · {{ text.versionNm }}</template></span>
              </span>
              <span v-if="text.useYn === 'N'" class="tag tag-warn">{{ $t('common.unused') }}</span>
              <span class="pick-count">{{ text.lines.length }}</span>
            </button>
          </li>
        </ul>
      </aside>

      <section class="panel panel-pad">
        <template v-if="form">
          <header class="detail-head">
            <h2>{{ creating ? $t('liturgy.add') : $t('liturgy.edit') }}</h2>
          </header>
          <form id="liturgy-form" class="form-stack" novalidate @submit.prevent="save">
            <div class="grid-3">
              <div class="field">
                <label class="field-label" for="lt-type">{{ $t('liturgy.type') }}<span class="req">*</span></label>
                <select id="lt-type" v-model="form.textTypeCd" class="select">
                  <option v-for="type in types" :key="type.code" :value="type.code">{{ type.codeNm }}</option>
                </select>
              </div>
              <div class="field">
                <label class="field-label" for="lt-title">{{ $t('liturgy.textTitle') }}<span class="req">*</span></label>
                <input id="lt-title" v-model="form.title" class="input" maxlength="200" data-test="liturgy-title"
                  :aria-invalid="!!errors.title">
              </div>
              <div class="field">
                <label class="field-label" for="lt-version">{{ $t('liturgy.version') }}</label>
                <input id="lt-version" v-model="form.versionNm" class="input" maxlength="100">
              </div>
            </div>
            <div class="field">
              <label class="field-label" for="lt-intro">{{ $t('liturgy.intro') }}</label>
              <input id="lt-intro" v-model="form.intro" class="input" maxlength="1000">
            </div>
            <div class="grid-2">
              <div class="field">
                <label class="field-label" for="lt-sort">{{ $t('admin.sortOrder') }}</label>
                <input id="lt-sort" v-model.number="form.sortOrder" class="input" type="number">
              </div>
              <label class="check use"><input v-model="form.useYn" type="checkbox" true-value="Y" false-value="N">
                {{ $t('common.use') }}</label>
            </div>

            <div class="section-bar">
              <h3 class="section-title">{{ $t('liturgy.lines') }}</h3>
              <span class="field-hint">{{ $t('liturgy.linesHint') }}</span>
            </div>
            <ol class="lines">
              <li v-for="(line, i) in form.lines" :key="i" class="line-row" data-test="liturgy-line">
                <select v-model="line.speakerCd" class="select speaker" :aria-label="$t('liturgy.speaker')">
                  <option :value="null">{{ $t('liturgy.noSpeaker') }}</option>
                  <option v-for="speaker in speakers" :key="speaker.code" :value="speaker.code">{{ speaker.codeNm }}</option>
                </select>
                <textarea v-model="line.content" class="textarea" maxlength="2000" rows="2"
                  :aria-label="$t('liturgy.content')" data-test="liturgy-line-content" />
                <RowTools :index="i" :length="form.lines.length" :label="$t('liturgy.line')"
                  @up="moveItem(form.lines, i, -1)" @down="moveItem(form.lines, i, 1)"
                  @remove="removeItem(form.lines, i)" />
              </li>
            </ol>
            <button type="button" class="btn btn-sm add-line" data-test="liturgy-line-add"
              @click="form.lines.push({ speakerCd: form.lines.at(-1)?.speakerCd === 'LEADER' ? 'PEOPLE' : form.lines.at(-1)?.speakerCd === 'PEOPLE' ? 'LEADER' : null, content: '' })">
              + {{ $t('liturgy.addLine') }}</button>
          </form>
          <div class="form-actions">
            <button type="button" class="btn" @click="form = null; creating = false">{{ $t('common.cancel') }}</button>
            <button type="submit" form="liturgy-form" class="btn btn-primary" :disabled="saving"
              data-test="liturgy-save">{{ $t('common.save') }}</button>
          </div>
        </template>
        <template v-else-if="selected">
          <header class="detail-head">
            <p class="muted">{{ code.codeName('LITURGY_TYPE', selected.textTypeCd) }}</p>
            <div class="page-actions">
              <button v-if="canEdit" type="button" class="btn btn-sm" data-test="liturgy-edit"
                @click="startEdit(selected)">{{ $t('common.edit') }}</button>
              <button v-if="canDel" type="button" class="btn btn-sm btn-danger" @click="deleting = selected">
                {{ $t('common.delete') }}</button>
            </div>
          </header>
          <LiturgyTextView :text="selected" />
        </template>
        <EmptyState v-else :title="$t('liturgy.pick')" description="" />
      </section>
    </div>

    <ConfirmDialog :open="deleting !== null" :title="$t('common.delete')"
      :message="t('liturgy.deleteConfirm', { name: deleting?.title ?? '' })"
      :confirm-label="$t('common.delete')" danger :busy="busy"
      @close="deleting = null" @confirm="confirmDelete" />
  </div>
</template>

<style scoped>
.section-bar {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--rule);
}

.section-bar .section-title {
  margin: 0;
}

.use {
  align-self: end;
  min-height: var(--control-h);
}

.lines {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.line-row {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--space-2);
}

.add-line {
  align-self: flex-start;
}
</style>
