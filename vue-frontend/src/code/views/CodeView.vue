<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/auth/store'
import { fetchCodes } from '@/code/api'
import type { Code } from '@/code/types'
import EmptyState from '@/global/components/EmptyState.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'

/**
 * 공통코드 조회 (GET /api/v1/codes/{groupCd}). 코드명은 사용자 언어로 온다.
 * 그룹 목록 API 가 없으므로 그룹코드를 입력해 조회한다. (아래 목록은 입력 도우미)
 */
const KNOWN_GROUPS = ['LANG', 'MODULE', 'ROLE_TYPE', 'USER_TYPE', 'MENU_VIEW_TYPE',
  'NOTICE_CATEGORY', 'NOTICE_TAG', 'MAIL_SEND_TYPE', 'MAIL_TARGET_TYPE', 'MAIL_STATUS']

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const feedback = useApiFeedback()

const groupCd = ref(typeof route.query.group === 'string' ? route.query.group : 'NOTICE_CATEGORY')
const codes = ref<Code[]>([])
const loadedGroup = ref('')
const loading = ref(false)

async function search(): Promise<void> {
  const group = groupCd.value.trim().toUpperCase()
  if (!group) return
  loading.value = true
  try {
    codes.value = await fetchCodes(group)
    loadedGroup.value = group
    if (route.query.group !== group) {
      await router.replace({ query: { group } })
    }
  } catch (error) {
    feedback.failure(error)
  } finally {
    loading.value = false
  }
}

// 언어를 바꾸면 코드명이 바뀌므로 다시 조회
watch(() => auth.user?.langCd, () => loadedGroup.value && search())
onMounted(search)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>{{ $t('code.title') }}</h1>
        <p class="lede">{{ $t('code.description') }}</p>
      </div>
    </header>

    <form class="filter-bar" @submit.prevent="search">
      <div class="field grow">
        <label class="field-label" for="group-cd">{{ $t('code.groupCd') }}</label>
        <input id="group-cd" v-model="groupCd" class="input mono" list="known-groups" maxlength="50"
          autocomplete="off" spellcheck="false">
        <datalist id="known-groups">
          <option v-for="group in KNOWN_GROUPS" :key="group" :value="group" />
        </datalist>
      </div>
      <button type="submit" class="btn btn-primary" :disabled="loading">{{ $t('common.search') }}</button>
    </form>

    <div class="groups">
      <button v-for="group in KNOWN_GROUPS" :key="group" type="button" class="group-chip mono"
        :class="{ on: loadedGroup === group }" @click="groupCd = group; search()">{{ group }}</button>
    </div>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th class="col-num">{{ $t('code.sortOrder') }}</th>
            <th>{{ $t('code.code') }}</th>
            <th>{{ $t('code.codeNm') }}</th>
            <th>{{ $t('code.attr1') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in codes" :key="item.code">
            <td class="col-num">{{ item.sortOrder }}</td>
            <td class="mono">{{ item.code }}</td>
            <td>{{ item.codeNm }}</td>
            <td class="mono muted">{{ item.attr1 ?? '' }}</td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="!codes.length && !loading && loadedGroup" :title="$t('code.empty')" />
    </div>
    <p class="table-foot">
      <span>{{ loadedGroup }} · {{ $t('common.count', { n: codes.length }) }}</span>
    </p>
  </div>
</template>

<style scoped>
.groups {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: var(--space-4);
}

.group-chip {
  height: 26px;
  padding: 0 9px;
  border: 1px solid var(--rule);
  border-radius: 3px;
  background: var(--surface);
  color: var(--ink-2);
  font-size: 11.5px;
  cursor: pointer;
}

.group-chip:hover {
  border-color: var(--rule-strong);
}

.group-chip.on {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}
</style>
