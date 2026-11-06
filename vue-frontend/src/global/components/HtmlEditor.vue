<script setup lang="ts">
import { TableKit } from '@tiptap/extension-table'
import { Placeholder } from '@tiptap/extensions'
import StarterKit from '@tiptap/starter-kit'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { isSafeExternalUrl } from '@/menu/link'

/**
 * 본문 에디터 (Tiptap / ProseMirror, WYSIWYG → HTML).
 *
 *  - 언어별 탭마다 하나씩 쓴다. editorLocale(LANG.ATTR1, 예 ko-KR)은 본문 영역의 lang 속성
 *    (맞춤법 검사·글꼴)에 쓰고, 도구 모음 문구는 화면 언어(vue-i18n)를 따른다.
 *  - 이미지는 본문에 넣지 않고 첨부파일로 등록한다. (서버 정화에서 data: URI 제거)
 *  - 링크는 http/https 만. 저장 전 서버에서 다시 정화한다 (HtmlSanitizer)
 */
const props = withDefaults(defineProps<{
  modelValue: string
  editorLocale: string
  height?: string
  placeholder?: string
}>(), { height: '420px', placeholder: '' })

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const { t } = useI18n()

let lastHtml = props.modelValue ?? ''

const editor = useEditor({
  content: lastHtml,
  extensions: [
    StarterKit.configure({
      heading: { levels: [2, 3, 4] },
      link: {
        openOnClick: false,
        autolink: true,
        protocols: ['http', 'https'],
        isAllowedUri: (url) => isSafeExternalUrl(url),
        HTMLAttributes: { rel: 'noopener noreferrer', target: '_blank' },
      },
    }),
    TableKit.configure({ table: { resizable: false } }),
    Placeholder.configure({ placeholder: () => props.placeholder }),
  ],
  editorProps: {
    attributes: { lang: props.editorLocale, spellcheck: 'true' },
  },
  onUpdate: ({ editor: current }) => {
    lastHtml = current.isEmpty ? '' : current.getHTML()
    emit('update:modelValue', lastHtml)
  },
})

// 외부에서 값이 바뀐 경우만 반영 (입력 중 커서 이동 방지)
watch(() => props.modelValue, (value) => {
  if (editor.value && (value ?? '') !== lastHtml) {
    lastHtml = value ?? ''
    editor.value.commands.setContent(lastHtml, { emitUpdate: false })
  }
})

// ------------------------------------------------------------------ 링크 입력

const linkOpen = ref(false)
const linkUrl = ref('')
const linkError = ref(false)
const linkInput = ref<HTMLInputElement | null>(null)

async function openLink(): Promise<void> {
  linkUrl.value = (editor.value?.getAttributes('link').href as string | undefined) ?? 'https://'
  linkError.value = false
  linkOpen.value = true
  await nextTick()
  linkInput.value?.select()
}

function applyLink(): void {
  const chain = editor.value?.chain().focus().extendMarkRange('link')
  if (!chain) return
  const url = linkUrl.value.trim()
  if (!url) {
    chain.unsetLink().run()
  } else if (!isSafeExternalUrl(url)) {
    linkError.value = true
    return
  } else {
    chain.setLink({ href: url }).run()
  }
  linkOpen.value = false
}

// ------------------------------------------------------------------ 도구 모음

interface Tool {
  key: string
  label: string
  run: () => void
  active?: () => boolean
  disabled?: () => boolean
}

const run = () => editor.value!.chain().focus()

// 화면 언어가 바뀌면 문구도 바뀌도록 computed
const groups = computed<Tool[][]>(() => [
  [
    { key: 'h2', label: 'H2', run: () => run().toggleHeading({ level: 2 }).run(),
      active: () => !!editor.value?.isActive('heading', { level: 2 }) },
    { key: 'h3', label: 'H3', run: () => run().toggleHeading({ level: 3 }).run(),
      active: () => !!editor.value?.isActive('heading', { level: 3 }) },
    { key: 'bold', label: 'B', run: () => run().toggleBold().run(),
      active: () => !!editor.value?.isActive('bold') },
    { key: 'italic', label: 'I', run: () => run().toggleItalic().run(),
      active: () => !!editor.value?.isActive('italic') },
    { key: 'strike', label: 'S', run: () => run().toggleStrike().run(),
      active: () => !!editor.value?.isActive('strike') },
  ],
  [
    { key: 'bulletList', label: '•', run: () => run().toggleBulletList().run(),
      active: () => !!editor.value?.isActive('bulletList') },
    { key: 'orderedList', label: '1.', run: () => run().toggleOrderedList().run(),
      active: () => !!editor.value?.isActive('orderedList') },
    { key: 'indent', label: '→', run: () => run().sinkListItem('listItem').run(),
      disabled: () => !editor.value?.can().sinkListItem('listItem') },
    { key: 'outdent', label: '←', run: () => run().liftListItem('listItem').run(),
      disabled: () => !editor.value?.can().liftListItem('listItem') },
  ],
  [
    { key: 'quote', label: '❝', run: () => run().toggleBlockquote().run(),
      active: () => !!editor.value?.isActive('blockquote') },
    { key: 'hr', label: '—', run: () => run().setHorizontalRule().run() },
    { key: 'code', label: '<>', run: () => run().toggleCode().run(),
      active: () => !!editor.value?.isActive('code') },
    { key: 'codeBlock', label: '{ }', run: () => run().toggleCodeBlock().run(),
      active: () => !!editor.value?.isActive('codeBlock') },
  ],
  [
    { key: 'link', label: t('editor.short.link'), run: () => void openLink(),
      active: () => !!editor.value?.isActive('link') },
    { key: 'table', label: '▦', run: () => run().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run() },
    { key: 'addRow', label: t('editor.short.addRow'), run: () => run().addRowAfter().run(),
      disabled: () => !editor.value?.can().addRowAfter() },
    { key: 'addColumn', label: t('editor.short.addColumn'), run: () => run().addColumnAfter().run(),
      disabled: () => !editor.value?.can().addColumnAfter() },
    { key: 'deleteTable', label: t('editor.short.deleteTable'), run: () => run().deleteTable().run(),
      disabled: () => !editor.value?.can().deleteTable() },
  ],
  [
    { key: 'undo', label: '↶', run: () => run().undo().run(),
      disabled: () => !editor.value?.can().undo() },
    { key: 'redo', label: '↷', run: () => run().redo().run(),
      disabled: () => !editor.value?.can().redo() },
  ],
])

onBeforeUnmount(() => editor.value?.destroy())
</script>

<template>
  <div class="html-editor" :style="{ '--editor-height': height }">
    <div class="toolbar" role="toolbar" :aria-label="t('editor.toolbar')">
      <div v-for="(group, index) in groups" :key="index" class="group">
        <button v-for="tool in group" :key="tool.key" type="button" class="tool"
          :class="{ active: tool.active?.() }" :disabled="!editor || tool.disabled?.()"
          :title="t(`editor.${tool.key}`)" :aria-label="t(`editor.${tool.key}`)"
          :aria-pressed="tool.active ? tool.active() : undefined" :data-test="`editor-tool-${tool.key}`"
          @mousedown.prevent @click="tool.run()">{{ tool.label }}</button>
      </div>
    </div>
    <form v-if="linkOpen" class="link-bar" @submit.prevent="applyLink">
      <label class="sr-only" :for="`link-${editorLocale}`">{{ t('editor.linkUrl') }}</label>
      <input :id="`link-${editorLocale}`" ref="linkInput" v-model="linkUrl" class="input" type="url"
        :aria-invalid="linkError" :placeholder="t('editor.linkPlaceholder')" data-test="editor-link-url"
        @keydown.esc.prevent="linkOpen = false">
      <button type="submit" class="btn btn-sm btn-primary">{{ t('editor.linkApply') }}</button>
      <button type="button" class="btn btn-sm" @click="linkOpen = false">{{ t('common.cancel') }}</button>
      <span v-if="linkError" class="link-error">{{ t('editor.linkInvalid') }}</span>
    </form>
    <EditorContent :editor="editor" class="content" />
  </div>
</template>

<style scoped>
.html-editor {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--rule-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
}

/* 포커스 표시는 본문 영역이 아니라 에디터 테두리에 */
.html-editor:focus-within {
  border-color: var(--accent);
  box-shadow: var(--focus-ring);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--rule);
  background: var(--paper);
}

.group {
  display: flex;
  gap: 2px;
}

.group + .group {
  padding-left: 6px;
  border-left: 1px solid var(--rule);
}

.tool {
  min-width: 28px;
  height: 28px;
  padding: 0 5px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--ink-2);
  font: 600 12px/1 var(--font-sans);
  cursor: pointer;
}

.tool:hover:not(:disabled) {
  border-color: var(--rule-strong);
  color: var(--ink);
}

.tool.active {
  border-color: var(--rule-strong);
  background: var(--surface);
  color: var(--ink);
}

.tool:disabled {
  opacity: 0.35;
  cursor: default;
}

.link-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--rule);
}

.link-bar .input {
  flex: 1;
  max-width: 420px;
}

.link-error {
  color: var(--danger);
  font-size: var(--text-sm);
}

.content {
  height: var(--editor-height);
  overflow-y: auto;
  padding: 12px 16px;
}

.content :deep(.ProseMirror) {
  min-height: calc(var(--editor-height) - 24px);
  outline: none;
  box-shadow: none;
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 1.7;
}

.content :deep(.ProseMirror p.is-editor-empty:first-child::before) {
  float: left;
  height: 0;
  color: var(--ink-3);
  content: attr(data-placeholder);
  pointer-events: none;
}

.content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
}

.content :deep(th),
.content :deep(td) {
  min-width: 60px;
  padding: 4px 8px;
  border: 1px solid var(--rule-strong);
  vertical-align: top;
}

.content :deep(th) {
  background: var(--paper);
  font-weight: 600;
}

.content :deep(.selectedCell) {
  background: var(--paper);
}

.content :deep(blockquote) {
  margin: 8px 0;
  padding-left: 12px;
  border-left: 3px solid var(--rule-strong);
  color: var(--ink-2);
}

.content :deep(pre) {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--paper);
  font-family: var(--font-mono);
  font-size: 13px;
}
</style>
