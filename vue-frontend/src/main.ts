import { createPinia } from 'pinia'
import { createApp } from 'vue'
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import App from './App.vue'
import { onUnauthorized } from './global/api/http'
import { i18n } from './global/i18n'
import { router } from './global/router'
import { resetSession } from './global/session'
import './global/styles/tokens.css'
import './global/styles/base.css'
import './global/styles/ui.css'

const app = createApp(App)
app.use(createPinia())
app.use(i18n)
app.use(router)

// 세션 만료(401) → 로그인 화면 (현재 화면으로 돌아오도록 returnTo)
onUnauthorized(() => {
  const current = router.currentRoute.value
  if (current.meta.public) {
    return
  }
  resetSession()
  void router.replace({ name: 'login', query: { returnTo: current.fullPath, expired: '1' } })
})

app.mount('#app')
