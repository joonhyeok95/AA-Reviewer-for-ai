<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { useCodeStore } from '@/code/store'
import type { Code } from '@/code/types'
import { ApiError } from '@/global/api/ApiError'
import ConfirmDialog from '@/global/components/ConfirmDialog.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { useToastStore } from '@/global/stores/toast'
import { formatDateTime, formatFileSize, fromDateTimeLocal, toDateTimeLocal } from '@/global/util/format'
import { isBlankHtml } from '@/global/util/html'
import {
  createNotice, deleteNoticeFile, fetchNoticeForEdit, noticeFileUrl, updateNotice, uploadNoticeFiles,
} from '@/notice/api'
import MailTargetPicker from '@/notice/components/MailTargetPicker.vue'
import NoticeEditor from '@/notice/components/NoticeEditor.vue'
import type { MailTarget, NoticeEdit, NoticeFile, NoticeSaveRequest } from '@/notice/types'
import { langState, validateNotice, type LangContent, type NoticeFormErrors } from '@/notice/validation'

/**
 * 공지 등록/수정.
 *  - 언어 탭 = 지원 언어(LANG) 순서. 최소 1개 언어의 제목+본문 필수, 일부만 입력한 언어는 오류
 *  - 대표 언어 미지정 = 처음 입력한 언어 (서버 규칙과 동일)
 *  - 첨부는 공지 공통. 등록 시에는 저장 후 업로드한다
 *  - 메일은 발송 전까지만 설정 변경 가능 (mailChangeable)
 */
const props = defineProps<{ id?: number }>()

const router = useRouter()
const code = useCodeStore()
const toast = useToastStore()
const feedback = useApiFeedback()
const { t } = useI18n()

const isEdit = computed(() => props.id !== undefined)
const loaded = ref(false)
const saving = ref(false)
const dirty = ref(false)
const saved = ref(false)

const categories = ref<Code[]>([])
const tags = ref<Code[]>([])
const sendTypes = ref<Code[]>([])

const contents = reactive<Record<string, LangContent>>({})
const activeLang = ref('')
const form = reactive({
  categoryCd: '',
  tagCd: '',
  baseLangCd: '',
  postDate: '',
  popupYn: 'N' as 'Y' | 'N',
  popupStartDt: '',
  popupEndDt: '',
  mailSendTypeCd: 'NONE',
  mailTargets: [] as MailTarget[],
})
const mail = reactive({ changeable: true, statusCd: null as string | null, sendDt: null as string | null })
const existingFiles = ref<NoticeFile[]>([])
const pendingFiles = ref<File[]>([])
const fileToDelete = ref<NoticeFile | null>(null)
const errors = ref<NoticeFormErrors>({})

const langs = computed(() => code.langs)
const filledLangs = computed(() => langs.value.filter((lang) =>
  langState(contents[lang.langCd]) === 'filled'))

function ensureContents(): void {
  for (const lang of langs.value) {
    contents[lang.langCd] ??= { title: '', contentHtml: '' }
  }
  if (!activeLang.value) {
    activeLang.value = langs.value[0]?.langCd ?? ''
  }
}

function fill(data: NoticeEdit): void {
  form.categoryCd = data.categoryCd
  form.tagCd = data.tagCd ?? ''
  form.baseLangCd = data.baseLangCd
  form.postDate = toDateTimeLocal(data.postDate)
  form.popupYn = data.popupYn
  form.popupStartDt = data.popupStartDt ?? ''
  form.popupEndDt = data.popupEndDt ?? ''
  form.mailSendTypeCd = data.mailSendTypeCd || 'NONE'
  form.mailTargets = data.mailTargets.map((target) => ({ ...target }))
  mail.changeable = data.mailChangeable
  mail.statusCd = data.mailStatusCd
  mail.sendDt = data.mailSendDt
  existingFiles.value = data.files
  for (const item of data.i18n) {
    contents[item.langCd] = { title: item.title ?? '', contentHtml: item.contentHtml ?? '' }
  }
  activeLang.value = data.baseLangCd
}

function buildRequest(): NoticeSaveRequest {
  const i18n = langs.value
    .filter((lang) => langState(contents[lang.langCd]) !== 'empty')
    .map((lang) => ({
      langCd: lang.langCd,
      title: contents[lang.langCd]!.title.trim(),
      contentHtml: isBlankHtml(contents[lang.langCd]!.contentHtml) ? '' : contents[lang.langCd]!.contentHtml,
    }))
  return {
    categoryCd: form.categoryCd,
    tagCd: form.tagCd || null,
    baseLangCd: form.baseLangCd && filledLangs.value.some((l) => l.langCd === form.baseLangCd)
      ? form.baseLangCd : null,
    postDate: fromDateTimeLocal(form.postDate),
    popupYn: form.popupYn,
    popupStartDt: form.popupYn === 'Y' ? form.popupStartDt || null : null,
    popupEndDt: form.popupYn === 'Y' ? form.popupEndDt || null : null,
    mailSendTypeCd: form.mailSendTypeCd,
    i18n,
    mailTargets: form.mailSendTypeCd === 'TARGET'
      ? form.mailTargets.map(({ targetType, targetCd, includeSubYn }) =>
        ({ targetType, targetCd, includeSubYn }))
      : [],
  }
}

async function submit(): Promise<void> {
  errors.value = validateNotice({
    langCds: langs.value.map((lang) => lang.langCd),
    contents,
    categoryCd: form.categoryCd,
    popupYn: form.popupYn,
    popupStartDt: form.popupStartDt,
    popupEndDt: form.popupEndDt,
    mailSendTypeCd: form.mailSendTypeCd,
    mailTargetCount: form.mailTargets.length,
  })
  if (Object.keys(errors.value).length) {
    const firstLang = errors.value.langs ? Object.keys(errors.value.langs)[0] : undefined
    if (firstLang) activeLang.value = firstLang
    toast.push('error', t('notice.form.invalid'), errors.value.summary)
    return
  }
  saving.value = true
  try {
    const request = buildRequest()
    const response = isEdit.value ? await updateNotice(props.id!, request) : await createNotice(request)
    const noticeId = response.result.noticeId
    saved.value = true
    if (pendingFiles.value.length) {
      try {
        await uploadNoticeFiles(noticeId, pendingFiles.value)
        pendingFiles.value = []
      } catch (error) {
        feedback.failure(error)
        await router.replace(`/support/notices/${noticeId}/edit`)
        return
      }
    }
    feedback.success(response)
    await router.push(`/support/notices/${noticeId}`)
  } catch (error) {
    if (error instanceof ApiError && error.fieldErrors.length) {
      errors.value = { summary: error.fieldErrors.map((e) => `${e.field}: ${e.reason}`).join('\n') }
    }
    feedback.failure(error)
  } finally {
    saving.value = false
  }
}

function onPickFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  pendingFiles.value = [...pendingFiles.value, ...Array.from(input.files ?? [])]
  input.value = ''
  dirty.value = true
}

function removePending(index: number): void {
  pendingFiles.value = pendingFiles.value.filter((_, i) => i !== index)
}

async function confirmDeleteFile(): Promise<void> {
  const file = fileToDelete.value
  if (!file || !props.id) return
  try {
    feedback.success(await deleteNoticeFile(props.id, file.fileId))
    existingFiles.value = existingFiles.value.filter((f) => f.fileId !== file.fileId)
  } catch (error) {
    feedback.failure(error)
  } finally {
    fileToDelete.value = null
  }
}

function tabState(langCd: string): 'filled' | 'partial' | 'empty' {
  return langState(contents[langCd])
}

onBeforeRouteLeave(() => {
  if (dirty.value && !saved.value) {
    return window.confirm(t('notice.form.leaveConfirm'))
  }
  return true
})

onMounted(async () => {
  try {
    const [category, tag, sendType] = await Promise.all([
      code.getCodes('NOTICE_CATEGORY'), code.getCodes('NOTICE_TAG'), code.getCodes('MAIL_SEND_TYPE'),
    ])
    categories.value = category
    tags.value = tag
    sendTypes.value = sendType
    ensureContents()
    if (isEdit.value) {
      fill(await fetchNoticeForEdit(props.id!))
      ensureContents()
    } else {
      form.categoryCd = category[0]?.code ?? ''
    }
    loaded.value = true
  } catch (error) {
    feedback.failure(error)
  }
})
</script>

<template>
  <div class="page wide">
    <header class="page-head">
      <div>
        <RouterLink :to="isEdit ? `/support/notices/${id}` : '/support/notices'" class="back">
          ← {{ isEdit ? $t('notice.form.backToDetail') : $t('notice.title') }}</RouterLink>
        <h1>{{ isEdit ? $t('notice.form.editTitle') : $t('notice.form.createTitle') }}</h1>
      </div>
      <div class="page-actions">
        <RouterLink :to="isEdit ? `/support/notices/${id}` : '/support/notices'" class="btn">
          {{ $t('common.cancel') }}</RouterLink>
        <button type="submit" form="notice-form" class="btn btn-primary" :disabled="saving || !loaded"
          data-test="notice-save">{{ saving ? $t('common.saving') : $t('common.save') }}</button>
      </div>
    </header>

    <form v-if="loaded" id="notice-form" class="layout" novalidate @submit.prevent="submit"
      @input="dirty = true">
      <div class="main">
        <p v-if="errors.summary" class="notice-line warn summary" role="alert">{{ errors.summary }}</p>

        <section class="panel">
          <div class="tabs" role="tablist" :aria-label="$t('notice.form.langTabs')">
            <button v-for="lang in langs" :key="lang.langCd" type="button" role="tab" class="tab"
              :aria-selected="activeLang === lang.langCd" :data-test="`tab-${lang.langCd}`"
              @click="activeLang = lang.langCd">
              <span class="state" :class="tabState(lang.langCd)" aria-hidden="true" />
              {{ lang.langNm }}
              <span class="mono muted">{{ lang.badge }}</span>
              <span v-if="form.baseLangCd === lang.langCd" class="tag tag-accent">{{ $t('notice.form.base') }}</span>
              <span v-if="errors.langs?.[lang.langCd]" class="sr-only">{{ errors.langs[lang.langCd] }}</span>
            </button>
          </div>
          <div v-for="lang in langs" v-show="activeLang === lang.langCd" :key="lang.langCd"
            class="lang-pane" role="tabpanel">
            <p v-if="errors.langs?.[lang.langCd]" class="field-error pane-error">{{ errors.langs[lang.langCd] }}</p>
            <div class="field">
              <label class="field-label" :for="`title-${lang.langCd}`">{{ $t('notice.form.title') }}</label>
              <input :id="`title-${lang.langCd}`" v-model="contents[lang.langCd]!.title" class="input title-input"
                maxlength="500" :lang="lang.langCd" :placeholder="$t('notice.form.titlePlaceholder', { lang: lang.langNm })"
                :data-test="`title-${lang.langCd}`">
            </div>
            <div class="field">
              <span class="field-label">{{ $t('notice.form.content') }}</span>
              <!-- 보이는 탭만 에디터 생성 (숨은 상태로 만들면 툴바 폭 계산이 0 이 됨). 내용은 contents 에 유지 -->
              <NoticeEditor v-if="activeLang === lang.langCd" v-model="contents[lang.langCd]!.contentHtml"
                :editor-locale="lang.editorLocale" :data-test="`editor-${lang.langCd}`"
                @update:model-value="dirty = true" />
            </div>
          </div>
          <p class="pane-foot field-hint">{{ $t('notice.form.langRule') }}</p>
        </section>

        <section class="panel panel-pad">
          <h2 class="section-title">{{ $t('notice.files') }}</h2>
          <p class="field-hint">{{ $t('notice.form.fileHint') }}</p>
          <ul class="file-list">
            <li v-for="file in existingFiles" :key="file.fileId">
              <a :href="noticeFileUrl(id!, file.fileId)" download>{{ file.orgFileNm }}</a>
              <span class="muted num">{{ formatFileSize(file.fileSize) }}</span>
              <button type="button" class="btn btn-sm btn-quiet" @click="fileToDelete = file">
                {{ $t('common.delete') }}</button>
            </li>
            <li v-for="(file, index) in pendingFiles" :key="`p-${index}`" class="pending">
              <span>{{ file.name }}</span>
              <span class="tag tag-info">{{ $t('notice.form.pendingUpload') }}</span>
              <span class="muted num">{{ formatFileSize(file.size) }}</span>
              <button type="button" class="btn btn-sm btn-quiet" @click="removePending(index)">
                {{ $t('common.remove') }}</button>
            </li>
          </ul>
          <label class="btn btn-sm file-btn">
            <input type="file" multiple class="sr-only" data-test="file-input" @change="onPickFiles">
            {{ $t('notice.form.addFiles') }}
          </label>
        </section>
      </div>

      <aside class="side">
        <section class="panel panel-pad stack">
          <h2 class="section-title">{{ $t('notice.form.posting') }}</h2>
          <div class="field">
            <label class="field-label" for="f-category">{{ $t('notice.category') }}<span class="req">*</span></label>
            <select id="f-category" v-model="form.categoryCd" class="select" :aria-invalid="!!errors.categoryCd">
              <option v-for="item in categories" :key="item.code" :value="item.code">{{ item.codeNm }}</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" for="f-tag">{{ $t('notice.tag') }}</label>
            <select id="f-tag" v-model="form.tagCd" class="select">
              <option value="">{{ $t('common.none') }}</option>
              <option v-for="item in tags" :key="item.code" :value="item.code">{{ item.codeNm }}</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" for="f-post">{{ $t('notice.postDate') }}</label>
            <input id="f-post" v-model="form.postDate" type="datetime-local" class="input num">
            <p class="field-hint">{{ $t('notice.form.postDateHint') }}</p>
          </div>
          <div class="field">
            <label class="field-label" for="f-base">{{ $t('notice.form.baseLang') }}</label>
            <select id="f-base" v-model="form.baseLangCd" class="select" data-test="base-lang">
              <option value="">{{ $t('notice.form.baseLangAuto') }}</option>
              <option v-for="lang in filledLangs" :key="lang.langCd" :value="lang.langCd">{{ lang.langNm }}</option>
            </select>
            <p class="field-hint">{{ $t('notice.form.baseLangHint') }}</p>
          </div>
        </section>

        <section class="panel panel-pad stack">
          <h2 class="section-title">{{ $t('notice.form.popup') }}</h2>
          <label class="check">
            <input v-model="form.popupYn" type="checkbox" true-value="Y" false-value="N" data-test="popup-yn">
            {{ $t('notice.form.popupEnable') }}
          </label>
          <div v-if="form.popupYn === 'Y'" class="grid-2 tight">
            <div class="field">
              <label class="field-label" for="f-ps">{{ $t('notice.form.popupStart') }}</label>
              <input id="f-ps" v-model="form.popupStartDt" type="date" class="input num" :aria-invalid="!!errors.popup">
            </div>
            <div class="field">
              <label class="field-label" for="f-pe">{{ $t('notice.form.popupEnd') }}</label>
              <input id="f-pe" v-model="form.popupEndDt" type="date" class="input num" :aria-invalid="!!errors.popup">
            </div>
          </div>
          <p v-if="errors.popup" class="field-error">{{ errors.popup }}</p>
        </section>

        <section class="panel panel-pad stack">
          <h2 class="section-title">{{ $t('notice.mail.title') }}</h2>
          <p v-if="!mail.changeable" class="notice-line">
            {{ $t('notice.mail.locked', { status: mail.statusCd ?? '', date: formatDateTime(mail.sendDt) }) }}</p>
          <div class="field">
            <label class="field-label" for="f-mail">{{ $t('notice.mail.sendType') }}</label>
            <select id="f-mail" v-model="form.mailSendTypeCd" class="select" :disabled="!mail.changeable"
              data-test="mail-send-type">
              <option v-for="item in sendTypes" :key="item.code" :value="item.code">{{ item.codeNm }}</option>
            </select>
            <p class="field-hint">{{ $t('notice.mail.hint') }}</p>
          </div>
        </section>
      </aside>

      <section v-if="form.mailSendTypeCd === 'TARGET'" class="panel panel-pad span-all">
        <h2 class="section-title">{{ $t('notice.mail.targets') }}</h2>
        <p class="field-hint picker-hint">{{ $t('notice.mail.targetsHint') }}</p>
        <MailTargetPicker v-model="form.mailTargets" :disabled="!mail.changeable" @update:model-value="dirty = true" />
        <p v-if="errors.mailTargets" class="field-error">{{ errors.mailTargets }}</p>
      </section>
    </form>

    <ConfirmDialog :open="fileToDelete !== null" :title="$t('notice.form.deleteFileTitle')"
      :message="fileToDelete?.orgFileNm ?? ''" :confirm-label="$t('common.delete')" danger
      @close="fileToDelete = null" @confirm="confirmDeleteFile" />
  </div>
</template>

<style scoped>
.wide {
  max-width: 1320px;
}

.back {
  display: inline-block;
  margin-bottom: var(--space-2);
  font-size: var(--text-sm);
  color: var(--ink-3);
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--space-5);
  align-items: start;
}

.main,
.side {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.span-all {
  grid-column: 1 / -1;
}

.stack {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.stack .section-title {
  margin-bottom: 0;
}

.tabs {
  padding: 0 var(--space-3);
}

.state {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  border: 1px solid var(--ink-4);
}

.state.filled {
  background: var(--accent);
  border-color: var(--accent);
}

.state.partial {
  background: var(--warn);
  border-color: var(--warn);
}

.lang-pane {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-5) 0;
}

.title-input {
  height: 40px;
  font-size: var(--text-lg);
  font-weight: 600;
}

.pane-error {
  margin-bottom: calc(var(--space-2) * -1);
}

.pane-foot {
  padding: var(--space-3) var(--space-5) var(--space-4);
}

.summary {
  white-space: pre-line;
}

.file-list {
  margin: var(--space-3) 0;
  padding: 0;
  list-style: none;
}

.file-list li {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 6px 0;
  border-bottom: 1px solid var(--rule);
  font-size: var(--text-sm);
}

.file-list li > :first-child {
  flex: 1;
}

.file-btn {
  cursor: pointer;
}

.tight {
  gap: var(--space-3);
}

.picker-hint {
  margin-bottom: var(--space-3);
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
