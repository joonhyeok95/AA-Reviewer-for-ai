<script setup lang="ts">
import { computed } from 'vue'
import EmptyState from '@/global/components/EmptyState.vue'
import { useMenuStore } from '@/menu/store'

/**
 * 본문형(CONTENT) 메뉴. 메뉴 본문(TB_MENU_LANG.MENU_CONTENT, 언어별)을 보여줄 화면이다.
 * 현재 백엔드에 본문 조회 API 가 없어 메뉴 이름과 안내만 표시한다.
 */
const props = defineProps<{ menuId: number }>()
const menu = useMenuStore()
const target = computed(() => menu.findById(props.menuId))
</script>

<template>
  <div class="page">
    <template v-if="target?.viewTypeCd === 'CONTENT'">
      <header class="page-head">
        <h1>{{ target.menuNm }}</h1>
      </header>
      <EmptyState :title="$t('pending.title')" :description="$t('pending.contentMenu')" />
    </template>
    <EmptyState v-else :title="$t('error.notFound.title')" :description="$t('error.notFound.content')" />
  </div>
</template>
