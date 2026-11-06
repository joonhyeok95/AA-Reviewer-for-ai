<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { YesNo } from '@/code/types'
import type { RoleMenuItem, RoleMenuNode } from '@/role/types'

/**
 * 권한별 메뉴 접근 (메뉴 × 조회/등록/수정/삭제).
 *  - 등록/수정/삭제를 켜면 조회도 켜진다, 조회를 끄면 모두 꺼진다
 *  - 조회를 켜면 상위 메뉴 조회도 켜진다 (상위가 없으면 사용자 메뉴에 보이지 않음 - 서버도 같은 규칙)
 *  - 권한코드(permCd)가 있는 메뉴의 체크가 API 권한(permCd.list 등)이 된다
 */
type Flag = 'listYn' | 'addYn' | 'editYn' | 'delYn'
const FLAGS: Flag[] = ['listYn', 'addYn', 'editYn', 'delYn']

const props = defineProps<{ menus: RoleMenuNode[]; disabled: boolean; saving: boolean }>()
const emit = defineEmits<{ save: [items: RoleMenuItem[]] }>()

interface Row {
  node: RoleMenuNode
  depth: number
  parentId: number | null
}

const state = ref<Record<number, Record<Flag, YesNo>>>({})

const rows = computed(() => {
  const result: Row[] = []
  const walk = (nodes: RoleMenuNode[], depth: number, parentId: number | null) => nodes.forEach((node) => {
    result.push({ node, depth, parentId })
    walk(node.children, depth + 1, node.menuId)
  })
  walk(props.menus, 0, null)
  return result
})
const parents = computed(() => new Map(rows.value.map((row) => [row.node.menuId, row.parentId])))

const original = computed(() => JSON.stringify(snapshot(props.menus)))
const dirty = computed(() => JSON.stringify(state.value) !== original.value)
const grantedCount = computed(() => Object.values(state.value).filter((f) => f.listYn === 'Y').length)

function snapshot(menus: RoleMenuNode[]): Record<number, Record<Flag, YesNo>> {
  const result: Record<number, Record<Flag, YesNo>> = {}
  const walk = (nodes: RoleMenuNode[]) => nodes.forEach((node) => {
    result[node.menuId] = { listYn: node.listYn, addYn: node.addYn, editYn: node.editYn, delYn: node.delYn }
    walk(node.children)
  })
  walk(menus)
  return result
}

function reset(): void {
  state.value = snapshot(props.menus)
}

function toggle(menuId: number, flag: Flag, on: boolean): void {
  const next = { ...state.value }
  const current = { ...next[menuId]! }
  current[flag] = on ? 'Y' : 'N'
  if (flag === 'listYn' && !on) {
    current.addYn = 'N'
    current.editYn = 'N'
    current.delYn = 'N'
  }
  if (flag !== 'listYn' && on) {
    current.listYn = 'Y'
  }
  next[menuId] = current
  if (current.listYn === 'Y') {
    let parentId = parents.value.get(menuId) ?? null
    while (parentId !== null) {
      next[parentId] = { ...next[parentId]!, listYn: 'Y' }
      parentId = parents.value.get(parentId) ?? null
    }
  }
  state.value = next
}

function save(): void {
  const items = Object.entries(state.value)
    .filter(([, flags]) => FLAGS.some((flag) => flags[flag] === 'Y'))
    .map(([menuId, flags]) => ({ menuId: Number(menuId), ...flags }))
  emit('save', items)
}

watch(() => props.menus, reset, { immediate: true })
</script>

<template>
  <div class="matrix">
    <div class="table-scroll">
      <table class="table">
        <thead>
          <tr>
            <th>{{ $t('roleAdmin.menu') }}</th>
            <th>{{ $t('menuAdmin.permCd') }}</th>
            <th v-for="flag in FLAGS" :key="flag" class="col-shrink center">
              {{ $t(`action.${flag.replace('Yn', '')}`) }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.node.menuId"
            :class="{ granted: state[row.node.menuId]?.listYn === 'Y', dim: row.node.useYn === 'N' }">
            <td>
              <span class="menu-nm" :style="{ paddingLeft: `${row.depth * 18}px` }">
                {{ row.node.menuNm }}
                <span v-if="row.node.displayYn === 'N'" class="tag">{{ $t('menuAdmin.hidden') }}</span>
                <span v-if="row.node.useYn === 'N'" class="tag tag-warn">{{ $t('common.unused') }}</span>
              </span>
            </td>
            <td class="mono perm">{{ row.node.permCd ? `${row.node.permCd}.*` : '' }}</td>
            <td v-for="flag in FLAGS" :key="flag" class="col-shrink center">
              <input type="checkbox" class="cb" :checked="state[row.node.menuId]?.[flag] === 'Y'"
                :disabled="disabled"
                :aria-label="`${row.node.menuNm} ${$t(`action.${flag.replace('Yn', '')}`)}`"
                :data-test="`rm-${row.node.menuId}-${flag}`"
                @change="toggle(row.node.menuId, flag, ($event.target as HTMLInputElement).checked)">
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!disabled" class="matrix-foot">
      <span class="muted num">{{ $t('roleAdmin.grantedMenus', { n: grantedCount }) }}</span>
      <span class="hint">{{ $t('roleAdmin.matrixHint') }}</span>
      <button type="button" class="btn" :disabled="!dirty" @click="reset">{{ $t('common.reset') }}</button>
      <button type="button" class="btn btn-primary" :disabled="!dirty || saving"
        data-test="role-menus-save" @click="save">{{ $t('common.save') }}</button>
    </div>
  </div>
</template>

<style scoped>
.table-scroll {
  overflow-x: auto;
}

.center {
  text-align: center;
}

.menu-nm {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

tr.granted .menu-nm {
  font-weight: 600;
}

tr.dim td {
  color: var(--ink-3);
}

.perm {
  font-size: var(--text-xs);
  color: var(--accent);
}

.cb {
  width: 16px;
  height: 16px;
  accent-color: var(--accent);
}

.matrix-foot {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  border-top: 1px solid var(--rule);
  background: var(--paper);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}

.hint {
  flex: 1;
  font-size: var(--text-xs);
  color: var(--ink-3);
}
</style>
