<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '@/auth/store'
import EmptyState from '@/global/components/EmptyState.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { fetchOrgTree } from '@/user/api'
import OrgDeptNode from '@/user/components/OrgDeptNode.vue'
import type { OrgDept, OrgWorkplace } from '@/user/types'

/** 조직도 (인사 마스터 사업장 → 부서). 명칭은 사용자 언어 (ko = 국문, 그 외 = 영문명) */
const auth = useAuthStore()
const feedback = useApiFeedback()
const tree = ref<OrgWorkplace[]>([])
const loaded = ref(false)

const countDepts = (depts: OrgDept[]): number =>
  depts.reduce((sum, dept) => sum + 1 + countDepts(dept.children), 0)

const total = computed(() => tree.value.reduce((sum, wp) => sum + countDepts(wp.depts), 0))

async function load(): Promise<void> {
  try {
    tree.value = await fetchOrgTree()
  } catch (error) {
    feedback.failure(error)
  } finally {
    loaded.value = true
  }
}

watch(() => auth.user?.langCd, load)
onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h1>{{ $t('org.title') }}</h1>
        <p class="lede">{{ $t('org.description') }}</p>
      </div>
      <p class="muted num summary">
        {{ $t('org.summary', { workplaces: tree.length, depts: total }) }}</p>
    </header>

    <div v-if="tree.length" class="workplaces">
      <section v-for="workplace in tree" :key="workplace.workplaceCd" class="panel panel-pad">
        <header class="wp-head">
          <h2>{{ workplace.workplaceNm }}</h2>
          <span class="mono muted">{{ workplace.workplaceCd }}</span>
        </header>
        <ul class="tree">
          <OrgDeptNode v-for="dept in workplace.depts" :key="dept.deptCd" :dept="dept" :depth="0" />
        </ul>
        <p v-if="!workplace.depts.length" class="muted">{{ $t('org.noDepts') }}</p>
      </section>
    </div>
    <EmptyState v-else-if="loaded" :title="$t('org.empty')" />
  </div>
</template>

<style scoped>
.summary {
  font-size: var(--text-sm);
}

.workplaces {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-5);
  align-items: start;
}

.wp-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--rule-strong);
}

.wp-head h2 {
  font-size: var(--text-lg);
}

.wp-head .mono {
  font-size: var(--text-xs);
}

.tree {
  margin: 0;
  padding: 0;
}
</style>
