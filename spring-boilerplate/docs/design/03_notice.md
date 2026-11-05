# 03. 공지사항 다국어 (v0.4)

| 항목 | 내용 |
|---|---|
| 상태 | 구현 완료 · 테스트 통과 (기본 110/110, Oracle 2/2, 실연동 E2E 49/49) |
| 작성일 | 2026-11-05 |
| DDL (Oracle) | [`db/ddl/02_notice.sql`](../../src/main/resources/db/ddl/02_notice.sql), 인사 마스터 변경분은 [`db/ddl/01_auth_menu_code.sql`](../../src/main/resources/db/ddl/01_auth_menu_code.sql) |
| 초기 데이터 (local) | [`db/data/02_notice.sql`](../../src/main/resources/db/data/02_notice.sql) (공지 코드, 메일 템플릿, 샘플 공지 4건) |
| 패키지 | `notice`, `mail`, `code`(언어/공통코드 API), `user`(조직도/사용자 언어), `global/util/HtmlSanitizer` |

---

## 1. 요구사항 추적표

| ID | 요구사항 | 구현 | 검증 |
|---|---|---|---|
| NT-60 | LANG 코드 기준 언어 탭 | `GET /api/v1/langs` (`LangService` — LANG 코드 `USE_YN='Y'`, 정렬순서). 입력 언어는 LANG 코드만 허용 (B2004) | `NoticeApiIntegrationTest` 지원 언어 3건, E2E |
| NT-61 | 최소 1개 언어 제목·본문 필수 | `NoticeService.versionsOf` — 제목·본문이 모두 빈 탭은 미입력으로 무시, 하나만 있으면 B2003, 입력 언어 0개면 B2002 | 등록 규칙 `contentRequired` |
| NT-62 | 대표 언어 (기본 = 처음 입력 언어) | `baseLangCd` 미지정 시 요청 `i18n` 배열에서 첫 번째로 입력된 언어. 입력하지 않은 언어 지정 시 B2006 | `englishOnly_baseLangIsFirst`, `bothLanguages_explicitBase`, `invalidLanguage` |
| NT-63 | 첨부파일 공통 관리 | `TB_NOTICE_FILE` 은 공지 단위 (언어 컬럼 없음) — **언어별 분리 여부는 확인 필요** | 첨부파일 2건, E2E 업로드/다운로드 |
| NT-64 | 조회 언어 = 사용자 언어 → 대표 언어 | 목록: 공지마다 표시 버전 1건 선택 (네이티브 SQL). 상세: `?lang=` → 사용자 언어 → 대표 언어 → 첫 버전 (`NoticeService.select`) | `englishUserList`, `koreanUserList`, E2E |
| NT-65 | KO/EN 배지 + 상세 언어 전환 | 목록/상세 `langs[{langCd, badge}]` (LANG 정렬순서), 상세 `?lang=en`, `fallback` 플래그 | `detailLanguageSwitch`, E2E |
| NT-66 | 사용자 언어 버전 기준 검색 (대체 표시분 포함) | 검색어를 **표시 버전**의 제목/본문 텍스트에 적용 (`NoticeI18nRepository.searchDisplayed`) | `searchByDisplayedVersion`, Oracle `noticeSearch`, E2E |
| NT-47 | 수신 언어 = 사용자 설정 언어 | `TB_USER.LANG_CD` (미설정/사용 중지 언어 → 기본 언어) | `NoticeMailServiceTest` |
| NT-48 | 해당 언어 버전 없으면 대표 언어 버전 | `NoticeMailService.compose` | `groupByRecipientLanguage`, `englishVersionForEnglishRecipient` |
| NT-49 | 템플릿은 수신자 언어 | `TB_MAIL_TEMPLATE` (언어별 머리글/안내/바닥글), 없으면 기본 언어 템플릿 | `groupByRecipientLanguage` |
| NT-4A | 언어별 그룹 일괄 발송 | 언어 그룹마다 메일 1통 (BCC, `batch-size` 단위 분할) | `groupByRecipientLanguage`, E2E 메일 |
| 지원 언어 | ko, en / 하드코딩 금지 / 에디터 언어팩 = ATTR1 | LANG 코드 `ko`(ATTR1 `ko-KR`), `en`(ATTR1 `en-US`) | `addLanguageWithoutCodeChange` (ja 추가) |
| 사업장/부서 | 코드만 저장, 명칭은 조회 시점 인사 마스터 | `TB_WORKPLACE`, `TB_DEPT` (국문/영문명), `OrgService` | `orgTreeEnglish`, `mailTargets` |
| 발송 시점 대상자 | 예약 게시 사이 인사이동 반영 | 수신자는 발송 직전에 인사 마스터에서 추출 | `recipientsAtSendTime` |
| 영문 명칭 | 인사 마스터 영문명 | `WORKPLACE_NM_EN`, `DEPT_NM_EN` | `orgTreeEnglish`, E2E |

---

## 2. 데이터 구조

> 테이블명은 프로젝트 규칙에 따라 `TB_` 접두사를 붙였다. (`NOTICE` → `TB_NOTICE`, `NOTICE_I18N` → `TB_NOTICE_I18N` …)
> 공통 컬럼 `CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT` 생략.

```
TB_NOTICE 1 ─── N TB_NOTICE_I18N        (작성한 언어만 존재)
          1 ─── N TB_NOTICE_FILE        (게시물 공통)
          1 ─── N TB_NOTICE_MAIL_TARGET ─── (코드) TB_WORKPLACE / TB_DEPT   ← 인사 마스터
          1 ─── N TB_NOTICE_MAIL_HIST   (수신자별 이력)
          1 ─── N TB_NOTICE_VIEW_LOG    (사용자·일자별 1건)
TB_MAIL_TEMPLATE (템플릿코드 + 언어)
```

### TB_NOTICE (공지사항)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| **NOTICE_ID** | NUMBER(19) | `SQ_NOTICE` |
| CATEGORY_CD | VARCHAR2(50) | 분류 (코드 `NOTICE_CATEGORY`: GENERAL, SYSTEM, EVENT) |
| TAG_CD | VARCHAR2(50) | 태그 (코드 `NOTICE_TAG`: IMPORTANT, URGENT), 선택 |
| BASE_LANG_CD | VARCHAR2(10) | 대표 언어 (LANG) |
| POST_DATE | TIMESTAMP | 게시일시. 미래면 예약 게시 (일반 사용자 목록·상세에서 제외) |
| POPUP_YN / POPUP_START_DT / POPUP_END_DT | VARCHAR2(1) / DATE | 팝업 (Y 이면 기간 필수, 종료 ≥ 시작) |
| MAIL_SEND_TYPE_CD | VARCHAR2(50) | 코드 `MAIL_SEND_TYPE`: NONE(발송 안 함), TARGET(대상 지정 발송) |
| MAIL_STATUS_CD | VARCHAR2(20) | **추가 컬럼** — NONE / READY / SENDING / DONE / FAIL. 예약 발송 대기와 다중 인스턴스 선점에 필요 |
| MAIL_SEND_DT | TIMESTAMP | **추가 컬럼** — 발송 완료일시 |
| VIEW_CNT | NUMBER(10) | 조회수 (`TB_NOTICE_VIEW_LOG` 로 사용자·일자별 1회) |
| DEL_YN | VARCHAR2(1) | 논리 삭제 (삭제 시 READY 메일은 NONE 으로 취소) |

인덱스: `IX_NOTICE_01 (DEL_YN, POST_DATE)`, `IX_NOTICE_02 (MAIL_STATUS_CD, POST_DATE)`

### TB_NOTICE_I18N (언어별 내용)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| **NOTICE_ID, LANG_CD** | | PK |
| TITLE | VARCHAR2(500) | 제목 |
| CONTENT_HTML | CLOB | 본문 HTML — **저장 시 XSS 정화** (`HtmlSanitizer`) |
| CONTENT_TEXT | CLOB | **추가 컬럼** — 태그를 제거한 텍스트. 검색 대상 (HTML 태그·속성이 검색되지 않도록) |

### TB_NOTICE_FILE (첨부파일)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| **FILE_ID** | NUMBER(19) | `SQ_NOTICE_FILE` |
| NOTICE_ID | NUMBER(19) | FK |
| ORG_FILE_NM | VARCHAR2(500) | 원본 파일명 (경로·제어문자 제거) |
| SAVE_PATH | VARCHAR2(1000) | 저장소 기준 상대경로 `yyyy/MM/{UUID}` |
| FILE_SIZE / FILE_EXT | NUMBER(19) / VARCHAR2(20) | 크기 / 소문자 확장자 |

### TB_NOTICE_MAIL_TARGET (메일 대상)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| **NOTICE_ID, TARGET_TYPE, TARGET_CD** | | PK. TARGET_TYPE = 코드 `MAIL_TARGET_TYPE` (WORKPLACE / DEPT) |
| INCLUDE_SUB_YN | VARCHAR2(1) | 하위부서 포함 (DEPT, 기본 Y) |

### TB_NOTICE_MAIL_HIST (발송 이력)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| **HIST_ID** | NUMBER(19) | `SQ_NOTICE_MAIL_HIST` |
| NOTICE_ID, RECEIVER_ID, LANG_CD, SEND_DT | | 공지 / 수신자 사용자ID / 발송 언어 / 발송일시 |
| STATUS_CD | VARCHAR2(20) | 코드 `MAIL_STATUS`: SUCCESS / FAIL |
| ERROR_MSG | VARCHAR2(1000) | **추가 컬럼** — 실패 사유 (이메일 없음, SMTP 오류 등) |

### TB_NOTICE_VIEW_LOG (조회 로그)
| **NOTICE_ID, USER_ID, VIEW_DATE** (PK) | 사용자·일자별 1건 — 있으면 조회수 증가 안 함 |
|---|---|

### TB_MAIL_TEMPLATE (메일 템플릿, 신규)
| 컬럼 | 타입 | 설명 |
|---|---|---|
| **TEMPLATE_CD, LANG_CD** | | PK (예: `NOTICE` + `ko`) |
| SUBJECT | VARCHAR2(500) | 제목 (예: `[공지] {{title}}`) |
| HEADER_HTML / GUIDE_HTML / FOOTER_HTML | VARCHAR2(4000) | 머리글 / 안내 문구 / 바닥글 |
| USE_YN | VARCHAR2(1) | |

치환자: `{{title}}`, `{{postDate}}`, `{{link}}`, `{{categoryNm}}` — 값은 HTML 이스케이프 후 치환 (제목은 개행 제거)

### 인사 마스터 (01 DDL 변경분)
| 테이블 | 변경 |
|---|---|
| `TB_WORKPLACE` (신규) | WORKPLACE_CD, WORKPLACE_NM(국문), **WORKPLACE_NM_EN(영문)**, SORT_ORDER, USE_YN |
| `TB_DEPT` | **DEPT_NM_EN(영문)**, **WORKPLACE_CD**(FK) 추가 |
| `TB_USER` | **LANG_CD**(사용자 설정 언어, NULL = 기본 언어) 추가 |

명칭 규칙: `ko` → 국문명, 그 외 언어 → 영문명 (영문명이 없으면 국문명). 인사 마스터는 국문/영문만 제공한다.

local 데이터: SEOUL(서울 본사 / Seoul Headquarters) — HQ, TX_TEAM, WS_TEAM / SUWON(수원 사업장 / Suwon Campus) — TF_TEAM, TF_LAB(영문명 없음)

### 공통코드 변경
| 그룹 | 코드 |
|---|---|
| `LANG` | **ko**(ATTR1 `ko-KR`), **en**(ATTR1 `en-US`) — 기존 `ko_KR`/`en_US` 에서 변경. 모든 `*_LANG` 테이블의 LANG_CD 도 `ko`/`en` 으로 이관 |
| `NOTICE_CATEGORY` | GENERAL, SYSTEM, EVENT |
| `NOTICE_TAG` | IMPORTANT, URGENT |
| `MAIL_SEND_TYPE` | NONE, TARGET |
| `MAIL_TARGET_TYPE` | WORKPLACE, DEPT |
| `MAIL_STATUS` | SUCCESS, FAIL |

`WORKPLACE` 공통코드 그룹은 원래 존재하지 않았으며, 사업장은 인사 마스터(`TB_WORKPLACE`)를 참조한다.

### 메뉴 / 권한
- 메뉴 15(공지사항) `PERM_CD = notice` → `TB_ROLE_MENU` 플래그가 `notice.list/add/edit/del` API 권한이 된다.
- 모듈마스터(TX/WS) 전체, TX 모듈매니저 삭제 제외, 일반 사용자(USER_* 역할) 조회.
- Keycloak: realm 역할 `notice.list/add/edit/del` + 같은 이름 client scope (setup.sh). BFF 요청 scope 에 추가.
- 최종 권한 = Keycloak scope ∩ DB 권한 (기존 인가 모델 그대로).

---

## 3. API

| Method | URL | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/v1/langs` | 로그인 | 지원 언어 (langCd, langNm(사용자 언어), editorLocale(ATTR1), badge, defaultLang) |
| GET | `/api/v1/codes/{groupCd}` | 로그인 | 공통코드 (사용자 언어 코드명) — 분류/태그/발송유형 선택 목록 |
| PUT | `/api/v1/users/me/lang` | 로그인 | 사용자 설정 언어 변경 (미등록 사용자 B3001, 미지원 언어 B3002) |
| GET | `/api/v1/org/tree` | 로그인 | 조직도 (사업장 → 부서 트리, 사용자 언어 명칭) |
| GET | `/api/v1/notices` | notice.list | 목록 (`keyword`, `categoryCd`, `includeScheduled`(관리자), 페이징, 게시일시 최신순) |
| GET | `/api/v1/notices/popups` | notice.list | 노출 중인 팝업 |
| GET | `/api/v1/notices/{id}` | notice.list | 상세 (`?lang=` 언어 전환), 조회수 증가 |
| GET | `/api/v1/notices/{id}/edit` | notice.edit | 수정 화면 (전체 언어 버전 + 메일 설정/대상(명칭) + 첨부) |
| POST | `/api/v1/notices` | notice.add | 등록 (201) |
| PUT | `/api/v1/notices/{id}` | notice.edit | 수정 (언어 버전은 요청 기준으로 교체) |
| DELETE | `/api/v1/notices/{id}` | notice.del | 논리 삭제 |
| POST | `/api/v1/notices/{id}/files` | notice.add 또는 notice.edit | 첨부 등록 (multipart `files`) |
| GET | `/api/v1/notices/{id}/files/{fileId}` | notice.list | 다운로드 (attachment, RFC 5987 파일명, nosniff) |
| DELETE | `/api/v1/notices/{id}/files/{fileId}` | notice.edit | 첨부 삭제 |
| GET | `/api/v1/notices/{id}/mail-histories` | notice.edit | 수신자별 발송 이력 |

`ROLE_SYS_ADMIN` 은 전체 허용. `notice.edit`(또는 SYS_ADMIN) 보유자 = 관리자 → 예약 공지 조회 가능.

### 오류 코드
| ID | 상황 | ID | 상황 |
|---|---|---|---|
| B2001 | 공지 없음/삭제/게시 전 (404) | B2009 | 대상 지정 발송인데 대상 없음 |
| B2002 | 제목·본문을 입력한 언어 없음 | B2010 | 인사 마스터에 없는 사업장/부서 |
| B2003 | 한 언어의 제목/본문 중 하나만 입력 | B2011 | 첨부 없음 (404) |
| B2004 | 지원하지 않는 언어 | B2012 | 허용되지 않은 확장자 |
| B2005 | 같은 언어 중복 | B2013 | 첨부 개수 초과 |
| B2006 | 대표 언어가 입력한 언어가 아님 | B2014 | 빈 파일 |
| B2007 | 분류/태그/발송유형 코드 오류 | B2015 | 파일 처리 오류 (500) |
| B2008 | 팝업 기간 오류 | B3001 / B3002 | 미등록 사용자 / 미지원 언어 (사용자 설정) |

---

## 4. 언어 결정 규칙

| 대상 | 규칙 |
|---|---|
| 사용자 언어 | `TB_USER.LANG_CD` (사용 중인 LANG) → `Accept-Language` 의 언어 → 기본 언어(LANG 정렬순서 첫 번째 = ko) |
| 목록 표시 | 사용자 언어 버전이 있으면 그것, 없으면 대표 언어 버전 (`fallback=true`) |
| 상세 표시 | `?lang=` 버전 → 사용자 언어 버전 → 대표 언어 버전 → 첫 버전. `fallback` = 원한 언어(요청 또는 사용자 언어)와 다른 버전을 보여줬는지 |
| 검색 | 위 "목록 표시" 버전의 제목·본문 텍스트에 대해 대소문자 무시 LIKE (`%`, `_` 이스케이프). 예: 영어 사용자는 한국어로 대체 표시되는 공지를 한국어로 검색 가능, 한국어 사용자는 한국어 버전이 있는 공지를 영어 제목으로 찾지 못함 |
| 코드명/메뉴명 | 사용자 언어 → 기본 언어 → 코드값 |
| 인사 마스터 명칭 | ko = 국문, 그 외 = 영문명(없으면 국문) |
| 배지 | 공지가 가진 언어를 LANG 정렬순서로, 대문자 (KO, EN) |

> CLOB 본문의 `LOWER/LIKE` 는 Hibernate JPQL 이 허용하지 않아 검색은 **네이티브 SQL** (Oracle / H2 Oracle 모드 공통, Oracle 실DB 검증 완료).

---

## 5. 메일 발송 흐름

1. 등록/수정 시 `MAIL_SEND_TYPE_CD = TARGET` 이면 `MAIL_STATUS_CD = READY` (NONE 이면 NONE). 즉시/예약은 **게시일시**로 구분.
2. 스케줄러(`NoticeConfig.NoticeMailScheduler`, `app.notice.mail.poll-interval`)가 `READY` 이고 게시일시가 도래한 공지를 조회.
3. `UPDATE ... SET MAIL_STATUS_CD='SENDING' WHERE NOTICE_ID=? AND MAIL_STATUS_CD='READY'` — 1건 갱신된 인스턴스만 발송 (원자적 선점, 다중 인스턴스 중복 발송 방지).
4. **발송 시점** 인사 마스터로 수신자 추출: 대상 사업장 → 소속 전체 부서 / 대상 부서 (+하위부서) → 소속 사용자(겸직 포함, 사용 중) → 중복 제거.
5. 수신자를 `LANG_CD` 별로 그룹핑 (미설정·사용 중지 언어 → 기본 언어).
6. 그룹마다: 템플릿 = 수신자 언어 (없으면 기본 언어 템플릿), 본문 = 수신자 언어 버전 (없으면 대표 언어 버전). 제목은 HTML 이스케이프.
7. 수신자를 BCC 로 `batch-size`(기본 100) 단위 분할 발송. 이메일 없는 사용자는 FAIL 이력(이메일 주소 없음)만 남긴다.
8. 수신자별 이력 저장, 발송 오류가 하나라도 있으면 공지 `FAIL`, 아니면 `DONE` + `MAIL_SEND_DT`. 발송(외부 호출)은 DB 트랜잭션 밖에서 수행.
9. 발송 중/완료(SENDING/DONE) 공지는 수정 시 메일 설정을 바꾸지 않는다. FAIL 은 수정하면 READY 로 재발송 대기.

`MailGateway`: `spring.mail.host` 가 있으면 JavaMailSender(SMTP), 없으면 로그만 남긴다 (local). 실연동 로그 예:
```
[Mail] (SMTP 미설정 - 로그 대체) subject=[Notice] E2E 메일 공지 lang=en bcc=1건
[Mail] (SMTP 미설정 - 로그 대체) subject=[공지] E2E 메일 공지 lang=ko bcc=1건
[NoticeMail] noticeId=101 결과=DONE 수신자=2명
```

---

## 6. 보안

| 항목 | 처리 |
|---|---|
| 저장형 XSS | 본문 HTML 을 저장 시 jsoup Safelist(relaxed) 로 정화 — script/style/iframe/on* 이벤트/javascript: URL 제거, 링크·이미지는 http/https(/mailto)만, 링크에 `rel="noopener noreferrer"` |
| 첨부 저장 | 웹 경로 밖 디렉터리(`app.notice.file.storage-path`)에 UUID 파일명으로 저장, 원본명은 DB 에만 |
| 경로 조작 | 원본 파일명에서 경로·제어문자 제거, 저장 경로가 저장소 밖으로 해석되면 거부 |
| 업로드 제한 | 허용 확장자 목록, 공지당 최대 개수(10), 빈 파일 거부, 크기는 `spring.servlet.multipart` (20MB / 요청 100MB) |
| 다운로드 | 파일이 해당 공지 소속인지 확인, `application/octet-stream` + `Content-Disposition: attachment` + `nosniff` |
| 예약 공지 | 관리 권한 없는 사용자에게 목록 제외 + 상세 404 |
| 메일 | 수신자 BCC (서로의 주소 비노출), 템플릿 치환값 이스케이프 |

---

## 7. 새 언어 추가 (코드 수정 없음)

1. `TB_CODE` 에 `LANG` 코드 추가 (`USE_YN='Y'`, `SORT_ORDER`, `ATTR1` = 에디터 언어팩 예: `ja-JP`)
2. `TB_CODE_LANG` 에 언어명 등록 (각 표시 언어별)
3. 메시지 리소스 `messages_{locale}.properties` 추가 — 없으면 기본 메시지(`messages.properties`, 한국어)로 응답
4. `TB_MAIL_TEMPLATE` 에 `NOTICE` 템플릿 등록 — 없으면 기본 언어 템플릿 사용
5. (선택) 코드/메뉴/권한의 `*_LANG` 이름 등록 — 없으면 기본 언어 이름

언어 탭·언어 선택·에디터 매핑·입력 검증·배지가 즉시 반영된다 (`addLanguageWithoutCodeChange` 테스트: ja 추가 후 언어 목록과 공지 등록/조회 확인). 공통코드는 60초 캐시이므로 최대 60초 뒤 반영. 인사 마스터는 국문/영문만 있어 다른 언어 사용자는 영문 명칭을 본다.

---

## 8. 설정

| 속성 | 환경변수 | 기본값 |
|---|---|---|
| `app.notice.file.storage-path` | `NOTICE_FILE_PATH` | `./data/notice-files` |
| `app.notice.file.max-files` | | 10 |
| `app.notice.file.allowed-extensions` | | pdf, doc(x), xls(x), ppt(x), hwp(x), txt, csv, zip, png, jpg, jpeg, gif |
| `app.notice.mail.scheduler-enabled` | `NOTICE_MAIL_SCHEDULER_ENABLED` | true (테스트 false) |
| `app.notice.mail.poll-interval` | `NOTICE_MAIL_POLL_INTERVAL` | 60s |
| `app.notice.mail.batch-size` | `NOTICE_MAIL_BATCH_SIZE` | 100 |
| `app.notice.mail.from` | `MAIL_FROM` | noreply@flowlog.local |
| `app.notice.mail.link-template` | `NOTICE_LINK_TEMPLATE` | `http://localhost:9909/notices/{id}` |
| `spring.mail.host/port/username/password` | (Boot 표준) | 미설정 = 로그 대체 |
| `spring.servlet.multipart.max-file-size / max-request-size` | `UPLOAD_MAX_FILE_SIZE` / `UPLOAD_MAX_REQUEST_SIZE` | 20MB / 100MB |

---

## 9. 확인 필요 / 가정 사항

1. **NT-63 언어별 첨부 분리** — 현재 게시물 공통. 언어별로 다른 첨부가 필요하면 `TB_NOTICE_FILE.LANG_CD`(NULL = 공통) 추가로 확장 가능.
2. **MAIL_SEND_TYPE 코드값** — 요구사항에 정의가 없어 `NONE`(발송 안 함) / `TARGET`(대상 지정 발송)으로 가정.
3. **즉시/예약 발송** — 별도 발송일시 없이 **게시일시**에 발송 (게시일시가 과거/현재면 다음 폴링 주기에 발송).
4. **조회수** — 사용자·일자별 1회 증가로 가정 (`TB_NOTICE_VIEW_LOG.VIEW_DATE`).
5. **WORKPLACE 공통코드 그룹** — 이 프로젝트 공통코드에는 원래 없었다. 사업장은 인사 마스터 참조로 구현.
6. **인사 마스터** — `TB_WORKPLACE/TB_DEPT/TB_USER(_DEPT)` 로컬 테이블. 인사 시스템 연동 배치/I/F 는 미구현.
7. **SMTP** — 설정 전까지 로그로 대체. 운영 SMTP 정보·발신 주소 확정 필요.
8. **다중 인스턴스 중복 발송 방지** — 상태 선점으로 구현. local 은 인스턴스마다 H2 가 분리되어 실연동 E2E 로는 검증 불가 → `NoticeMailServiceTest.noDuplicateAndScheduledSkipped` 로 검증.
9. **사용자 설정 언어 우선** — `Accept-Language` 보다 `TB_USER.LANG_CD` 가 우선한다. **메뉴명에도 적용되는 동작 변경** (예: admin 은 언어 설정 ko 이므로 en-US 헤더를 보내도 한국어 메뉴명).
10. **응답 `locale` 필드** — 표준 응답의 `locale` 은 기존대로 `ko_KR`/`en_US` 형식 (메시지 리소스 Locale). 데이터 언어 코드는 `ko`/`en`.
11. **이메일 없는 수신자** — 발송 실패 이력(FAIL, "이메일 주소 없음")만 남기고 공지 상태에는 영향 없음.

---

## 10. 테스트

| 클래스 | 건수 | 내용 |
|---|---|---|
| `NoticeApiIntegrationTest` | 23 | 지원 언어 3, 등록 규칙 7, 조회 언어/배지/검색 7, 수정/삭제/권한 2, 첨부 2, 조직도/공통코드 2 |
| `NoticeMailServiceTest` | 5 | 언어별 그룹 발송, 중복 발송 없음·예약 미발송, 발송 시점 인사 마스터, 실패 이력, 영어 버전 |
| `OracleSchemaTest` | 2 | Oracle DDL/데이터/validate + 공지 네이티브 검색 (CLOB LIKE) |
| E2E `scenario_notice` | 9 | 언어 목록, 대체 표시·배지, 대체 표시분 검색, 언어 전환, 조직도 영문, 등록(XSS 정화), Gateway 경유 첨부, 권한, 메일 언어별 발송 |
