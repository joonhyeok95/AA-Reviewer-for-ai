<script setup lang="ts">
import { watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import ToastHost from '@/global/components/ToastHost.vue'

const route = useRoute()
const { t, locale } = useI18n()

// 문서 제목 = 화면 제목 · Flowlog (언어 변경 시 갱신)
watchEffect(() => {
  void locale.value
  const key = route.meta.title
  document.title = key ? `${t(key)} · Flowlog` : 'Flowlog'
})
</script>

<template>
  <RouterView />
  <ToastHost />
</template>
