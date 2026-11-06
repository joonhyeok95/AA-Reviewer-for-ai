<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import LangBadges from '@/global/components/LangBadges.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { changeLang, resetSession } from '@/global/session'
import { useMenuStore } from '@/menu/store'

const props = defineProps<{ activeUrl?: string }>()
const emit = defineEmits<{ toggleNav: [] }>()

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const code = useCodeStore()
const menu = useMenuStore()
const feedback = useApiFeedback()

const userMenuOpen = ref(false)
const changingLang = ref(false)
const userMenu = ref<HTMLElement | null>(null)

/** 브레드크럼 : 메뉴 트리 경로 (메뉴에 없는 화면은 화면 제목) */
const trail = computed(() => {
  const names = props.activeUrl ? menu.findTrail(props.activeUrl).map((node) => node.menuNm) : []
  if (!names.length && route.meta.title) {
    names.push(t(route.meta.title))
  }
  return names
})

const langBadges = computed(() => code.langs.map((lang) => ({ langCd: lang.langCd,
  badge: lang.badge })))

async function onLang(langCd: string): Promise<void> {
  if (changingLang.value || langCd === auth.user?.langCd) return
  changingLang.value = true
  try {
    await changeLang(langCd)
  } catch (error) {
    feedback.failure(error)
  } finally {
    changingLang.value = false
  }
}

async function onLogout(): Promise<void> {
  userMenuOpen.value = false
  try {
    await auth.logout()
  } catch (error) {
    feedback.failure(error)
  }
  resetSession()
  await router.replace({ name: 'login', query: { loggedOut: '1' } })
}

function onDocumentClick(event: MouseEvent): void {
  if (userMenu.value && !userMenu.value.contains(event.target as Node)) {
    userMenuOpen.value = false
  }
}

function onKey(event: KeyboardEvent): void {
  if (event.key === 'Escape') userMenuOpen.value = false
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <header class="topbar">
    <button type="button" class="btn btn-quiet btn-sm nav-toggle" :aria-label="$t('nav.menu')"
      @click="emit('toggleNav')">☰</button>
    <ol class="crumbs" :aria-label="$t('nav.breadcrumb')">
      <li v-for="(name, index) in trail" :key="index">{{ name }}</li>
    </ol>
    <div class="tools">
      <div class="lang" :aria-busy="changingLang">
        <span class="sr-only">{{ $t('nav.language') }}</span>
        <LangBadges :langs="langBadges" :active="auth.user?.langCd" selectable
          @select="onLang" />
      </div>
      <div ref="userMenu" class="user">
        <button type="button" class="user-btn" :aria-expanded="userMenuOpen" aria-haspopup="menu"
          data-test="user-menu" @click="userMenuOpen = !userMenuOpen">
          <span class="avatar" aria-hidden="true">{{ auth.displayName.slice(0, 1) }}</span>
          <span class="user-name">{{ auth.displayName }}</span>
        </button>
        <div v-if="userMenuOpen" class="menu" role="menu">
          <div class="menu-head">
            <strong>{{ auth.displayName }}</strong>
            <span class="mono">{{ auth.user?.userId }}</span>
            <span class="muted">{{ $t(`auth.type.${auth.user?.authType ?? 'OTHER'}`) }}</span>
          </div>
          <RouterLink role="menuitem" to="/me" class="menu-item" @click="userMenuOpen = false">
            {{ $t('profile.title') }}</RouterLink>
          <RouterLink role="menuitem" to="/org" class="menu-item" @click="userMenuOpen = false">
            {{ $t('org.title') }}</RouterLink>
          <button type="button" role="menuitem" class="menu-item" data-test="logout"
            @click="onLogout">{{ $t('auth.logout') }}</button>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  height: var(--topbar-h);
  padding: 0 var(--space-6) 0 var(--space-8);
  border-bottom: 1px solid var(--rule);
  background: color-mix(in srgb, var(--paper) 92%, transparent);
  backdrop-filter: blur(6px);
}

.nav-toggle {
  display: none;
}

.crumbs {
  display: flex;
  flex: 1;
  min-width: 0;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-sm);
  color: var(--ink-3);
}

.crumbs li {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.crumbs li + li::before {
  content: '/';
  margin: 0 8px;
  color: var(--ink-4);
}

.crumbs li:last-child {
  color: var(--ink);
  font-weight: 600;
}

.tools {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.lang[aria-busy='true'] {
  opacity: 0.5;
  pointer-events: none;
}

.user {
  position: relative;
}

.user-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px 0 4px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: none;
  cursor: pointer;
}

.user-btn:hover,
.user-btn[aria-expanded='true'] {
  border-color: var(--rule);
  background: var(--surface);
}

.avatar {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--accent);
  color: var(--accent-ink);
  font-size: 12px;
  font-weight: 700;
}

.user-name {
  font-size: var(--text-sm);
  font-weight: 550;
}

.menu {
  position: absolute;
  right: 0;
  top: calc(100% + 6px);
  min-width: 220px;
  padding: 6px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-overlay);
}

.menu-head {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 8px 10px 10px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--rule);
  font-size: var(--text-sm);
}

.menu-head .muted {
  font-size: var(--text-xs);
}

.menu-item {
  display: block;
  width: 100%;
  padding: 7px 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--ink);
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
}

.menu-item:hover {
  background: var(--surface-sunken);
  text-decoration: none;
}

@media (max-width: 960px) {
  .topbar {
    padding: 0 var(--space-4);
  }

  .nav-toggle {
    display: inline-flex;
  }

  .user-name {
    display: none;
  }
}
</style>
