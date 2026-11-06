<script setup lang="ts">
import Editor from '@toast-ui/editor'
import '@toast-ui/editor/dist/toastui-editor.css'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * 본문 에디터 (TOAST UI Editor, WYSIWYG → HTML).
 *
 *  - 에디터 언어팩 = 지원 언어의 LANG.ATTR1 (예: ko-KR, en-US). 언어별 탭마다 그 언어의 언어팩을 쓴다.
 *    언어팩 파일은 이름 규칙(ko-KR → ko-kr.js)으로 찾으므로 새 언어를 추가해도 코드 수정이 없다.
 *    파일이 없는 언어(en-US 는 내장)는 기본(영어) UI 로 표시된다.
 *  - 이미지는 본문에 넣지 않고 첨부파일로 등록한다. (서버 정화에서 data: URI 제거)
 *  - 사용 통계 전송 끔 (usageStatistics: false)
 */
const props = withDefaults(defineProps<{
  modelValue: string
  editorLocale: string
  height?: string
  placeholder?: string
}>(), { height: '420px', placeholder: '' })

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const LOCALE_PACKS = import.meta.glob('/node_modules/@toast-ui/editor/dist/esm/i18n/*.js')

const host = ref<HTMLElement | null>(null)
let editor: Editor | null = null
let lastHtml = ''
let unmounted = false

async function loadLocalePack(locale: string): Promise<void> {
  const loader = LOCALE_PACKS[`/node_modules/@toast-ui/editor/dist/esm/i18n/${locale.toLowerCase()}.js`]
  if (loader) {
    await loader()
  }
}

onMounted(async () => {
  await loadLocalePack(props.editorLocale)
  // 언어팩을 읽는 동안 탭을 옮겨 이미 사라졌으면 만들지 않는다
  if (unmounted || !host.value) return
  editor = new Editor({
    el: host.value,
    height: props.height,
    initialEditType: 'wysiwyg',
    previewStyle: 'vertical',
    hideModeSwitch: true,
    language: props.editorLocale,
    placeholder: props.placeholder,
    usageStatistics: false,
    autofocus: false,
    toolbarItems: [
      ['heading', 'bold', 'italic', 'strike'],
      ['hr', 'quote'],
      ['ul', 'ol', 'indent', 'outdent'],
      ['table', 'link'],
      ['code', 'codeblock'],
    ],
    events: {
      change: () => {
        if (!editor) return
        lastHtml = editor.getHTML()
        emit('update:modelValue', lastHtml)
      },
    },
  })
  if (props.modelValue) {
    editor.setHTML(props.modelValue, false)
    lastHtml = editor.getHTML()
  }
})

// 외부에서 값이 바뀐 경우만 반영 (입력 중 커서 이동 방지)
watch(() => props.modelValue, (value) => {
  if (editor && value !== lastHtml) {
    editor.setHTML(value ?? '', false)
    lastHtml = editor.getHTML()
  }
})

onBeforeUnmount(() => {
  unmounted = true
  editor?.destroy()
  editor = null
})
</script>

<template>
  <div ref="host" class="notice-editor" :lang="editorLocale" />
</template>

<style scoped>
.notice-editor :deep(.toastui-editor-defaultUI) {
  border-color: var(--rule-strong);
  border-radius: var(--radius-sm);
  font-family: var(--font-sans);
}

.notice-editor :deep(.toastui-editor-defaultUI-toolbar) {
  background: var(--paper);
  border-bottom-color: var(--rule);
}

.notice-editor :deep(.toastui-editor-contents) {
  font-family: var(--font-sans);
  font-size: 14px;
}

.notice-editor :deep(.ProseMirror) {
  min-height: 100%;
}
</style>
