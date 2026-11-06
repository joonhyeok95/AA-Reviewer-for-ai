<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import type { Code } from '@/code/types'
import { ApiError } from '@/global/api/ApiError'
import ConfirmDialog from '@/global/components/ConfirmDialog.vue'
import EmptyState from '@/global/components/EmptyState.vue'
import LangNamesInput from '@/global/components/LangNamesInput.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { formatDate, formatDateTime } from '@/global/util/format'
import { useMenuStore } from '@/menu/store'
import {
  createRole, deleteRole, deleteRoleGrant, fetchRoleGrantHistories, fetchRoleGrants,
  fetchRoleMenus, fetchRoles,
  saveRoleMenus, updateRole,
} from '@/role/api'
import RoleGrantDialog from '@/role/components/RoleGrantDialog.vue'
import RoleMenuMatrix from '@/role/components/RoleMenuMatrix.vue'
import type {
  GrantTargetType, RoleAdmin, RoleGrant, RoleGrantHistory, RoleGrants, RoleMenuItem, RoleMenus,
  RoleSaveRequest,
} from '@/role/types'

/**
 * 권한 관리. 왼쪽 권한 목록 → 오른쪽 탭 (기본 정보 / 메뉴 권한 / 부여 / 변경 이력).
 *  - API 권한 = IdP scope ∩ DB 업무 권한. 여기서 바꾸는 메뉴 권한·부여가 DB 업무 권한이며 다음 요청부터 반영
 *  - 시스템관리자 권한(레벨 1)은 시스템관리자만 변경·부여 (서버 B6004)
 *  - 기본 권한은 삭제·유형/레벨 변경 불가, 부여 내역이 있으면 삭제 불가
 */
type Tab = 'info' | 'menus' | 'grants' | 'history'
const MAX_END = '9999-12-31'

const auth = useAuthStore()
const code = useCodeStore()
const menuStore = useMenuStore()
const feedback = useApiFeedback()
const { t } = useI18n()

const roles = ref<RoleAdmin[]>([])
const selectedId = ref<number | null>(null)
const creating = ref(false)
const tab = ref<Tab>('info')
const roleTypes = ref<Code[]>([])
const modules = ref<Code[]>([])
const roleMenus = ref<RoleMenus | null>(null)
const grants = ref<RoleGrants | null>(null)
const histories = ref<RoleGrantHistory[] | null>(null)
const saving = ref(false)
const errors = ref<Record<string, string>>({})
const confirm = ref<{ kind: 'role' } | { kind: 'grant'; grant: RoleGrant } | null>(null)
const grantDialog = ref<{ open: boolean; type: GrantTargetType; grant: RoleGrant | null }>({
  open: false, type: 'USER', grant: null,
})

const form = reactive<RoleSaveRequest>(emptyForm())

const selected = computed(() => roles.value.find((r) => r.roleId === selectedId.value) ?? null)
const canAdd = computed(() => auth.can('role', 'add'))
const canEdit = computed(() => auth.can('role', 'edit'))
const canDel = computed(() => auth.can('role', 'del'))
/** 시스템관리자 권한(레벨 1)은 시스템관리자만 */
const lockedForMe = computed(() => !!selected.value && selected.value.roleLevel === 1
  && !auth.isSysAdmin)
const editable = computed(() => (creating.value ? canAdd.value : canEdit.value) && !lockedForMe.value)
const isSystem = computed(() => selected.value?.systemYn === 'Y')

function emptyForm(): RoleSaveRequest {
  return {
    roleCd: '', names: {}, roleTypeCd: 'GENERAL_USER', roleLevel: 4, moduleCd: null,
    roleDesc: '', sortOrder: 0, useYn: 'Y',
  }
}

function fillFrom(role: RoleAdmin | null): void {
  errors.value = {}
  Object.assign(form, role ? {
    roleCd: role.roleCd, names: { ...role.names }, roleTypeCd: role.roleTypeCd,
    roleLevel: role.roleLevel, moduleCd: role.moduleCd, roleDesc: role.roleDesc ?? '',
    sortOrder: role.sortOrder, useYn: role.useYn,
  } : { ...emptyForm(), sortOrder: Math.max(0, ...roles.value.map((r) => r.sortOrder)) + 1 })
}

async function loadRoles(): Promise<void> {
  try {
    roles.value = await fetchRoles()
  } catch (error) {
    feedback.failure(error)
  }
}

async function loadTab(): Promise<void> {
  if (!selectedId.value) return
  try {
    if (tab.value === 'menus') roleMenus.value = await fetchRoleMenus(selectedId.value)
    if (tab.value === 'grants') grants.value = await fetchRoleGrants(selectedId.value)
    if (tab.value === 'history') histories.value = await fetchRoleGrantHistories(selectedId.value)
  } catch (error) {
    feedback.failure(error)
  }
}

function pick(roleId: number): void {
  creating.value = false
  selectedId.value = roleId
  roleMenus.value = null
  grants.value = null
  histories.value = null
  fillFrom(selected.value)
  void loadTab()
}

function startCreate(): void {
  creating.value = true
  selectedId.value = null
  tab.value = 'info'
  fillFrom(null)
}

async function saveRole(): Promise<void> {
  errors.value = {}
  saving.value = true
  try {
    const body = { ...form, moduleCd: form.moduleCd || null, roleCd: form.roleCd.trim().toUpperCase() }
    const response = creating.value ? await createRole(body)
      : await updateRole(selectedId.value!, body)
    feedback.success(response)
    await loadRoles()
    pick(response.result.roleId)
  } catch (error) {
    if (error instanceof ApiError) {
      errors.value = Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.reason]))
      if (['B4001', 'B4002'].includes(error.messageId)) errors.value.names = error.content
    }
    feedback.failure(error)
  } finally {
    saving.value = false
  }
}

async function onSaveMenus(items: RoleMenuItem[]): Promise<void> {
  if (!selectedId.value) return
  saving.value = true
  try {
    const response = await saveRoleMenus(selectedId.value, items)
    feedback.success(response)
    roleMenus.value = response.result
    await afterAuthorityChange()
  } catch (error) {
    feedback.failure(error)
  } finally {
    saving.value = false
  }
}

/** 내 권한이 바뀌었을 수 있으므로 내 정보·메뉴를 다시 불러온다 */
async function afterAuthorityChange(): Promise<void> {
  await Promise.all([loadRoles(), auth.load(), menuStore.load()])
}

function onGrantSaved(result: RoleGrants): void {
  grantDialog.value.open = false
  grants.value = result
  void afterAuthorityChange()
}

async function onConfirm(): Promise<void> {
  const target = confirm.value
  if (!target || !selectedId.value) return
  try {
    if (target.kind === 'role') {
      feedback.success(await deleteRole(selectedId.value))
      selectedId.value = null
      creating.value = false
      await loadRoles()
    } else {
      feedback.success(await deleteRoleGrant(selectedId.value, target.grant.targetType,
        target.grant.grantId))
      grants.value = await fetchRoleGrants(selectedId.value)
      await afterAuthorityChange()
    }
  } catch (error) {
    feedback.failure(error)
  } finally {
    confirm.value = null
  }
}

function period(grant: Pick<RoleGrant, 'startDt' | 'endDt'>): string {
  return grant.endDt === MAX_END ? `${formatDate(grant.startDt)} ~ ${t('roleAdmin.noEnd')}`
    : `${formatDate(grant.startDt)} ~ ${formatDate(grant.endDt)}`
}

const statusClass: Record<string, string> = { ACTIVE: 'tag-accent', SCHEDULED: 'tag-info', EXPIRED: '' }
const actionClass: Record<string, string> = { GRANT: 'tag-accent', UPDATE: 'tag-info', REVOKE: 'tag-danger' }

watch(tab, () => void loadTab())
watch(() => auth.user?.langCd, async () => {
  code.clearCodes()
  ;[roleTypes.value, modules.value] = await Promise.all([
    code.getCodes('ROLE_TYPE'), code.getCodes('MODULE')])
  await loadRoles()
  void loadTab()
})

onMounted(async () => {
  try {
    ;[roleTypes.value, modules.value] = await Promise.all([
      code.getCodes('ROLE_TYPE'), code.getCodes('MODULE')])
  } catch (error) {
    feedback.failure(error)
  }
  await loadRoles()
})
</script>

<template>
  <div class="page wide">
    <header class="page-head">
      <div>
        <h1>{{ $t('roleAdmin.title') }}</h1>
        <p class="lede">{{ $t('roleAdmin.lede') }}</p>
      </div>
      <div class="page-actions">
        <button v-if="canAdd" type="button" class="btn btn-primary" data-test="role-add"
          @click="startCreate">{{ $t('roleAdmin.add') }}</button>
      </div>
    </header>

    <div class="split">
      <aside class="split-list" :aria-label="$t('roleAdmin.roles')">
        <ul class="split-list-body">
          <li v-for="role in roles" :key="role.roleId">
            <button type="button" class="pick" :aria-current="role.roleId === selectedId"
              :data-test="`role-${role.roleCd}`" @click="pick(role.roleId)">
              <span class="level num" :title="$t('roleAdmin.level')">{{ role.roleLevel }}</span>
              <span class="pick-main">
                <span>{{ role.roleNm }}</span>
                <span class="pick-sub">{{ role.roleCd }}</span>
              </span>
              <span v-if="role.useYn === 'N'" class="tag tag-warn">{{ $t('common.unused') }}</span>
              <span class="pick-count" :title="$t('roleAdmin.grantCount')">
                {{ role.userGrantCnt + role.deptGrantCnt }}</span>
            </button>
          </li>
        </ul>
      </aside>

      <section v-if="selected || creating" class="panel">
        <header class="detail-head">
          <div>
            <h2>{{ creating ? $t('roleAdmin.add') : selected?.roleNm }}
              <span v-if="selected" class="mono muted role-cd">{{ selected.roleCd }}</span></h2>
            <p v-if="selected" class="sub">
              {{ $t('roleAdmin.summary', { users: selected.userGrantCnt, depts: selected.deptGrantCnt,
                                           menus: selected.menuCnt }) }}
              <span v-if="isSystem" class="tag">{{ $t('roleAdmin.builtIn') }}</span>
            </p>
          </div>
        </header>
        <div v-if="!creating" class="tabs tabs-pad" role="tablist">
          <button v-for="item in (['info', 'menus', 'grants', 'history'] as Tab[])" :key="item" type="button"
            role="tab" class="tab" :aria-selected="tab === item" :data-test="`role-tab-${item}`"
            @click="tab = item">{{ $t(`roleAdmin.tab.${item}`) }}</button>
        </div>
        <p v-if="lockedForMe" class="notice-line warn locked">{{ $t('roleAdmin.sysAdminOnly') }}</p>

        <!-- 기본 정보 -->
        <form v-if="tab === 'info' || creating" class="form-stack panel-pad" novalidate
          @submit.prevent="saveRole">
          <fieldset class="form-stack plain" :disabled="!editable">
            <div class="grid-2">
              <div class="field">
                <label class="field-label" for="r-cd">{{ $t('roleAdmin.roleCd') }}<span class="req">*</span></label>
                <input id="r-cd" v-model="form.roleCd" class="input mono" maxlength="50"
                  :disabled="!creating" :aria-invalid="!!errors.roleCd" spellcheck="false"
                  data-test="role-cd">
                <p class="field-hint">{{ creating ? $t('roleAdmin.roleCdHint') : $t('admin.keyLocked') }}</p>
                <p v-if="errors.roleCd" class="field-error">{{ errors.roleCd }}</p>
              </div>
              <div class="field">
                <label class="field-label" for="r-module">{{ $t('menuAdmin.module') }}</label>
                <select id="r-module" v-model="form.moduleCd" class="select">
                  <option :value="null">{{ $t('roleAdmin.allModules') }}</option>
                  <option v-for="item in modules" :key="item.code" :value="item.code">{{ item.codeNm }}</option>
                </select>
              </div>
            </div>
            <LangNamesInput v-model="form.names" :label="$t('roleAdmin.roleNm')" id-prefix="r-nm"
              :error="errors.names" />
            <div class="grid-3">
              <div class="field">
                <label class="field-label" for="r-type">{{ $t('roleAdmin.roleType') }}</label>
                <select id="r-type" v-model="form.roleTypeCd" class="select" :disabled="isSystem">
                  <option v-for="item in roleTypes" :key="item.code" :value="item.code"
                    :disabled="item.code === 'SYSTEM_ADMIN' && !auth.isSysAdmin">{{ item.codeNm }}</option>
                </select>
              </div>
              <div class="field">
                <label class="field-label" for="r-level">{{ $t('roleAdmin.level') }}</label>
                <select id="r-level" v-model.number="form.roleLevel" class="select" :disabled="isSystem">
                  <option v-for="level in [1, 2, 3, 4]" :key="level" :value="level"
                    :disabled="level === 1 && !auth.isSysAdmin">{{ $t(`roleAdmin.levels.${level}`) }}</option>
                </select>
              </div>
              <div class="field">
                <label class="field-label" for="r-sort">{{ $t('admin.sortOrder') }}</label>
                <input id="r-sort" v-model.number="form.sortOrder" type="number" min="0" max="99999"
                  class="input num">
              </div>
            </div>
            <div class="field">
              <label class="field-label" for="r-desc">{{ $t('admin.desc') }}</label>
              <textarea id="r-desc" v-model="form.roleDesc" class="textarea" maxlength="1000" rows="2" />
            </div>
            <label class="check"><input v-model="form.useYn" type="checkbox" true-value="Y"
              false-value="N">{{ $t('common.use') }}</label>
          </fieldset>
          <div class="form-actions">
            <button v-if="selected && canDel && !isSystem && !lockedForMe" type="button"
              class="btn btn-danger left" data-test="role-delete"
              @click="confirm = { kind: 'role' }">{{ $t('common.delete') }}</button>
            <button v-if="creating" type="button" class="btn" @click="creating = false">{{ $t('common.cancel') }}</button>
            <button v-if="editable" type="submit" class="btn btn-primary" :disabled="saving"
              data-test="role-save">{{ $t('common.save') }}</button>
          </div>
        </form>

        <!-- 메뉴 권한 -->
        <template v-else-if="tab === 'menus'">
          <p v-if="roleMenus?.allMenus" class="notice-line all">{{ $t('roleAdmin.allMenus') }}</p>
          <RoleMenuMatrix v-else-if="roleMenus" :menus="roleMenus.menus"
            :disabled="!canEdit || lockedForMe" :saving="saving" @save="onSaveMenus" />
        </template>

        <!-- 부여 -->
        <div v-else-if="tab === 'grants' && grants" class="grants">
          <section v-for="type in (['USER', 'DEPT'] as GrantTargetType[])" :key="type" class="grant-block">
            <header class="block-head">
              <h3>{{ $t(type === 'USER' ? 'roleAdmin.userGrants' : 'roleAdmin.deptGrants') }}
                <span class="muted num">{{ (type === 'USER' ? grants.users : grants.depts).length }}</span></h3>
              <button v-if="canEdit && !lockedForMe" type="button" class="btn btn-sm"
                :data-test="`grant-add-${type}`"
                @click="grantDialog = { open: true, type, grant: null }">
                {{ $t(type === 'USER' ? 'roleAdmin.grantUser' : 'roleAdmin.grantDept') }}</button>
            </header>
            <table v-if="(type === 'USER' ? grants.users : grants.depts).length" class="table">
              <thead>
                <tr>
                  <th>{{ $t('roleAdmin.target') }}</th>
                  <th>{{ $t(type === 'USER' ? 'roleAdmin.mainDept' : 'roleAdmin.workplace') }}</th>
                  <th class="col-date">{{ $t('roleAdmin.period') }}</th>
                  <th class="col-shrink">{{ $t('roleAdmin.status') }}</th>
                  <th>{{ $t('roleAdmin.reason') }}</th>
                  <th v-if="canEdit && !lockedForMe" class="col-shrink"><span class="sr-only">{{ $t('common.actions') }}</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="grant in (type === 'USER' ? grants.users : grants.depts)" :key="grant.grantId"
                  :class="{ expired: grant.status === 'EXPIRED' }" :data-test="`grant-row-${grant.targetId}`">
                  <td><strong>{{ grant.targetNm ?? '—' }}</strong> <span class="mono muted">{{ grant.targetId }}</span></td>
                  <td class="muted">{{ grant.targetSubNm }}</td>
                  <td class="col-date">{{ period(grant) }}</td>
                  <td class="col-shrink"><span class="tag" :class="statusClass[grant.status]">
                    {{ $t(`roleAdmin.statuses.${grant.status}`) }}</span></td>
                  <td class="muted reason">{{ grant.grantReason }}</td>
                  <td v-if="canEdit && !lockedForMe" class="col-shrink">
                    <div class="row-actions">
                      <button type="button" class="btn btn-sm" :data-test="`grant-edit-${grant.targetId}`"
                        @click="grantDialog = { open: true, type, grant }">{{ $t('roleAdmin.changePeriod') }}</button>
                      <button type="button" class="btn btn-sm btn-danger" :data-test="`grant-revoke-${grant.targetId}`"
                        @click="confirm = { kind: 'grant', grant }">{{ $t('roleAdmin.revoke') }}</button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-else class="muted empty-line">{{ $t('roleAdmin.noGrants') }}</p>
          </section>
        </div>

        <!-- 변경 이력 (감사) -->
        <div v-else-if="tab === 'history' && histories" class="grants">
          <p class="muted history-hint">{{ $t('roleAdmin.historyHint') }}</p>
          <table v-if="histories.length" class="table" data-test="grant-history">
            <thead>
              <tr>
                <th class="col-date">{{ $t('roleAdmin.changedAt') }}</th>
                <th class="col-shrink">{{ $t('roleAdmin.action') }}</th>
                <th>{{ $t('roleAdmin.target') }}</th>
                <th class="col-date">{{ $t('roleAdmin.period') }}</th>
                <th>{{ $t('roleAdmin.reason') }}</th>
                <th>{{ $t('roleAdmin.changedBy') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="hist in histories" :key="hist.histId" :data-test="`history-${hist.actionCd}-${hist.targetId}`">
                <td class="col-date muted">{{ formatDateTime(hist.changedAt) }}</td>
                <td class="col-shrink"><span class="tag" :class="actionClass[hist.actionCd]">
                  {{ $t(`roleAdmin.actions.${hist.actionCd}`) }}</span></td>
                <td><strong>{{ hist.targetNm ?? '—' }}</strong> <span class="mono muted">{{ hist.targetId }}</span>
                  <span class="muted">· {{ $t(hist.targetType === 'USER' ? 'roleAdmin.targetUser' : 'roleAdmin.targetDept') }}</span></td>
                <td class="col-date">{{ period(hist) }}</td>
                <td class="muted reason">{{ hist.reason }}</td>
                <td>{{ hist.changedByNm ?? hist.changedBy }} <span class="mono muted">{{ hist.changedBy }}</span></td>
              </tr>
            </tbody>
          </table>
          <p v-else class="muted empty-line">{{ $t('roleAdmin.noHistory') }}</p>
        </div>
      </section>
      <section v-else class="panel">
        <EmptyState :title="$t('roleAdmin.pick')" :description="$t('roleAdmin.pickHint')" />
      </section>
    </div>

    <RoleGrantDialog v-if="selected" :open="grantDialog.open" :role-id="selected.roleId"
      :role-nm="selected.roleNm" :target-type="grantDialog.type" :grant="grantDialog.grant"
      @close="grantDialog.open = false" @saved="onGrantSaved" />
    <ConfirmDialog :open="confirm !== null"
      :title="confirm?.kind === 'role' ? $t('roleAdmin.deleteTitle') : $t('roleAdmin.revokeTitle')"
      :message="confirm?.kind === 'grant'
        ? t('roleAdmin.revokeConfirm', { target: confirm.grant.targetNm ?? confirm.grant.targetId })
        : t('roleAdmin.deleteConfirm', { role: selected?.roleCd ?? '' })"
      :confirm-label="confirm?.kind === 'role' ? $t('common.delete') : $t('roleAdmin.revoke')" danger
      @close="confirm = null" @confirm="onConfirm" />
  </div>
</template>

<style scoped>
.wide {
  max-width: 1320px;
}

.level {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 1px solid var(--rule-strong);
  border-radius: 50%;
  font-size: 11px;
  color: var(--ink-2);
}

.role-cd {
  font-size: var(--text-xs);
  font-weight: 400;
}

.tabs-pad {
  padding: 0 var(--space-4);
}

.locked,
.all {
  margin: var(--space-4) var(--space-5) 0;
}

.all {
  margin-bottom: var(--space-5);
}

.plain {
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}

.grants {
  padding: var(--space-2) 0 var(--space-4);
}

.grant-block + .grant-block {
  margin-top: var(--space-5);
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-5);
}

.block-head h3 {
  font-size: var(--text-md);
}

.grant-block .table th:first-child,
.grant-block .table td:first-child {
  padding-left: var(--space-5);
}

tr.expired td {
  color: var(--ink-3);
}

.reason {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}

.empty-line {
  padding: 0 var(--space-5);
  font-size: var(--text-sm);
}

.history-hint {
  padding: var(--space-3) var(--space-5);
  font-size: var(--text-sm);
}

[data-test="grant-history"] th:first-child,
[data-test="grant-history"] td:first-child {
  padding-left: var(--space-5);
}
</style>
