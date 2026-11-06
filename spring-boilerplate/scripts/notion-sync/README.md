# 노션 동기화 (docs → Notion)

저장소 `docs/*.md` 가 원본이고, 노션 페이지 본문은 여기서 **통째로 다시 쓴다.** 노션에서 직접 고친 내용은 다음 동기화 때 사라지므로 수정은 저장소 문서에서 한다.

| 파일 | 역할 |
|---|---|
| `pages.json` | 문서 ↔ 노션 페이지 ID 매핑 (새 문서를 노션에 올리려면 여기에 추가) |
| `sync.mjs` | Markdown → 노션 블록 변환(martian) 후 페이지 본문 교체. 맨 위에 원본 파일·커밋 안내 |
| 저장소 루트 `.github/workflows/notion-sync.yml` | main 에 `spring-boilerplate/docs/**` 가 바뀌면 바뀐 문서만 동기화 (수동 실행 시 전체) |

## 준비 (1회)

1. 노션 → 설정 → 연결 → **내부 통합(Internal integration)** 생성, 권한 「콘텐츠 읽기·수정·삽입」
2. 노션 상위 페이지 「spring-boilerplate (Flowlog Backend API)」 → ··· → 연결 추가 → 위 통합 선택 (하위 페이지에 상속)
3. GitHub 저장소 → Settings → Secrets → `NOTION_TOKEN` 에 통합 토큰 저장 (토큰은 코드·문서에 쓰지 않는다)

## 실행

```bash
cd scripts/notion-sync && npm install
node sync.mjs --dry-run                              # 토큰 없이 변환만 확인 (문서별 블록 수)
NOTION_TOKEN=... node sync.mjs                       # 전체
NOTION_TOKEN=... node sync.mjs docs/design/03_notice.md
```

## 동기화하지 않는 페이지

저장소에 원본이 없는 노션 전용 페이지(「07. 인증·인가 Workflow」, 「08. 단계별 화면 캡처」, 상위 페이지 요약)와 다른 저장소 문서(Gateway·화면)는 매핑하지 않는다.
