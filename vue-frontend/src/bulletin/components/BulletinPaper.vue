<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCodeStore } from '@/code/store'
import CatechismUnitView from '@/catechism/components/CatechismUnitView.vue'
import LiturgyTextView from '@/liturgy/components/LiturgyTextView.vue'
import { parseIso } from '@/calendar/dates'
import { bulletinPhotoUrl } from '@/bulletin/api'
import { toGrid } from '@/bulletin/form'
import type { BulletinDetail } from '@/bulletin/types'

/**
 * 주보 보기 (인쇄 주보 배치) : 표지 · 예배순서 · 말씀메모 · 전례문 / 교회소식 · 섬김위원 · 교리문답
 * 교회 이름·섬기는 이는 공통코드 CHURCH_INFO (공통코드관리에서 수정)
 *
 * print 모드 (보기 화면이 body 에 한 벌 더 둔다) : A4 세로 2장
 *  - 1장 : 표지 · 예배순서 · 말씀메모 · 전례문 / 2장 : 교회소식 · 섬김위원 · 교리문답 (각 장 2단)
 *  - 장마다 내용이 넘치면 글자 크기를 줄여(zoom) 딱 한 장에 맞춘다 - 화면 밖에서 미리 재고, 인쇄 직전(beforeprint)에 다시 잰다
 *  - 인쇄할 때는 앱 화면(#app)을 숨기고 이 사본만 찍는다 (@page A4, 여백 0 - 장 안쪽 여백으로)
 */
const props = withDefaults(defineProps<{ bulletin: BulletinDetail; print?: boolean }>(), { print: false })
const { t, locale } = useI18n()
const code = useCodeStore()
void code.getCodes('CHURCH_INFO')

const churchName = computed(() => code.codeName('CHURCH_INFO', 'NAME'))
const servedBy = computed(() => code.codeName('CHURCH_INFO', 'SERVED_BY'))
const dateText = computed(() => new Intl.DateTimeFormat(locale.value,
  { year: 'numeric', month: 'long', day: 'numeric' }).format(parseIso(props.bulletin.worshipDt)))
const issueText = computed(() => {
  const { volumeNo, issueNo } = props.bulletin
  return volumeNo || issueNo ? t('bulletin.issue', { volume: volumeNo ?? '-', issue: issueNo ?? '-' }) : ''
})
const duties = computed(() => toGrid(props.bulletin.duties))
const shortDate = (iso: string): string => new Intl.DateTimeFormat(locale.value,
  { month: 'numeric', day: 'numeric' }).format(parseIso(iso))
const lines = (text: string | null): string[] => (text ?? '').split('\n').filter(Boolean)
const test = (name: string): string | undefined => (props.print ? undefined : name)

// ------------------------------------------------------------------ 인쇄 (A4 2장 맞춤)
const root = ref<HTMLElement | null>(null)
const MIN_ZOOM = 0.3

/** 장마다 내용이 장 높이에 들어가는 가장 큰 배율 (이분 탐색, 최대 1) */
function fit(): void {
  if (!props.print || !root.value) return
  for (const page of root.value.querySelectorAll<HTMLElement>('.page')) {
    const flow = page.querySelector<HTMLElement>('.page-flow')
    if (!flow) continue
    const style = getComputedStyle(page)
    const room = page.clientHeight - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom)
    const fits = (zoom: number): boolean => {
      flow.style.zoom = String(zoom)
      return flow.getBoundingClientRect().height <= room
    }
    if (fits(1)) continue
    let low = MIN_ZOOM
    let high = 1
    for (let i = 0; i < 12; i++) {
      const mid = (low + high) / 2
      if (fits(mid)) low = mid
      else high = mid
    }
    flow.style.zoom = String(low)
    flow.dataset.zoom = low.toFixed(3)
  }
}

let pageStyle: HTMLStyleElement | null = null
onMounted(async () => {
  if (!props.print) return
  document.documentElement.classList.add('bulletin-printing')
  pageStyle = document.createElement('style')
  pageStyle.textContent = '@page { size: A4 portrait; margin: 0; }'
  document.head.appendChild(pageStyle)
  window.addEventListener('beforeprint', fit)
  await nextTick()
  fit()
  await document.fonts?.ready
  fit()
})
onBeforeUnmount(() => {
  if (!props.print) return
  document.documentElement.classList.remove('bulletin-printing')
  pageStyle?.remove()
  window.removeEventListener('beforeprint', fit)
})
watch(() => [props.bulletin, churchName.value, servedBy.value], async () => {
  await nextTick()
  fit()
}, { deep: true })
</script>

<template>
  <div ref="root" class="paper" :class="{ print }" :data-test="print ? 'bulletin-print' : 'bulletin-paper'"
    :aria-hidden="print ? 'true' : undefined">
    <!-- 1장 : 표지 · 예배순서 · 말씀메모 · 전례문 -->
    <div class="page"><div class="page-flow">
    <!-- 표지 -->
    <section class="sheet cover">
      <p class="issue num">{{ dateText }} <span v-if="issueText">{{ issueText }}</span></p>
      <h2 class="church">{{ churchName }}</h2>
      <p class="served">{{ servedBy }}</p>
      <img v-if="bulletin.hasPhoto" class="cover-photo" :src="bulletinPhotoUrl(bulletin)"
        :alt="$t('bulletin.photo')" :data-test="test('bulletin-photo')" @load="fit">
      <p v-if="bulletin.title" class="special">{{ bulletin.title }}</p>
      <blockquote v-if="bulletin.themeVerse" class="verse">
        <p>{{ bulletin.themeVerse }}</p>
        <cite v-if="bulletin.themeVerseRef">({{ bulletin.themeVerseRef }})</cite>
      </blockquote>
    </section>

    <!-- 예배순서 · 말씀메모 -->
    <section class="sheet">
      <h3 class="badge">{{ $t('bulletin.orders') }}</h3>
      <table class="order-table" :data-test="test('bulletin-orders')">
        <tbody>
          <tr v-for="(order, i) in bulletin.orders" :key="i">
            <th scope="row">{{ order.itemNm }}</th>
            <td>{{ order.detail }}</td>
          </tr>
        </tbody>
      </table>
      <template v-if="bulletin.sermonTitle || bulletin.sermonScripture">
        <p class="sermon"><strong>{{ bulletin.sermonTitle }}</strong>
          <span v-if="bulletin.sermonScripture" class="muted"> {{ bulletin.sermonScripture }}</span>
          <span v-if="bulletin.preacherNm" class="muted"> · {{ bulletin.preacherNm }}</span></p>
      </template>
      <h3 class="badge">{{ $t('bulletin.memo') }}</h3>
      <div class="memo">{{ bulletin.sermonMemo }}</div>
    </section>

    <section v-for="text in bulletin.liturgyTexts" :key="`t-${text.textId}`" class="sheet">
      <LiturgyTextView :text="text" />
    </section>
    </div></div>

    <!-- 2장 : 교회소식 · 섬김위원 · 교리문답 -->
    <div class="page"><div class="page-flow">
    <!-- 교회소식 · 섬김위원 -->
    <section class="sheet">
      <h3 class="badge">{{ $t('bulletin.news.title') }}</h3>
      <ol class="news" :data-test="test('bulletin-news')">
        <li v-for="(section, si) in bulletin.news" :key="si" class="section">
          <p class="section-line"><span class="num">{{ si + 1 }}.</span>
            <strong v-if="section.title" class="section-name">{{ section.title }}</strong>
            <span v-for="(line, li) in lines(section.content)" :key="li" :class="{ hl: section.highlightYn === 'Y' }">
              {{ line }}</span></p>
          <ul v-if="section.children.length" class="items">
            <li v-for="(item, ii) in section.children" :key="ii" :class="{ hl: item.highlightYn === 'Y' }">
              <span v-if="item.title" class="item-title">{{ item.title }}</span>
              <span v-if="item.content" class="item-content">{{ item.content }}</span>
              <span v-if="item.scheduleText" class="schedule">– {{ item.scheduleText }}</span>
              <ul v-if="item.children.length" class="subs">
                <li v-for="(sub, ci) in item.children" :key="ci" :class="{ hl: sub.highlightYn === 'Y' }">
                  <span v-if="sub.title" class="item-title">{{ sub.title }}</span>{{ sub.content }}
                  <span v-if="sub.scheduleText" class="schedule">– {{ sub.scheduleText }}</span>
                </li>
              </ul>
            </li>
          </ul>
        </li>
      </ol>
      <template v-if="duties.roles.length">
        <h4 class="sub-badge">{{ $t('bulletin.duty.title') }}</h4>
        <table class="duty-table" :data-test="test('bulletin-duties')">
          <thead>
            <tr>
              <th />
              <th v-for="day in duties.dates" :key="day" class="num" :class="{ current: day === bulletin.worshipDt }">
                {{ shortDate(day) }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="role in duties.roles" :key="role.roleNm">
              <th scope="row">{{ role.roleNm }}</th>
              <td v-for="day in duties.dates" :key="day" :class="{ current: day === bulletin.worshipDt }">
                {{ role.members[day] }}</td>
            </tr>
          </tbody>
        </table>
      </template>
    </section>

    <!-- 교리문답 -->
    <section v-for="unit in bulletin.catechismUnits" :key="`c-${unit.unitId}`" class="sheet">
      <CatechismUnitView :unit="unit" show-catechism />
    </section>
    </div></div>
  </div>
</template>

<style scoped>
.paper {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-4);
}

/* 화면 : 장 구분 없이 카드 격자 */
.paper:not(.print) .page,
.paper:not(.print) .page-flow {
  display: contents;
}

/* ---------------------------------------------------------- 인쇄 사본 (A4 세로 2장, 장마다 2단) */
.paper.print {
  display: block;
  position: fixed;
  top: 0;
  left: -10000px;
  width: 210mm;
  color: #000;
  pointer-events: none;
  print-color-adjust: exact;
  -webkit-print-color-adjust: exact;
}

.print .page {
  box-sizing: border-box;
  width: 210mm;
  /* 297mm 꽉 채우면 반올림으로 빈 3장이 생길 수 있어 조금 덜 */
  height: 296mm;
  padding: 10mm 11mm;
  overflow: hidden;
  background: #fff;
}

.print .page-flow {
  column-count: 2;
  column-gap: 7mm;
  column-rule: 1px solid var(--rule);
}

.print .sheet {
  margin: 0 0 4mm;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: none;
}

.print .cover {
  column-span: all;
  margin-bottom: 5mm;
  padding-bottom: 4mm;
  border-bottom: 1px solid var(--rule);
}

.print .cover-photo {
  max-height: 70mm;
}

.print .order-table tr,
.print .duty-table tr,
.print .news > li,
.print .verse {
  break-inside: avoid;
}

.print .badge {
  break-after: avoid;
}

@media print {
  .paper.print {
    position: static;
  }

  .print .page {
    break-after: page;
  }

  .print .page:last-child {
    break-after: auto;
  }
}

.sheet {
  padding: var(--space-5);
  border: 1px solid var(--rule);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.cover-photo {
  display: block;
  width: 100%;
  max-height: 420px;
  margin: var(--space-3) 0;
  border-radius: var(--radius-md);
  object-fit: cover;
}

.cover {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.issue {
  align-self: stretch;
  margin: 0 0 var(--space-5);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--rule);
  font-size: var(--text-sm);
  font-weight: 600;
  text-align: right;
}

.church {
  margin: 0;
  font-size: var(--text-2xl);
  letter-spacing: 0.3em;
}

.served {
  margin: var(--space-1) 0 0;
  color: var(--ink-2);
}

.special {
  margin: var(--space-3) 0 0;
  color: var(--accent);
  font-weight: 600;
}

.verse {
  margin: var(--space-8) 0 0;
  line-height: 2;
  white-space: pre-line;
}

.verse p {
  margin: 0;
}

.verse cite {
  font-style: normal;
}

.badge {
  display: inline-block;
  margin: 0 0 var(--space-3);
  padding: 4px 14px;
  border-radius: 999px;
  background: var(--ink-2);
  color: var(--paper);
  font-size: var(--text-sm);
}

.sub-badge {
  margin: var(--space-4) 0 var(--space-2);
  font-size: var(--text-sm);
}

.order-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.order-table th,
.order-table td {
  padding: 6px 10px;
  border-bottom: 1px solid var(--rule);
  text-align: left;
}

.order-table th {
  width: 40%;
  border-right: 1px solid var(--rule-strong);
  font-weight: 600;
  text-align: center;
}

.order-table tr:nth-child(odd) {
  background: var(--surface-sunken);
}

.sermon {
  margin: var(--space-3) 0;
  font-size: var(--text-sm);
}

.memo {
  min-height: 120px;
  padding: var(--space-3);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  white-space: pre-line;
}

.news {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-sm);
  line-height: 1.7;
}

.section-line {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
  margin: 0;
}

.section-name {
  background: var(--surface-sunken);
}

.items,
.subs {
  margin: 0;
  padding-left: 1.6em;
  list-style: none;
}

.items > li::before,
.subs > li::before {
  margin-right: 4px;
  color: var(--ink-3);
  content: '·';
}

.item-title {
  margin-right: 6px;
  font-weight: 600;
}

.item-content {
  white-space: pre-line;
}

.schedule {
  margin-left: 6px;
  color: var(--ink-3);
}

.hl,
.hl .schedule {
  color: var(--info);
  font-weight: 600;
}

.duty-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-xs);
  text-align: center;
}

.duty-table th,
.duty-table td {
  padding: 4px;
  border: 1px solid var(--rule-strong);
}

.duty-table .current {
  background: var(--accent-soft);
}
</style>
