# CLAUDE.md

Vue 3 + TypeScript + Vite 화면. spring-gateway(9909) 경유로 spring-boilerplate API 를 쓴다.
상세 규칙은 `docs/DEVELOPMENT_GUIDE.md` 가 기준이며, 아래는 작업 시 반드시 지킬 핵심 요약이다.

## Commands
- 개발 : `npm run dev` (:5173) → 브라우저는 항상 **http://localhost:9909** (Gateway). 5173 직접 접속 금지
- 검사 : `npm run type-check` / `npm test` (Vitest) / `npm run build`
- E2E : 인프라(Redis, Keycloak) + 백엔드(9091) + Gateway(9909) + `npm run dev` 또는 `npm run preview` 실행 후 `npm run e2e`
  (설치된 Edge 사용, 캡처 `e2e/screenshots/`. 반복 실행 가능)
- 작업 후 항상 `npm run type-check && npm test` 통과 확인. 화면 흐름을 바꾸면 `npm run e2e`

## 구조
- 공통 `src/global/{api,components,composables,i18n,layout,router,stores,styles,util,views}`, `global/session.ts`
- 도메인 `src/{auth,code,user,menu,sample,notice}/{api.ts,types.ts,store.ts?,views,components}` - 백엔드 도메인/DTO 와 1:1
- 신규 도메인은 `sample` 폴더 구조를 따른다

## 필수 규칙
- API 는 `global/api/http.ts` (baseURL `/cm`) 로만 호출. 조회 `getResult`, 변경 `postResult/putResult/deleteResult`. 절대 URL·axios 직접 생성 금지
- 알림 문구는 서버 표준 응답의 `messageTitle/messageContent` (`useApiFeedback`). 오류는 `ApiError`
- 인증: 화면은 토큰을 다루지 않는다 (SESSION 쿠키만). localStorage 에 인증 정보 저장 금지. 로그인은 `loginUrl(method, returnTo)`
- CSRF: axios 가 XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더 자동 전송. 별도 처리 금지
- 권한: `auth.can(resource, action)` (authorities = IdP scope ∩ DB 권한). 라우트 `meta.resource/action`. 버튼 숨김은 편의, 인가는 서버
- 라우트 경로 = 백엔드 메뉴 URL. 숫자 파라미터는 `([0-9]+)` (Vue Router 5 는 `\\d` 미지원)
- **다국어**: 언어 목록 하드코딩 금지 → `code.langs` (GET /langs). 화면 언어 = `/auth/me` 의 `langCd`, Accept-Language = `editorLocale`. 언어 변경은 `changeLang()`. 데이터 이름은 서버 번역 사용
- 화면 문구는 `global/i18n/messages/*.ts` 모든 언어에 같은 키 (messages.spec.ts 검사). `@ { } | $` 직접 사용 금지
- 언어가 바뀌면 다시 조회: `watch(() => auth.user?.langCd, load)`
- v-html 은 `sanitizeHtml()` 만. 외부 URL 은 `isSafeExternalUrl` (http/https)
- 스타일: `tokens.css` 변수만, 공통 클래스(`ui.css`) 조합 + `<style scoped>`. 그라데이션/그림자 남발/이모지/보라색 금지. `td` 에 flex 직접 금지
- 테스트 셀렉터 `data-test`. 단위 테스트 이름 한글
- 비밀번호·시크릿을 코드/문서에 쓰지 않는다 (E2E 는 local realm 파일에서 읽음)
