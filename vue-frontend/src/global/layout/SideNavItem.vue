<script setup lang="ts">
import { computed } from 'vue'
import { isSafeExternalUrl, resolveMenuLink } from '@/menu/link'
import type { MenuNode } from '@/menu/types'

/** 메뉴 트리 항목 (재귀). depth 0 은 섹션 제목으로 쓴다. */
const props = defineProps<{ menu: MenuNode; depth: number; activeUrl?: string }>()
const emit = defineEmits<{ navigate: [] }>()

const link = computed(() => resolveMenuLink(props.menu))
const active = computed(() => link.value.kind === 'route' && props.menu.menuUrl === props.activeUrl)
</script>

<template>
  <li class="item" :class="`depth-${depth}`">
    <RouterLink v-if="link.kind === 'route'" :to="link.to" class="link"
      :class="{ active }" :aria-current="active ? 'page' : undefined"
      @click="emit('navigate')">
      <span class="label">{{ menu.menuNm }}</span>
      <span v-if="menu.adminMenu" class="admin">{{ $t('nav.admin') }}</span>
    </RouterLink>
    <a v-else-if="link.kind === 'external' && isSafeExternalUrl(link.href)" :href="link.href"
      class="link" :target="link.newWindow ? '_blank' : undefined"
      :rel="link.newWindow ? 'noopener noreferrer' : undefined">
      <span class="label">{{ menu.menuNm }}</span>
      <span class="ext" aria-hidden="true">↗</span>
    </a>
    <span v-else class="group">{{ menu.menuNm }}</span>
    <ul v-if="menu.children.length" class="children">
      <SideNavItem v-for="child in menu.children" :key="child.menuId" :menu="child"
        :depth="depth + 1" :active-url="activeUrl" @navigate="emit('navigate')" />
    </ul>
  </li>
</template>

<style scoped>
.item {
  list-style: none;
}

.children {
  margin: 0;
  padding: 0;
}

.link,
.group {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 5px 12px 5px calc(14px + var(--indent, 0px));
  border-left: 2px solid transparent;
  font-size: var(--text-sm);
  line-height: 1.35;
}

.link {
  color: var(--ink-2);
}

.link:hover {
  background: var(--surface-sunken);
  color: var(--ink);
  text-decoration: none;
}

.link.active {
  border-left-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 650;
}

.group {
  color: var(--ink-3);
  font-weight: 600;
}

.depth-0 > .group {
  margin-top: var(--space-4);
  font-size: var(--text-xs);
  color: var(--ink-3);
  letter-spacing: 0.02em;
}

.depth-1 {
  --indent: 0px;
}

.depth-2 {
  --indent: 12px;
}

.depth-3 {
  --indent: 24px;
}

.admin {
  padding: 0 4px;
  border: 1px solid var(--rule-strong);
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
  color: var(--ink-3);
}

.ext {
  font-size: 11px;
  color: var(--ink-4);
}
</style>
