/**
 * 화면 E2E - 실제 Keycloak + spring-boilerplate + spring-gateway + 화면(Vite) 을 브라우저로 검증한다.
 *
 * 준비 : Redis, Keycloak 컨테이너 / spring-boilerplate(9091) / spring-gateway(9909) / npm run dev(5173)
 * 실행 : npm run e2e           (기본 Edge, E2E_BROWSER=chrome 이면 Chrome)
 *        E2E_HEADED=1 npm run e2e   (브라우저 표시)
 * 결과 : 콘솔 PASS/FAIL, 화면 캡처 e2e/screenshots/*.png
 *
 * 테스트 계정 비밀번호는 코드에 쓰지 않고 local realm import 파일에서 읽는다. (local 전용 계정)
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:9909'
const HERE = dirname(fileURLToPath(import.meta.url))
const SHOTS = join(HERE, 'screenshots')
const REALM = process.env.E2E_REALM_FILE
  ?? join(HERE, '../../spring-boilerplate/infra/keycloak/realm/flowlog-realm.json')
mkdirSync(SHOTS, { recursive: true })

const realm = JSON.parse(readFileSync(REALM, 'utf-8'))
function passwordOf(username) {
  const user = realm.users.find((u) => u.username === username)
  const credential = user?.credentials?.find((c) => c.type === 'password')
  if (!credential) throw new Error(`realm 파일에 ${username} 비밀번호가 없음`)
  return credential.value
}

const results = []
const cspViolations = []
function check(name, ok, detail = '') {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  (${detail})` : ''}`)
}

let currentStep = ''
async function step(name, fn) {
  currentStep = name
  try {
    await fn()
  } catch (error) {
    check(name, false, String(error.message ?? error).split('\n')[0])
  }
}

const browser = await chromium.launch({
  channel: process.env.E2E_BROWSER === 'chrome' ? 'chrome' : 'msedge',
  headless: !process.env.E2E_HEADED,
})

async function newPage(locale = 'ko-KR') {
  const context = await browser.newContext({ locale, viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1 })
  const page = await context.newPage()
  page.on('pageerror', (error) => {
    const stack = (error.stack ?? '').split('\n').slice(1, 5).join(' | ')
    console.log(`  [pageerror] (${currentStep}) ${error.message}  ${stack}`)
  })
  page.on('console', (message) => {
    if (message.type() === 'error' && /Content Security Policy/i.test(message.text())) {
      cspViolations.push(message.text())
    }
  })
  return page
}

/** 로그인 화면 → Keycloak → 화면 복귀 */
async function login(page, username, method = 'oauth2', path = '/') {
  await page.goto(`${BASE}${path}`)
  await page.waitForURL(/\/login/)
  await page.click(`[data-test="login-${method}"]`)
  await page.waitForSelector('#username')
  await page.fill('#username', username)
  await page.fill('#password', passwordOf(username))
  await page.click('#kc-login')
  await page.waitForURL((url) => url.origin === new URL(BASE).origin && !url.pathname.startsWith('/login')
    && !url.pathname.startsWith('/cm/'))
}

/** 사용자 언어 설정 (이전 실행이 바꾼 값을 되돌려 반복 실행 가능하게) */
async function setUserLang(page, langCd) {
  const xsrf = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? ''
  const response = await page.request.put(`${BASE}/cm/api/v1/users/me/lang`, {
    data: { langCd }, headers: { 'X-XSRF-TOKEN': xsrf },
  })
  if (!response.ok()) throw new Error(`언어 설정 실패 ${response.status()}`)
}

async function closePopups(page) {
  for (let i = 0; i < 5; i += 1) {
    const close = page.locator('[data-test="popup-close"]')
    if (!(await close.waitFor({ timeout: 2500 }).then(() => true).catch(() => false))) return
    await close.click()
  }
}

async function shot(page, name) {
  await page.screenshot({ path: join(SHOTS, `${name}.png`), fullPage: true })
}

async function typeEditor(page, langCd, text) {
  const editor = page.locator(`[data-test="editor-${langCd}"] .ProseMirror`)
  await editor.click()
  await editor.pressSequentially(text)
}

const stamp = Date.now().toString().slice(-6)

// ------------------------------------------------------------------
// 1. 관리자 (admin, 시스템관리자) - 전체 기능
// ------------------------------------------------------------------
const admin = await newPage()
await step('admin 로그인', async () => {
  await admin.goto(`${BASE}/support/notices`)
  await admin.waitForURL(/\/login\?returnTo=/)
  check('미로그인 → 로그인 화면 (returnTo 유지)',
    new URL(admin.url()).searchParams.get('returnTo') === '/support/notices', admin.url())
  await shot(admin, '01-login')
  await login(admin, 'admin', 'oauth2', '/support/notices')
  check('Keycloak 로그인 후 원래 화면(/support/notices)으로 복귀', new URL(admin.url()).pathname === '/support/notices',
    admin.url())
})

await step('팝업 공지', async () => {
  const popup = admin.locator('dialog[open]')
  const visible = await popup.waitFor({ timeout: 5000 }).then(() => true).catch(() => false)
  check('로그인 후 팝업 공지 표시', visible)
  if (visible) await shot(admin, '02-popup')
  await closePopups(admin)
  // 이전 실행이 바꾼 언어 설정을 되돌린다 (반복 실행)
  await setUserLang(admin, 'ko')
  await admin.reload()
})

await step('공지 목록', async () => {
  await admin.waitForSelector('[data-test="notice-row"]')
  const rows = await admin.locator('[data-test="notice-row"]').count()
  check('공지 목록 조회 (언어 배지, 대체 표시 포함)', rows >= 3, `rows=${rows}`)
  const fallback = await admin.locator('[data-test="notice-row"] .fallback').count()
  check('ko 사용자 - ko 버전 없는 공지는 "원문 EN" 표시', fallback >= 1, `fallback=${fallback}`)
  await shot(admin, '03-notice-list')
  await admin.fill('[data-test="notice-keyword"]', '점검')
  await admin.click('[data-test="notice-search"]')
  await admin.waitForURL(/keyword=/)
  await admin.waitForTimeout(400)
  const searched = await admin.locator('[data-test="notice-row"]').count()
  check('검색어 조회 (URL 에 조건 유지)', searched >= 1 && admin.url().includes('keyword='), `rows=${searched}`)
})

await step('공지 상세 - 언어 전환/대체', async () => {
  await admin.goto(`${BASE}/support/notices/2?lang=en`)
  await admin.waitForSelector('[data-test="notice-title"]')
  const fallbackLine = await admin.locator('[data-test="notice-fallback"]').isVisible()
  check('EN 요청 + EN 버전 없음 → 대표 언어 버전 + 안내 문구', fallbackLine)
  await admin.goto(`${BASE}/support/notices/1`)
  await admin.waitForSelector('[data-test="notice-title"]')
  const koTitle = await admin.locator('[data-test="notice-title"]').innerText()
  check('표시 언어 = 사용자 언어(KO)', await admin.locator('.lang-bar [data-test="lang-ko"]').getAttribute('aria-pressed') === 'true')
  await admin.click('.lang-bar [data-test="lang-en"]')
  await admin.waitForURL(/lang=en/)
  await admin.waitForFunction((before) =>
    document.querySelector('[data-test="notice-title"]')?.textContent?.trim() !== before, koTitle)
  const enTitle = await admin.locator('[data-test="notice-title"]').innerText()
  check('언어 배지로 EN 버전 전환', enTitle !== koTitle, `${koTitle} → ${enTitle}`)
  await shot(admin, '04-notice-detail-en')
})

let createdId = 0
await step('공지 등록 (2개 언어 + 첨부)', async () => {
  await admin.goto(`${BASE}/support/notices/new`)
  await admin.waitForSelector('[data-test="title-ko"]')
  await admin.fill('[data-test="title-ko"]', `E2E 화면 공지 ${stamp}`)
  await typeEditor(admin, 'ko', '화면 E2E 로 등록한 공지입니다.')
  await admin.click('[data-test="tab-en"]')
  await admin.fill('[data-test="title-en"]', `E2E screen notice ${stamp}`)
  await typeEditor(admin, 'en', 'Created by the screen E2E.')
  const upload = join(HERE, 'sample-upload.txt')
  writeFileSync(upload, `e2e ${stamp}\n`)
  await admin.setInputFiles('[data-test="file-input"]', upload)
  await shot(admin, '05-notice-form')
  await admin.click('[data-test="notice-save"]')
  await admin.waitForURL(/\/support\/notices\/\d+$/)
  createdId = Number(new URL(admin.url()).pathname.split('/').pop())
  const title = await admin.locator('[data-test="notice-title"]').innerText()
  const files = await admin.locator('.files li').count()
  check('등록 → 상세 이동, 제목·첨부 1건', title.includes(stamp) && files === 1,
    `id=${createdId} files=${files}`)
})

await step('공지 수정 (EN 제목 변경)', async () => {
  await admin.click('[data-test="notice-edit"]')
  await admin.waitForSelector('[data-test="title-en"]', { state: 'attached' })
  await admin.click('[data-test="tab-en"]')
  await admin.fill('[data-test="title-en"]', `E2E edited ${stamp}`)
  await admin.click('[data-test="notice-save"]')
  await admin.waitForURL(new RegExp(`/support/notices/${createdId}$`))
  await admin.goto(`${BASE}/support/notices/${createdId}?lang=en`)
  await admin.waitForSelector('[data-test="notice-title"]')
  const title = await admin.locator('[data-test="notice-title"]').innerText()
  check('수정 후 EN 버전 제목 반영', title === `E2E edited ${stamp}`, title)
})

await step('메일 대상 지정 + 이력', async () => {
  await admin.goto(`${BASE}/support/notices/${createdId}/edit`)
  await admin.waitForSelector('[data-test="mail-send-type"]')
  await admin.selectOption('[data-test="mail-send-type"]', 'TARGET')
  await admin.click('[data-test="org-dept-TF_TEAM"]')
  await shot(admin, '06-mail-targets')
  await admin.click('[data-test="notice-save"]')
  await admin.waitForURL(new RegExp(`/support/notices/${createdId}$`))
  await admin.goto(`${BASE}/support/notices/${createdId}/edit`)
  await admin.waitForSelector('.picker .chip')
  const chips = await admin.locator('.picker .chip').count()
  check('대상 지정 메일 (부서 TF_TEAM) 저장', chips === 1, `chips=${chips}`)
  await admin.goto(`${BASE}/support/notices/${createdId}`)
  await admin.click('[data-test="notice-mail-history"]')
  const opened = await admin.locator('dialog[open]').isVisible()
  check('메일 이력 대화상자', opened)
  await admin.keyboard.press('Escape')
})

await step('공지 삭제', async () => {
  await admin.goto(`${BASE}/support/notices/${createdId}`)
  await admin.click('[data-test="notice-delete"]')
  await admin.click('[data-test="confirm"]')
  await admin.waitForURL(/\/support\/notices$/)
  const response = await admin.request.get(`${BASE}/cm/api/v1/notices/${createdId}`)
  check('삭제 후 상세 404', response.status() === 404, `status=${response.status()}`)
})

await step('입력 검증 (제목만 입력)', async () => {
  await admin.goto(`${BASE}/support/notices/new`)
  await admin.waitForSelector('[data-test="title-ko"]')
  await admin.fill('[data-test="title-ko"]', '제목만')
  await admin.click('[data-test="notice-save"]')
  const error = await admin.locator('.pane-error').isVisible()
  check('제목만 입력한 언어 → 화면에서 오류 안내 (저장 안 함)', error && admin.url().endsWith('/new'))
  admin.once('dialog', (dialog) => dialog.accept())
})

await step('거래 목록 (샘플 CRUD)', async () => {
  await admin.goto(`${BASE}/tx/trades/list`)
  await admin.waitForSelector('[data-test="sample-row"]')
  await admin.click('[data-test="sample-create"]')
  await admin.fill('[data-test="sample-name"]', `E2E 거래 ${stamp}`)
  await admin.click('[data-test="sample-save"]')
  await admin.locator('[data-test="toast-success"]').first().waitFor()
  await admin.fill('[data-test="sample-search-name"]', stamp)
  await admin.click('[data-test="sample-search"]')
  await admin.waitForTimeout(500)
  const rows = await admin.locator('[data-test="sample-row"]').count()
  check('거래 등록 → 검색', rows === 1, `rows=${rows}`)
  await shot(admin, '07-sample-list')
  await admin.click('[data-test="sample-edit"]')
  await admin.fill('[data-test="sample-name"]', `E2E 거래 수정 ${stamp}`)
  await admin.click('[data-test="sample-save"]')
  await admin.waitForTimeout(500)
  const name = await admin.locator('[data-test="sample-row"] .name').innerText()
  check('거래 수정', name.includes('수정'), name)
  await admin.click('[data-test="sample-delete"]')
  await admin.click('[data-test="confirm"]')
  await admin.waitForTimeout(600)
  const after = await admin.locator('[data-test="sample-row"]').count()
  check('거래 삭제', after === 0, `rows=${after}`)
})

await step('공통코드 / 조직도 / 내 정보', async () => {
  await admin.goto(`${BASE}/system/codes?group=NOTICE_CATEGORY`)
  await admin.waitForSelector('.table tbody tr')
  const codes = await admin.locator('.table tbody tr').count()
  check('공통코드 조회 (NOTICE_CATEGORY)', codes >= 3, `rows=${codes}`)
  await admin.goto(`${BASE}/org`)
  await admin.waitForSelector('.workplaces .panel')
  const wps = await admin.locator('.workplaces .panel').count()
  check('조직도 (사업장 → 부서)', wps >= 2, `workplaces=${wps}`)
  await shot(admin, '08-org')
  await admin.goto(`${BASE}/me`)
  await admin.waitForSelector('.chips .tag')
  check('내 정보 - 권한 표시', (await admin.locator('.chips .tag').count()) >= 1)
})

await step('화면 언어 변경 (KO → EN → KO)', async () => {
  await admin.goto(`${BASE}/system/codes?group=NOTICE_CATEGORY`)
  await admin.waitForSelector('.table tbody tr')
  const groupName = '[data-test="group-NOTICE_CATEGORY"] .pick-main span:first-child'
  const before = await admin.locator(groupName).innerText()
  await admin.click('.topbar [data-test="lang-en"]')
  await admin.waitForFunction(() => document.documentElement.lang === 'en')
  await admin.waitForFunction(([selector, b]) =>
    document.querySelector(selector)?.textContent?.trim() !== b, [groupName, before])
  const after = await admin.locator(groupName).innerText()
  const heading = await admin.locator('h1').innerText()
  check('EN 전환 - 화면 문구·그룹명 영어', heading === 'Common codes' && after !== before,
    `${before} → ${after}`)
  await shot(admin, '09-codes-en')
  await admin.reload()
  await admin.waitForSelector('.table tbody tr')
  check('새로고침 후에도 EN 유지 (사용자 설정 저장)', (await admin.locator('h1').innerText()) === 'Common codes')
  await admin.click('.topbar [data-test="lang-ko"]')
  await admin.waitForFunction(() => document.documentElement.lang === 'ko')
})

await step('메뉴 유형 (CONTENT 본문 / 준비 중)', async () => {
  await admin.goto(`${BASE}/`)
  await admin.click('.side a[href^="/content/"]')
  await admin.waitForURL(/\/content\/\d+$/)
  await admin.waitForSelector('[data-test="content-body"]')
  check('CONTENT 메뉴 → 언어별 본문 표시 (GET /menus/{id}/content)',
    (await admin.locator('[data-test="content-body"]').innerText()).includes('TX'))
  await admin.goto(`${BASE}/ws/status/list`)
  await admin.waitForSelector('.empty')
  check('화면 없는 메뉴 URL → 준비 중 (메뉴명 표시)', (await admin.locator('h1').count()) === 1)
})

const codeCd = `E2E${stamp}`
await step('공통코드관리', async () => {
  await admin.goto(`${BASE}/system/codes?group=NOTICE_TAG`)
  await admin.waitForSelector('[data-test="code-row"]')
  const before = await admin.locator('[data-test="code-row"]').count()
  await admin.click('[data-test="code-add"]')
  await admin.fill('[data-test="code-cd"]', codeCd)
  await admin.fill('[data-test="c-nm-ko"]', `E2E 태그 ${stamp}`)
  await admin.fill('[data-test="c-nm-en"]', `E2E tag ${stamp}`)
  await admin.click('[data-test="code-save"]')
  await admin.waitForFunction((n) => document.querySelectorAll('[data-test="code-row"]').length === n + 1, before)
  check('코드 추가 (언어별 이름)', true)
  await shot(admin, '12-code-admin')

  await admin.click(`[data-test="code-edit-${codeCd}"]`)
  await admin.click('[data-test="code-use-n"]')
  await admin.click('[data-test="code-save"]')
  await admin.waitForSelector('tr.unused')
  const tags = await admin.request.get(`${BASE}/cm/api/v1/codes/NOTICE_TAG`)
  const active = (await tags.json()).result.map((c) => c.code)
  check('미사용으로 바꾸면 사용자 코드 API 에서 즉시 빠짐 (캐시 삭제)', !active.includes(codeCd),
    active.join(','))

  await admin.click(`[data-test="code-delete-${codeCd}"]`)
  await admin.click('[data-test="confirm"]')
  await admin.waitForFunction((n) => document.querySelectorAll('[data-test="code-row"]').length === n, before)
  check('코드 삭제', true)

  await admin.click('[data-test="group-MAIL_STATUS"]')
  await admin.waitForSelector('[data-test="code-row"]')
  check('시스템 그룹은 삭제 버튼 없음',
    (await admin.locator('[data-test="group-delete"], [data-test^="code-delete-"]').count()) === 0)
})

let contentMenuId = 0
await step('메뉴관리 (본문 메뉴 추가 → 사이드 메뉴·본문 → 순서 → 삭제)', async () => {
  await admin.goto(`${BASE}/system/menus`)
  await admin.waitForSelector('[data-test="menu-11"]')
  await admin.click('[data-test="menu-11"]')
  await admin.click('[data-test="menu-add-child"]')
  await admin.fill('[data-test="m-nm-ko"]', `E2E 안내 ${stamp}`)
  await admin.fill('[data-test="m-nm-en"]', `E2E guide ${stamp}`)
  await admin.selectOption('[data-test="menu-type"]', 'CONTENT')
  const editor = admin.locator('.html-editor .ProseMirror')
  await editor.click()
  await editor.pressSequentially(`E2E 본문 ${stamp}`)
  await shot(admin, '13-menu-admin')
  await admin.click('[data-test="menu-save"]')
  await admin.waitForSelector('[data-test="menu-delete"]')
  contentMenuId = Number((await admin.locator('.detail-head .sub').innerText()).match(/ID (\d+)/)[1])
  check('본문 메뉴 등록', contentMenuId > 0, `id=${contentMenuId}`)

  await admin.click(`.side a[href="/content/${contentMenuId}"]`)
  await admin.waitForSelector('[data-test="content-body"]')
  check('사이드 메뉴에 바로 나타나고 본문 표시 (내 메뉴 다시 조회)',
    (await admin.locator('[data-test="content-body"]').innerText()).includes(stamp))

  await admin.goto(`${BASE}/system/menus`)
  await admin.waitForSelector(`[data-test="menu-${contentMenuId}"]`)
  await admin.hover(`[data-test="menu-${contentMenuId}"]`)
  await admin.click(`[data-test="menu-up-${contentMenuId}"]`)
  await admin.waitForTimeout(800)
  const order = await admin.request.get(`${BASE}/cm/api/v1/menus`)
  const system = (await order.json()).result.find((m) => m.menuId === 11)
  const ids = system.children.map((m) => m.menuId)
  check('위로 이동 (같은 상위 메뉴 순서 변경)', ids.indexOf(contentMenuId) === ids.length - 2,
    ids.join(','))

  await admin.click(`[data-test="menu-${contentMenuId}"]`)
  await admin.click('[data-test="menu-delete"]')
  await admin.click('[data-test="confirm"]')
  await admin.waitForSelector(`[data-test="menu-${contentMenuId}"]`, { state: 'detached' })
  check('메뉴 삭제', true)
})

const roleCd = `E2E_ROLE_${stamp}`
await step('권한관리 (권한 추가 → 메뉴 권한 → 사용자 부여)', async () => {
  await admin.goto(`${BASE}/system/roles`)
  await admin.waitForSelector('[data-test="role-SYS_ADMIN"]')
  await admin.click('[data-test="role-add"]')
  await admin.fill('[data-test="role-cd"]', roleCd)
  await admin.fill('[data-test="r-nm-ko"]', `E2E 권한 ${stamp}`)
  await admin.click('[data-test="role-save"]')
  await admin.waitForSelector(`[data-test="role-${roleCd}"]`)
  check('권한 추가', true)

  await admin.click('[data-test="role-tab-menus"]')
  await admin.waitForSelector('[data-test="rm-12-listYn"]')
  await admin.click('[data-test="rm-12-listYn"]')
  check('하위 메뉴 조회를 켜면 상위(시스템관리) 조회도 켜짐',
    await admin.locator('[data-test="rm-11-listYn"]').isChecked())
  await admin.click('[data-test="role-menus-save"]')
  await admin.locator('[data-test="toast-success"]').first().waitFor()
  await shot(admin, '14-role-menus')

  await admin.click('[data-test="role-tab-grants"]')
  await admin.click('[data-test="grant-add-USER"]')
  await admin.fill('[data-test="grant-user-q"]', 'emp01')
  await admin.click('[data-test="grant-user-search"]')
  await admin.click('[data-test="grant-user-emp01"]')
  await admin.click('[data-test="grant-save"]')
  await admin.waitForSelector('[data-test="grant-row-emp01"]')
  check('사용자 부여 (검색 → 선택 → 유효)',
    (await admin.locator('[data-test="grant-row-emp01"]').innerText()).length > 0)
  await shot(admin, '15-role-grants')
})

await step('홈', async () => {
  await admin.goto(`${BASE}/`)
  await admin.waitForSelector('.home-grid')
  await shot(admin, '10-home')
  check('홈 (최근 공지, 권한)', await admin.locator('.home-grid .panel').count() >= 2)
})

await step('로그아웃', async () => {
  await admin.click('[data-test="user-menu"]')
  await admin.click('[data-test="logout"]')
  await admin.waitForURL(/\/login\?loggedOut=1/)
  const me = await admin.request.get(`${BASE}/cm/api/v1/auth/me`)
  check('로그아웃 → 로그인 화면, 세션 폐기(401)', me.status() === 401, `status=${me.status()}`)
  await admin.goto(`${BASE}/`)
  await admin.waitForURL(/\/login/)
  await admin.click('[data-test="login-oauth2"]')
  await admin.waitForSelector('#username', { timeout: 5000 })
  check('Keycloak SSO 세션도 종료 (다시 로그인 화면)', true)
})
await admin.context().close()

// ------------------------------------------------------------------
// 2. 조회 전용 (tx.viewer : board.list, sample.list)
// ------------------------------------------------------------------
const viewer = await newPage('en-US')
await step('조회 전용 사용자', async () => {
  await login(viewer, 'tx.viewer', 'oauth2', '/support/notices')
  await closePopups(viewer)
  await viewer.waitForSelector('[data-test="notice-row"]')
  check('조회 전용 - 공지 등록 버튼 없음', (await viewer.locator('[data-test="notice-create"]').count()) === 0)
  check('LANG_CD 미설정 + 브라우저 en-US → 화면 영어', (await viewer.locator('h1').innerText()) === 'Notices')
  await shot(viewer, '11-viewer-notices-en')
  await viewer.goto(`${BASE}/support/notices/new`)
  await viewer.waitForURL(/\/forbidden/)
  check('등록 화면 직접 접근 → 권한 없음 화면', viewer.url().includes('/forbidden'))
  await viewer.goto(`${BASE}/tx/trades/list`)
  await viewer.waitForSelector('[data-test="sample-row"]')
  check('조회 전용 - 거래 등록/삭제 버튼 없음',
    (await viewer.locator('[data-test="sample-create"], [data-test="sample-delete"]').count()) === 0)
  const forged = await viewer.request.post(`${BASE}/cm/api/v1/samples`, {
    data: { name: 'x', useYn: 'Y' },
    headers: { 'X-XSRF-TOKEN': (await viewer.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? '' },
  })
  check('버튼을 우회한 API 호출도 서버가 거부 (403)', forged.status() === 403, `status=${forged.status()}`)
})
await viewer.context().close()

// ------------------------------------------------------------------
// 3. 권한관리에서 부여한 권한의 효과 (emp01) + 회수/삭제
// ------------------------------------------------------------------
const emp = await newPage()
await step('부여받은 권한 효과 (emp01)', async () => {
  await login(emp, 'emp01', 'oauth2', '/')
  await closePopups(emp)
  await emp.waitForSelector('.side')
  check('DB 메뉴 권한 → 사이드 메뉴에 공통코드관리 표시',
    (await emp.locator('.side a[href="/system/codes"]').count()) === 1)
  await emp.goto(`${BASE}/system/codes`)
  await emp.waitForURL(/\/forbidden/)
  const api = await emp.request.get(`${BASE}/cm/api/v1/code-groups`)
  check('IdP 역할(code.list)이 없으면 화면·API 불가 (IdP scope ∩ DB 권한)', api.status() === 403,
    `status=${api.status()}`)
})

const cleanup = await newPage()
await step('권한 회수 → 즉시 메뉴에서 빠짐, 권한 삭제', async () => {
  await login(cleanup, 'admin', 'oauth2', '/system/roles')
  await closePopups(cleanup)
  await cleanup.click(`[data-test="role-${roleCd}"]`)
  await cleanup.click('[data-test="role-tab-grants"]')
  await cleanup.click('[data-test="grant-revoke-emp01"]')
  await cleanup.click('[data-test="confirm"]')
  await cleanup.waitForSelector('[data-test="grant-row-emp01"]', { state: 'detached' })
  await emp.goto(`${BASE}/`)
  await emp.waitForSelector('.side')
  check('회수 후 emp01 메뉴에서 공통코드관리 사라짐 (권한 캐시 삭제)',
    (await emp.locator('.side a[href="/system/codes"]').count()) === 0)

  await cleanup.click('[data-test="role-tab-info"]')
  await cleanup.click('[data-test="role-delete"]')
  await cleanup.click('[data-test="confirm"]')
  await cleanup.waitForSelector(`[data-test="role-${roleCd}"]`, { state: 'detached' })
  check('권한 삭제', true)
})
await emp.context().close()
await cleanup.context().close()

// ------------------------------------------------------------------
// 4. SAML 로그인 (emp01)
// ------------------------------------------------------------------
const saml = await newPage()
await step('SAML 로그인', async () => {
  await login(saml, 'emp01', 'saml2', '/me')
  await closePopups(saml)
  await saml.waitForSelector('.dl')
  const text = await saml.locator('.dl').innerText()
  check('SAML 로그인 → returnTo(/me) 복귀, 인증 방식 SAML', new URL(saml.url()).pathname === '/me'
    && text.includes('SAML'), saml.url())
})
await saml.context().close()

check('브라우저 콘솔 CSP 위반 없음 (npm run preview 는 운영과 같은 CSP)', cspViolations.length === 0,
  cspViolations[0]?.slice(0, 120) ?? '')

await browser.close()

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} PASS`)
process.exit(failed.length ? 1 : 0)
