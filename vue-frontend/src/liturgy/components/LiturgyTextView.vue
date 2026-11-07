<script setup lang="ts">
import { useCodeStore } from '@/code/store'
import type { LiturgyText } from '@/liturgy/types'

/** 전례문 보기 (주보·전례문 화면 공용) - 화자가 있으면 "인도자 : …" 교독 형식 */
defineProps<{ text: LiturgyText }>()
const code = useCodeStore()
void code.getCodes('LITURGY_SPEAKER')
</script>

<template>
  <article class="liturgy" data-test="liturgy-text">
    <h3 class="title">{{ text.title }}<span v-if="text.versionNm" class="version">({{ text.versionNm }})</span></h3>
    <p v-if="text.intro" class="intro">{{ text.intro }}</p>
    <div v-for="(line, i) in text.lines" :key="i" class="line"
      :class="[line.speakerCd ? `speaker-${line.speakerCd.toLowerCase()}` : 'plain']">
      <span v-if="line.speakerCd" class="who">{{ code.codeName('LITURGY_SPEAKER', line.speakerCd) }}</span>
      <p class="content">{{ line.content }}</p>
    </div>
  </article>
</template>

<style scoped>
.title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-md);
}

.version {
  margin-left: 4px;
  color: var(--ink-3);
  font-size: var(--text-sm);
  font-weight: 400;
}

.intro {
  margin: 0 0 var(--space-3);
  color: var(--ink-3);
  font-size: var(--text-sm);
}

.line {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  line-height: 1.7;
}

.who {
  flex: none;
  min-width: 3.5em;
  color: var(--ink-3);
  font-size: var(--text-sm);
}

.content {
  margin: 0;
  white-space: pre-line;
}

.speaker-leader .content {
  color: var(--ink-2);
  font-size: var(--text-sm);
}

.speaker-people .content,
.speaker-all .content {
  font-weight: 600;
}

.plain .content {
  font-weight: 600;
}
</style>
