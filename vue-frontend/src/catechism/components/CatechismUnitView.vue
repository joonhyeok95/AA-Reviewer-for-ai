<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCodeStore } from '@/code/store'
import { qaRange, unitLabel } from '@/catechism/labels'
import type { UnitDetail } from '@/catechism/types'

/**
 * 교리문답 단위 보기 (주보·교리문답 화면 공용).
 *  예) 하이델베르크 요리문답 · 제45주일 (116~119문) 우리의 구속에 관하여
 *      제116문  인도자 : 질문 / 성도 : 답 단락… (근거 성구)
 */
const props = defineProps<{ unit: UnitDetail; showCatechism?: boolean }>()
const { t } = useI18n()
const code = useCodeStore()
void code.getCodes('CATECHISM_UNIT_TYPE')

const typeName = (cd: string): string => code.codeName('CATECHISM_UNIT_TYPE', cd)
const heading = computed(() => {
  const label = unitLabel(props.unit, typeName, t, false)
  const range = qaRange(props.unit.qas, t)
  return range ? `${label} (${range})` : label
})
</script>

<template>
  <article class="unit" data-test="catechism-unit">
    <header class="unit-head">
      <p v-if="showCatechism" class="catechism-name">{{ unit.catechismTitle }}</p>
      <h3 class="unit-title">{{ heading }}</h3>
      <p v-if="unit.title" class="unit-sub">{{ unit.title }}</p>
    </header>
    <section v-for="qa in unit.qas" :key="qa.questionNo ?? qa.question" class="qa">
      <h4 class="qa-no"><mark>{{ $t('catechism.qaOne', { no: qa.questionNo }) }}</mark></h4>
      <p class="line leader"><span class="who">{{ $t('catechism.leader') }}</span>{{ qa.question }}</p>
      <div class="line people">
        <span class="who">{{ $t('catechism.people') }}</span>
        <div class="answers">
          <div v-for="(answer, i) in qa.answers" :key="i" class="answer">
            <p>{{ answer.content }}</p>
            <p v-if="answer.proofs.length" class="proofs">
              <span v-for="(proof, j) in answer.proofs" :key="j" :title="proof.bibleText ?? ''">
                {{ proof.bibleRef }}</span>
            </p>
          </div>
        </div>
      </div>
    </section>
    <p v-if="!unit.qas.length" class="muted">{{ $t('catechism.noQa') }}</p>
  </article>
</template>

<style scoped>
.unit-head {
  margin-bottom: var(--space-3);
  text-align: center;
}

.catechism-name {
  margin: 0;
  color: var(--ink-3);
  font-size: var(--text-xs);
}

.unit-title {
  margin: 0;
  font-size: var(--text-md);
}

.unit-sub {
  margin: 2px 0 0;
  color: var(--ink-2);
  font-size: var(--text-sm);
}

.qa + .qa {
  margin-top: var(--space-4);
}

.qa-no {
  margin: 0 0 var(--space-1);
  font-size: var(--text-sm);
}

.qa-no mark {
  padding: 0 4px;
  background: var(--warn-soft);
  color: var(--ink);
}

.line {
  display: flex;
  gap: var(--space-2);
  margin: 0 0 var(--space-1);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.leader {
  color: var(--ink-2);
}

.who {
  flex: none;
  min-width: 3.5em;
  color: var(--ink-3);
  font-weight: 600;
}

.answers p {
  margin: 0;
  white-space: pre-line;
}

.answer + .answer {
  margin-top: var(--space-1);
}

.proofs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  color: var(--accent);
  font-size: var(--text-xs);
}
</style>
