<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { matchMenuUrl } from '@/menu/link'
import { useMenuStore } from '@/menu/store'
import NoticePopupHost from '@/notice/components/NoticePopupHost.vue'
import SideNavItem from './SideNavItem.vue'
import TopBar from './TopBar.vue'

/**
 * 로그인 후 기본 레이아웃 : 좌측 메뉴(GET /menus/me) + 상단 바 + 본문.
 * 메뉴는 서버가 사용자 권한·언어로 걸러서 주므로 화면은 그대로 그린다.
 */
const route = useRoute()
const menu = useMenuStore()
const navOpen = ref(false)

const activeUrl = computed(() => matchMenuUrl(route.path, menu.menus))

watch(() => route.fullPath, () => {
  navOpen.value = false
})
</script>

<template>
  <div class="shell" :class="{ 'nav-open': navOpen }">
    <a class="skip" href="#main">{{ $t('nav.skip') }}</a>
    <aside class="side" :aria-label="$t('nav.menu')">
      <RouterLink to="/" class="brand" :aria-label="$t('nav.home')">
        <svg width="22" height="22" viewBox="0 0 32 32" aria-hidden="true">
          <rect width="32" height="32" rx="6" fill="currentColor" />
          <path d="M8 10h16M8 16h11M8 22h7" stroke="var(--accent-ink)" stroke-width="2.6"
            stroke-linecap="round" />
        </svg>
        <span>flowlog</span>
      </RouterLink>
      <nav class="nav">
        <ul class="nav-list">
          <SideNavItem v-for="item in menu.mainMenus" :key="item.menuId" :menu="item" :depth="0"
            :active-url="activeUrl" @navigate="navOpen = false" />
        </ul>
        <ul v-if="menu.sideMenus.length" class="nav-list nav-side">
          <SideNavItem v-for="item in menu.sideMenus" :key="item.menuId" :menu="item" :depth="1"
            :active-url="activeUrl" @navigate="navOpen = false" />
        </ul>
      </nav>
    </aside>
    <div class="scrim" @click="navOpen = false" />
    <div class="body">
      <TopBar :active-url="activeUrl" @toggle-nav="navOpen = !navOpen" />
      <main id="main" tabindex="-1">
        <RouterView />
      </main>
    </div>
    <NoticePopupHost />
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: var(--sidebar-w) minmax(0, 1fr);
  min-height: 100vh;
}

.skip {
  position: absolute;
  left: -999px;
  top: 8px;
  z-index: 300;
  padding: 6px 10px;
  background: var(--ink);
  color: var(--surface);
}

.skip:focus {
  left: 8px;
}

.side {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
  border-right: 1px solid var(--rule);
  background: var(--paper);
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  height: var(--topbar-h);
  padding: 0 16px;
  color: var(--accent);
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.brand span {
  color: var(--ink);
}

.brand:hover {
  text-decoration: none;
}

.nav {
  padding: 4px 0 var(--space-8);
}

.nav-list {
  margin: 0;
  padding: 0;
}

.nav-side {
  margin-top: var(--space-5);
  padding-top: var(--space-3);
  border-top: 1px solid var(--rule);
}

.body {
  min-width: 0;
}

main {
  outline: none;
}

.scrim {
  display: none;
}

@media (max-width: 960px) {
  .shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .side {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 120;
    width: var(--sidebar-w);
    transform: translateX(-100%);
    transition: transform 0.18s;
  }

  .nav-open .side {
    transform: none;
  }

  .nav-open .scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 110;
    background: rgb(27 29 26 / 0.3);
  }
}
</style>
