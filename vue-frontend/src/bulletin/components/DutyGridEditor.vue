<script setup lang="ts">
import { addDays } from '@/calendar/dates'
import RowTools from '@/global/components/RowTools.vue'
import { moveItem, removeItem } from '@/global/util/list'
import type { DutyGrid } from '@/bulletin/types'

/**
 * 섬김위원 표 편집 : 행 = 역할(식사·설거지·청소·안내), 열 = 날짜, 칸 = 담당자.
 * [+ 역할] 행 추가, [+ 날짜] 마지막 날짜 +7일 열 추가.
 */
const grid = defineModel<DutyGrid>({ required: true })
defineProps<{ highlight?: string }>()

function addRole(): void {
  grid.value.roles.push({ roleNm: '', members: {} })
}

function addDate(): void {
  const last = grid.value.dates.at(-1)
  grid.value.dates.push(last ? addDays(last, 7) : '')
}

function changeDate(index: number, value: string): void {
  const before = grid.value.dates[index]
  grid.value.dates[index] = value
  for (const role of grid.value.roles) {
    if (before in role.members) {
      role.members[value] = role.members[before]
      delete role.members[before]
    }
  }
}

function removeDate(index: number): void {
  const [day] = grid.value.dates.splice(index, 1)
  for (const role of grid.value.roles) delete role.members[day]
}
</script>

<template>
  <div class="duty">
    <div class="table-wrap">
      <table class="table duty-table">
        <thead>
          <tr>
            <th class="role-col">{{ $t('bulletin.duty.role') }}</th>
            <th v-for="(day, di) in grid.dates" :key="di" :class="{ current: day === highlight }">
              <div class="date-head">
                <input :value="day" class="input date" type="date" :aria-label="$t('bulletin.duty.date')"
                  @change="changeDate(di, ($event.target as HTMLInputElement).value)">
                <button type="button" class="x" :aria-label="$t('bulletin.duty.removeDate')"
                  :title="$t('bulletin.duty.removeDate')" @click="removeDate(di)">&times;</button>
              </div>
            </th>
            <th class="col-shrink"><span class="sr-only">{{ $t('common.actions') }}</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(role, ri) in grid.roles" :key="ri" data-test="duty-row">
            <td class="role-col"><input v-model="role.roleNm" class="input" maxlength="50"
              :aria-label="$t('bulletin.duty.role')" data-test="duty-role"></td>
            <td v-for="day in grid.dates" :key="day" :class="{ current: day === highlight }">
              <input v-model="role.members[day]" class="input" maxlength="200"
                :aria-label="`${role.roleNm} ${day}`" data-test="duty-cell">
            </td>
            <td class="col-shrink">
              <RowTools :index="ri" :length="grid.roles.length" :label="$t('bulletin.duty.role')"
                @up="moveItem(grid.roles, ri, -1)" @down="moveItem(grid.roles, ri, 1)"
                @remove="removeItem(grid.roles, ri)" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="duty-actions">
      <button type="button" class="btn btn-sm" data-test="duty-add-role" @click="addRole">+ {{ $t('bulletin.duty.addRole') }}</button>
      <button type="button" class="btn btn-sm" data-test="duty-add-date" @click="addDate">+ {{ $t('bulletin.duty.addDate') }}</button>
    </div>
  </div>
</template>

<style scoped>
.duty-table th,
.duty-table td {
  padding: 6px;
  vertical-align: middle;
}

.role-col {
  width: 130px;
}

.date-head {
  display: flex;
  align-items: center;
  gap: 2px;
}

.date {
  min-width: 140px;
}

.x {
  border: 0;
  background: none;
  color: var(--ink-3);
  font-size: var(--text-md);
  cursor: pointer;
}

.x:hover {
  color: var(--danger);
}

.current {
  background: var(--accent-soft);
}

.duty-actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-2);
}
</style>
