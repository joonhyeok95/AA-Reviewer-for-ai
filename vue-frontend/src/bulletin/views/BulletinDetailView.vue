<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import ConfirmDialog from '@/global/components/ConfirmDialog.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { formatDate } from '@/global/util/format'
import { bulletinPdfUrl, deleteBulletin, fetchBulletin } from '@/bulletin/api'
import BulletinPaper from '@/bulletin/components/BulletinPaper.vue'
import type { BulletinDetail } from '@/bulletin/types'

/** 주보 보기 + 수정 / 다음 주 주보 만들기 / 삭제 / 인쇄 */
const props = defineProps<{ id: number }>()
const auth = useAuthStore()
const code = useCodeStore()
const feedback = useApiFeedback()
const router = useRouter()
const { t } = useI18n()

const bulletin = ref<BulletinDetail | null>(null)
const deleting = ref(false)
const busy = ref(false)
const canAdd = computed(() => auth.can('bulletin', 'add'))
const canEdit = computed(() => auth.can('bulletin', 'edit'))
const canDel = computed(() => auth.can('bulletin', 'del'))

async function load(): Promise<void> {
  try {
    await code.getCodes('BULLETIN_STATUS')
    bulletin.value = await fetchBulletin(props.id)
  } catch (error) {
    feedback.failure(error)
  }
}

async function remove(): Promise<void> {
  busy.value = true
  try {
    feedback.success(await deleteBulletin(props.id))
    await router.push({ name: 'bulletin-list' })
  } catch (error) {
    feedback.failure(error)
  } finally {
    busy.value = false
  }
}

function print(): void {
  window.print()
}

watch(() => props.id, load)
watch(() => auth.user?.langCd, () => {
  code.clearCodes()
  void load()
})
onMounted(load)
</script>

<template>
  <div class="page wide">
    <header class="page-head">
      <div>
        <RouterLink class="back" :to="{ name: 'bulletin-list' }">&larr; {{ $t('common.backToList') }}</RouterLink>
        <h1 v-if="bulletin">{{ formatDate(bulletin.worshipDt) }} {{ $t('bulletin.title') }}
          <span class="tag" :class="bulletin.statusCd === 'PUBLISHED' ? 'tag-accent' : 'tag-warn'">
            {{ code.codeName('BULLETIN_STATUS', bulletin.statusCd) }}</span></h1>
      </div>
      <div v-if="bulletin" class="page-actions no-print">
        <a class="btn btn-primary" :href="bulletinPdfUrl(bulletin.bulletinId)" download
          data-test="bulletin-pdf">{{ $t('bulletin.pdf') }}</a>
        <button type="button" class="btn" @click="print">{{ $t('bulletin.print') }}</button>
        <RouterLink v-if="canAdd" class="btn" data-test="bulletin-next-week"
          :to="{ name: 'bulletin-new', query: { from: bulletin.bulletinId } }">{{ $t('bulletin.nextWeek') }}</RouterLink>
        <RouterLink v-if="canEdit" class="btn btn-primary" data-test="bulletin-edit"
          :to="{ name: 'bulletin-edit', params: { id: bulletin.bulletinId } }">{{ $t('common.edit') }}</RouterLink>
        <button v-if="canDel" type="button" class="btn btn-danger" data-test="bulletin-delete"
          @click="deleting = true">{{ $t('common.delete') }}</button>
      </div>
    </header>
    <BulletinPaper v-if="bulletin" :bulletin="bulletin" />
    <!-- 인쇄용 A4 2장 사본 (화면 밖, 인쇄할 때만 보임) -->
    <Teleport to="body">
      <BulletinPaper v-if="bulletin" :bulletin="bulletin" print />
    </Teleport>
    <ConfirmDialog :open="deleting" :title="$t('common.delete')"
      :message="t('bulletin.deleteConfirm', { date: bulletin ? formatDate(bulletin.worshipDt) : '' })"
      :confirm-label="$t('common.delete')" danger :busy="busy"
      @close="deleting = false" @confirm="remove" />
  </div>
</template>

<style scoped>
.back {
  color: var(--ink-3);
  font-size: var(--text-sm);
}

h1 .tag {
  margin-left: var(--space-2);
  vertical-align: middle;
}

@media print {
  .no-print {
    display: none;
  }
}
</style>
