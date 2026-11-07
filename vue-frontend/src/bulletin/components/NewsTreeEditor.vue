<script setup lang="ts">
import RowTools from '@/global/components/RowTools.vue'
import { moveItem, removeItem } from '@/global/util/list'
import { emptyNews, MAX_NEWS_DEPTH } from '@/bulletin/form'
import type { NewsNode } from '@/bulletin/types'

/**
 * 교회소식 편집 (재귀) : 섹션(1단계) → 항목(2단계) → 하위 항목(3단계)
 *  - 섹션 : 제목(기도제목) + 내용
 *  - 항목 : 제목(금요기도회) + 일정(매주, 11/14) + 내용(여러 줄) + 강조
 */
const props = defineProps<{ nodes: NewsNode[]; depth: number; prefix?: string }>()

function add(): void {
  props.nodes.push(emptyNews())
}

function label(index: number): string {
  return props.prefix ? `${props.prefix}-${index + 1}` : `${index + 1}`
}
</script>

<template>
  <ol class="news-list" :class="`depth-${depth}`">
    <li v-for="(node, i) in nodes" :key="i" class="news-node" :data-test="`news-depth-${depth}`">
      <div class="news-row">
        <span class="news-no num">{{ label(i) }}</span>
        <div class="news-fields">
          <div class="news-line">
            <input v-model="node.title" class="input title" maxlength="200"
              :placeholder="depth === 1 ? $t('bulletin.news.sectionTitle') : $t('bulletin.news.itemTitle')"
              :aria-label="$t('bulletin.news.title')" data-test="news-title">
            <input v-if="depth > 1" v-model="node.scheduleText" class="input schedule" maxlength="100"
              :placeholder="$t('bulletin.news.schedule')" :aria-label="$t('bulletin.news.schedule')">
            <label class="check"><input v-model="node.highlightYn" type="checkbox" true-value="Y" false-value="N">
              {{ $t('bulletin.news.highlight') }}</label>
            <RowTools :index="i" :length="nodes.length" :label="$t('bulletin.news.title')"
              @up="moveItem(nodes, i, -1)" @down="moveItem(nodes, i, 1)" @remove="removeItem(nodes, i)" />
          </div>
          <textarea v-model="node.content" class="textarea content" maxlength="4000" rows="1"
            :placeholder="$t('bulletin.news.content')" :aria-label="$t('bulletin.news.content')"
            data-test="news-content" />
          <NewsTreeEditor v-if="depth < MAX_NEWS_DEPTH" :nodes="node.children" :depth="depth + 1"
            :prefix="label(i)" />
        </div>
      </div>
    </li>
    <li class="news-add">
      <button type="button" class="btn btn-sm" :class="{ 'btn-quiet': depth > 1 }"
        :data-test="`news-add-${depth}`" @click="add">
        + {{ depth === 1 ? $t('bulletin.news.addSection') : depth === 2 ? $t('bulletin.news.addItem')
          : $t('bulletin.news.addSubItem') }}</button>
    </li>
  </ol>
</template>

<style scoped>
.news-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.news-list.depth-2,
.news-list.depth-3 {
  margin-top: var(--space-2);
  padding-left: var(--space-4);
  border-left: 2px solid var(--rule);
}

.depth-1 > .news-node {
  padding: var(--space-3);
  border: 1px solid var(--rule);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.news-row {
  display: flex;
  gap: var(--space-2);
}

.news-no {
  flex: none;
  min-width: 2.5em;
  padding-top: 8px;
  color: var(--ink-3);
  font-size: var(--text-xs);
}

.news-fields {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
}

.news-line {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.news-line .title {
  flex: 1;
  min-width: 0;
}

.news-line .schedule {
  flex: 0 0 140px;
}

.content {
  min-height: 38px;
}
</style>
