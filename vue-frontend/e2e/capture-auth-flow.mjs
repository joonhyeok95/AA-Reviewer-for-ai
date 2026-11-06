/**
 * 인증·인가 Workflow 단계별 화면 캡처 (문서용).
 *
 * 준비 : run-e2e.mjs 와 같음 (Redis, Keycloak, 백엔드, Gateway 9909, 화면)
 * 실행 : node e2e/capture-auth-flow.mjs
 * 결과 : e2e/screenshots/auth-flow/NN-*.png
 *
 * 테스트 계정 비밀번호는 local realm import 파일에서 읽는다. (local 전용 계정)
 */
import { mkdirSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:9909'
const HERE = dirname(fileURLToPath(import.meta.url))
const SHOTS = join(HERE, 'screenshots', 'auth-flow')
const REALM = join(HERE, '../../spring-boilerplate/infra/keycloak/realm/flowlog-realm.json')
mkdirSync(SHOTS, { recursive: true })

const realm = JSON.parse(readFileSync(REALM, 'utf-8'))
const passwordOf = (username) => realm.users.find((u) => u.username === username)
  .credentials.find((c) => c.type === 'password').value

const browser = await chromium.launch({ channel: 'msedge', headless: true })

async function newPage() {
  const context = await browser.newContext({ locale: 'ko-KR',
    viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 })
  return context.newPage()
}

async function shot(page, name, fullPage = false) {
  await page.waitForTimeout(400)
  await page.screenshot({ path: join(SHOTS, `${name}.png`), fullPage })
  console.log(`saved ${name}.png`)
}

async function closePopups(page) {
  for (let i = 0; i < 5; i += 1) {
    const close = page.locator('[data-test="popup-close"]')
    if (!(await close.waitFor({ timeout: 2000 }).then(() => true).catch(() => false))) return
    await close.click()
  }
}

/** JSON 응답을 읽기 좋게 보여 주는 페이지로 캡처 */
async function shotJson(page, name, title, method, path) {
  const xsrf = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const response = await page.request.fetch(`${BASE}${path}`, {
    method, headers: method === 'GET' ? {} : { 'X-XSRF-TOKEN': xsrf, 'Content-Type': 'application/json' },
    data: method === 'GET' ? undefined : '{}',
  })
  const body = await response.text()
  let pretty = body
  try { pretty = JSON.stringify(JSON.parse(body), null, 2) } catch { /* 원문 */ }
  const view = await page.context().newPage()
  await view.setContent(`<html><body style="font-family:Consolas,monospace;margin:24px;background:#fafafa">
    <div style="font:600 18px sans-serif;margin-bottom:8px"></div>
    <div style="font:14px sans-serif;color:#444;margin-bottom:12px"></div>
    <pre style="background:#fff;border:1px solid #ddd;padding:16px;font-size:14px;white-space:pre-wrap"></pre>
    </body></html>`)
  await view.evaluate(([t, line, json]) => {
    const [h, sub] = document.querySelectorAll('div')
    h.textContent = t
    sub.textContent = line
    document.querySelector('pre').textContent = json
  }, [title, `${method} ${path} → HTTP ${response.status()}`, pretty])
  await view.setViewportSize({ width: 1000, height: 700 })
  await view.screenshot({ path: join(SHOTS, `${name}.png`), fullPage: true })
  console.log(`saved ${name}.png (${response.status()})`)
  await view.close()
}

async function login(page, username, path = '/', method = 'oauth2', capture = null) {
  await page.goto(`${BASE}${path}`)
  await page.waitForURL(/\/login/)
  if (capture) await shot(page, capture.login)
  await page.click(`[data-test="login-${method}"]`)
  await page.waitForSelector('#username')
  await page.fill('#username', username)
  await page.fill('#password', passwordOf(username))
  if (capture) await shot(page, capture.keycloak)
  await page.click('#kc-login')
  await page.waitForURL((url) => url.origin === new URL(BASE).origin
    && !url.pathname.startsWith('/login') && !url.pathname.startsWith('/cm/'))
  await closePopups(page)
}

// 0. 미로그인 API 호출 → 401
const anon = await newPage()
await anon.goto(`${BASE}/login`)
await shotJson(anon, '00-anonymous-401', '미로그인 상태의 API 호출 → 401 (세션 없음)', 'GET',
  '/cm/api/v1/notices')

// 1~4. board.writer : 로그인 → 원래 화면 복귀 → 권한
const writer = await newPage()
await login(writer, 'board.writer', '/support/notices', 'oauth2',
  { login: '01-login-returnTo', keycloak: '02-keycloak-login' })
await writer.waitForSelector('[data-test="notice-row"]')
await shot(writer, '03-writer-returned-notices')
await writer.goto(`${BASE}/`)
await writer.waitForTimeout(800)
await shot(writer, '04-writer-home-permissions', true)
await writer.goto(`${BASE}/me`)
await writer.waitForTimeout(800)
await shot(writer, '05-writer-profile', true)
await shotJson(writer, '06-writer-auth-me', 'board.writer 의 /auth/me - 최종 권한 (scope ∩ DB)',
  'GET', '/cm/api/v1/auth/me')
await writer.goto(`${BASE}/support/notices/1`)
await writer.waitForSelector('[data-test="notice-title"]')
await shot(writer, '07-writer-detail-no-delete')

// 8. board.manager : 삭제 버튼
const manager = await newPage()
await login(manager, 'board.manager', '/support/notices/1')
await manager.waitForSelector('[data-test="notice-delete"]')
await shot(manager, '08-manager-detail-delete')

// 9~11. board.reader : 버튼 없음 → 직접 접근 거부 → API 우회 거부
const reader = await newPage()
await login(reader, 'board.reader', '/support/notices')
await reader.waitForSelector('[data-test="notice-row"]')
await shot(reader, '09-reader-notices-no-create')
await reader.goto(`${BASE}/support/notices/new`)
await reader.waitForURL(/\/forbidden/)
await shot(reader, '10-reader-forbidden')
await shotJson(reader, '11-reader-api-403', 'board.reader 가 버튼을 우회해 등록 API 호출 → 403',
  'POST', '/cm/api/v1/notices')

// 12. board.limited : Keycloak 4개, DB 조회만 → list 만
const limited = await newPage()
await login(limited, 'board.limited', '/')
await shotJson(limited, '12-limited-auth-me',
  'board.limited - Keycloak 역할 4개지만 DB 권한이 조회뿐 → SCOPE_board.list 만', 'GET',
  '/cm/api/v1/auth/me')

// 13~14. admin : 권한관리 화면에서 DB 권한 확인
const admin = await newPage()
await login(admin, 'admin', '/system/roles')
await admin.waitForSelector('[data-test="role-BOARD_WRITER"]')
await admin.click('[data-test="role-BOARD_WRITER"]')
await admin.click('[data-test="role-tab-menus"]')
await admin.waitForSelector('[data-test="rm-15-listYn"]')
await shot(admin, '13-admin-role-board-writer-menus', true)
const grantsTab = admin.locator('[data-test="role-tab-grants"]')
if (await grantsTab.count()) {
  await grantsTab.click()
  await admin.waitForTimeout(800)
  await shot(admin, '14-admin-role-board-writer-grants', true)
}

// 15. 로그아웃 → 로그인 화면, 세션 폐기
await writer.click('[data-test="user-menu"]')
await writer.click('[data-test="logout"]')
await writer.waitForURL(/\/login/)
await shot(writer, '15-logout')

// 16. SAML 로그인
const saml = await newPage()
await login(saml, 'tx.manager', '/me', 'saml2')
await saml.waitForTimeout(800)
await shot(saml, '16-saml-profile', true)

await browser.close()
