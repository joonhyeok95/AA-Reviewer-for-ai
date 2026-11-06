/**
 * 저장소 문서(원본) → 노션 페이지 본문 동기화.
 *
 *   NOTION_TOKEN=secret_xxx node sync.mjs            # 전체 (pages.json)
 *   NOTION_TOKEN=secret_xxx node sync.mjs docs/design/03_notice.md
 *   node sync.mjs --dry-run                          # 토큰 없이 변환만 확인 (블록 수)
 *
 * - 원본은 저장소 docs/*.md 다. 노션 페이지 본문은 매번 통째로 다시 쓴다 (노션에서 직접 고친 내용은 사라짐).
 * - 페이지 제목은 바꾸지 않는다. 문서 첫 줄의 H1 은 제목과 겹치므로 본문에서 뺀다.
 * - 본문 맨 위에 "자동 동기화" 안내(원본 파일, 커밋)를 넣는다.
 * - 노션 통합(integration)을 만들고 각 페이지(또는 상위 페이지)에 연결해야 한다. 토큰은 환경변수로만.
 */
import { execSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { Client } from '@notionhq/client'
import { markdownToBlocks } from '@tryfabric/martian'

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, '../..')
const DRY_RUN = process.argv.includes('--dry-run')
const only = process.argv.slice(2).filter((arg) => !arg.startsWith('--'))
const { pages } = JSON.parse(readFileSync(join(HERE, 'pages.json'), 'utf-8'))
const targets = only.length ? pages.filter((page) => only.includes(page.file)) : pages

/** 노션 API 한 번에 추가할 수 있는 블록 수 */
const APPEND_LIMIT = 100

function commitOf(file) {
  try {
    return execSync(`git log -1 --format=%h -- "${file}"`, { cwd: REPO }).toString().trim()
  } catch {
    return 'unknown'
  }
}

function toBlocks(file) {
  const markdown = readFileSync(join(REPO, file), 'utf-8').replace(/^﻿/, '')
    .replace(/^# .*\r?\n/, '')
  const notice = {
    object: 'block',
    type: 'callout',
    callout: {
      icon: { type: 'emoji', emoji: '🔄' },
      color: 'gray_background',
      rich_text: [{
        type: 'text',
        text: { content: `저장소 ${file} (커밋 ${commitOf(file)}) 에서 자동 동기화된 페이지입니다. 수정은 저장소 문서에서 하세요.` },
      }],
    },
  }
  return [notice, ...markdownToBlocks(markdown, { strictImageUrls: false })]
}

async function listChildren(notion, pageId) {
  const ids = []
  let cursor
  do {
    const response = await notion.blocks.children.list({ block_id: pageId, start_cursor: cursor })
    ids.push(...response.results
      // 하위 페이지·DB 는 지우지 않는다
      .filter((block) => block.type !== 'child_page' && block.type !== 'child_database')
      .map((block) => block.id))
    cursor = response.has_more ? response.next_cursor : undefined
  } while (cursor)
  return ids
}

async function sync(notion, { file, pageId }) {
  const blocks = toBlocks(file)
  if (DRY_RUN) {
    console.log(`[dry-run] ${file} → ${pageId} : 블록 ${blocks.length}개`)
    return
  }
  for (const id of await listChildren(notion, pageId)) {
    await notion.blocks.delete({ block_id: id })
  }
  for (let from = 0; from < blocks.length; from += APPEND_LIMIT) {
    await notion.blocks.children.append({
      block_id: pageId, children: blocks.slice(from, from + APPEND_LIMIT),
    })
  }
  console.log(`[sync] ${file} → ${pageId} : 블록 ${blocks.length}개`)
}

if (!DRY_RUN && !process.env.NOTION_TOKEN) {
  console.error('NOTION_TOKEN 환경변수가 없습니다. (변환만 확인하려면 --dry-run)')
  process.exit(1)
}
const unmapped = only.filter((file) => !pages.some((page) => page.file === file))
if (unmapped.length) {
  console.log(`[skip] pages.json 에 매핑이 없는 문서 : ${unmapped.join(', ')}`)
}
if (!targets.length) {
  console.log('동기화할 문서가 없습니다.')
  process.exit(0)
}

const notion = DRY_RUN ? null : new Client({ auth: process.env.NOTION_TOKEN })
let failed = 0
for (const target of targets) {
  try {
    await sync(notion, target)
  } catch (error) {
    failed += 1
    console.error(`[실패] ${target.file} : ${error.message}`)
  }
}
process.exit(failed ? 1 : 0)
