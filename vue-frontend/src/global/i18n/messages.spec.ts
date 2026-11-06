import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import en from './messages/en'
import ko from './messages/ko'

type Tree = { [key: string]: string | Tree }

function flatten(tree: Tree, prefix = ''): string[] {
  return Object.entries(tree).flatMap(([key, value]) =>
    typeof value === 'string' ? [`${prefix}${key}`] : flatten(value, `${prefix}${key}.`))
}

function valueOf(tree: Tree, key: string): string {
  return key.split('.').reduce<string | Tree>((node, part) => (node as Tree)[part]!, tree) as string
}

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return sourceFiles(path)
    return /\.(vue|ts)$/.test(name) && !name.endsWith('.spec.ts') ? [path] : []
  })
}

describe('화면 문구', () => {
  const koKeys = flatten(ko as Tree)

  it('언어 파일의 키가 모두 같다', () => {
    expect(flatten(en as Tree).sort()).toEqual([...koKeys].sort())
  })

  it('vue-i18n 예약 문자(@ |)를 쓰지 않는다', () => {
    const values = [ko, en].flatMap((tree) =>
      flatten(tree as Tree).map((key) => valueOf(tree as Tree, key)))
    expect(values.filter((value) => /[@|]/.test(value))).toEqual([])
  })

  it('소스에서 쓰는 고정 키가 모두 정의되어 있다', () => {
    const used = new Set<string>()
    for (const file of sourceFiles(join(__dirname, '../..'))) {
      const source = readFileSync(file, 'utf-8')
      for (const match of source.matchAll(/\$?\bt\(\s*'([a-zA-Z0-9_.]+)'/g)) used.add(match[1]!)
      for (const match of source.matchAll(/title: '([a-z]+\.[a-zA-Z.]+)'/g)) used.add(match[1]!)
    }
    expect(used.size).toBeGreaterThan(100)
    expect([...used].filter((key) => !koKeys.includes(key))).toEqual([])
  })
})
