<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import { ApiError } from '@/global/api/ApiError'
import EmptyState from '@/global/components/EmptyState.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { fetchOccurrences } from '@/calendar/api'
import { parseIso } from '@/calendar/dates'
import type { CalendarOccurrence } from '@/calendar/types'
import { qaRange, unitLabel } from '@/catechism/labels'
import { bulletinPhotoUrl, fetchCurrentBulletin } from '@/bulletin/api'
import { toGrid, weekOf } from '@/bulletin/form'
import type { BulletinDetail, NewsNode } from '@/bulletin/types'

/**
 * 교회 대시보드 - 이번 주 주보 요약.
 *  예배 · 예배순서 · 이번 주 축하(달력) · 섬김위원(이번 주) · 다가오는 일정 · 교회소식 · 교리문답
 */
const auth = useAuthStore()
const code = useCodeStore()
const feedback = useApiFeedback()
const { t, locale } = useI18n()

const bulletin = ref<BulletinDetail | null>(null)
const celebrations = ref<CalendarOccurrence[]>([])
const missing = ref(false)
const loading = ref(false)

const canAdd = computed(() => auth.can('bulletin', 'add'))
const canCalendar = computed(() => auth.can('calendar', 'list'))
const churchName = computed(() => code.codeName('CHURCH_INFO', 'NAME'))
const servedBy = computed(() => code.codeName('CHURCH_INFO', 'SERVED_BY'))
const dateText = (iso: string, withWeekday = false): string => new Intl.DateTimeFormat(locale.value,
  { month: 'long', day: 'numeric', ...(withWeekday ? { weekday: 'short' } : { year: 'numeric' }) })
  .format(parseIso(iso))

const dutyThisWeek = computed(() => {
  if (!bulletin.value) return []
  const grid = toGrid(bulletin.value.duties)
  return grid.roles.map((role) => ({ roleNm: role.roleNm, members: role.members[bulletin.value!.worshipDt] ?? '' }))
    .filter((role) => role.members)
})
/** 일정이 적힌 소식 항목 (행사 및 일정) */
const schedules = computed(() => {
  const collect = (nodes: NewsNode[]): NewsNode[] =>
    nodes.flatMap((node) => [...(node.scheduleText ? [node] : []), ...collect(node.children)])
  return collect(bulletin.value?.news ?? [])
})
const typeName = (cd: string): string => code.codeName('CATECHISM_UNIT_TYPE', cd)

async function load(): Promise<void> {
  loading.value = true
  missing.value = false
  try {
    await Promise.all([code.getCodes('CHURCH_INFO'), code.getCodes('CATECHISM_UNIT_TYPE'),
      code.getCodes('CAL_EVENT_TYPE').catch(() => [])])
    bulletin.value = await fetchCurrentBulletin()
    if (canCalendar.value) {
      const { from, to } = weekOf(bulletin.value.worshipDt)
      celebrations.value = (await fetchOccurrences(from, to))
        .filter((item) => ['BIRTHDAY', 'WEDDING'].includes(item.eventTypeCd))
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      missing.value = true
      bulletin.value = null
    } else {
      feedback.failure(error)
    }
  } finally {
    loading.value = false
  }
}

watch(() => auth.user?.langCd, () => {
  code.clearCodes()
  void load()
})
onMounted(load)
</script>

<template>
  <div class="page wide" :aria-busy="loading">
    <header class="page-head">
      <div>
        <h1>{{ $t('bulletin.dashboard.title') }}</h1>
        <p class="lede">{{ churchName }} · {{ servedBy }}</p>
      </div>
      <div class="page-actions">
        <RouterLink class="btn" :to="{ name: 'bulletin-list' }">{{ $t('bulletin.dashboard.all') }}</RouterLink>
        <RouterLink v-if="canAdd" class="btn btn-primary" :to="{ name: 'bulletin-new' }">+ {{ $t('bulletin.create') }}</RouterLink>
      </div>
    </header>

    <EmptyState v-if="missing" :title="$t('bulletin.dashboard.none')"
      :description="canAdd ? $t('bulletin.dashboard.noneHint') : ''" />

    <template v-if="bulletin">
      <!-- 이번 주 예배 -->
      <section class="hero panel panel-pad" data-test="dashboard-hero">
        <img v-if="bulletin.hasPhoto" class="hero-photo" :src="bulletinPhotoUrl(bulletin)"
          :alt="$t('bulletin.photo')" data-test="dashboard-photo">
        <div>
          <p class="hero-date num">{{ dateText(bulletin.worshipDt) }}
            <span v-if="bulletin.volumeNo || bulletin.issueNo" class="muted">
              {{ $t('bulletin.issue', { volume: bulletin.volumeNo ?? '-', issue: bulletin.issueNo ?? '-' }) }}</span>
            <span v-if="bulletin.title" class="tag tag-info">{{ bulletin.title }}</span></p>
          <h2 class="hero-title">{{ bulletin.sermonTitle || bulletin.sermonScripture || $t('bulletin.dashboard.worship') }}</h2>
          <p class="muted">
            <span v-if="bulletin.sermonTitle && bulletin.sermonScripture">{{ bulletin.sermonScripture }} · </span>
            {{ bulletin.preacherNm }}</p>
        </div>
        <blockquote v-if="bulletin.themeVerse" class="hero-verse">
          {{ bulletin.themeVerse }} <cite v-if="bulletin.themeVerseRef">({{ bulletin.themeVerseRef }})</cite>
        </blockquote>
        <RouterLink class="btn btn-primary hero-link" data-test="dashboard-open"
          :to="{ name: 'bulletin-detail', params: { id: bulletin.bulletinId } }">{{ $t('bulletin.dashboard.open') }}</RouterLink>
      </section>

      <div class="cards">
        <!-- 예배순서 -->
        <section class="panel panel-pad card">
          <h3 class="card-title">{{ $t('bulletin.orders') }}</h3>
          <ol class="order">
            <li v-for="(order, i) in bulletin.orders" :key="i">
              <span class="order-item">{{ order.itemNm }}</span><span class="muted">{{ order.detail }}</span>
            </li>
          </ol>
        </section>

        <!-- 이번 주 축하 (달력) -->
        <section v-if="canCalendar" class="panel panel-pad card" data-test="dashboard-celebrations">
          <h3 class="card-title">{{ $t('bulletin.dashboard.celebrations') }}</h3>
          <ul v-if="celebrations.length" class="plain">
            <li v-for="item in celebrations" :key="`${item.eventId}-${item.startDt}`">
              <span class="tag" :class="item.eventTypeCd === 'BIRTHDAY' ? 'tag-accent' : 'tag-info'">
                {{ code.codeName('CAL_EVENT_TYPE', item.eventTypeCd) }}</span>
              {{ item.title }} <span class="muted num">{{ dateText(item.startDt, true) }}</span>
            </li>
          </ul>
          <p v-else class="muted">{{ $t('bulletin.dashboard.noCelebrations') }}</p>
          <RouterLink class="more" :to="{ name: 'calendar' }">{{ $t('bulletin.dashboard.calendar') }} &rarr;</RouterLink>
        </section>

        <!-- 섬김위원 -->
        <section class="panel panel-pad card">
          <h3 class="card-title">{{ $t('bulletin.duty.title') }} <span class="muted num">{{ dateText(bulletin.worshipDt, true) }}</span></h3>
          <dl v-if="dutyThisWeek.length" class="duty">
            <template v-for="role in dutyThisWeek" :key="role.roleNm">
              <dt>{{ role.roleNm }}</dt><dd>{{ role.members }}</dd>
            </template>
          </dl>
          <p v-else class="muted">{{ $t('bulletin.dashboard.noDuty') }}</p>
        </section>

        <!-- 다가오는 일정 -->
        <section class="panel panel-pad card">
          <h3 class="card-title">{{ $t('bulletin.dashboard.schedules') }}</h3>
          <ul v-if="schedules.length" class="plain">
            <li v-for="(item, i) in schedules" :key="i" :class="{ hl: item.highlightYn === 'Y' }">
              <strong>{{ item.title }}</strong> <span class="muted">{{ item.scheduleText }}</span>
            </li>
          </ul>
          <p v-else class="muted">{{ $t('bulletin.dashboard.noSchedules') }}</p>
        </section>

        <!-- 교회소식 -->
        <section class="panel panel-pad card wide-card">
          <h3 class="card-title">{{ $t('bulletin.news.title') }}</h3>
          <ol class="news">
            <li v-for="(section, i) in bulletin.news" :key="i">
              <strong class="news-title">{{ section.title }}</strong>
              <span v-if="section.content" class="muted">{{ section.content }}</span>
              <span v-if="section.children.length" class="muted">
                — {{ section.children.map((child) => child.title || child.content).filter(Boolean).join(', ') }}</span>
            </li>
          </ol>
        </section>

        <!-- 이번 주 교리문답 -->
        <section v-for="unit in bulletin.catechismUnits" :key="unit.unitId" class="panel panel-pad card wide-card"
          data-test="dashboard-catechism">
          <h3 class="card-title">{{ unit.catechismTitle }} ·
            {{ unitLabel(unit, typeName, t, false) }} <span class="muted">({{ qaRange(unit.qas, t) }})</span></h3>
          <ul class="plain">
            <li v-for="qa in unit.qas" :key="qa.questionNo ?? qa.question">
              <span class="tag tag-warn">{{ $t('catechism.qaOne', { no: qa.questionNo }) }}</span> {{ qa.question }}
            </li>
          </ul>
          <RouterLink class="more" :to="{ name: 'catechism', query: { unit: unit.unitId } }"
            data-test="dashboard-catechism-link">{{ $t('catechism.title') }} &rarr;</RouterLink>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-5);
  margin-bottom: var(--space-4);
}

.hero-photo {
  grid-column: 1 / -1;
  width: 100%;
  max-height: 320px;
  border-radius: var(--radius-md);
  object-fit: cover;
}

.hero-date {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
}

.hero-title {
  margin: var(--space-1) 0;
  font-size: var(--text-xl);
}

.hero p {
  margin: 0;
}

.hero-verse {
  margin: 0;
  padding-left: var(--space-4);
  border-left: 3px solid var(--accent);
  color: var(--ink-2);
  line-height: 1.7;
}

.hero-verse cite {
  font-style: normal;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-4);
}

.card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.wide-card {
  grid-column: span 2;
}

.card-title {
  margin: 0 0 var(--space-1);
  font-size: var(--text-md);
}

.order,
.news,
.plain {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-sm);
}

.order li {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: 4px;
  border-bottom: 1px dashed var(--rule);
}

.order-item {
  font-weight: 600;
}

.news {
  counter-reset: news;
}

.news li::before {
  margin-right: 6px;
  color: var(--ink-3);
  counter-increment: news;
  content: counter(news) '.';
}

.news-title {
  margin-right: 6px;
}

.duty {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px var(--space-4);
  margin: 0;
  font-size: var(--text-sm);
}

.duty dt {
  color: var(--ink-3);
}

.duty dd {
  margin: 0;
  font-weight: 600;
}

.hl strong {
  color: var(--info);
}

.more {
  margin-top: auto;
  font-size: var(--text-sm);
}

@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .wide-card {
    grid-column: auto;
  }
}
</style>
