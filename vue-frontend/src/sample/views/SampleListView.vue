<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/auth/store'
import type { PageResponse } from '@/global/api/types'
import ConfirmDialog from '@/global/components/ConfirmDialog.vue'
import EmptyState from '@/global/components/EmptyState.vue'
import UiPagination from '@/global/components/UiPagination.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { formatDateTime } from '@/global/util/format'
import { deleteSample, fetchSamples } from '@/sample/api'
import SampleFormDialog from '@/sample/components/SampleFormDialog.vue'
import type { Sample, SampleSearch } from '@/sample/types'

/** 거래 목록 (샘플 API : 목록/등록/수정/삭제, 권한 sample.*) */
type SortKey = 'id' | 'name' | 'updatedAt'

const auth = useAuthStore()
const feedback = useApiFeedback()
const { t } = useI18n()

const search = reactive<SampleSearch>({ name: '', useYn: '' })
const page = ref(0)
const sort = reactive<{ key: SortKey; dir: 'asc' | 'desc' }>({ key: 'id', dir: 'desc' })
const result = ref<PageResponse<Sample> | null>(null)
const loading = ref(false)

const formOpen = ref(false)
const editing = ref<Sample | null>(null)
const deleting = ref<Sample | null>(null)
const deleteBusy = ref(false)

const canAdd = computed(() => auth.can('sample', 'add'))
const canEdit = computed(() => auth.can('sample', 'edit'))
const canDel = computed(() => auth.can('sample', 'del'))

async function load(): Promise<void> {
  loading.value = true
  try {
    result.value = await fetchSamples(search,
      { page: page.value, size: 10, sort: `${sort.key},${sort.dir}` })
  } catch (error) {
    feedback.failure(error)
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 0
  void load()
}

function onReset(): void {
  search.name = ''
  search.useYn = ''
  onSearch()
}

function onSort(key: SortKey): void {
  sort.dir = sort.key === key && sort.dir === 'desc' ? 'asc' : 'desc'
  sort.key = key
  void load()
}

function onPage(next: number): void {
  page.value = next
  void load()
}

function openCreate(): void {
  editing.value = null
  formOpen.value = true
}

function openEdit(sample: Sample): void {
  editing.value = sample
  formOpen.value = true
}

function onSaved(): void {
  formOpen.value = false
  void load()
}

async function confirmDelete(): Promise<void> {
  if (!deleting.value) return
  deleteBusy.value = true
  try {
    feedback.success(await deleteSample(deleting.value.id))
    deleting.value = null
    if (result.value?.content.length === 1 && page.value > 0) page.value -= 1
    await load()
  } catch (error) {
    feedback.failure(error)
  } finally {
    deleteBusy.value = false
  }
}

function ariaSort(key: SortKey): 'ascending' | 'descending' | 'none' {
  if (sort.key !== key) return 'none'
  return sort.dir === 'asc' ? 'ascending' : 'descending'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>{{ $t('sample.title') }}</h1>
        <p class="lede">{{ $t('sample.lede') }}</p>
      </div>
      <div class="page-actions">
        <button v-if="canAdd" type="button" class="btn btn-primary" data-test="sample-create"
          @click="openCreate">{{ $t('common.create') }}</button>
      </div>
    </header>

    <form class="filter-bar" @submit.prevent="onSearch">
      <div class="field grow">
        <label class="field-label" for="s-name">{{ $t('sample.name') }}</label>
        <input id="s-name" v-model="search.name" class="input" maxlength="100"
          :placeholder="$t('sample.namePlaceholder')" data-test="sample-search-name">
      </div>
      <div class="field">
        <label class="field-label" for="s-use">{{ $t('sample.useYn') }}</label>
        <select id="s-use" v-model="search.useYn" class="select">
          <option value="">{{ $t('common.all') }}</option>
          <option value="Y">{{ $t('common.use') }}</option>
          <option value="N">{{ $t('common.unused') }}</option>
        </select>
      </div>
      <button type="submit" class="btn btn-primary" data-test="sample-search">{{ $t('common.search') }}</button>
      <button type="button" class="btn btn-quiet" @click="onReset">{{ $t('common.reset') }}</button>
    </form>

    <div class="table-wrap" :aria-busy="loading">
      <table class="table">
        <thead>
          <tr>
            <th class="col-num" :aria-sort="ariaSort('id')">
              <button type="button" class="sort" @click="onSort('id')">ID</button></th>
            <th :aria-sort="ariaSort('name')">
              <button type="button" class="sort" @click="onSort('name')">{{ $t('sample.name') }}</button></th>
            <th>{{ $t('sample.description') }}</th>
            <th class="col-shrink">{{ $t('sample.useYn') }}</th>
            <th class="col-date" :aria-sort="ariaSort('updatedAt')">
              <button type="button" class="sort" @click="onSort('updatedAt')">{{ $t('common.updatedAt') }}</button></th>
            <th v-if="canEdit || canDel" class="col-shrink"><span class="sr-only">{{ $t('common.actions') }}</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="sample in result?.content ?? []" :key="sample.id" data-test="sample-row">
            <td class="col-num">{{ sample.id }}</td>
            <td class="name">{{ sample.name }}</td>
            <td class="desc muted">{{ sample.description }}</td>
            <td class="col-shrink">
              <span class="tag" :class="sample.useYn === 'Y' ? 'tag-accent' : ''">
                {{ sample.useYn === 'Y' ? $t('common.use') : $t('common.unused') }}</span>
            </td>
            <td class="col-date">{{ formatDateTime(sample.updatedAt) }}
              <span class="by">{{ sample.updatedBy }}</span></td>
            <td v-if="canEdit || canDel" class="col-shrink">
              <div class="row-actions">
                <button v-if="canEdit" type="button" class="btn btn-sm" data-test="sample-edit"
                  @click="openEdit(sample)">{{ $t('common.edit') }}</button>
                <button v-if="canDel" type="button" class="btn btn-sm btn-danger"
                  data-test="sample-delete" @click="deleting = sample">{{ $t('common.delete') }}</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="result && !result.content.length" :title="$t('sample.empty')"
        :description="$t('common.emptySearchHint')" />
    </div>
    <div v-if="result" class="table-foot">
      <span class="num">{{ $t('common.total', { n: result.totalElements }) }}</span>
      <UiPagination :page="result.page" :total-pages="result.totalPages" @change="onPage" />
    </div>

    <SampleFormDialog :open="formOpen" :sample="editing" @close="formOpen = false" @saved="onSaved" />
    <ConfirmDialog :open="deleting !== null" :title="$t('sample.deleteTitle')"
      :message="t('sample.deleteConfirm', { name: deleting?.name ?? '' })"
      :confirm-label="$t('common.delete')" danger :busy="deleteBusy"
      @close="deleting = null" @confirm="confirmDelete" />
  </div>
</template>

<style scoped>
.sort {
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

th[aria-sort='ascending'] .sort::after {
  content: ' ↑';
}

th[aria-sort='descending'] .sort::after {
  content: ' ↓';
}

th[aria-sort='ascending'],
th[aria-sort='descending'] {
  color: var(--ink);
}

.name {
  font-weight: 550;
}

.desc {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.by {
  display: block;
  font-size: var(--text-xs);
  color: var(--ink-4);
}

.row-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}

.table-wrap[aria-busy='true'] {
  opacity: 0.6;
}
</style>
