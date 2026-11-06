<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/auth/store'
import { deleteCode, deleteCodeGroup, fetchAdminCodes, fetchCodeGroups } from '@/code/api'
import CodeDialog from '@/code/components/CodeDialog.vue'
import CodeGroupDialog from '@/code/components/CodeGroupDialog.vue'
import { useCodeStore } from '@/code/store'
import type { CodeAdmin, CodeGroupAdmin } from '@/code/types'
import ConfirmDialog from '@/global/components/ConfirmDialog.vue'
import EmptyState from '@/global/components/EmptyState.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'

/**
 * 공통코드 관리. 왼쪽 그룹 목록 → 오른쪽 그룹의 코드 (언어별 이름 열은 지원 언어 수만큼).
 *  - 시스템 그룹은 삭제 불가, 그 그룹의 코드도 삭제 불가 (사용 여부로 관리)
 *  - 저장하면 서버 공통코드 캐시가 비워져 다른 화면에도 즉시 반영된다
 */
const LANG_GROUP = 'LANG'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const code = useCodeStore()
const feedback = useApiFeedback()
const { t } = useI18n()

const groups = ref<CodeGroupAdmin[]>([])
const codes = ref<CodeAdmin[]>([])
const filter = ref('')
const selectedCd = ref(typeof route.query.group === 'string' ? route.query.group : '')
const loadingCodes = ref(false)

const groupDialog = ref<{ open: boolean; group: CodeGroupAdmin | null }>({ open: false, group: null })
const codeDialog = ref<{ open: boolean; item: CodeAdmin | null }>({ open: false, item: null })
const confirm = ref<{ kind: 'group' | 'code'; target: string } | null>(null)
const deleting = ref(false)

const canAdd = computed(() => auth.can('code', 'add'))
const canEdit = computed(() => auth.can('code', 'edit'))
const canDel = computed(() => auth.can('code', 'del'))

const selected = computed(() => groups.value.find((g) => g.groupCd === selectedCd.value))
const filtered = computed(() => {
  const keyword = filter.value.trim().toLowerCase()
  return keyword ? groups.value.filter((g) => g.groupCd.toLowerCase().includes(keyword)
    || (g.groupNm ?? '').toLowerCase().includes(keyword)) : groups.value
})
const nextGroupSort = computed(() => Math.max(0, ...groups.value.map((g) => g.sortOrder)) + 1)
const nextCodeSort = computed(() => Math.max(0, ...codes.value.map((c) => c.sortOrder)) + 1)

async function loadGroups(): Promise<void> {
  try {
    groups.value = await fetchCodeGroups()
    if (!selected.value) {
      selectedCd.value = groups.value[0]?.groupCd ?? ''
    }
  } catch (error) {
    feedback.failure(error)
  }
}

async function loadCodes(): Promise<void> {
  if (!selectedCd.value) {
    codes.value = []
    return
  }
  loadingCodes.value = true
  try {
    codes.value = await fetchAdminCodes(selectedCd.value)
  } catch (error) {
    feedback.failure(error)
  } finally {
    loadingCodes.value = false
  }
}

/** 저장 후 : 목록 갱신 + 화면 공통코드 캐시 비움, 언어 그룹이면 지원 언어 다시 조회 */
async function afterChange(groupCd?: string): Promise<void> {
  code.clearCodes()
  if (groupCd) selectedCd.value = groupCd
  await Promise.all([loadGroups(), loadCodes()])
  if (selectedCd.value === LANG_GROUP) {
    await code.loadLangs()
  }
}

function onGroupSaved(groupCd: string): void {
  groupDialog.value.open = false
  void afterChange(groupCd)
}

function onCodeSaved(): void {
  codeDialog.value.open = false
  void afterChange()
}

async function confirmDelete(): Promise<void> {
  const target = confirm.value
  if (!target || !selected.value) return
  deleting.value = true
  try {
    if (target.kind === 'group') {
      feedback.success(await deleteCodeGroup(target.target))
      selectedCd.value = ''
    } else {
      feedback.success(await deleteCode(selected.value.groupCd, target.target))
    }
    confirm.value = null
    await afterChange()
  } catch (error) {
    feedback.failure(error)
  } finally {
    deleting.value = false
  }
}

watch(selectedCd, (groupCd) => {
  void router.replace({ query: groupCd ? { group: groupCd } : {} })
  void loadCodes()
})
watch(() => auth.user?.langCd, () => void afterChange())

onMounted(async () => {
  await loadGroups()
  await loadCodes()
})
</script>

<template>
  <div class="page wide">
    <header class="page-head">
      <div>
        <h1>{{ $t('codeAdmin.title') }}</h1>
        <p class="lede">{{ $t('codeAdmin.lede') }}</p>
      </div>
      <div class="page-actions">
        <button v-if="canAdd" type="button" class="btn btn-primary" data-test="group-add"
          @click="groupDialog = { open: true, group: null }">{{ $t('codeAdmin.addGroup') }}</button>
      </div>
    </header>

    <div class="split">
      <aside class="split-list" :aria-label="$t('codeAdmin.groups')">
        <div class="split-list-head">
          <input v-model="filter" class="input" type="search" :placeholder="$t('codeAdmin.filter')"
            :aria-label="$t('codeAdmin.filter')">
        </div>
        <ul class="split-list-body">
          <li v-for="group in filtered" :key="group.groupCd">
            <button type="button" class="pick" :aria-current="group.groupCd === selectedCd"
              :data-test="`group-${group.groupCd}`" @click="selectedCd = group.groupCd">
              <span class="pick-main">
                <span>{{ group.groupNm }}</span>
                <span class="pick-sub">{{ group.groupCd }}</span>
              </span>
              <span v-if="group.systemYn === 'Y'" class="tag">{{ $t('admin.system') }}</span>
              <span v-if="group.useYn === 'N'" class="tag tag-warn">{{ $t('common.unused') }}</span>
              <span class="pick-count">{{ group.codeCnt }}</span>
            </button>
          </li>
        </ul>
      </aside>

      <section v-if="selected" class="panel">
        <header class="detail-head">
          <div>
            <h2>{{ selected.groupNm }} <span class="mono muted code-cd">{{ selected.groupCd }}</span></h2>
            <p class="sub">{{ selected.groupDesc || $t('admin.noDesc') }}</p>
            <p v-if="selected.systemYn === 'Y'" class="sub">{{ $t('codeAdmin.systemHint') }}</p>
          </div>
          <div class="page-actions">
            <button v-if="canEdit" type="button" class="btn btn-sm" data-test="group-edit"
              @click="groupDialog = { open: true, group: selected }">{{ $t('common.edit') }}</button>
            <button v-if="canDel && selected.systemYn !== 'Y'" type="button"
              class="btn btn-sm btn-danger" data-test="group-delete"
              @click="confirm = { kind: 'group', target: selected.groupCd }">{{ $t('common.delete') }}</button>
            <button v-if="canAdd" type="button" class="btn btn-sm btn-primary" data-test="code-add"
              @click="codeDialog = { open: true, item: null }">{{ $t('codeAdmin.addCode') }}</button>
          </div>
        </header>
        <div class="table-scroll" :aria-busy="loadingCodes">
          <table class="table">
            <thead>
              <tr>
                <th class="col-num">{{ $t('admin.sortOrder') }}</th>
                <th>{{ $t('code.code') }}</th>
                <th v-for="lang in code.langs" :key="lang.langCd">
                  {{ $t('code.codeNm') }} <span class="mono muted">{{ lang.badge }}</span></th>
                <th>{{ $t('code.attr1') }}</th>
                <th class="col-shrink">{{ $t('admin.useYn') }}</th>
                <th v-if="canEdit || canDel" class="col-shrink"><span class="sr-only">{{ $t('common.actions') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in codes" :key="item.code" :class="{ unused: item.useYn === 'N' }"
                data-test="code-row">
                <td class="col-num">{{ item.sortOrder }}</td>
                <td class="mono">{{ item.code }}</td>
                <td v-for="lang in code.langs" :key="lang.langCd">
                  <span v-if="item.names[lang.langCd]">{{ item.names[lang.langCd] }}</span>
                  <span v-else class="muted fallback" :title="t('admin.fallbackPlaceholder')">—</span>
                </td>
                <td class="mono muted">{{ item.attr1 }}</td>
                <td class="col-shrink">
                  <span class="tag" :class="item.useYn === 'Y' ? 'tag-accent' : 'tag-warn'">
                    {{ item.useYn === 'Y' ? $t('common.use') : $t('common.unused') }}</span>
                </td>
                <td v-if="canEdit || canDel" class="col-shrink">
                  <div class="row-actions">
                    <button v-if="canEdit" type="button" class="btn btn-sm" :data-test="`code-edit-${item.code}`"
                      @click="codeDialog = { open: true, item }">{{ $t('common.edit') }}</button>
                    <button v-if="canDel && selected.systemYn !== 'Y'" type="button"
                      class="btn btn-sm btn-danger" :data-test="`code-delete-${item.code}`"
                      @click="confirm = { kind: 'code', target: item.code }">{{ $t('common.delete') }}</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
          <EmptyState v-if="!codes.length && !loadingCodes" :title="$t('code.empty')" />
        </div>
      </section>
      <section v-else class="panel">
        <EmptyState :title="$t('codeAdmin.pickGroup')" />
      </section>
    </div>

    <CodeGroupDialog :open="groupDialog.open" :group="groupDialog.group" :next-sort="nextGroupSort"
      @close="groupDialog.open = false" @saved="onGroupSaved" />
    <CodeDialog v-if="selected" :open="codeDialog.open" :group-cd="selected.groupCd"
      :item="codeDialog.item" :next-sort="nextCodeSort" @close="codeDialog.open = false"
      @saved="onCodeSaved" />
    <ConfirmDialog :open="confirm !== null"
      :title="confirm?.kind === 'group' ? $t('codeAdmin.deleteGroup') : $t('codeAdmin.deleteCode')"
      :message="confirm?.kind === 'group' ? t('codeAdmin.deleteGroupConfirm', { cd: confirm.target })
        : t('codeAdmin.deleteCodeConfirm', { cd: confirm?.target ?? '' })"
      :confirm-label="$t('common.delete')" danger :busy="deleting" @close="confirm = null"
      @confirm="confirmDelete" />
  </div>
</template>

<style scoped>
.wide {
  max-width: 1320px;
}

.code-cd {
  font-size: var(--text-xs);
  font-weight: 400;
}

.table-scroll {
  overflow-x: auto;
}

.table-scroll[aria-busy='true'] {
  opacity: 0.6;
}

tr.unused td {
  color: var(--ink-3);
}

.fallback {
  cursor: help;
}

.row-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}
</style>
