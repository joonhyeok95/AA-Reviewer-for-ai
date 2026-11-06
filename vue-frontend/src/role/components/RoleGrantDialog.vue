<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/global/api/ApiError'
import UiDialog from '@/global/components/UiDialog.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { today } from '@/global/util/format'
import { createRoleGrant, updateRoleGrant } from '@/role/api'
import type { GrantTargetType, RoleGrant, RoleGrants } from '@/role/types'
import { fetchOrgTree, searchUsers } from '@/user/api'
import type { OrgDept, UserSummary } from '@/user/types'

/**
 * 권한 부여 / 기간 변경.
 *  - 사용자 : ID·이름으로 검색해서 선택
 *  - 부서 : 조직도에서 선택 (소속 사용자 전원에게 적용)
 *  - 종료일을 비우면 무기한. 같은 대상에게 기간이 겹치게 줄 수 없다 (B6010)
 */
const MAX_END = '9999-12-31'

const props = defineProps<{
  open: boolean
  roleId: number
  roleNm: string
  targetType: GrantTargetType
  grant: RoleGrant | null
}>()
const emit = defineEmits<{ close: []; saved: [grants: RoleGrants] }>()

const feedback = useApiFeedback()
const { t } = useI18n()
const form = reactive({ targetId: '', targetLabel: '', startDt: '', endDt: '', grantReason: '' })
const keyword = ref('')
const users = ref<UserSummary[]>([])
const depts = ref<{ deptCd: string; label: string }[]>([])
const searching = ref(false)
const saving = ref(false)
const error = ref('')
const editing = computed(() => props.grant !== null)

watch(() => props.open, async (open) => {
  if (!open) return
  error.value = ''
  keyword.value = ''
  users.value = []
  form.targetId = props.grant?.targetId ?? ''
  form.targetLabel = props.grant ? `${props.grant.targetNm ?? ''} (${props.grant.targetId})` : ''
  form.startDt = props.grant?.startDt ?? today()
  form.endDt = props.grant && props.grant.endDt !== MAX_END ? props.grant.endDt : ''
  form.grantReason = props.grant?.grantReason ?? ''
  if (!props.grant && props.targetType === 'DEPT' && !depts.value.length) {
    await loadDepts()
  }
})

async function loadDepts(): Promise<void> {
  try {
    const result: { deptCd: string; label: string }[] = []
    const walk = (nodes: OrgDept[], depth: number, wp: string) => nodes.forEach((dept) => {
      result.push({ deptCd: dept.deptCd, label: `${wp} · ${'— '.repeat(depth)}${dept.deptNm}` })
      walk(dept.children, depth + 1, wp)
    })
    ;(await fetchOrgTree()).forEach((wp) => walk(wp.depts, 0, wp.workplaceNm))
    depts.value = result
  } catch (e) {
    feedback.failure(e)
  }
}

async function search(): Promise<void> {
  if (!keyword.value.trim()) return
  searching.value = true
  try {
    users.value = await searchUsers(keyword.value.trim())
  } catch (e) {
    feedback.failure(e)
  } finally {
    searching.value = false
  }
}

function pick(user: UserSummary): void {
  form.targetId = user.userId
  form.targetLabel = `${user.userNm} (${user.userId})`
}

async function submit(): Promise<void> {
  error.value = ''
  if (!form.targetId) {
    error.value = t(props.targetType === 'USER' ? 'roleAdmin.pickUser' : 'roleAdmin.pickDept')
    return
  }
  saving.value = true
  try {
    const body = {
      targetType: props.targetType, targetId: form.targetId, startDt: form.startDt,
      endDt: form.endDt || null, grantReason: form.grantReason,
    }
    const response = props.grant
      ? await updateRoleGrant(props.roleId, props.targetType, props.grant.grantId, body)
      : await createRoleGrant(props.roleId, body)
    feedback.success(response)
    emit('saved', response.result)
  } catch (e) {
    if (e instanceof ApiError && e.content) error.value = e.content
    feedback.failure(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <UiDialog :open="open"
    :title="editing ? $t('roleAdmin.editGrant') : $t(targetType === 'USER' ? 'roleAdmin.grantUser' : 'roleAdmin.grantDept')"
    width="560px" @close="emit('close')">
    <form id="grant-form" class="form-stack" novalidate @submit.prevent="submit">
      <p class="muted role">{{ $t('roleAdmin.grantRole', { role: roleNm }) }}</p>

      <div v-if="editing" class="field">
        <span class="field-label">{{ $t('roleAdmin.target') }}</span>
        <p class="target">{{ form.targetLabel }}</p>
      </div>

      <template v-else-if="targetType === 'USER'">
        <div class="field">
          <label class="field-label" for="gu-q">{{ $t('roleAdmin.searchUser') }}</label>
          <div class="search-row">
            <input id="gu-q" v-model="keyword" class="input" maxlength="50" autocomplete="off"
              :placeholder="$t('roleAdmin.searchUserPlaceholder')" data-test="grant-user-q"
              @keydown.enter.prevent="search">
            <button type="button" class="btn" :disabled="searching" data-test="grant-user-search"
              @click="search">{{ $t('common.search') }}</button>
          </div>
        </div>
        <ul v-if="users.length" class="results" role="listbox">
          <li v-for="user in users" :key="user.userId">
            <label class="result" :class="{ on: form.targetId === user.userId }">
              <input type="radio" name="grant-user" :checked="form.targetId === user.userId"
                :data-test="`grant-user-${user.userId}`" @change="pick(user)">
              <span class="name">{{ user.userNm }}</span>
              <span class="mono muted">{{ user.userId }}</span>
              <span class="muted dept">{{ user.deptNm }}</span>
            </label>
          </li>
        </ul>
      </template>

      <div v-else class="field">
        <label class="field-label" for="gd-dept">{{ $t('roleAdmin.dept') }}</label>
        <select id="gd-dept" v-model="form.targetId" class="select" data-test="grant-dept">
          <option value="">{{ $t('roleAdmin.pickDept') }}</option>
          <option v-for="dept in depts" :key="dept.deptCd" :value="dept.deptCd">{{ dept.label }}</option>
        </select>
        <p class="field-hint">{{ $t('roleAdmin.deptHint') }}</p>
      </div>

      <div class="grid-2">
        <div class="field">
          <label class="field-label" for="g-start">{{ $t('roleAdmin.startDt') }}<span class="req">*</span></label>
          <input id="g-start" v-model="form.startDt" type="date" class="input num" required>
        </div>
        <div class="field">
          <label class="field-label" for="g-end">{{ $t('roleAdmin.endDt') }}</label>
          <input id="g-end" v-model="form.endDt" type="date" class="input num" data-test="grant-end">
          <p class="field-hint">{{ $t('roleAdmin.endHint') }}</p>
        </div>
      </div>
      <div class="field">
        <label class="field-label" for="g-reason">{{ $t('roleAdmin.reason') }}</label>
        <input id="g-reason" v-model="form.grantReason" class="input" maxlength="1000">
      </div>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="btn" @click="emit('close')">{{ $t('common.cancel') }}</button>
      <button type="submit" form="grant-form" class="btn btn-primary" :disabled="saving"
        data-test="grant-save">{{ $t('common.save') }}</button>
    </template>
  </UiDialog>
</template>

<style scoped>
.role {
  font-size: var(--text-sm);
}

.target {
  font-weight: 600;
}

.search-row {
  display: flex;
  gap: var(--space-2);
}

.results {
  max-height: 220px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
}

.result {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 8px 10px;
  border-bottom: 1px solid var(--rule);
  font-size: var(--text-sm);
  cursor: pointer;
}

.result:hover {
  background: var(--surface-hover);
}

.result.on {
  background: var(--accent-soft);
}

.result input {
  accent-color: var(--accent);
}

.result .mono {
  font-size: var(--text-xs);
}

.dept {
  margin-left: auto;
  font-size: var(--text-xs);
}
</style>
