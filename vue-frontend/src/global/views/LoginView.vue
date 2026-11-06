<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { loginUrl, type LoginMethod } from '@/auth/api'
import { useAuthStore } from '@/auth/store'
import { hasMessages, setUiLocale } from '@/global/i18n'

/**
 * 로그인 화면. 로그인 자체는 Keycloak 화면에서 하고, 성공하면 서버가 returnTo 로 돌려보낸다.
 * (화면은 비밀번호·토큰을 다루지 않는다)
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n()
const checking = ref(true)

/** 같은 호스트의 절대 경로만 (서버도 같은 규칙으로 다시 검사) */
const returnTo = computed(() => {
  const value = typeof route.query.returnTo === 'string' ? route.query.returnTo : '/'
  return /^\/(?![/\\])/.test(value) && !value.startsWith('/login') ? value : '/'
})

const notice = computed(() => {
  if (route.query.expired) return t('login.expired')
  if (route.query.loggedOut) return t('login.loggedOut')
  return ''
})

function start(method: LoginMethod): void {
  window.location.assign(loginUrl(method, returnTo.value))
}

onMounted(async () => {
  // 로그인 전에는 지원 언어 API 를 못 쓰므로 브라우저 언어 중 번역본이 있는 언어로 표시
  const browser = navigator.language.slice(0, 2)
  if (hasMessages(browser)) {
    setUiLocale(browser)
  }
  try {
    if (await auth.load()) {
      await router.replace(returnTo.value)
      return
    }
  } catch {
    // 서버 미기동 등 - 로그인 버튼은 그대로 보여준다
  }
  checking.value = false
})
</script>

<template>
  <div class="login">
    <section class="ledger" aria-hidden="true">
      <div class="ledger-mark">
        <svg width="34" height="34" viewBox="0 0 32 32">
          <rect width="32" height="32" rx="6" fill="currentColor" />
          <path d="M8 10h16M8 16h11M8 22h7" stroke="var(--accent-ink)" stroke-width="2.6"
            stroke-linecap="round" />
        </svg>
        <span>flowlog</span>
      </div>
      <p class="ledger-line">{{ $t('login.tagline') }}</p>
    </section>

    <section class="panel-area">
      <div class="signin">
        <h1>{{ $t('login.title') }}</h1>
        <p class="lede">{{ $t('login.description') }}</p>

        <p v-if="notice" class="notice-line" role="status">{{ notice }}</p>

        <div class="actions">
          <button type="button" class="btn btn-primary btn-lg" :disabled="checking"
            data-test="login-oauth2" @click="start('oauth2')">{{ $t('login.oauth2') }}</button>
          <button type="button" class="btn btn-lg" :disabled="checking" data-test="login-saml2"
            @click="start('saml2')">{{ $t('login.saml2') }}</button>
        </div>

        <dl class="facts">
          <dt>{{ $t('login.oauth2') }}</dt>
          <dd>{{ $t('login.oauth2Hint') }}</dd>
          <dt>{{ $t('login.saml2') }}</dt>
          <dd>{{ $t('login.saml2Hint') }}</dd>
        </dl>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 6fr);
  min-height: 100vh;
}

/* 장부 줄무늬 : 선 간격 28px, 왼쪽 여백선 1개 */
.ledger {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: var(--space-10);
  background-color: var(--accent);
  background-image:
    linear-gradient(90deg, transparent 63px, rgb(246 244 236 / 0.22) 63px, rgb(246 244 236 / 0.22) 64px, transparent 64px),
    repeating-linear-gradient(180deg, transparent 0 27px, rgb(246 244 236 / 0.09) 27px 28px);
  color: var(--accent-ink);
}

.ledger-mark {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-left: 48px;
  color: var(--accent-ink);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.ledger-mark svg {
  color: rgb(246 244 236 / 0.16);
}

.ledger-line {
  max-width: 26ch;
  padding-left: 48px;
  font-size: var(--text-2xl);
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: -0.02em;
}

.panel-area {
  display: grid;
  place-items: center;
  padding: var(--space-10) var(--space-6);
}

.signin {
  width: min(400px, 100%);
}

.signin h1 {
  font-size: var(--text-2xl);
}

.lede {
  margin-top: var(--space-2);
  color: var(--ink-3);
}

.notice-line {
  margin-top: var(--space-5);
}

.actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-6);
}

.btn-lg {
  height: 44px;
  font-size: var(--text-md);
}

.facts {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px var(--space-4);
  margin: var(--space-8) 0 0;
  padding-top: var(--space-4);
  border-top: 1px solid var(--rule);
  font-size: var(--text-xs);
}

.facts dt {
  font-weight: 600;
  color: var(--ink-2);
  white-space: nowrap;
}

.facts dd {
  margin: 0;
  color: var(--ink-3);
}

@media (max-width: 860px) {
  .login {
    grid-template-columns: 1fr;
  }

  .ledger {
    min-height: 180px;
    padding: var(--space-6);
  }

  .ledger-line {
    font-size: var(--text-xl);
  }
}
</style>
