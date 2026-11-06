<script setup lang="ts">
import { ref, watch } from 'vue'
import { useCodeStore } from '@/code/store'
import EmptyState from '@/global/components/EmptyState.vue'
import UiDialog from '@/global/components/UiDialog.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { formatDateTime } from '@/global/util/format'
import { fetchMailHistories } from '@/notice/api'
import type { NoticeMailHist } from '@/notice/types'

/** 메일 발송 이력 (수신자별 언어, 결과) - notice.edit 권한 */
const props = defineProps<{ open: boolean; noticeId: number }>()
const emit = defineEmits<{ close: [] }>()

const code = useCodeStore()
const feedback = useApiFeedback()
const items = ref<NoticeMailHist[]>([])
const loaded = ref(false)

watch(() => props.open, async (open) => {
  if (!open) return
  loaded.value = false
  try {
    await code.getCodes('MAIL_STATUS')
    items.value = await fetchMailHistories(props.noticeId)
  } catch (error) {
    feedback.failure(error)
  } finally {
    loaded.value = true
  }
})
</script>

<template>
  <UiDialog :open="open" :title="$t('notice.mail.historyTitle')" width="720px" @close="emit('close')">
    <table v-if="items.length" class="table">
      <thead>
        <tr>
          <th>{{ $t('notice.mail.receiver') }}</th>
          <th class="col-shrink">{{ $t('notice.mail.lang') }}</th>
          <th class="col-shrink">{{ $t('notice.mail.status') }}</th>
          <th class="col-date">{{ $t('notice.mail.sendDt') }}</th>
          <th>{{ $t('notice.mail.error') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.histId">
          <td class="mono">{{ item.receiverId }}</td>
          <td class="col-shrink mono">{{ item.langCd.toUpperCase() }}</td>
          <td class="col-shrink">
            <span class="tag" :class="item.statusCd === 'SUCCESS' ? 'tag-accent' : 'tag-danger'">
              {{ code.codeName('MAIL_STATUS', item.statusCd) }}</span>
          </td>
          <td class="col-date">{{ formatDateTime(item.sendDt) }}</td>
          <td class="muted">{{ item.errorMsg }}</td>
        </tr>
      </tbody>
    </table>
    <EmptyState v-else-if="loaded" :title="$t('notice.mail.noHistory')" />
  </UiDialog>
</template>
