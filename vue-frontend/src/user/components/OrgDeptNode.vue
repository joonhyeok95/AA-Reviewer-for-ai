<script setup lang="ts">
import type { OrgDept } from '@/user/types'

/**
 * 부서 트리 노드 (재귀).
 * selectable 이면 체크박스로 메일 대상(부서)을 고르고, 선택한 부서는 "하위 포함" 을 정할 수 있다.
 */
const props = defineProps<{
  dept: OrgDept
  depth: number
  selectable?: boolean
  disabled?: boolean
  isSelected?: (deptCd: string) => boolean
  includesSub?: (deptCd: string) => boolean
}>()

const emit = defineEmits<{ toggle: [deptCd: string]; toggleSub: [deptCd: string] }>()

function selected(): boolean {
  return props.isSelected?.(props.dept.deptCd) ?? false
}
</script>

<template>
  <li class="node" :style="{ '--depth': depth }">
    <div class="row">
      <label v-if="selectable" class="check">
        <input type="checkbox" :checked="selected()" :disabled="disabled"
          :data-test="`org-dept-${dept.deptCd}`" @change="emit('toggle', dept.deptCd)">
        <span>{{ dept.deptNm }}</span>
      </label>
      <span v-else class="name">{{ dept.deptNm }}</span>
      <span class="mono code">{{ dept.deptCd }}</span>
      <label v-if="selectable && selected() && dept.children.length" class="check sub">
        <input type="checkbox" :checked="includesSub?.(dept.deptCd)" :disabled="disabled"
          @change="emit('toggleSub', dept.deptCd)">
        <span>{{ $t('org.includeSub') }}</span>
      </label>
    </div>
    <ul v-if="dept.children.length" class="children">
      <OrgDeptNode v-for="child in dept.children" :key="child.deptCd" :dept="child"
        :depth="depth + 1" :selectable="selectable" :disabled="disabled" :is-selected="isSelected"
        :includes-sub="includesSub" @toggle="emit('toggle', $event)"
        @toggle-sub="emit('toggleSub', $event)" />
    </ul>
  </li>
</template>

<style scoped>
.node {
  list-style: none;
}

.children {
  margin: 0;
  padding: 0;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 32px;
  padding: 4px 0 4px calc(var(--depth) * 20px);
  border-bottom: 1px dashed var(--rule);
  font-size: var(--text-sm);
}

.row::before {
  content: '';
  width: 10px;
  height: 1px;
  background: var(--rule-strong);
  flex-shrink: 0;
}

.code {
  font-size: 11px;
  color: var(--ink-4);
}

.sub {
  margin-left: auto;
  font-size: var(--text-xs);
  color: var(--ink-2);
}
</style>
