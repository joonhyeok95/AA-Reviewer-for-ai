<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import type { Code } from '@/code/types'
import { ApiError } from '@/global/api/ApiError'
import ConfirmDialog from '@/global/components/ConfirmDialog.vue'
import EmptyState from '@/global/components/EmptyState.vue'
import UiDialog from '@/global/components/UiDialog.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import {
  createCatechism, createUnit, deleteCatechism, deleteUnit, fetchCatechisms, fetchUnit,
  updateCatechism, updateUnit,
} from '@/catechism/api'
import CatechismUnitEditor from '@/catechism/components/CatechismUnitEditor.vue'
import CatechismUnitView from '@/catechism/components/CatechismUnitView.vue'
import { flattenUnits, selfAndDescendants, unitLabel } from '@/catechism/labels'
import type {
  Catechism, CatechismSaveRequest, UnitDetail, UnitNode, UnitSaveRequest,
} from '@/catechism/types'

/**
 * 교리문답 - 왼쪽 : 교리문답별 단위 트리 (부 → 주일), 오른쪽 : 선택한 단위의 문답.
 * 조회 권한만 있으면 보기, catechism.add/edit/del 이 있으면 단위·문답 편집.
 */
const auth = useAuthStore()
const code = useCodeStore()
const feedback = useApiFeedback()
const { t } = useI18n()

const catechisms = ref<Catechism[]>([])
const types = ref<Code[]>([])
const selected = ref<{ catechismId: number; unitId: number | null } | null>(null)
const detail = ref<UnitDetail | null>(null)
const editing = ref(false)
const form = ref<UnitSaveRequest | null>(null)
const errors = ref<Record<string, string>>({})
const saving = ref(false)
const loading = ref(false)
const confirm = ref<{ kind: 'unit' | 'catechism'; id: number; name: string } | null>(null)
const busy = ref(false)
const catDialog = reactive<{ open: boolean; target: Catechism | null; form: CatechismSaveRequest }>({
  open: false, target: null,
  form: { catechismCd: '', title: '', description: '', sortOrder: 0, useYn: 'Y' },
})

const canAdd = computed(() => auth.can('catechism', 'add'))
const canEdit = computed(() => auth.can('catechism', 'edit'))
const canDel = computed(() => auth.can('catechism', 'del'))
const current = computed(() => catechisms.value.find((c) => c.catechismId === selected.value?.catechismId))
const typeName = (cd: string): string => types.value.find((type) => type.code === cd)?.codeNm ?? cd
const excludedParents = computed(() => {
  if (!current.value || !selected.value?.unitId) return []
  const node = flattenUnits(current.value.units).find(({ unit }) => unit.unitId === selected.value?.unitId)
  return node ? selfAndDescendants(node.unit) : []
})

function rangeOf(unit: UnitNode): string {
  if (!unit.qaCount) return ''
  return unit.firstQuestionNo === unit.lastQuestionNo
    ? t('catechism.qaOne', { no: unit.firstQuestionNo })
    : t('catechism.qaRange', { from: unit.firstQuestionNo, to: unit.lastQuestionNo })
}

async function load(): Promise<void> {
  try {
    const [list, typeCodes] = await Promise.all([fetchCatechisms(), code.getCodes('CATECHISM_UNIT_TYPE')])
    catechisms.value = list
    types.value = typeCodes
    if (!selected.value && list.length) {
      const firstUnit = flattenUnits(list[0].units).find(({ unit }) => unit.qaCount > 0)?.unit
      await select(list[0].catechismId, firstUnit?.unitId ?? null)
    }
  } catch (error) {
    feedback.failure(error)
  }
}

async function select(catechismId: number, unitId: number | null): Promise<void> {
  selected.value = { catechismId, unitId }
  editing.value = false
  errors.value = {}
  detail.value = null
  if (unitId === null) return
  loading.value = true
  try {
    detail.value = await fetchUnit(unitId)
  } catch (error) {
    feedback.failure(error)
  } finally {
    loading.value = false
  }
}

function startEdit(): void {
  if (!detail.value) return
  form.value = {
    parentUnitId: detail.value.parentUnitId, unitTypeCd: detail.value.unitTypeCd,
    unitNo: detail.value.unitNo, title: detail.value.title ?? '', sortOrder: detail.value.sortOrder,
    qas: detail.value.qas.map((qa) => ({
      questionNo: qa.questionNo, question: qa.question,
      answers: qa.answers.map((a) => ({ content: a.content,
        proofs: a.proofs.map((p) => ({ bibleRef: p.bibleRef, bibleText: p.bibleText ?? '' })) })),
    })),
  }
  editing.value = true
}

/** 새 단위 : 선택한 단위를 상위로 (주일이면 같은 부 아래), 번호는 형제 다음 */
function startCreate(catechismId: number, parent: UnitNode | null): void {
  const catechism = catechisms.value.find((c) => c.catechismId === catechismId)
  const siblings = parent ? parent.children : catechism?.units ?? []
  const lastNo = Math.max(0, ...siblings.map((unit) => unit.unitNo ?? 0))
  selected.value = { catechismId, unitId: null }
  detail.value = null
  errors.value = {}
  form.value = {
    parentUnitId: parent?.unitId ?? null,
    unitTypeCd: parent ? 'LORDS_DAY' : 'PART',
    unitNo: lastNo + 1, title: '', sortOrder: lastNo + 1, qas: [],
  }
  editing.value = true
}

async function save(): Promise<void> {
  if (!form.value || !selected.value) return
  errors.value = {}
  saving.value = true
  try {
    const body: UnitSaveRequest = {
      ...form.value,
      qas: form.value.qas.map((qa) => ({
        ...qa,
        answers: qa.answers.map((answer) => ({
          ...answer, proofs: answer.proofs.filter((proof) => proof.bibleRef.trim()),
        })),
      })),
    }
    const response = selected.value.unitId === null
      ? await createUnit(selected.value.catechismId, body)
      : await updateUnit(selected.value.unitId, body)
    feedback.success(response)
    await reloadTree()
    await select(response.result.catechismId, response.result.unitId)
  } catch (error) {
    if (error instanceof ApiError && error.fieldErrors.length) {
      errors.value = Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.reason]))
    }
    feedback.failure(error)
  } finally {
    saving.value = false
  }
}

async function reloadTree(): Promise<void> {
  catechisms.value = await fetchCatechisms()
}

function openCatechismDialog(target: Catechism | null): void {
  catDialog.target = target
  catDialog.form = target
    ? { catechismCd: target.catechismCd, title: target.title, description: target.description ?? '',
      sortOrder: target.sortOrder, useYn: target.useYn }
    : { catechismCd: '', title: '', description: '', sortOrder: catechisms.value.length + 1, useYn: 'Y' }
  catDialog.open = true
}

async function saveCatechism(): Promise<void> {
  try {
    const response = catDialog.target
      ? await updateCatechism(catDialog.target.catechismId, catDialog.form)
      : await createCatechism(catDialog.form)
    feedback.success(response)
    catDialog.open = false
    await reloadTree()
  } catch (error) {
    feedback.failure(error)
  }
}

async function confirmDelete(): Promise<void> {
  if (!confirm.value) return
  busy.value = true
  try {
    const response = confirm.value.kind === 'unit' ? await deleteUnit(confirm.value.id)
      : await deleteCatechism(confirm.value.id)
    feedback.success(response)
    confirm.value = null
    selected.value = null
    detail.value = null
    editing.value = false
    await reloadTree()
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
        <h1>{{ $t('catechism.title') }}</h1>
        <p class="lede">{{ $t('catechism.lede') }}</p>
      </div>
      <div class="page-actions">
        <button v-if="canAdd" type="button" class="btn" data-test="catechism-add"
          @click="openCatechismDialog(null)">+ {{ $t('catechism.addCatechism') }}</button>
      </div>
    </header>

    <div class="split">
      <aside class="split-list" :aria-label="$t('catechism.tree')">
        <div class="split-list-body tree">
          <section v-for="catechism in catechisms" :key="catechism.catechismId" class="cat">
            <div class="cat-head">
              <strong>{{ catechism.title }}</strong>
              <span v-if="catechism.useYn === 'N'" class="tag tag-warn">{{ $t('common.unused') }}</span>
              <span class="cat-actions">
                <button v-if="canEdit" type="button" class="btn btn-sm btn-quiet"
                  @click="openCatechismDialog(catechism)">{{ $t('common.edit') }}</button>
                <button v-if="canAdd" type="button" class="btn btn-sm btn-quiet" data-test="unit-add-root"
                  @click="startCreate(catechism.catechismId, null)">+ {{ $t('catechism.addPart') }}</button>
              </span>
            </div>
            <ul class="nodes">
              <template v-for="{ unit, depth } in flattenUnits(catechism.units)" :key="unit.unitId">
                <li class="node" :style="{ paddingLeft: `${depth * 16}px` }">
                  <button type="button" class="pick" :aria-current="selected?.unitId === unit.unitId"
                    :data-test="`unit-${unit.unitId}`" @click="select(catechism.catechismId, unit.unitId)">
                    <span class="pick-main">
                      <span>{{ unitLabel(unit, typeName, t, false) }}</span>
                      <span v-if="unit.title" class="pick-sub">{{ unit.title }}</span>
                    </span>
                    <span v-if="unit.qaCount" class="pick-count">{{ rangeOf(unit) }}</span>
                  </button>
                  <button v-if="canAdd && depth === 0" type="button" class="add-child"
                    :title="$t('catechism.addChild')" :aria-label="$t('catechism.addChild')"
                    data-test="unit-add-child" @click="startCreate(catechism.catechismId, unit)">+</button>
                </li>
              </template>
            </ul>
          </section>
          <EmptyState v-if="!catechisms.length" :title="$t('catechism.empty')" description="" />
        </div>
      </aside>

      <section class="panel panel-pad" :aria-busy="loading">
        <template v-if="editing && form">
          <header class="detail-head">
            <h2>{{ selected?.unitId ? $t('catechism.editUnit') : $t('catechism.newUnit') }}</h2>
            <div class="page-actions">
              <button type="button" class="btn" @click="selected && select(selected.catechismId, selected.unitId)">
                {{ $t('common.cancel') }}</button>
              <button type="button" class="btn btn-primary" :disabled="saving" data-test="unit-save"
                @click="save">{{ $t('common.save') }}</button>
            </div>
          </header>
          <CatechismUnitEditor v-model="form" :types="types" :units="current?.units ?? []"
            :excluded-parents="excludedParents" :errors="errors" />
          <div class="form-actions">
            <button type="button" class="btn btn-primary" :disabled="saving" @click="save">{{ $t('common.save') }}</button>
          </div>
        </template>
        <template v-else-if="detail">
          <header class="detail-head">
            <p class="crumbs">{{ detail.catechismTitle }}
              <template v-for="item in detail.path.slice(0, -1)" :key="item.unitId">
                › {{ unitLabel(item, typeName, t) }}</template>
            </p>
            <div class="page-actions">
              <button v-if="canEdit" type="button" class="btn btn-sm" data-test="unit-edit" @click="startEdit">
                {{ $t('common.edit') }}</button>
              <button v-if="canDel" type="button" class="btn btn-sm btn-danger" data-test="unit-delete"
                @click="confirm = { kind: 'unit', id: detail.unitId, name: unitLabel(detail, typeName, t) }">
                {{ $t('common.delete') }}</button>
            </div>
          </header>
          <CatechismUnitView :unit="detail" />
        </template>
        <template v-else-if="current && selected && !selected.unitId">
          <header class="detail-head">
            <h2>{{ current.title }}</h2>
            <div class="page-actions">
              <button v-if="canDel" type="button" class="btn btn-sm btn-danger"
                @click="confirm = { kind: 'catechism', id: current.catechismId, name: current.title }">
                {{ $t('common.delete') }}</button>
            </div>
          </header>
          <p class="muted">{{ current.description }}</p>
        </template>
        <EmptyState v-else :title="$t('catechism.pick')" description="" />
      </section>
    </div>

    <UiDialog :open="catDialog.open" :title="catDialog.target ? $t('catechism.editCatechism') : $t('catechism.addCatechism')"
      @close="catDialog.open = false">
      <form id="catechism-form" class="form-stack" @submit.prevent="saveCatechism">
        <div class="grid-2">
          <div class="field">
            <label class="field-label" for="cat-cd">{{ $t('catechism.code') }}<span class="req">*</span></label>
            <input id="cat-cd" v-model="catDialog.form.catechismCd" class="input mono" maxlength="30"
              :readonly="!!catDialog.target" placeholder="HEIDELBERG">
          </div>
          <div class="field">
            <label class="field-label" for="cat-sort">{{ $t('admin.sortOrder') }}</label>
            <input id="cat-sort" v-model.number="catDialog.form.sortOrder" class="input" type="number">
          </div>
        </div>
        <div class="field">
          <label class="field-label" for="cat-title">{{ $t('catechism.name') }}<span class="req">*</span></label>
          <input id="cat-title" v-model="catDialog.form.title" class="input" maxlength="200">
        </div>
        <div class="field">
          <label class="field-label" for="cat-desc">{{ $t('catechism.description') }}</label>
          <textarea id="cat-desc" v-model="catDialog.form.description" class="textarea" maxlength="1000" />
        </div>
        <label class="check"><input v-model="catDialog.form.useYn" type="checkbox" true-value="Y" false-value="N">
          {{ $t('common.use') }}</label>
      </form>
      <template #footer>
        <button type="button" class="btn" @click="catDialog.open = false">{{ $t('common.cancel') }}</button>
        <button type="submit" form="catechism-form" class="btn btn-primary">{{ $t('common.save') }}</button>
      </template>
    </UiDialog>

    <ConfirmDialog :open="confirm !== null" :title="$t('common.delete')"
      :message="t('catechism.deleteConfirm', { name: confirm?.name ?? '' })"
      :confirm-label="$t('common.delete')" danger :busy="busy"
      @close="confirm = null" @confirm="confirmDelete" />
  </div>
</template>

<style scoped>
.tree {
  padding: var(--space-2) 0;
}

.cat + .cat {
  margin-top: var(--space-3);
  border-top: 1px solid var(--rule);
}

.cat-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
}

.cat-actions {
  display: inline-flex;
  margin-left: auto;
}

.nodes {
  margin: 0;
  padding: 0;
  list-style: none;
}

.node {
  display: flex;
  align-items: center;
}

.node .pick {
  flex: 1;
  min-width: 0;
}

.add-child {
  flex: none;
  width: 26px;
  height: 26px;
  margin-right: var(--space-2);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--ink-3);
  cursor: pointer;
}

.add-child:hover {
  color: var(--accent);
}

.crumbs {
  margin: 0;
  color: var(--ink-3);
  font-size: var(--text-sm);
}
</style>
