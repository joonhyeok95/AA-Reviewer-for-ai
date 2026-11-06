<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/auth/store'
import type { Action } from '@/auth/types'
import EmptyState from '@/global/components/EmptyState.vue'
import LangBadges from '@/global/components/LangBadges.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { formatDate, today } from '@/global/util/format'
import { resolveMenuLink } from '@/menu/link'
import { useMenuStore } from '@/menu/store'
import type { MenuNode } from '@/menu/types'
import { fetchNotices } from '@/notice/api'
import type { NoticeSummary } from '@/notice/types'

const auth = useAuthStore()
const menu = useMenuStore()
const feedback = useApiFeedback()

const ACTIONS: Action[] = ['list', 'add', 'edit', 'del']
const notices = ref<NoticeSummary[]>([])
const noticesLoaded = ref(false)
const canNotice = computed(() => auth.can('notice', 'list'))

/** 권한 표 : authorities 의 SCOPE_{리소스}.{작업} 을 리소스별로 묶는다 */
const permissionRows = computed(() => {
  const resources = new Map<string, Set<string>>()
  for (const authority of auth.user?.authorities ?? []) {
    const match = /^SCOPE_([a-z0-9-]+)\.(list|add|edit|del)$/.exec(authority)
    if (match) {
      const [, resource, action] = match
      if (!resources.has(resource!)) resources.set(resource!, new Set())
      resources.get(resource!)!.add(action!)
    }
  }
  return [...resources.entries()].sort(([a], [b]) => a.localeCompare(b))
    .map(([resource, actions]) => ({ resource, actions }))
})

/** 바로가기 : 이동 가능한 말단 메뉴 */
const shortcuts = computed(() => {
  const result: { menu: MenuNode; to: string }[] = []
  const walk = (nodes: MenuNode[]) => nodes.forEach((node) => {
    const link = resolveMenuLink(node)
    if (link.kind === 'route') result.push({ menu: node, to: link.to })
    walk(node.children)
  })
  walk(menu.menus)
  return result.slice(0, 8)
})

onMounted(async () => {
  if (!canNotice.value) return
  try {
    notices.value = (await fetchNotices({}, { page: 0, size: 5 })).content
  } catch (error) {
    feedback.failure(error)
  } finally {
    noticesLoaded.value = true
  }
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>{{ auth.displayName }}</h1>
        <p class="lede num">
          {{ today() }} · {{ $t(`auth.type.${auth.user?.authType ?? 'OTHER'}`) }}
          <template v-if="auth.user?.roles.length">
            · {{ auth.user.roles.map((role) => role.roleCd).join(', ') }}
          </template>
        </p>
      </div>
    </header>

    <div class="home-grid">
      <section v-if="canNotice" class="panel">
        <header class="block-head">
          <h2 class="section-title">{{ $t('home.recentNotices') }}</h2>
          <RouterLink to="/support/notices" class="more">{{ $t('common.viewAll') }}</RouterLink>
        </header>
        <ul v-if="notices.length" class="notice-list">
          <li v-for="notice in notices" :key="notice.noticeId">
            <RouterLink :to="`/support/notices/${notice.noticeId}`" class="notice-link">
              <span v-if="notice.tagNm" class="tag tag-danger">{{ notice.tagNm }}</span>
              <span class="title">{{ notice.title }}</span>
              <LangBadges :langs="notice.langs" :active="notice.displayLangCd" />
              <span class="date num">{{ formatDate(notice.postDate) }}</span>
            </RouterLink>
          </li>
        </ul>
        <EmptyState v-else-if="noticesLoaded" :title="$t('notice.empty')" />
      </section>

      <section class="panel">
        <header class="block-head">
          <h2 class="section-title">{{ $t('home.myPermissions') }}</h2>
          <RouterLink to="/me" class="more">{{ $t('profile.title') }}</RouterLink>
        </header>
        <table v-if="permissionRows.length" class="table perm">
          <thead>
            <tr>
              <th>{{ $t('home.resource') }}</th>
              <th v-for="action in ACTIONS" :key="action" class="col-shrink">
                {{ $t(`action.${action}`) }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in permissionRows" :key="row.resource">
              <td class="mono">{{ row.resource }}</td>
              <td v-for="action in ACTIONS" :key="action" class="mark">
                <span v-if="row.actions.has(action)" class="on"
                  :aria-label="$t('home.allowed')">●</span>
                <span v-else class="off" :aria-label="$t('home.denied')">·</span>
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-else :title="auth.isSysAdmin ? $t('home.sysAdmin') : $t('home.noPermission')" />
      </section>

      <section v-if="shortcuts.length" class="panel span-2">
        <header class="block-head">
          <h2 class="section-title">{{ $t('home.shortcuts') }}</h2>
        </header>
        <ul class="shortcuts">
          <li v-for="item in shortcuts" :key="item.menu.menuId">
            <RouterLink :to="item.to">{{ item.menu.menuNm }}</RouterLink>
            <span class="mono muted">{{ item.menu.menuUrl }}</span>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
.home-grid {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: var(--space-5);
  align-items: start;
}

.span-2 {
  grid-column: 1 / -1;
}

.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5) 0;
}

.more {
  font-size: var(--text-xs);
}

.notice-list {
  margin: 0;
  padding: 0 0 var(--space-2);
  list-style: none;
}

.notice-link {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 10px var(--space-5);
  border-top: 1px solid var(--rule);
  color: var(--ink);
  font-size: var(--text-sm);
}

.notice-link:hover {
  background: var(--surface-hover);
  text-decoration: none;
}

.notice-link .title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-link .date {
  color: var(--ink-3);
  font-size: var(--text-xs);
}

.perm {
  margin-top: var(--space-2);
}

.perm th:first-child,
.perm td:first-child {
  padding-left: var(--space-5);
}

.mark {
  text-align: center;
}

.mark .on {
  color: var(--accent);
  font-size: 10px;
}

.mark .off {
  color: var(--ink-4);
}

.shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  margin: 0;
  padding: var(--space-3) var(--space-5) var(--space-5);
  list-style: none;
  gap: var(--space-2) var(--space-5);
}

.shortcuts li {
  display: flex;
  flex-direction: column;
  padding: 8px 0;
  border-bottom: 1px solid var(--rule);
  font-size: var(--text-sm);
}

.shortcuts .mono {
  font-size: var(--text-xs);
}

@media (max-width: 960px) {
  .home-grid {
    grid-template-columns: 1fr;
  }
}
</style>
