<script setup lang="ts">
import type { MenuAdminNode } from '@/menu/types'

/** 메뉴 관리 트리 항목 (재귀). 펼침/선택/같은 상위 안에서 위·아래 이동 */
defineProps<{
  node: MenuAdminNode
  depth: number
  selectedId: number | null
  collapsed: Set<number>
  canMove: boolean
  first: boolean
  last: boolean
}>()

const emit = defineEmits<{
  select: [menuId: number]
  toggle: [menuId: number]
  move: [menuId: number, direction: -1 | 1]
}>()
</script>

<template>
  <li class="tree-item">
    <div class="row" :class="{ on: node.menuId === selectedId }" :style="{ '--depth': depth }">
      <button v-if="node.children.length" type="button" class="twisty"
        :aria-expanded="!collapsed.has(node.menuId)" :aria-label="$t('menuAdmin.toggle')"
        @click="emit('toggle', node.menuId)"><span class="chevron" aria-hidden="true" /></button>
      <span v-else class="twisty-space" />
      <button type="button" class="label" :aria-current="node.menuId === selectedId"
        :data-test="`menu-${node.menuId}`" @click="emit('select', node.menuId)">
        <span class="name" :class="{ dim: node.useYn === 'N' }">{{ node.menuNm }}</span>
        <span class="type mono">{{ node.viewTypeCd }}</span>
        <span v-if="node.permCd" class="perm mono">{{ node.permCd }}</span>
        <span v-if="node.displayYn === 'N'" class="tag">{{ $t('menuAdmin.hidden') }}</span>
        <span v-if="node.useYn === 'N'" class="tag tag-warn">{{ $t('common.unused') }}</span>
      </button>
      <span v-if="canMove" class="moves">
        <button type="button" class="move" :disabled="first" :aria-label="$t('menuAdmin.moveUp')"
          :data-test="`menu-up-${node.menuId}`" @click="emit('move', node.menuId, -1)">↑</button>
        <button type="button" class="move" :disabled="last" :aria-label="$t('menuAdmin.moveDown')"
          :data-test="`menu-down-${node.menuId}`" @click="emit('move', node.menuId, 1)">↓</button>
      </span>
    </div>
    <ul v-if="node.children.length && !collapsed.has(node.menuId)" class="children">
      <MenuTreeItem v-for="(child, index) in node.children" :key="child.menuId" :node="child"
        :depth="depth + 1" :selected-id="selectedId" :collapsed="collapsed" :can-move="canMove"
        :first="index === 0" :last="index === node.children.length - 1"
        @select="emit('select', $event)" @toggle="emit('toggle', $event)"
        @move="(id, dir) => emit('move', id, dir)" />
    </ul>
  </li>
</template>

<style scoped>
.tree-item {
  list-style: none;
}

.children {
  margin: 0;
  padding: 0;
}

.row {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 0 6px 0 calc(6px + var(--depth) * 16px);
  border-left: 2px solid transparent;
}

.row:hover {
  background: var(--surface-hover);
}

.row.on {
  border-left-color: var(--accent);
  background: var(--accent-soft);
}

.twisty,
.twisty-space {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--ink-3);
  font-size: 11px;
  cursor: pointer;
}

.chevron {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-right: 1.5px solid currentColor;
  border-bottom: 1.5px solid currentColor;
  transform: translateY(-2px) rotate(45deg);
  transition: transform 0.12s;
}

.twisty[aria-expanded='false'] .chevron {
  transform: rotate(-45deg);
}

.label {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 4px;
  border: 0;
  background: none;
  color: var(--ink);
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
}

.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.name.dim {
  color: var(--ink-3);
  text-decoration: line-through;
}

.type {
  font-size: 10px;
  color: var(--ink-4);
}

.perm {
  font-size: 10px;
  color: var(--accent);
}

.moves {
  display: flex;
  visibility: hidden;
}

.row:hover .moves,
.row.on .moves,
.row:focus-within .moves {
  visibility: visible;
}

.move {
  width: 22px;
  height: 22px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 3px;
  background: none;
  color: var(--ink-3);
  cursor: pointer;
}

.move:hover:not(:disabled) {
  border-color: var(--rule-strong);
  background: var(--surface);
  color: var(--ink);
}

.move:disabled {
  opacity: 0.3;
  cursor: default;
}
</style>
