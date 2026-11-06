<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { fetchOrgTree } from '@/user/api'
import OrgDeptNode from '@/user/components/OrgDeptNode.vue'
import type { OrgDept, OrgWorkplace } from '@/user/types'
import type { MailTarget } from '@/notice/types'

/**
 * 메일 대상 선택 (인사 마스터 조직도). 코드만 저장하고 수신자는 발송 시점에 서버가 계산한다.
 *  - 사업장 선택 = 그 사업장 전체
 *  - 부서 선택 = 그 부서 (하위 포함 선택 가능)
 */
const props = defineProps<{ modelValue: MailTarget[]; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: MailTarget[]] }>()

const feedback = useApiFeedback()
const tree = ref<OrgWorkplace[]>([])

const names = computed(() => {
  const map = new Map<string, string>()
  const walk = (depts: OrgDept[]) => depts.forEach((dept) => {
    map.set(`DEPT:${dept.deptCd}`, dept.deptNm)
    walk(dept.children)
  })
  tree.value.forEach((wp) => {
    map.set(`WORKPLACE:${wp.workplaceCd}`, wp.workplaceNm)
    walk(wp.depts)
  })
  return map
})

function find(type: MailTarget['targetType'], cd: string): MailTarget | undefined {
  return props.modelValue.find((t) => t.targetType === type && t.targetCd === cd)
}

function toggle(type: MailTarget['targetType'], cd: string): void {
  if (props.disabled) return
  const next = find(type, cd)
    ? props.modelValue.filter((t) => !(t.targetType === type && t.targetCd === cd))
    : [...props.modelValue, { targetType: type, targetCd: cd, includeSubYn: type === 'DEPT' ? 'Y' : 'N' } as MailTarget]
  emit('update:modelValue', next)
}

function toggleSub(cd: string): void {
  if (props.disabled) return
  emit('update:modelValue', props.modelValue.map((t) => t.targetType === 'DEPT' && t.targetCd === cd
    ? { ...t, includeSubYn: t.includeSubYn === 'Y' ? 'N' : 'Y' } : t))
}

const isDeptSelected = (cd: string) => find('DEPT', cd) !== undefined
const deptIncludesSub = (cd: string) => find('DEPT', cd)?.includeSubYn === 'Y'

function label(target: MailTarget): string {
  return names.value.get(`${target.targetType}:${target.targetCd}`) ?? target.targetNm ?? target.targetCd
}

onMounted(async () => {
  try {
    tree.value = await fetchOrgTree()
  } catch (error) {
    feedback.failure(error)
  }
})
</script>

<template>
  <div class="picker" :class="{ disabled }">
    <div class="selected" aria-live="polite">
      <span v-if="!modelValue.length" class="muted">{{ $t('notice.mail.noTargets') }}</span>
      <span v-for="target in modelValue" :key="`${target.targetType}:${target.targetCd}`" class="chip">
        <span class="chip-type">{{ $t(`notice.mail.targetType.${target.targetType}`) }}</span>
        {{ label(target) }}
        <span v-if="target.targetType === 'DEPT' && target.includeSubYn === 'Y'" class="muted">
          {{ $t('org.withSub') }}</span>
        <button v-if="!disabled" type="button" class="chip-x"
          :aria-label="$t('common.remove')" @click="toggle(target.targetType, target.targetCd)">×</button>
      </span>
    </div>
    <div class="tree">
      <section v-for="wp in tree" :key="wp.workplaceCd" class="wp">
        <label class="check wp-row">
          <input type="checkbox" :checked="find('WORKPLACE', wp.workplaceCd) !== undefined"
            :disabled="disabled" :data-test="`org-wp-${wp.workplaceCd}`"
            @change="toggle('WORKPLACE', wp.workplaceCd)">
          <strong>{{ wp.workplaceNm }}</strong>
          <span class="muted all">{{ $t('org.wholeWorkplace') }}</span>
        </label>
        <ul class="depts">
          <OrgDeptNode v-for="dept in wp.depts" :key="dept.deptCd" :dept="dept" :depth="0" selectable
            :disabled="disabled" :is-selected="isDeptSelected" :includes-sub="deptIncludesSub"
            @toggle="toggle('DEPT', $event)" @toggle-sub="toggleSub" />
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
.picker {
  border: 1px solid var(--rule-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
}

.picker.disabled {
  background: var(--surface-sunken);
}

.selected {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 44px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--rule);
  font-size: var(--text-sm);
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 4px 0 8px;
  border: 1px solid var(--rule-strong);
  border-radius: 3px;
  background: var(--paper);
  font-size: var(--text-xs);
}

.chip-type {
  color: var(--accent);
  font-weight: 650;
}

.chip-x {
  border: 0;
  background: none;
  color: var(--ink-3);
  font-size: 15px;
  line-height: 1;
  cursor: pointer;
}

.tree {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--space-4);
  max-height: 320px;
  padding: var(--space-3) var(--space-4) var(--space-4);
  overflow-y: auto;
}

.wp-row {
  padding: 6px 0;
  border-bottom: 1px solid var(--rule-strong);
  width: 100%;
}

.all {
  margin-left: auto;
  font-size: var(--text-xs);
}

.depts {
  margin: 0;
  padding: 0;
}
</style>
