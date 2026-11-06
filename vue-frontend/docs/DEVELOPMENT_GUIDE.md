# vue-frontend 개발 가이드

> 기준 문서 : [spring-boilerplate 개발 가이드](../../spring-boilerplate/docs/DEVELOPMENT_GUIDE.md)
>
> 백엔드 가이드의 원칙(도메인별 패키지, 계층 분리, 표준 응답, 다국어 하드코딩 금지, 기본 거부, 토큰 비노출)을
> 화면에 맞게 옮긴 문서입니다. 백엔드와 다른 점은 **[화면]** 으로 표시합니다.

---

## 0. 기술 스택

| 구분 | 내용 |
|---|---|
| Framework | Vue 3.5 (Composition API, `<script setup lang="ts">`) |
| Language | TypeScript 5.9 (strict) |
| Build | Vite 8 (`npm run dev` / `npm run build`) |
| Router / 상태 | Vue Router 5 (history 모드) / Pinia 4 |
| 다국어 | vue-i18n 11 (Composition 모드) |
| HTTP | axios (`/cm` = Gateway API 라우트) |
| 에디터 | TOAST UI Editor 3 (WYSIWYG → HTML, 언어팩 = `LANG.ATTR1`) |
| 보안 | DOMPurify (v-html 심층 방어) |
| 글꼴 | Pretendard Variable (self-host, `pretendard` 패키지) |
| 테스트 | Vitest + Vue Test Utils (단위) / playwright-core + 설치된 Edge·Chrome (E2E) |

UI 라이브러리(Element Plus, Vuetify 등)는 쓰지 않는다. 화면 공통 요소는 `global/components` 와 `global/styles` 에 둔다.

---

## 1. 실행 구조 **[화면]**

```
브라우저 ── http://localhost:9909 ──▶ spring-gateway
                                      ├─ /cm/**  → spring-boilerplate :9091 (/cm 제거)
                                      └─ /**     → 화면 (local: Vite :5173 / 운영: nginx :8080)
```

- **화면과 API 는 같은 Origin(Gateway)** 이다. CORS·프록시 설정이 없고, 브라우저는 SESSION(HttpOnly)·XSRF-TOKEN 쿠키만 가진다.
- 5173 에 직접 접속하지 않는다. (API 호출과 로그인 redirect 가 Gateway 주소 기준)
- Gateway 의 화면 라우트는 Rate Limit 미적용, `/actuator/**` 는 제외된다.

---

## 2. 패키지 구조

백엔드와 같은 **도메인별** 구조. 도메인 이름도 백엔드와 맞춘다. (`auth`, `code`, `user`, `menu`, `sample`, `notice`)

```
src
 ├─ global                 # 공통 (백엔드 global 대응)
 │  ├─ api                 # http(axios), ApiError, 표준 응답 타입(MessageResponse, PageResponse)
 │  ├─ components          # 공통 컴포넌트 (UiDialog, ConfirmDialog, UiPagination, EmptyState, LangBadges, ToastHost)
 │  ├─ composables         # 공통 composable (useApiFeedback)
 │  ├─ i18n                # vue-i18n 설정, messages/{langCd}.ts (화면 문구 번역본)
 │  ├─ layout              # AppShell(메뉴+상단바), SideNavItem, TopBar
 │  ├─ router              # 라우트, 인증/권한 가드
 │  ├─ stores              # 공통 store (toast)
 │  ├─ styles              # tokens.css(디자인 토큰), base.css, ui.css(공통 클래스)
 │  ├─ util                # format(날짜/크기), html(정화)
 │  ├─ views               # 로그인, 홈, 권한 없음, 준비 중, IFRAME/CONTENT 메뉴
 │  └─ session.ts          # 로그인 세션 준비(사용자·언어·메뉴), 언어 변경
 └─ {도메인}               # 예) notice
    ├─ api.ts              # API 호출 함수 (백엔드 Controller 1:1)
    ├─ types.ts            # 요청/응답 타입 (백엔드 DTO record 1:1, 필드명 동일)
    ├─ store.ts            # (필요할 때만) Pinia store
    ├─ validation.ts       # (필요할 때만) 화면 검증 - 서버 규칙과 동일하게
    ├─ components          # 도메인 전용 컴포넌트
    └─ views               # 라우트 화면 (XxxListView, XxxDetailView, XxxFormView)
```

> 신규 도메인은 `sample` 폴더를 복사해 시작한다. (목록/검색/페이징/등록·수정 대화상자/삭제 확인)
> 관리 화면(목록 + 상세)은 `code/views/CodeAdminView` · `menu/views/MenuAdminView` · `role/views/RoleAdminView` 를 참고한다.
>  - 레이아웃 : `ui.css` 의 `split` / `split-list` / `pick` / `detail-head` / `form-stack` / `form-actions`
>  - 언어별 이름 입력 : `global/components/LangNamesInput.vue` (지원 언어마다 한 칸, 기본 언어 필수, `{언어코드: 이름}`)
>  - 리치 텍스트 : `global/components/HtmlEditor.vue` (공지 본문, 메뉴 본문 공용)

| 계층 | 역할 | 금지 |
|---|---|---|
| `views` | 화면 상태, 사용자 이벤트, api 호출 조합 | axios 직접 호출, 색상값 직접 작성 |
| `components` | 표시·입력 (props / emit) | 라우터 이동·API 호출 (도메인 컴포넌트 중 조회 전용은 허용) |
| `api.ts` | URL·파라미터 구성, `result` 꺼내기 | 화면 상태 변경, 알림 표시 |
| `store.ts` | 여러 화면이 공유하는 상태 | 화면 하나만 쓰는 상태 |

---

## 3. 코딩 규칙

- `.editorconfig` : 2칸 들여쓰기, LF, 100자.
- TypeScript strict. `any` 금지 (불가피하면 `unknown` 후 좁히기).
- 컴포넌트 : `<script setup lang="ts">`, `defineProps<{...}>()` / `defineEmits<{...}>()` 타입 선언.
- 파일명 : 컴포넌트 PascalCase(`NoticeListView.vue`), 그 외 camelCase(`api.ts`, `validation.ts`).
- 화면 이름 접미사 : `...View.vue` (라우트), `...Dialog.vue` (대화상자).
- 주석은 "왜" 를 쓴다. 공개 함수·컴포넌트 상단에 역할 1~3줄.
- 테스트 셀렉터는 `data-test="..."` 를 쓴다. (클래스/문구에 의존하지 않음)

---

## 4. API 연동 **[화면]**

### 4.1 호출

```ts
// notice/api.ts
export function fetchNotices(search: NoticeSearch, page: PageQuery): Promise<PageResponse<NoticeSummary>> {
  return getResult<PageResponse<NoticeSummary>>('/api/v1/notices', { params: { ...search, ...page } })
}
```

- 조회는 `getResult<T>()` (표준 응답의 `result` 반환), 변경은 `postResult / putResult / deleteResult` (표준 응답 전체 반환 → 메시지 알림에 사용).
- URL 은 백엔드 경로 그대로(`/api/v1/...`). `/cm` 은 `http.ts` 의 baseURL 이 붙인다. **절대 URL 금지.**
- 페이징 `page` 는 0부터 (백엔드와 동일), 화면 표시만 1부터.
- 날짜는 서버 값(`LocalDateTime` 문자열, Asia/Seoul) 그대로 쓴다. 표시는 `formatDateTime`, 입력은 `toDateTimeLocal / fromDateTimeLocal`.

### 4.2 응답 / 오류 메시지

- 모든 응답은 표준 형식(`messageId, messageTitle, messageContent, result`)이다. Gateway 오류(G0001~G0006)도 같은 형식.
- 오류는 `ApiError` 로 변환된다. **알림 문구는 서버가 다국어 처리한 `messageTitle / messageContent` 를 그대로 쓴다.** (`useApiFeedback().success / failure`)
- 검증 오류(400)는 `error.fieldErrors` 로 필드 아래에 표시 (`SampleFormDialog` 참고).
- 화면에서 먼저 검증하는 규칙(예: 공지 언어별 제목+본문)은 **서버 규칙과 같게** 두고(`notice/validation.ts`), 최종 검증은 서버가 한다.

### 4.3 인증 / CSRF

- 로그인 : `loginUrl('oauth2' | 'saml2', returnTo)` 로 이동 → Keycloak → 서버가 `returnTo`(같은 호스트 절대 경로)로 돌려보낸다.
- **화면은 토큰을 다루지 않는다.** 토큰은 서버 세션(Redis)에만 있다. localStorage/sessionStorage 에 인증 정보 저장 금지.
- 변경 요청(POST/PUT/DELETE)은 axios 가 `XSRF-TOKEN` 쿠키를 `X-XSRF-TOKEN` 헤더로 자동 전송한다. (서버가 첫 요청부터 쿠키 발급, 경로 `/`)
- 401(세션 만료) → `http.ts` 의 핸들러가 로그인 화면으로 이동(`returnTo` = 현재 화면). 로그인 여부 확인용 호출만 `silent401: true`.
- 로그아웃 : `POST /cm/logout` (서버 세션 + Keycloak SSO 세션 종료).

### 4.4 권한

- 화면 권한 = `GET /auth/me` 의 `authorities` (서버가 계산한 **IdP scope ∩ DB 업무 권한**). `auth.can('board', 'add')` (작업은 list/add/edit/del, scope 동사 write/update/delete 도 같은 작업으로 인정).
- 라우트 `meta.resource / action` 으로 화면 진입을 막고(권한 없음 화면), 버튼은 `v-if="can(...)"`.
- **버튼을 숨기는 것은 편의일 뿐이다.** 실제 인가는 항상 서버가 한다. (E2E 에서 버튼 우회 호출 403 확인)
- 메뉴는 `GET /menus/me` 를 그대로 그린다. (서버가 권한·언어로 걸러서 줌)

### 4.5 메뉴 → 화면

| 메뉴 유형 | 화면 |
|---|---|
| MENU (말단) | 메뉴 URL = 라우트 경로 (예: `/support/notices`). 라우트가 없으면 "화면 준비 중" |
| MENU (하위 있음) | 묶음 (이동 없음) |
| LINK | 외부 URL (http/https 만), 새 창 여부 = `NEW_WINDOW_YN` |
| IFRAME | `/frame/{menuId}` (sandbox iframe) |
| CONTENT | `/content/{menuId}` → `GET /menus/{id}/content` (사용자 언어 본문, 없으면 기본 언어 + 안내) |

새 화면 = 백엔드 메뉴 URL 과 같은 경로로 `global/router/index.ts` 에 라우트 추가 + `meta.resource`.
숫자 파라미터는 `:id([0-9]+)` 로 쓴다. (Vue Router 5 는 `\\d` 이스케이프를 매칭하지 못함)

---

## 5. 다국어 **[화면]**

- **언어 목록은 하드코딩하지 않는다.** 공통코드 `LANG` (`GET /api/v1/langs`) 이 기준. 언어 선택, 공지 언어 탭, 에디터 언어팩 모두 이 목록으로 만든다.
- 화면 언어 = 서버가 정한 사용자 언어(`/auth/me` 의 `langCd`: 사용자 설정 → Accept-Language → 기본 언어).
- 요청 헤더 `Accept-Language` = 현재 언어의 `editorLocale`(LANG.ATTR1, 예 `ko-KR`) → 서버 메시지도 같은 언어.
- 언어 변경 = `changeLang()` : `PUT /users/me/lang` 저장 → 사용자·메뉴·코드 다시 조회.
- 화면 문구는 `global/i18n/messages/{langCd}.ts`. 번역본이 없는 언어는 기본 언어 문구로 표시된다.
  - 새 키는 **모든 언어 파일**에 추가 (`en.ts` 는 `ko.ts` 타입으로 검사 + `messages.spec.ts` 가 누락/미정의 키 검사)
  - vue-i18n 예약 문자(`@ { } | $`)를 문구에 직접 쓰지 않는다.
- 데이터 이름(메뉴·공통코드·조직·공지)은 **서버가 번역해서 준다.** 화면에서 번역하지 않는다.
- 언어를 바꾸면 화면의 코드명/목록이 바뀌어야 하므로 `watch(() => auth.user?.langCd, 다시조회)` 를 둔다.
- 공지 에디터 언어팩 : 탭 언어의 `editorLocale` → `@toast-ui/editor/dist/esm/i18n/{소문자}.js` (이름 규칙으로 찾음, 없으면 영어 UI).

---

### 4.6 관리 화면

- 관리 데이터를 바꾼 뒤에는 화면 쪽 캐시도 갱신한다 : 공통코드 `code.clearCodes()` (LANG 이면 `code.loadLangs()`),
  메뉴 `useMenuStore().load()`, 권한/부여 `auth.load()` + 메뉴 (내 권한이 바뀌었을 수 있음)
- 버튼은 `can(resource, action)` 으로, 시스템관리자 권한(레벨 1)은 `auth.isSysAdmin` 일 때만 편집 UI 를 연다 (서버도 B6004 로 거부)
- 키(그룹코드·코드·권한코드)는 등록 후 수정 불가 → 수정 시 입력칸 비활성

## 6. 디자인 / 화면 작성 **[화면]**

방향 : **업무 화면(Operate)**. 장부 같은 종이 톤 + 짙은 녹색 강조 1개. 정렬·여백·선·숫자 정렬로 위계를 만든다.

- 색·글꼴·간격은 `global/styles/tokens.css` 변수만 쓴다. 컴포넌트에 색상값을 직접 쓰지 않는다.
- 공통 클래스(`page`, `page-head`, `filter-bar`, `table-wrap`/`table`, `btn`/`btn-primary`/`btn-danger`, `field`, `tag`, `tabs`, `notice-line`, `dl`)를 조합하고, 화면 전용 스타일만 `<style scoped>` 에.
- 하지 않는 것 : 그라데이션 배경, 카드마다 그림자, 이모지 아이콘, 의미 없는 장식 아이콘, 과한 둥근 모서리, 보라색 계열 강조.
- 숫자·날짜 열은 `col-num` / `col-date` (오른쪽 정렬·고정폭 숫자). 표 셀(`td`)에 `display:flex` 를 직접 주지 말고 안쪽 `div` 에.
- 한글 줄바꿈 : 본문 `word-break: keep-all` (base.css).
- 빈 목록·권한 없음·준비 중은 `EmptyState` 로 이유를 설명한다.
- 접근성 : 라벨(`label for`), 표 정렬 `aria-sort`, 탭 `role="tab" aria-selected`, 대화상자는 native `<dialog>` (포커스 가두기/ESC).
- 에디터 HTML 표시는 `v-html="sanitizeHtml(...)"` (서버 정화 + 화면 DOMPurify).
- 에디터는 보이는 탭만 생성한다 (`v-if`). 숨긴 채로 만들면 TOAST UI 툴바 폭이 0 으로 계산된다.
  알려진 문제 : TOAST UI 3.2(유지보수 종료)는 해제 직후 예약된 툴바 다시 그리기로 드물게 `removeChild` 콘솔 오류를 낸다. 기능 영향은 없고, 장기적으로 편집기 교체(Tiptap 등) 검토 대상.

---

## 7. 테스트

| 구분 | 명령 | 대상 |
|---|---|---|
| 타입 검사 | `npm run type-check` | 전체 (vue-tsc) |
| 단위 | `npm test` | `src/**/*.spec.ts` - 순수 로직(검증, 메뉴 링크, 오류 변환, 권한), 공통 컴포넌트, 다국어 키 |
| E2E | `npm run e2e` | 실제 Keycloak + 백엔드 + Gateway + 화면. 결과 캡처 `e2e/screenshots/` |

- 단위 테스트 이름은 한글로 "상황 - 기대 결과".
- E2E 는 브라우저를 내려받지 않고 설치된 Edge(기본) / Chrome(`E2E_BROWSER=chrome`)을 쓴다.
- E2E 테스트 계정 비밀번호는 코드에 쓰지 않고 local realm import 파일에서 읽는다.
- `npm run preview` 는 운영과 같은 CSP 헤더를 보낸다 → 빌드 결과로 E2E 를 돌리면 CSP 위반도 검사된다.
- 기능 추가 시 : 순수 로직은 단위 테스트, 화면 흐름은 E2E 시나리오를 함께 추가한다.

---

## 8. 빌드 / 배포

```bash
npm ci && npm run build          # dist/ (vue-tsc 타입 검사 포함)
docker build -t flowlog/vue-frontend .   # nginx(비루트, 8080) + SPA fallback + 보안 헤더(CSP)
```

- Gateway 환경변수 `WEB_SERVICE_URI` 를 nginx 주소로 지정한다. (local 기본 `http://localhost:5173`)
- 정적 파일은 `/assets/*` 장기 캐시, `index.html` no-cache.
- 환경별로 바뀌는 값(API 주소 등)이 없도록 유지한다. (같은 Origin 의 `/cm`)

---

## 9. Git

백엔드 가이드와 동일 : Conventional Commits (`feat`, `fix`, `refactor`, `docs`, `test`, `chore`), `main` / `develop` / `feature/{기능}`.
