<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import { changeLang } from '@/global/session'
import { useToastStore } from '@/global/stores/toast'

/** 내 정보 : GET /auth/me + 언어 설정 (PUT /users/me/lang) */
const auth = useAuthStore()
const code = useCodeStore()
const toast = useToastStore()
const feedback = useApiFeedback()
const { t } = useI18n()
const saving = ref(false)

const user = computed(() => auth.user)
const scopes = computed(() => user.value?.authorities.filter((a) => a.startsWith('SCOPE_')) ?? [])
const roles = computed(() => user.value?.authorities.filter((a) => a.startsWith('ROLE_')) ?? [])

async function onLang(langCd: string): Promise<void> {
  if (saving.value || langCd === user.value?.langCd) return
  saving.value = true
  try {
    await changeLang(langCd)
    toast.push('success', t('profile.langSaved'), code.langOf(langCd)?.langNm)
  } catch (error) {
    feedback.failure(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-if="user" class="page">
    <header class="page-head">
      <div>
        <h1>{{ $t('profile.title') }}</h1>
        <p class="lede">{{ $t('profile.description') }}</p>
      </div>
    </header>

    <div class="profile-grid">
      <section class="panel panel-pad">
        <h2 class="section-title">{{ $t('profile.account') }}</h2>
        <dl class="dl">
          <dt>{{ $t('profile.userId') }}</dt>
          <dd class="mono">{{ user.userId }}</dd>
          <dt>{{ $t('profile.userNm') }}</dt>
          <dd>{{ user.userNm ?? '—' }}</dd>
          <dt>{{ $t('profile.authType') }}</dt>
          <dd>{{ $t(`auth.type.${user.authType}`) }}</dd>
          <dt>{{ $t('profile.clientId') }}</dt>
          <dd class="mono">{{ user.clientId ?? '—' }}</dd>
          <dt>{{ $t('profile.registered') }}</dt>
          <dd>{{ user.registered ? $t('common.yes') : $t('common.no') }}</dd>
        </dl>
      </section>

      <section class="panel panel-pad">
        <h2 class="section-title">{{ $t('profile.language') }}</h2>
        <p class="field-hint">{{ $t('profile.languageHint') }}</p>
        <div class="lang-options" role="radiogroup" :aria-label="$t('profile.language')"
          :aria-busy="saving">
          <label v-for="lang in code.langs" :key="lang.langCd" class="lang-option"
            :class="{ on: user.langCd === lang.langCd }">
            <input type="radio" name="lang" :value="lang.langCd" :checked="user.langCd === lang.langCd"
              :disabled="saving" :data-test="`profile-lang-${lang.langCd}`"
              @change="onLang(lang.langCd)">
            <span class="lang-name">{{ lang.langNm }}</span>
            <span class="mono muted">{{ lang.editorLocale }}</span>
            <span v-if="lang.defaultLang" class="tag">{{ $t('profile.defaultLang') }}</span>
          </label>
        </div>
        <p class="field-hint source">
          {{ user.userLangCd ? $t('profile.langFromUser') : $t('profile.langFromBrowser') }}
        </p>
      </section>

      <section class="panel span-2">
        <h2 class="section-title pad">{{ $t('profile.roles') }}</h2>
        <table class="table">
          <thead>
            <tr>
              <th>{{ $t('profile.roleCd') }}</th>
              <th>{{ $t('profile.roleType') }}</th>
              <th class="col-num">{{ $t('profile.roleLevel') }}</th>
              <th>{{ $t('profile.module') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="role in user.roles" :key="role.roleCd">
              <td class="mono">{{ role.roleCd }}</td>
              <td>{{ role.roleTypeCd }}</td>
              <td class="col-num">{{ role.roleLevel }}</td>
              <td>{{ role.moduleCd ?? '—' }}</td>
            </tr>
            <tr v-if="!user.roles.length">
              <td colspan="4" class="muted">{{ $t('profile.noRoles') }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="panel panel-pad span-2">
        <h2 class="section-title">{{ $t('profile.authorities') }}</h2>
        <p class="field-hint">{{ $t('profile.authoritiesHint') }}</p>
        <div class="chips">
          <span v-for="scope in scopes" :key="scope" class="tag tag-accent mono">{{ scope }}</span>
          <span v-for="role in roles" :key="role" class="tag mono">{{ role }}</span>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5);
  align-items: start;
}

.span-2 {
  grid-column: 1 / -1;
}

.pad {
  padding: var(--space-4) var(--space-5) 0;
}

.lang-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: var(--space-3);
}

.lang-options[aria-busy='true'] {
  opacity: 0.6;
}

.lang-option {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px 12px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.lang-option:hover {
  border-color: var(--rule-strong);
}

.lang-option.on {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.lang-option input {
  accent-color: var(--accent);
}

.lang-name {
  flex: 1;
  font-weight: 600;
}

.lang-option .mono {
  font-size: var(--text-xs);
}

.source {
  margin-top: var(--space-3);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: var(--space-3);
}

@media (max-width: 860px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
