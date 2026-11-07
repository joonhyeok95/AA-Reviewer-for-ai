<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Code } from '@/code/types'
import RowTools from '@/global/components/RowTools.vue'
import { moveItem, removeItem } from '@/global/util/list'
import { flattenUnits, unitLabel } from '@/catechism/labels'
import type { Qa, UnitNode, UnitSaveRequest } from '@/catechism/types'

/**
 * 교리문답 단위 편집 (여러 단계 + 추가)
 *   단위(상위·유형·번호·제목)
 *     └ 문답 n개 [+ 문답]      : 문 번호, 질문(인도자)
 *         └ 답 단락 n개 [+ 단락] : 첫째, 둘째 …(성도)
 *             └ 근거 성구 n개 [+ 성구]
 */
const form = defineModel<UnitSaveRequest>({ required: true })
const props = defineProps<{
  types: Code[]
  /** 같은 교리문답의 단위 트리 (상위 선택) */
  units: UnitNode[]
  /** 상위로 고를 수 없는 단위 (자기 자신·하위) */
  excludedParents: number[]
  errors: Record<string, string>
}>()
const { t } = useI18n()

const typeName = (cd: string): string => props.types.find((type) => type.code === cd)?.codeNm ?? cd
const parentOptions = computed(() => flattenUnits(props.units)
  .filter(({ unit }) => !props.excludedParents.includes(unit.unitId)))

function addQa(): void {
  const last = form.value.qas.at(-1)?.questionNo
  form.value.qas.push({ questionNo: last ? last + 1 : null, question: '',
    answers: [{ content: '', proofs: [] }] })
}

function addAnswer(qa: Qa): void {
  qa.answers.push({ content: '', proofs: [] })
}

function err(path: string): string | undefined {
  return props.errors[path]
}
</script>

<template>
  <div class="unit-editor">
    <div class="grid-3">
      <div class="field">
        <label class="field-label" for="unit-parent">{{ $t('catechism.parent') }}</label>
        <select id="unit-parent" v-model="form.parentUnitId" class="select" data-test="unit-parent">
          <option :value="null">{{ $t('catechism.topLevel') }}</option>
          <option v-for="{ unit, depth } in parentOptions" :key="unit.unitId" :value="unit.unitId">
            {{ '  '.repeat(depth) }}{{ unitLabel(unit, typeName, t) }}</option>
        </select>
      </div>
      <div class="field">
        <label class="field-label" for="unit-type">{{ $t('catechism.unitType') }}<span class="req">*</span></label>
        <select id="unit-type" v-model="form.unitTypeCd" class="select" data-test="unit-type">
          <option v-for="type in types" :key="type.code" :value="type.code">{{ type.codeNm }}</option>
        </select>
      </div>
      <div class="field">
        <label class="field-label" for="unit-no">{{ $t('catechism.unitNoLabel') }}</label>
        <input id="unit-no" v-model.number="form.unitNo" class="input" type="number" min="1"
          data-test="unit-no">
      </div>
    </div>
    <div class="grid-2">
      <div class="field">
        <label class="field-label" for="unit-title">{{ $t('catechism.unitTitle') }}</label>
        <input id="unit-title" v-model="form.title" class="input" maxlength="200" data-test="unit-title">
      </div>
      <div class="field">
        <label class="field-label" for="unit-sort">{{ $t('admin.sortOrder') }}</label>
        <input id="unit-sort" v-model.number="form.sortOrder" class="input" type="number">
      </div>
    </div>

    <div class="section-bar">
      <h3 class="section-title">{{ $t('catechism.qas') }} <span class="muted num">{{ form.qas.length }}</span></h3>
      <button type="button" class="btn btn-sm" data-test="qa-add" @click="addQa">+ {{ $t('catechism.addQa') }}</button>
    </div>
    <p v-if="!form.qas.length" class="field-hint">{{ $t('catechism.qaHint') }}</p>

    <ol class="qa-list">
      <li v-for="(qa, qi) in form.qas" :key="qi" class="qa-card" data-test="qa-card">
        <div class="qa-head">
          <label class="no">
            <span>{{ $t('catechism.questionNo') }}</span>
            <input v-model.number="qa.questionNo" class="input no-input" type="number" min="1"
              :aria-invalid="!!err(`qas[${qi}].questionNo`)" data-test="qa-no">
          </label>
          <RowTools :index="qi" :length="form.qas.length" :label="$t('catechism.qa')"
            @up="moveItem(form.qas, qi, -1)" @down="moveItem(form.qas, qi, 1)"
            @remove="removeItem(form.qas, qi)" />
        </div>
        <div class="field">
          <label class="field-label">{{ $t('catechism.question') }} ({{ $t('catechism.leader') }})</label>
          <textarea v-model="qa.question" class="textarea short" maxlength="2000"
            :aria-invalid="!!err(`qas[${qi}].question`)" data-test="qa-question" />
          <p v-if="err(`qas[${qi}].question`)" class="field-error">{{ err(`qas[${qi}].question`) }}</p>
        </div>

        <div class="answers">
          <div v-for="(answer, ai) in qa.answers" :key="ai" class="answer" data-test="answer">
            <div class="answer-head">
              <span class="field-label">{{ $t('catechism.answer') }} {{ ai + 1 }} ({{ $t('catechism.people') }})</span>
              <RowTools :index="ai" :length="qa.answers.length" :label="$t('catechism.answer')"
                @up="moveItem(qa.answers, ai, -1)" @down="moveItem(qa.answers, ai, 1)"
                @remove="removeItem(qa.answers, ai)" />
            </div>
            <textarea v-model="answer.content" class="textarea" maxlength="4000"
              :aria-invalid="!!err(`qas[${qi}].answers[${ai}].content`)" data-test="answer-content" />
            <div class="proofs">
              <div v-for="(proof, pi) in answer.proofs" :key="pi" class="proof" data-test="proof">
                <input v-model="proof.bibleRef" class="input ref" maxlength="100"
                  :placeholder="$t('catechism.bibleRef')" :aria-label="$t('catechism.bibleRef')"
                  data-test="proof-ref">
                <input v-model="proof.bibleText" class="input" maxlength="2000"
                  :placeholder="$t('catechism.bibleText')" :aria-label="$t('catechism.bibleText')">
                <RowTools :index="pi" :length="answer.proofs.length" :label="$t('catechism.proof')"
                  @up="moveItem(answer.proofs, pi, -1)" @down="moveItem(answer.proofs, pi, 1)"
                  @remove="removeItem(answer.proofs, pi)" />
              </div>
              <button type="button" class="btn btn-sm btn-quiet" data-test="proof-add"
                @click="answer.proofs.push({ bibleRef: '', bibleText: '' })">+ {{ $t('catechism.addProof') }}</button>
            </div>
          </div>
          <button type="button" class="btn btn-sm" data-test="answer-add" @click="addAnswer(qa)">
            + {{ $t('catechism.addAnswer') }}</button>
        </div>
      </li>
    </ol>
    <button v-if="form.qas.length" type="button" class="btn btn-sm" @click="addQa">
      + {{ $t('catechism.addQa') }}</button>
  </div>
</template>

<style scoped>
.unit-editor {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.section-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: var(--space-3);
  border-top: 1px solid var(--rule);
}

.section-bar .section-title {
  margin: 0;
}

.qa-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.qa-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--rule);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.qa-head,
.answer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.no {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: 600;
}

.no-input {
  width: 90px;
}

.textarea.short {
  min-height: 56px;
}

.answers {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding-left: var(--space-4);
  border-left: 2px solid var(--accent-soft);
}

.answer {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.proofs {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
  padding-left: var(--space-4);
}

.proof {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
}

.proof .ref {
  flex: 0 0 160px;
}

.unit-editor > .btn,
.answers > .btn {
  align-self: flex-start;
}
</style>
