<script setup lang="ts">
import EmptyState from '@/global/components/EmptyState.vue'
</script>

<template>
  <div class="page">
    <EmptyState :title="$t('error.forbidden.title')" :description="$t('error.forbidden.content')">
      <RouterLink to="/" class="btn">{{ $t('nav.home') }}</RouterLink>
    </EmptyState>
  </div>
</template>
