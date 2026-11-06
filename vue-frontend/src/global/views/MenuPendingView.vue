<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import EmptyState from '@/global/components/EmptyState.vue'
import { useMenuStore } from '@/menu/store'

/**
 * 등록된 화면이 없는 경로.
 *  - 내 메뉴에 있는 URL : 메뉴는 있지만 화면이 아직 개발되지 않음 ("준비 중")
 *  - 메뉴에도 없는 URL : 없는 화면 (404)
 */
const route = useRoute()
const menu = useMenuStore()

const trail = computed(() => menu.findTrail(route.path))
const target = computed(() => trail.value.at(-1))
</script>

<template>
  <div class="page">
    <template v-if="target">
      <header class="page-head">
        <div>
          <h1>{{ target.menuNm }}</h1>
          <p class="lede mono">{{ target.menuUrl }}</p>
        </div>
      </header>
      <EmptyState :title="$t('pending.title')" :description="$t('pending.content')" />
    </template>
    <EmptyState v-else :title="$t('error.notFound.title')" :description="$t('error.notFound.content')">
      <RouterLink to="/" class="btn">{{ $t('nav.home') }}</RouterLink>
    </EmptyState>
  </div>
</template>
