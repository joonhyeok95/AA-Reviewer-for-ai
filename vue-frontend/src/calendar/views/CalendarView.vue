<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import type { Code } from '@/code/types'
import EmptyState from '@/global/components/EmptyState.vue'
import UiDialog from '@/global/components/UiDialog.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { today } from '@/global/util/format'
import { fetchOccurrences } from '@/calendar/api'
import CalendarEventDialog from '@/calendar/components/CalendarEventDialog.vue'
import {
  addMonths, formatYearMonth, groupForList, monthGrid, monthRange, occurrencesByDay, parseIso,
  parseYearMonth, yearMonthOf, type YearMonth,
} from '@/calendar/dates'
import type { CalendarOccurrence, CalendarViewMode } from '@/calendar/types'

/**
 * 생일 (캘린더) - 생일·결혼기념일·휴일 등 일정. 권한 calendar.*
 *  - 좌측 [<] 오늘 [>] 월 이동, 가운데 연·월, 우측 월/목록 보기 전환
 *  - 보고 있는 달과 보기 방식은 주소(?ym=2026-11&view=list)에 남겨 새로고침·공유해도 유지
 *  - 일정 유형 색은 공통코드 CAL_EVENT_TYPE 의 ATTR1 (accent/info/danger/warn), danger = 휴일 표시
 */
const TYPE_GROUP = 'CAL_EVENT_TYPE'
const MAX_CHIPS = 3

const auth = useAuthStore()
const code = useCodeStore()
const feedback = useApiFeedback()
const route = useRoute()
const router = useRouter()
const { t, te, locale } = useI18n()

const todayIso = ref(today())
const cursor = ref<YearMonth>(parseYearMonth(route.query.ym) ?? yearMonthOf(todayIso.value))
const mode = ref<CalendarViewMode>(route.query.view === 'list' ? 'list' : 'month')
const occurrences = ref<CalendarOccurrence[]>([])
const types = ref<Code[]>([])
const loading = ref(false)

const dialogOpen = ref(false)
const dialogEventId = ref<number | null>(null)
const dialogDate = ref(todayIso.value)
const dayOpen = ref<string | null>(null)

const canAdd = computed(() => auth.can('calendar', 'add'))
const canEdit = computed(() => auth.can('calendar', 'edit'))
const canDel = computed(() => auth.can('calendar', 'del'))

const days = computed(() => monthGrid(cursor.value))
const byDay = computed(() => occurrencesByDay(occurrences.value, days.value))
const listGroups = computed(() => groupForList(occurrences.value, cursor.value))
const typeByCode = computed(() => new Map(types.value.map((type) => [type.code, type])))

const monthTitle = computed(() => new Intl.DateTimeFormat(locale.value,
  { year: 'numeric', month: 'long' }).format(new Date(cursor.value.year, cursor.value.month - 1, 1)))
const weekdays = computed(() => {
  const format = new Intl.DateTimeFormat(locale.value, { weekday: 'short' })
  // 2023-01-01 = 일요일
  return Array.from({ length: 7 }, (_, i) => format.format(new Date(2023, 0, 1 + i)))
})
const dayLabel = (iso: string): string => new Intl.DateTimeFormat(locale.value,
  { month: 'long', day: 'numeric', weekday: 'short' }).format(parseIso(iso))
const shortDate = (iso: string): string => new Intl.DateTimeFormat(locale.value,
  { month: 'numeric', day: 'numeric' }).format(parseIso(iso))

async function load(): Promise<void> {
  loading.value = true
  try {
    const grid = days.value
    const [result, typeCodes] = await Promise.all([
      fetchOccurrences(grid[0], grid[grid.length - 1]),
      code.getCodes(TYPE_GROUP),
    ])
    occurrences.value = result
    types.value = typeCodes
  } catch (error) {
    feedback.failure(error)
  } finally {
    loading.value = false
  }
}

function syncQuery(): void {
  void router.replace({ query: { ...route.query, ym: formatYearMonth(cursor.value), view: mode.value } })
}

function move(delta: number): void {
  cursor.value = addMonths(cursor.value, delta)
}

function goToday(): void {
  todayIso.value = today()
  cursor.value = yearMonthOf(todayIso.value)
}

function setMode(next: CalendarViewMode): void {
  mode.value = next
}

function color(typeCd: string): string {
  return typeByCode.value.get(typeCd)?.attr1 ?? 'neutral'
}

function typeName(typeCd: string): string {
  return typeByCode.value.get(typeCd)?.codeNm ?? typeCd
}

function isHoliday(day: string): boolean {
  return (byDay.value.get(day) ?? []).some((item) => color(item.eventTypeCd) === 'danger')
}

/** 생일 = 만 n세, 결혼기념일 = n주년, 그 외 반복 = n년째 (최초 해는 표시 안 함) */
function yearsText(item: CalendarOccurrence): string {
  if (item.repeatYearlyYn !== 'Y' || item.yearsSince <= 0) return ''
  const key = `calendar.years.${item.eventTypeCd}`
  return t(te(key) ? key : 'calendar.years.other', { n: item.yearsSince })
}


function periodText(item: CalendarOccurrence): string {
  return item.startDt === item.endDt ? ''
    : t('calendar.period', { start: shortDate(item.startDt), end: shortDate(item.endDt) })
}

function openCreate(day?: string): void {
  if (!canAdd.value) return
  dayOpen.value = null
  dialogEventId.value = null
  dialogDate.value = day ?? (cursor.value.year === yearMonthOf(todayIso.value).year
    && cursor.value.month === yearMonthOf(todayIso.value).month
    ? todayIso.value : monthRange(cursor.value).from)
  dialogOpen.value = true
}

function openEvent(item: CalendarOccurrence): void {
  dayOpen.value = null
  dialogEventId.value = item.eventId
  dialogOpen.value = true
}

function onSaved(): void {
  dialogOpen.value = false
  void load()
}

watch(cursor, () => {
  syncQuery()
  void load()
})
watch(mode, syncQuery)
watch(() => auth.user?.langCd, () => {
  code.clearCodes()
  void load()
})
onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>{{ $t('calendar.title') }}</h1>
        <p class="lede">{{ $t('calendar.lede') }}</p>
      </div>
      <div class="page-actions">
        <button v-if="canAdd" type="button" class="btn btn-primary" data-test="calendar-create"
          @click="openCreate()">{{ $t('calendar.create') }}</button>
      </div>
    </header>

    <div class="cal-toolbar">
      <div class="nav" role="group" :aria-label="$t('calendar.navigation')">
        <button type="button" class="btn btn-sm icon" :aria-label="$t('calendar.prev')"
          :title="$t('calendar.prev')" data-test="calendar-prev" @click="move(-1)">&lsaquo;</button>
        <button type="button" class="btn btn-sm" data-test="calendar-today" @click="goToday">
          {{ $t('calendar.today') }}</button>
        <button type="button" class="btn btn-sm icon" :aria-label="$t('calendar.next')"
          :title="$t('calendar.next')" data-test="calendar-next" @click="move(1)">&rsaquo;</button>
      </div>
      <h2 class="month-title num" aria-live="polite" data-test="calendar-month">{{ monthTitle }}</h2>
      <div class="segmented" role="group" :aria-label="$t('calendar.viewMode')">
        <button type="button" class="seg" :aria-pressed="mode === 'month'" data-test="calendar-view-month"
          @click="setMode('month')">{{ $t('calendar.viewMonth') }}</button>
        <button type="button" class="seg" :aria-pressed="mode === 'list'" data-test="calendar-view-list"
          @click="setMode('list')">{{ $t('calendar.viewList') }}</button>
      </div>
    </div>

    <ul v-if="types.length" class="legend" :aria-label="$t('calendar.type')">
      <li v-for="type in types" :key="type.code">
        <span class="dot" :class="`c-${type.attr1 ?? 'neutral'}`" aria-hidden="true" />{{ type.codeNm }}
      </li>
    </ul>

    <!-- 월 달력 -->
    <div v-if="mode === 'month'" class="month panel" :aria-busy="loading" data-test="calendar-month-grid">
      <div class="weekdays" aria-hidden="true">
        <div v-for="(name, i) in weekdays" :key="name" class="weekday"
          :class="{ sun: i === 0, sat: i === 6 }">{{ name }}</div>
      </div>
      <div class="grid">
        <div v-for="(day, i) in days" :key="day" class="cell" data-test="calendar-day" :data-day="day"
          :class="{
            outside: yearMonthOf(day).month !== cursor.month,
            today: day === todayIso,
            sun: i % 7 === 0 || isHoliday(day),
            sat: i % 7 === 6 && !isHoliday(day),
          }">
          <div class="cell-head">
            <button v-if="canAdd" type="button" class="date num" :aria-label="t('calendar.addOn', { date: dayLabel(day) })"
              :title="t('calendar.addOn', { date: dayLabel(day) })" @click="openCreate(day)">
              {{ parseIso(day).getDate() }}</button>
            <span v-else class="date num">{{ parseIso(day).getDate() }}</span>
          </div>
          <ul class="chips">
            <li v-for="item in (byDay.get(day) ?? []).slice(0, MAX_CHIPS)" :key="`${item.eventId}-${item.startDt}`">
              <button type="button" class="chip" :class="`c-${color(item.eventTypeCd)}`"
                :title="[typeName(item.eventTypeCd), item.title, yearsText(item)].filter(Boolean).join(' · ')"
                data-test="calendar-chip" @click="openEvent(item)">
                <span class="chip-title">{{ item.title }}</span>
                <span v-if="yearsText(item)" class="chip-years">{{ yearsText(item) }}</span>
              </button>
            </li>
          </ul>
          <button v-if="(byDay.get(day)?.length ?? 0) > MAX_CHIPS" type="button" class="more"
            data-test="calendar-more" @click="dayOpen = day">
            {{ t('calendar.more', { n: (byDay.get(day)?.length ?? 0) - MAX_CHIPS }) }}</button>
        </div>
      </div>
    </div>

    <!-- 목록 -->
    <div v-else class="list panel" :aria-busy="loading" data-test="calendar-list">
      <section v-for="group in listGroups" :key="group.day" class="day-group">
        <h3 class="day-title num" :class="{ today: group.day === todayIso }">{{ dayLabel(group.day) }}</h3>
        <ul class="rows">
          <li v-for="item in group.items" :key="`${item.eventId}-${item.startDt}`">
            <button type="button" class="row" data-test="calendar-row" @click="openEvent(item)">
              <span class="tag" :class="`tag-${color(item.eventTypeCd)}`">{{ typeName(item.eventTypeCd) }}</span>
              <span class="row-title">{{ item.title }}</span>
              <span v-if="yearsText(item)" class="row-meta">{{ yearsText(item) }}</span>
              <span v-if="periodText(item)" class="row-meta num">{{ periodText(item) }}</span>
              <span v-if="item.repeatYearlyYn === 'Y'" class="tag tag-outline">{{ $t('calendar.yearly') }}</span>
              <span v-if="item.memo" class="row-memo muted">{{ item.memo }}</span>
            </button>
          </li>
        </ul>
      </section>
      <EmptyState v-if="!loading && !listGroups.length" :title="$t('calendar.empty')"
        :description="canAdd ? $t('calendar.emptyHint') : ''" />
    </div>

    <UiDialog :open="dayOpen !== null" :title="dayOpen ? t('calendar.dayEvents', { date: dayLabel(dayOpen) }) : ''"
      @close="dayOpen = null">
      <ul class="rows">
        <li v-for="item in (dayOpen ? byDay.get(dayOpen) ?? [] : [])" :key="`${item.eventId}-${item.startDt}`">
          <button type="button" class="row" @click="openEvent(item)">
            <span class="tag" :class="`tag-${color(item.eventTypeCd)}`">{{ typeName(item.eventTypeCd) }}</span>
            <span class="row-title">{{ item.title }}</span>
            <span v-if="yearsText(item)" class="row-meta">{{ yearsText(item) }}</span>
          </button>
        </li>
      </ul>
      <template #footer>
        <button v-if="canAdd && dayOpen" type="button" class="btn" @click="openCreate(dayOpen)">
          {{ $t('calendar.create') }}</button>
        <button type="button" class="btn btn-primary" @click="dayOpen = null">{{ $t('common.close') }}</button>
      </template>
    </UiDialog>

    <CalendarEventDialog :open="dialogOpen" :event-id="dialogEventId" :initial-date="dialogDate"
      :types="types" :can-edit="canEdit" :can-delete="canDel"
      @close="dialogOpen = false" @saved="onSaved" />
  </div>
</template>

<style scoped>
/* ---------- 상단 : [<] 오늘 [>] / 연·월 / 월·목록 ---------- */
.cal-toolbar {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-3);
}

.nav {
  display: flex;
  gap: var(--space-1);
}

.icon {
  min-width: 32px;
  font-size: var(--text-lg);
  line-height: 1;
}

.month-title {
  margin: 0;
  font-size: var(--text-xl);
  text-align: center;
}

.segmented {
  display: inline-flex;
  justify-self: end;
  border: 1px solid var(--rule-strong);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.seg {
  height: 30px;
  padding: 0 var(--space-4);
  border: 0;
  background: var(--surface);
  color: var(--ink-2);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
}

.seg + .seg {
  border-left: 1px solid var(--rule-strong);
}

.seg[aria-pressed='true'] {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.seg:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

/* ---------- 범례 / 유형 색 (공통코드 ATTR1) ---------- */
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  margin: 0 0 var(--space-3);
  padding: 0;
  list-style: none;
  color: var(--ink-3);
  font-size: var(--text-xs);
}

.legend li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.c-accent { --chip-bg: var(--accent-soft); --chip-fg: var(--accent); }
.c-info { --chip-bg: var(--info-soft); --chip-fg: var(--info); }
.c-danger { --chip-bg: var(--danger-soft); --chip-fg: var(--danger); }
.c-warn { --chip-bg: var(--warn-soft); --chip-fg: var(--warn); }
.c-neutral { --chip-bg: var(--surface-sunken); --chip-fg: var(--ink-2); }

.dot.c-accent, .dot.c-info, .dot.c-danger, .dot.c-warn, .dot.c-neutral {
  background: var(--chip-fg);
}

.tag-neutral {
  background: var(--surface-sunken);
}

/* ---------- 월 달력 ---------- */
.month {
  overflow: hidden;
}

.weekdays,
.grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}

.weekday {
  padding: var(--space-2);
  border-bottom: 1px solid var(--rule);
  color: var(--ink-3);
  font-size: var(--text-xs);
  font-weight: 600;
  text-align: center;
}

.cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 112px;
  padding: var(--space-1) var(--space-1) var(--space-2);
  border-right: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule);
  min-width: 0;
}

.cell:nth-child(7n) {
  border-right: 0;
}

.cell:nth-last-child(-n + 7) {
  border-bottom: 0;
}

.cell.outside {
  background: var(--surface-sunken);
}

.cell.outside .date {
  color: var(--ink-4);
}

.sun .date,
.weekday.sun {
  color: var(--danger);
}

.sat .date,
.weekday.sat {
  color: var(--info);
}

.cell-head {
  display: flex;
}

.date {
  min-width: 26px;
  height: 26px;
  padding: 0 4px;
  border: 0;
  border-radius: 13px;
  background: none;
  color: var(--ink);
  font: inherit;
  font-size: var(--text-sm);
  line-height: 26px;
  text-align: center;
}

button.date {
  cursor: pointer;
}

button.date:hover {
  background: var(--surface-hover);
}

.today .date {
  background: var(--accent);
  color: var(--paper);
  font-weight: 600;
}

.chips {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.chip {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  height: 22px;
  padding: 0 6px;
  border: 0;
  border-radius: var(--radius-sm);
  background: var(--chip-bg);
  color: var(--chip-fg);
  font: inherit;
  font-size: var(--text-xs);
  text-align: left;
  cursor: pointer;
}

.chip:hover {
  filter: brightness(0.96);
}

.chip-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-years {
  flex: none;
  opacity: 0.8;
}

.more {
  align-self: flex-start;
  padding: 0 6px;
  border: 0;
  background: none;
  color: var(--ink-3);
  font: inherit;
  font-size: var(--text-xs);
  cursor: pointer;
}

.more:hover {
  color: var(--ink);
  text-decoration: underline;
}

/* ---------- 목록 ---------- */
.list {
  padding: var(--space-2) var(--space-5) var(--space-4);
}

.day-group + .day-group {
  border-top: 1px solid var(--rule);
}

.day-title {
  margin: var(--space-4) 0 var(--space-2);
  color: var(--ink-2);
  font-size: var(--text-sm);
}

.day-title.today {
  color: var(--accent);
}

.rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0 0 var(--space-3);
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  color: var(--ink);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.row:hover {
  background: var(--surface-hover);
}

.row-title {
  font-weight: 600;
}

.row-meta {
  color: var(--ink-3);
  font-size: var(--text-sm);
}

.row-memo {
  flex-basis: 100%;
  font-size: var(--text-sm);
}

@media (max-width: 720px) {
  .cal-toolbar {
    grid-template-columns: 1fr auto;
  }

  .month-title {
    grid-column: 1 / -1;
    grid-row: 1;
  }

  .cell {
    min-height: 72px;
  }

  .chip-years {
    display: none;
  }
}
</style>
