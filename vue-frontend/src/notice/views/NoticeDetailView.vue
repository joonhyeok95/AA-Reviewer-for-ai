<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/auth/store'
import ConfirmDialog from '@/global/components/ConfirmDialog.vue'
import EmptyState from '@/global/components/EmptyState.vue'
import LangBadges from '@/global/components/LangBadges.vue'
import { ApiError } from '@/global/api/ApiError'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { formatDate, formatDateTime, formatFileSize } from '@/global/util/format'
import { sanitizeHtml } from '@/global/util/html'
import { deleteNotice, fetchNotice, noticeFileUrl } from '@/notice/api'
import MailHistoryDialog from '@/notice/components/MailHistoryDialog.vue'
import type { NoticeDetail } from '@/notice/types'

/**
 * 공지 상세. 표시 언어 = ?lang(전환) → 사용자 언어 → 대표 언어.
 * 요청 언어 버전이 없으면 대표 언어 버전과 함께 안내 문구를 보여준다. (fallback)
 */
const props = defineProps<{ id: number }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const feedback = useApiFeedback()
const { t } = useI18n()

const notice = ref<NoticeDetail | null>(null)
const notFound = ref(false)
const deleteOpen = ref(false)
const deleteBusy = ref(false)
const historyOpen = ref(false)

const lang = computed(() => typeof route.query.lang === 'string' ? route.query.lang : undefined)
const requestedLang = computed(() => lang.value ?? auth.user?.langCd ?? '')
const canEdit = computed(() => auth.can('board', 'edit'))
const canDel = computed(() => auth.can('board', 'del'))
const html = computed(() => notice.value ? sanitizeHtml(notice.value.contentHtml) : '')

async function load(): Promise<void> {
  try {
    notice.value = await fetchNotice(props.id, lang.value)
    notFound.value = false
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound.value = true
      return
    }
    feedback.failure(error)
  }
}

function switchLang(langCd: string): void {
  void router.replace({ query: { ...route.query, lang: langCd } })
}

async function confirmDelete(): Promise<void> {
  deleteBusy.value = true
  try {
    feedback.success(await deleteNotice(props.id))
    await router.push('/support/notices')
  } catch (error) {
    feedback.failure(error)
  } finally {
    deleteBusy.value = false
    deleteOpen.value = false
  }
}

watch([() => props.id, lang, () => auth.user?.langCd], load, { immediate: true })
</script>

<template>
  <div class="page">
    <EmptyState v-if="notFound" :title="$t('notice.notFound')">
      <RouterLink to="/support/notices" class="btn">{{ $t('common.backToList') }}</RouterLink>
    </EmptyState>

    <article v-else-if="notice" class="article">
      <RouterLink to="/support/notices" class="back">← {{ $t('notice.title') }}</RouterLink>

      <header class="article-head">
        <p class="kicker">
          <span>{{ notice.categoryNm }}</span>
          <span v-if="notice.tagNm" class="tag tag-danger">{{ notice.tagNm }}</span>
          <span v-if="notice.popupYn === 'Y'" class="tag tag-info">
            {{ $t('notice.popupTag') }} {{ formatDate(notice.popupStartDt) }} ~ {{ formatDate(notice.popupEndDt) }}</span>
        </p>
        <h1 data-test="notice-title">{{ notice.title }}</h1>
        <dl class="meta num">
          <div><dt>{{ $t('notice.postDate') }}</dt><dd>{{ formatDateTime(notice.postDate) }}</dd></div>
          <div><dt>{{ $t('notice.author') }}</dt><dd>{{ notice.createdBy }}</dd></div>
          <div><dt>{{ $t('notice.views') }}</dt><dd>{{ notice.viewCnt }}</dd></div>
          <div><dt>{{ $t('common.updatedAt') }}</dt><dd>{{ formatDateTime(notice.updatedAt) }}</dd></div>
        </dl>
      </header>

      <div class="lang-bar">
        <span class="lang-label">{{ $t('notice.availableLangs') }}</span>
        <LangBadges :langs="notice.langs" :active="notice.displayLangCd" selectable
          @select="switchLang" />
        <span class="muted base">{{ $t('notice.baseLang', { lang: notice.baseLangCd.toUpperCase() }) }}</span>
      </div>

      <p v-if="notice.fallback" class="notice-line warn" data-test="notice-fallback">
        {{ t('notice.fallbackLine', { requested: requestedLang.toUpperCase(),
                                      shown: notice.displayLangCd.toUpperCase() }) }}
      </p>

      <div class="notice-content body" data-test="notice-content" v-html="html" />

      <section v-if="notice.files.length" class="files">
        <h2 class="section-title">{{ $t('notice.files') }} <span class="muted num">{{ notice.files.length }}</span></h2>
        <ul>
          <li v-for="file in notice.files" :key="file.fileId">
            <a :href="noticeFileUrl(notice.noticeId, file.fileId)" download>{{ file.orgFileNm }}</a>
            <span class="muted num">{{ formatFileSize(file.fileSize) }}</span>
          </li>
        </ul>
      </section>

      <footer class="article-foot">
        <RouterLink to="/support/notices" class="btn">{{ $t('common.backToList') }}</RouterLink>
        <span class="spacer" />
        <button v-if="canEdit" type="button" class="btn" data-test="notice-mail-history"
          @click="historyOpen = true">{{ $t('notice.mail.history') }}</button>
        <RouterLink v-if="canEdit" :to="`/support/notices/${notice.noticeId}/edit`" class="btn"
          data-test="notice-edit">{{ $t('common.edit') }}</RouterLink>
        <button v-if="canDel" type="button" class="btn btn-danger" data-test="notice-delete"
          @click="deleteOpen = true">{{ $t('common.delete') }}</button>
      </footer>
    </article>

    <ConfirmDialog :open="deleteOpen" :title="$t('notice.deleteTitle')"
      :message="$t('notice.deleteConfirm')" :confirm-label="$t('common.delete')" danger
      :busy="deleteBusy" @close="deleteOpen = false" @confirm="confirmDelete" />
    <MailHistoryDialog :open="historyOpen" :notice-id="id" @close="historyOpen = false" />
  </div>
</template>

<style scoped>
.article {
  max-width: 820px;
}

.back {
  display: inline-block;
  margin-bottom: var(--space-4);
  font-size: var(--text-sm);
  color: var(--ink-3);
}

.article-head {
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--rule-strong);
}

.kicker {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  font-size: var(--text-sm);
  color: var(--accent);
  font-weight: 600;
}

.article-head h1 {
  font-size: var(--text-2xl);
  line-height: 1.3;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-6);
  margin: var(--space-4) 0 0;
  font-size: var(--text-sm);
}

.meta div {
  display: flex;
  gap: 6px;
}

.meta dt {
  color: var(--ink-3);
}

.meta dd {
  margin: 0;
}

.lang-bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--rule);
  font-size: var(--text-sm);
}

.lang-label {
  color: var(--ink-3);
}

.base {
  margin-left: auto;
  font-size: var(--text-xs);
}

.notice-line {
  margin-top: var(--space-4);
}

.body {
  padding: var(--space-6) 0 var(--space-8);
}

.files {
  padding: var(--space-4) var(--space-5);
  background: var(--surface-sunken);
  border-radius: var(--radius-md);
}

.files ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.files li {
  display: flex;
  gap: var(--space-3);
  padding: 4px 0;
  font-size: var(--text-sm);
}

.article-foot {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-6);
  padding-top: var(--space-4);
  border-top: 1px solid var(--rule);
}

.spacer {
  flex: 1;
}
</style>
