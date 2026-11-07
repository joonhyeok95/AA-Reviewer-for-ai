<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import type { Code } from '@/code/types'
import { ApiError } from '@/global/api/ApiError'
import RowTools from '@/global/components/RowTools.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { useToastStore } from '@/global/stores/toast'
import { today } from '@/global/util/format'
import { moveItem, removeItem } from '@/global/util/list'
import { fetchOccurrences } from '@/calendar/api'
import { addDays, parseIso } from '@/calendar/dates'
import { fetchCatechisms } from '@/catechism/api'
import { flattenUnits, unitLabel } from '@/catechism/labels'
import type { Catechism } from '@/catechism/types'
import { fetchLiturgyTexts } from '@/liturgy/api'
import type { LiturgyText } from '@/liturgy/types'
import {
  PHOTO_ACCEPT, PHOTO_MAX_MB, bulletinPhotoUrl, createBulletin, deleteBulletinPhoto, fetchBulletin,
  fetchBulletins, updateBulletin, uploadBulletinPhoto,
} from '@/bulletin/api'
import DutyGridEditor from '@/bulletin/components/DutyGridEditor.vue'
import NewsTreeEditor from '@/bulletin/components/NewsTreeEditor.vue'
import {
  type BulletinForm, celebrationNews, emptyForm, emptyOrder, formFromDetail, templateForm,
  toSaveRequest, weekOf, weeklyCatechismUnits,
} from '@/bulletin/form'

/**
 * 주보 등록·수정.
 *  - 예배순서·교회소식(섹션→항목→하위)·섬김위원(역할×날짜)·교리문답·전례문은 모두 [+] 로 n개
 *  - 새 주보 : 가장 최근 주보(또는 ?from=)의 내용을 모두 템플릿으로 채움 (예배일 +7, 호 +1, 작성 중).
 *    "빈 양식으로" 로 비우고 새로 쓸 수 있다
 *  - 교회소식 "달력에서 축하 가져오기" = 그 주(일~토)의 생일·결혼기념일 (calendar.list 권한)
 */
const props = defineProps<{ id?: number }>()
const auth = useAuthStore()
const code = useCodeStore()
const feedback = useApiFeedback()
const toast = useToastStore()
const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()

const form = ref<BulletinForm>(emptyForm(nextSunday()))
const statuses = ref<Code[]>([])
const unitTypes = ref<Code[]>([])
const catechisms = ref<Catechism[]>([])
const texts = ref<LiturgyText[]>([])
const templated = ref(false)
/** 대표사진 : 저장된 사진 주소 / 새로 고른 파일(미리보기 data URL) / 지우기 */
const photo = ref<{ savedUrl: string | null; file: File | null; preview: string | null; remove: boolean }>(
  { savedUrl: null, file: null, preview: null, remove: false })
const pickCatechism = ref<number | null>(null)
const pickUnit = ref<number | null>(null)
const pickText = ref<number | null>(null)
const errors = ref<Record<string, string>>({})
const saving = ref(false)

const editing = computed(() => props.id !== undefined)
const canCelebrate = computed(() => auth.can('calendar', 'list'))
const typeName = (cd: string): string => unitTypes.value.find((type) => type.code === cd)?.codeNm ?? cd
const unitOptions = computed(() => flattenUnits(
  catechisms.value.find((c) => c.catechismId === pickCatechism.value)?.units ?? []))
const allUnits = computed(() => catechisms.value.flatMap((c) =>
  flattenUnits(c.units).map(({ unit }) => ({ unit, catechism: c }))))

function nextSunday(): string {
  const now = today()
  const offset = (7 - parseIso(now).getDay()) % 7
  return addDays(now, offset)
}

function unitName(unitId: number): string {
  const found = allUnits.value.find(({ unit }) => unit.unitId === unitId)
  return found ? `${found.catechism.title} · ${unitLabel(found.unit, typeName, t)}` : `#${unitId}`
}

function textName(textId: number): string {
  const text = texts.value.find((each) => each.textId === textId)
  return text ? `${text.title}${text.versionNm ? ` (${text.versionNm})` : ''}` : `#${textId}`
}

async function load(): Promise<void> {
  try {
    const [statusCodes, typeCodes, catechismList, textList] = await Promise.all([
      code.getCodes('BULLETIN_STATUS'), code.getCodes('CATECHISM_UNIT_TYPE'),
      fetchCatechisms(), fetchLiturgyTexts()])
    statuses.value = statusCodes
    unitTypes.value = typeCodes
    catechisms.value = catechismList
    texts.value = textList
    pickCatechism.value = catechismList[0]?.catechismId ?? null
    if (props.id !== undefined) {
      const detail = await fetchBulletin(props.id)
      form.value = formFromDetail(detail)
      photo.value.savedUrl = detail.hasPhoto ? bulletinPhotoUrl(detail) : null
    } else {
      const fromId = route.query.from ? Number(route.query.from)
        : (await fetchBulletins({ page: 0, size: 1 })).content[0]?.bulletinId
      if (fromId !== undefined) {
        form.value = templateForm(await fetchBulletin(fromId), nextSunday())
        templated.value = true
      } else {
        form.value.liturgyTextIds = textList.filter((text) => text.useYn === 'Y').map((text) => text.textId)
      }
      applyWeeklyCatechism()
    }
  } catch (error) {
    feedback.failure(error)
  }
}

/** 템플릿 대신 빈 양식 (예배일은 유지, 전례문은 사용 중인 것) */
function clearForm(): void {
  const blank = emptyForm(form.value.worshipDt)
  blank.liturgyTextIds = texts.value.filter((text) => text.useYn === 'Y').map((text) => text.textId)
  form.value = blank
  templated.value = false
  applyWeeklyCatechism()
}

/** 새 주보 : 교리문답을 예배일 주차(그해 n번째 주일 = 제n주일)에 맞춘다 */
const autoUnitIds = ref<number[] | null>(null)
function applyWeeklyCatechism(): void {
  form.value.catechismUnitIds = weeklyCatechismUnits(form.value.catechismUnitIds, catechisms.value,
    form.value.worshipDt)
  autoUnitIds.value = [...form.value.catechismUnitIds]
}

// 예배일을 바꾸면 다시 맞춘다 (직접 고친 교리문답은 건드리지 않음)
watch(() => form.value.worshipDt, (date, before) => {
  if (editing.value || !date || date === before || autoUnitIds.value === null) return
  if (autoUnitIds.value.join() === form.value.catechismUnitIds.join()) applyWeeklyCatechism()
})

/** 사진 고르기 : 형식·크기는 화면에서 먼저 거르고, 서버가 내용으로 다시 판별 */
function pickPhoto(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!PHOTO_ACCEPT.split(',').includes(file.type)) {
    toast.push('error', t('bulletin.photoType'))
    return
  }
  if (file.size > PHOTO_MAX_MB * 1024 * 1024) {
    toast.push('error', t('bulletin.photoSize', { mb: PHOTO_MAX_MB }))
    return
  }
  // 미리보기는 data URL (CSP img-src 에 blob: 없음)
  const reader = new FileReader()
  reader.onload = () => {
    photo.value = { ...photo.value, file, preview: String(reader.result), remove: false }
  }
  reader.readAsDataURL(file)
}

function removePhoto(): void {
  photo.value = { ...photo.value, file: null, preview: null, remove: photo.value.savedUrl !== null }
}

/** 주보 저장 뒤 사진 반영 (새 주보는 ID 가 생긴 뒤라야 올릴 수 있다) */
async function savePhoto(bulletinId: number): Promise<void> {
  if (photo.value.file) await uploadBulletinPhoto(bulletinId, photo.value.file)
  else if (photo.value.remove) await deleteBulletinPhoto(bulletinId)
}

const photoShown = computed(() => photo.value.preview
  ?? (photo.value.remove ? null : photo.value.savedUrl))

function addUnit(): void {
  if (pickUnit.value !== null && !form.value.catechismUnitIds.includes(pickUnit.value)) {
    form.value.catechismUnitIds.push(pickUnit.value)
  }
  pickUnit.value = null
}

function addText(): void {
  if (pickText.value !== null && !form.value.liturgyTextIds.includes(pickText.value)) {
    form.value.liturgyTextIds.push(pickText.value)
  }
  pickText.value = null
}

/** 그 주의 생일·결혼기념일로 "축하" 섹션을 만들거나 바꾼다 */
async function importCelebrations(): Promise<void> {
  try {
    const { from, to } = weekOf(form.value.worshipDt)
    const [occurrences, calendarTypes] = await Promise.all([
      fetchOccurrences(from, to), code.getCodes('CAL_EVENT_TYPE')])
    const names = Object.fromEntries(calendarTypes.filter((type) => ['BIRTHDAY', 'WEDDING'].includes(type.code))
      .map((type) => [type.code, type.codeNm]))
    const weekday = (iso: string): string => new Intl.DateTimeFormat(locale.value, { weekday: 'short' })
      .format(parseIso(iso))
    const section = celebrationNews(occurrences, names, weekday,
      t('bulletin.news.celebration'))
    const index = form.value.news.findIndex((node) => node.title === section.title)
    if (index >= 0) form.value.news.splice(index, 1, section)
    else form.value.news.push(section)
    toast.push('info', t('bulletin.news.celebrationDone', { n: occurrences.length }))
  } catch (error) {
    feedback.failure(error)
  }
}

async function save(): Promise<void> {
  errors.value = {}
  saving.value = true
  try {
    const body = toSaveRequest(form.value)
    const response = props.id !== undefined ? await updateBulletin(props.id, body)
      : await createBulletin(body)
    const bulletinId = response.result.bulletinId
    try {
      await savePhoto(bulletinId)
      feedback.success(response)
    } catch (error) {
      // 주보는 저장됨 - 사진 오류만 알리고 보기 화면으로 (수정에서 다시 올릴 수 있다)
      feedback.failure(error)
    }
    await router.push({ name: 'bulletin-detail', params: { id: bulletinId } })
  } catch (error) {
    if (error instanceof ApiError && error.fieldErrors.length) {
      errors.value = Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.reason]))
    }
    feedback.failure(error)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page wide">
    <header class="page-head">
      <div>
        <h1>{{ editing ? $t('bulletin.editTitle') : $t('bulletin.createTitle') }}</h1>
        <p class="lede">{{ $t('bulletin.formLede') }}</p>
        <p v-if="!editing && templated" class="lede" data-test="bulletin-templated">{{ $t('bulletin.templated') }}</p>
        <p v-if="!editing" class="lede">{{ $t('bulletin.weeklyCatechism') }}</p>
      </div>
      <div class="page-actions">
        <button v-if="!editing && templated" type="button" class="btn"
          data-test="bulletin-clear" @click="clearForm">{{ $t('bulletin.clearForm') }}</button>
        <button type="button" class="btn" @click="router.back()">{{ $t('common.cancel') }}</button>
        <button type="button" class="btn btn-primary" :disabled="saving" data-test="bulletin-save"
          @click="save">{{ $t('common.save') }}</button>
      </div>
    </header>

    <form class="sections" novalidate @submit.prevent="save">
      <!-- 1. 기본 정보 -->
      <section class="panel panel-pad">
        <h2 class="section-title">{{ $t('bulletin.basic') }}</h2>
        <div class="grid-3">
          <div class="field">
            <label class="field-label" for="b-date">{{ $t('bulletin.worshipDt') }}<span class="req">*</span></label>
            <input id="b-date" v-model="form.worshipDt" class="input" type="date" required
              :aria-invalid="!!errors.worshipDt" data-test="bulletin-date">
          </div>
          <div class="field">
            <label class="field-label" for="b-vol">{{ $t('bulletin.volume') }}</label>
            <input id="b-vol" v-model.number="form.volumeNo" class="input" type="number" min="1">
          </div>
          <div class="field">
            <label class="field-label" for="b-issue">{{ $t('bulletin.issueNo') }}</label>
            <input id="b-issue" v-model.number="form.issueNo" class="input" type="number" min="1" data-test="bulletin-issue">
          </div>
          <div class="field">
            <label class="field-label" for="b-status">{{ $t('bulletin.status') }}<span class="req">*</span></label>
            <select id="b-status" v-model="form.statusCd" class="select" data-test="bulletin-status">
              <option v-for="status in statuses" :key="status.code" :value="status.code">{{ status.codeNm }}</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" for="b-special">{{ $t('bulletin.special') }}</label>
            <input id="b-special" v-model="form.title" class="input" maxlength="200" :placeholder="$t('bulletin.specialHint')">
          </div>
          <div class="field">
            <label class="field-label" for="b-preacher">{{ $t('bulletin.preacher') }}</label>
            <input id="b-preacher" v-model="form.preacherNm" class="input" maxlength="100">
          </div>
        </div>
        <div class="grid-2">
          <div class="field">
            <label class="field-label" for="b-sermon">{{ $t('bulletin.sermonTitle') }}</label>
            <input id="b-sermon" v-model="form.sermonTitle" class="input" maxlength="200" data-test="bulletin-sermon">
          </div>
          <div class="field">
            <label class="field-label" for="b-scripture">{{ $t('bulletin.scripture') }}</label>
            <input id="b-scripture" v-model="form.sermonScripture" class="input" maxlength="100">
          </div>
        </div>
        <div class="grid-verse">
          <div class="field">
            <label class="field-label" for="b-verse">{{ $t('bulletin.themeVerse') }}</label>
            <textarea id="b-verse" v-model="form.themeVerse" class="textarea" maxlength="2000" />
          </div>
          <div class="field">
            <label class="field-label" for="b-verse-ref">{{ $t('bulletin.themeVerseRef') }}</label>
            <input id="b-verse-ref" v-model="form.themeVerseRef" class="input" maxlength="100">
          </div>
        </div>
      </section>

      <!-- 대표사진 -->
      <section class="panel panel-pad">
        <div class="section-bar">
          <h2 class="section-title">{{ $t('bulletin.photo') }}</h2>
          <div class="photo-actions">
            <label class="btn btn-sm">
              {{ photoShown ? $t('bulletin.photoChange') : `+ ${$t('bulletin.photoAdd')}` }}
              <input type="file" class="file-input" :accept="PHOTO_ACCEPT" data-test="bulletin-photo-input"
                @change="pickPhoto">
            </label>
            <button v-if="photoShown" type="button" class="btn btn-sm" data-test="bulletin-photo-remove"
              @click="removePhoto">{{ $t('common.delete') }}</button>
          </div>
        </div>
        <img v-if="photoShown" class="photo-preview" :src="photoShown" :alt="$t('bulletin.photo')"
          data-test="bulletin-photo-preview">
        <p v-else class="muted">{{ $t('bulletin.photoHint', { mb: PHOTO_MAX_MB }) }}</p>
      </section>

      <!-- 2. 예배순서 -->
      <section class="panel panel-pad">
        <div class="section-bar">
          <h2 class="section-title">{{ $t('bulletin.orders') }} <span class="muted num">{{ form.orders.length }}</span></h2>
          <button type="button" class="btn btn-sm" data-test="order-add" @click="form.orders.push(emptyOrder())">
            + {{ $t('bulletin.addOrder') }}</button>
        </div>
        <ol class="rows">
          <li v-for="(order, i) in form.orders" :key="i" class="order-row" data-test="order-row">
            <span class="num muted">{{ i + 1 }}</span>
            <input v-model="order.itemNm" class="input" maxlength="100" :placeholder="$t('bulletin.orderItem')"
              :aria-label="$t('bulletin.orderItem')" data-test="order-item">
            <input v-model="order.detail" class="input" maxlength="500" :placeholder="$t('bulletin.orderDetail')"
              :aria-label="$t('bulletin.orderDetail')" data-test="order-detail">
            <RowTools :index="i" :length="form.orders.length" :label="$t('bulletin.orderItem')"
              @up="moveItem(form.orders, i, -1)" @down="moveItem(form.orders, i, 1)"
              @remove="removeItem(form.orders, i)" />
          </li>
        </ol>
      </section>

      <!-- 3. 교회소식 -->
      <section class="panel panel-pad">
        <div class="section-bar">
          <h2 class="section-title">{{ $t('bulletin.news.title') }}</h2>
          <button v-if="canCelebrate" type="button" class="btn btn-sm" data-test="news-celebration"
            @click="importCelebrations">{{ $t('bulletin.news.importCelebration') }}</button>
        </div>
        <p class="field-hint">{{ $t('bulletin.news.hint') }}</p>
        <NewsTreeEditor :nodes="form.news" :depth="1" />
      </section>

      <!-- 4. 섬김위원 -->
      <section class="panel panel-pad">
        <h2 class="section-title">{{ $t('bulletin.duty.title') }}</h2>
        <DutyGridEditor v-model="form.duties" :highlight="form.worshipDt" />
      </section>

      <!-- 5. 교리문답 · 전례문 -->
      <section class="panel panel-pad two-col">
        <div>
          <h2 class="section-title">{{ $t('bulletin.catechism') }}</h2>
          <ol class="rows">
            <li v-for="(unitId, i) in form.catechismUnitIds" :key="unitId" class="pick-row" data-test="bulletin-unit">
              <span>{{ unitName(unitId) }}</span>
              <RowTools :index="i" :length="form.catechismUnitIds.length" :label="$t('bulletin.catechism')"
                @up="moveItem(form.catechismUnitIds, i, -1)" @down="moveItem(form.catechismUnitIds, i, 1)"
                @remove="removeItem(form.catechismUnitIds, i)" />
            </li>
          </ol>
          <div class="adder">
            <select v-model="pickCatechism" class="select" :aria-label="$t('catechism.title')">
              <option v-for="c in catechisms" :key="c.catechismId" :value="c.catechismId">{{ c.title }}</option>
            </select>
            <select v-model="pickUnit" class="select" :aria-label="$t('bulletin.pickUnit')" data-test="bulletin-pick-unit">
              <option :value="null">{{ $t('bulletin.pickUnit') }}</option>
              <option v-for="{ unit, depth } in unitOptions" :key="unit.unitId" :value="unit.unitId"
                :disabled="!unit.qaCount">{{ '  '.repeat(depth) }}{{ unitLabel(unit, typeName, t) }}</option>
            </select>
            <button type="button" class="btn btn-sm" :disabled="pickUnit === null" data-test="bulletin-add-unit"
              @click="addUnit">+ {{ $t('common.create') }}</button>
          </div>
        </div>
        <div>
          <h2 class="section-title">{{ $t('bulletin.liturgy') }}</h2>
          <ol class="rows">
            <li v-for="(textId, i) in form.liturgyTextIds" :key="textId" class="pick-row" data-test="bulletin-text">
              <span>{{ textName(textId) }}</span>
              <RowTools :index="i" :length="form.liturgyTextIds.length" :label="$t('bulletin.liturgy')"
                @up="moveItem(form.liturgyTextIds, i, -1)" @down="moveItem(form.liturgyTextIds, i, 1)"
                @remove="removeItem(form.liturgyTextIds, i)" />
            </li>
          </ol>
          <div class="adder">
            <select v-model="pickText" class="select" :aria-label="$t('bulletin.pickText')">
              <option :value="null">{{ $t('bulletin.pickText') }}</option>
              <option v-for="text in texts" :key="text.textId" :value="text.textId">{{ textName(text.textId) }}</option>
            </select>
            <button type="button" class="btn btn-sm" :disabled="pickText === null" @click="addText">
              + {{ $t('common.create') }}</button>
          </div>
        </div>
      </section>

      <!-- 6. 말씀메모 -->
      <section class="panel panel-pad">
        <h2 class="section-title">{{ $t('bulletin.memo') }}</h2>
        <textarea v-model="form.sermonMemo" class="textarea" maxlength="4000" :aria-label="$t('bulletin.memo')" />
      </section>

      <div class="form-actions">
        <button type="button" class="btn" @click="router.back()">{{ $t('common.cancel') }}</button>
        <button type="submit" class="btn btn-primary" :disabled="saving">{{ $t('common.save') }}</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.photo-actions {
  display: flex;
  gap: var(--space-2);
}

/* 파일 선택은 버튼 모양 label 로 (input 은 화면에서 숨기되 키보드·스크린리더는 유지) */
.file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.photo-preview {
  display: block;
  width: 100%;
  max-width: 640px;
  max-height: 360px;
  border-radius: var(--radius-md);
  object-fit: cover;
}

.sections {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.sections .panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.section-title {
  margin: 0;
}

.section-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.grid-verse {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 1fr);
  gap: var(--space-4);
}

.rows {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.order-row {
  display: grid;
  grid-template-columns: 2em minmax(0, 1fr) minmax(0, 1.4fr) auto;
  align-items: center;
  gap: var(--space-2);
}

.two-col {
  display: grid !important;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-6) !important;
}

.two-col > div {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.pick-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
}

.adder {
  display: flex;
  gap: var(--space-2);
}

.adder .select {
  min-width: 0;
}

@media (max-width: 900px) {
  .two-col,
  .grid-verse {
    grid-template-columns: 1fr;
  }
}
</style>
