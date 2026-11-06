<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/auth/store'
import { useCodeStore } from '@/code/store'
import type { Code, LangNames, YesNo } from '@/code/types'
import { ApiError } from '@/global/api/ApiError'
import ConfirmDialog from '@/global/components/ConfirmDialog.vue'
import EmptyState from '@/global/components/EmptyState.vue'
import HtmlEditor from '@/global/components/HtmlEditor.vue'
import LangNamesInput from '@/global/components/LangNamesInput.vue'
import { useApiFeedback } from '@/global/composables/useApiFeedback'
import {
  createMenu, deleteMenu, fetchMenu, fetchMenuTree, reorderMenus, updateMenu,
} from '@/menu/api'
import MenuTreeItem from '@/menu/components/MenuTreeItem.vue'
import { useMenuStore } from '@/menu/store'
import type { MenuAdminNode, MenuDetail, MenuSaveRequest, MenuViewType } from '@/menu/types'

/**
 * 메뉴 관리. 왼쪽 전체 메뉴 트리(미사용·미노출 포함, 위/아래 이동) → 오른쪽 선택 메뉴 편집.
 *  - URL 은 유형별 : MENU = /경로 (하위 메뉴 묶음이면 비워도 됨), LINK·IFRAME = http(s), CONTENT = 없음(본문)
 *  - 권한코드(permCd)를 주면 권한관리의 조회/등록/수정/삭제가 API 권한(permCd.list …)이 된다
 *  - 저장하면 서버 권한 캐시가 비워지고, 내 메뉴도 다시 불러온다
 */
interface MenuForm {
  parentMenuId: number | null
  moduleCd: string
  viewTypeCd: MenuViewType
  menuUrl: string
  permCd: string
  sortOrder: number
  newWindowYn: YesNo
  adminMenuYn: YesNo
  sideMenuYn: YesNo
  displayYn: YesNo
  useYn: YesNo
  names: LangNames
  contents: Record<string, string>
}

const auth = useAuthStore()
const code = useCodeStore()
const menuStore = useMenuStore()
const feedback = useApiFeedback()
const { t } = useI18n()

const tree = ref<MenuAdminNode[]>([])
const collapsed = ref(new Set<number>())
const selectedId = ref<number | null>(null)
const detail = ref<MenuDetail | null>(null)
const creating = ref(false)
const viewTypes = ref<Code[]>([])
const modules = ref<Code[]>([])
const contentLang = ref('')
const saving = ref(false)
const confirmDelete = ref(false)
const errors = ref<Record<string, string>>({})

const form = reactive<MenuForm>(emptyForm(null))

const canAdd = computed(() => auth.can('menu', 'add'))
const canEdit = computed(() => auth.can('menu', 'edit'))
const canDel = computed(() => auth.can('menu', 'del'))
const editable = computed(() => (creating.value ? canAdd.value : canEdit.value))
const showForm = computed(() => creating.value || detail.value !== null)

/** 상위 메뉴 선택지 : 자기 자신과 하위 메뉴 제외 (순환 방지) */
const parentOptions = computed(() => {
  const options: { menuId: number; label: string }[] = []
  const walk = (nodes: MenuAdminNode[], depth: number) => nodes.forEach((node) => {
    if (!creating.value && node.menuId === selectedId.value) return
    options.push({ menuId: node.menuId, label: `${'— '.repeat(depth)}${node.menuNm}` })
    walk(node.children, depth + 1)
  })
  walk(tree.value, 0)
  return options
})

const urlHint = computed(() => t(`menuAdmin.urlHint.${form.viewTypeCd}`))

function emptyForm(parentMenuId: number | null): MenuForm {
  return {
    parentMenuId, moduleCd: '', viewTypeCd: 'MENU', menuUrl: '', permCd: '', sortOrder: 1,
    newWindowYn: 'N', adminMenuYn: 'N', sideMenuYn: 'N', displayYn: 'Y', useYn: 'Y',
    names: {}, contents: {},
  }
}

function fill(source: MenuForm): void {
  Object.assign(form, source)
  contentLang.value = code.langs[0]?.langCd ?? ''
  errors.value = {}
}

function siblingsOf(parentId: number | null): MenuAdminNode[] {
  if (parentId === null) return tree.value
  const find = (nodes: MenuAdminNode[]): MenuAdminNode | undefined => {
    for (const node of nodes) {
      if (node.menuId === parentId) return node
      const found = find(node.children)
      if (found) return found
    }
    return undefined
  }
  return find(tree.value)?.children ?? []
}

async function loadTree(): Promise<void> {
  try {
    tree.value = await fetchMenuTree()
  } catch (error) {
    feedback.failure(error)
  }
}

async function select(menuId: number): Promise<void> {
  try {
    const data = await fetchMenu(menuId)
    creating.value = false
    selectedId.value = menuId
    detail.value = data
    const names: LangNames = {}
    const contents: Record<string, string> = {}
    data.i18n.forEach((item) => {
      names[item.langCd] = item.menuNm
      contents[item.langCd] = item.menuContent ?? ''
    })
    fill({
      parentMenuId: data.parentMenuId, moduleCd: data.moduleCd ?? '', viewTypeCd: data.viewTypeCd,
      menuUrl: data.menuUrl ?? '', permCd: data.permCd ?? '', sortOrder: data.sortOrder,
      newWindowYn: data.newWindowYn, adminMenuYn: data.adminMenuYn, sideMenuYn: data.sideMenuYn,
      displayYn: data.displayYn, useYn: data.useYn, names, contents,
    })
  } catch (error) {
    feedback.failure(error)
  }
}

function startCreate(parentMenuId: number | null): void {
  creating.value = true
  detail.value = null
  selectedId.value = null
  const nextSort = Math.max(0, ...siblingsOf(parentMenuId).map((m) => m.sortOrder)) + 1
  fill({ ...emptyForm(parentMenuId), sortOrder: nextSort })
}

function toggle(menuId: number): void {
  const next = new Set(collapsed.value)
  if (next.has(menuId)) next.delete(menuId)
  else next.add(menuId)
  collapsed.value = next
}

async function move(menuId: number, direction: -1 | 1): Promise<void> {
  const node = findNode(tree.value, menuId)
  if (!node) return
  const ids = siblingsOf(node.parentMenuId).map((m) => m.menuId)
  const index = ids.indexOf(menuId)
  const target = index + direction
  if (target < 0 || target >= ids.length) return
  ;[ids[index], ids[target]] = [ids[target]!, ids[index]!]
  try {
    await reorderMenus(node.parentMenuId, ids)
    await Promise.all([loadTree(), menuStore.load()])
  } catch (error) {
    feedback.failure(error)
  }
}

function findNode(nodes: MenuAdminNode[], menuId: number): MenuAdminNode | undefined {
  for (const node of nodes) {
    if (node.menuId === menuId) return node
    const found = findNode(node.children, menuId)
    if (found) return found
  }
  return undefined
}

function buildRequest(): MenuSaveRequest {
  const content = form.viewTypeCd === 'CONTENT'
  return {
    parentMenuId: form.parentMenuId,
    moduleCd: form.moduleCd || null,
    viewTypeCd: form.viewTypeCd,
    menuUrl: content ? null : form.menuUrl.trim() || null,
    permCd: form.permCd.trim() || null,
    sortOrder: form.sortOrder,
    newWindowYn: form.newWindowYn,
    adminMenuYn: form.adminMenuYn,
    sideMenuYn: form.sideMenuYn,
    displayYn: form.displayYn,
    useYn: form.useYn,
    i18n: code.langs.map((lang) => ({
      langCd: lang.langCd,
      menuNm: form.names[lang.langCd] ?? '',
      menuContent: content ? form.contents[lang.langCd] || null : null,
    })),
  }
}

async function save(): Promise<void> {
  errors.value = {}
  saving.value = true
  try {
    const request = buildRequest()
    const response = creating.value ? await createMenu(request)
      : await updateMenu(selectedId.value!, request)
    feedback.success(response)
    await Promise.all([loadTree(), menuStore.load()])
    await select(response.result.menuId)
  } catch (error) {
    if (error instanceof ApiError) {
      errors.value = Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.reason]))
      if (['B4001', 'B4002'].includes(error.messageId)) errors.value.names = error.content
      if (error.messageId === 'B5004') errors.value.menuUrl = error.content
      if (error.messageId === 'B5003') errors.value.parentMenuId = error.content
    }
    feedback.failure(error)
  } finally {
    saving.value = false
  }
}

async function remove(): Promise<void> {
  if (!selectedId.value) return
  try {
    feedback.success(await deleteMenu(selectedId.value))
    confirmDelete.value = false
    detail.value = null
    selectedId.value = null
    await Promise.all([loadTree(), menuStore.load()])
  } catch (error) {
    confirmDelete.value = false
    feedback.failure(error)
  }
}

watch(() => auth.user?.langCd, async () => {
  code.clearCodes()
  viewTypes.value = await code.getCodes('MENU_VIEW_TYPE')
  modules.value = await code.getCodes('MODULE')
  await loadTree()
})

onMounted(async () => {
  try {
    ;[viewTypes.value, modules.value] = await Promise.all([
      code.getCodes('MENU_VIEW_TYPE'), code.getCodes('MODULE'),
    ])
  } catch (error) {
    feedback.failure(error)
  }
  await loadTree()
})
</script>

<template>
  <div class="page wide">
    <header class="page-head">
      <div>
        <h1>{{ $t('menuAdmin.title') }}</h1>
        <p class="lede">{{ $t('menuAdmin.lede') }}</p>
      </div>
      <div class="page-actions">
        <button v-if="canAdd" type="button" class="btn btn-primary" data-test="menu-add-root"
          @click="startCreate(null)">{{ $t('menuAdmin.addRoot') }}</button>
      </div>
    </header>

    <div class="split">
      <aside class="split-list" :aria-label="$t('menuAdmin.tree')">
        <ul class="split-list-body tree">
          <MenuTreeItem v-for="(node, index) in tree" :key="node.menuId" :node="node" :depth="0"
            :selected-id="selectedId" :collapsed="collapsed" :can-move="canEdit"
            :first="index === 0" :last="index === tree.length - 1"
            @select="select" @toggle="toggle" @move="move" />
        </ul>
      </aside>

      <section v-if="showForm" class="panel">
        <header class="detail-head">
          <div>
            <h2>{{ creating ? $t('menuAdmin.newMenu') : (form.names[code.defaultLang?.langCd ?? ''] || '—') }}</h2>
            <p v-if="detail" class="sub mono">ID {{ detail.menuId }} · {{ $t('menuAdmin.roleCnt', { n: detail.roleCnt }) }}
              · {{ $t('menuAdmin.childCnt', { n: detail.childCnt }) }}</p>
          </div>
          <div v-if="detail && canAdd" class="page-actions">
            <button type="button" class="btn btn-sm" data-test="menu-add-child"
              @click="startCreate(detail.menuId)">{{ $t('menuAdmin.addChild') }}</button>
          </div>
        </header>

        <form class="form-stack panel-pad" novalidate @submit.prevent="save">
          <fieldset class="form-stack plain" :disabled="!editable">
            <LangNamesInput v-model="form.names" :label="$t('menuAdmin.menuNm')" id-prefix="m-nm"
              :error="errors.names" />

            <div class="grid-2">
              <div class="field">
                <label class="field-label" for="m-parent">{{ $t('menuAdmin.parent') }}</label>
                <select id="m-parent" v-model="form.parentMenuId" class="select"
                  :aria-invalid="!!errors.parentMenuId" data-test="menu-parent">
                  <option :value="null">{{ $t('menuAdmin.root') }}</option>
                  <option v-for="option in parentOptions" :key="option.menuId" :value="option.menuId">
                    {{ option.label }}</option>
                </select>
                <p v-if="errors.parentMenuId" class="field-error">{{ errors.parentMenuId }}</p>
              </div>
              <div class="field">
                <label class="field-label" for="m-type">{{ $t('menuAdmin.viewType') }}<span class="req">*</span></label>
                <select id="m-type" v-model="form.viewTypeCd" class="select" data-test="menu-type">
                  <option v-for="item in viewTypes" :key="item.code" :value="item.code">
                    {{ item.code }} · {{ item.codeNm }}</option>
                </select>
              </div>
            </div>

            <div v-if="form.viewTypeCd !== 'CONTENT'" class="field">
              <label class="field-label" for="m-url">URL</label>
              <input id="m-url" v-model="form.menuUrl" class="input mono" maxlength="1000"
                :aria-invalid="!!errors.menuUrl" spellcheck="false" data-test="menu-url">
              <p class="field-hint">{{ urlHint }}</p>
              <p v-if="errors.menuUrl" class="field-error">{{ errors.menuUrl }}</p>
            </div>

            <div class="grid-3">
              <div class="field">
                <label class="field-label" for="m-perm">{{ $t('menuAdmin.permCd') }}</label>
                <input id="m-perm" v-model="form.permCd" class="input mono" maxlength="50"
                  spellcheck="false" :aria-invalid="!!errors.permCd" data-test="menu-perm">
                <p class="field-hint">{{ $t('menuAdmin.permHint') }}</p>
                <p v-if="errors.permCd" class="field-error">{{ errors.permCd }}</p>
              </div>
              <div class="field">
                <label class="field-label" for="m-module">{{ $t('menuAdmin.module') }}</label>
                <select id="m-module" v-model="form.moduleCd" class="select">
                  <option value="">{{ $t('common.none') }}</option>
                  <option v-for="item in modules" :key="item.code" :value="item.code">{{ item.codeNm }}</option>
                </select>
              </div>
              <div class="field">
                <label class="field-label" for="m-sort">{{ $t('admin.sortOrder') }}</label>
                <input id="m-sort" v-model.number="form.sortOrder" type="number" min="0" max="99999"
                  class="input num">
              </div>
            </div>

            <fieldset class="field plain">
              <legend class="field-label">{{ $t('menuAdmin.options') }}</legend>
              <div class="checks">
                <label class="check"><input v-model="form.useYn" type="checkbox" true-value="Y"
                  false-value="N">{{ $t('common.use') }}</label>
                <label class="check"><input v-model="form.displayYn" type="checkbox" true-value="Y"
                  false-value="N">{{ $t('menuAdmin.display') }}</label>
                <label v-if="form.viewTypeCd === 'LINK'" class="check"><input v-model="form.newWindowYn"
                  type="checkbox" true-value="Y" false-value="N">{{ $t('menuAdmin.newWindow') }}</label>
                <label class="check"><input v-model="form.adminMenuYn" type="checkbox" true-value="Y"
                  false-value="N">{{ $t('menuAdmin.adminMenu') }}</label>
                <label class="check"><input v-model="form.sideMenuYn" type="checkbox" true-value="Y"
                  false-value="N">{{ $t('menuAdmin.sideMenu') }}</label>
              </div>
              <p class="field-hint">{{ $t('menuAdmin.optionsHint') }}</p>
            </fieldset>

            <div v-if="form.viewTypeCd === 'CONTENT'" class="field">
              <span class="field-label">{{ $t('menuAdmin.content') }}</span>
              <div class="tabs" role="tablist">
                <button v-for="lang in code.langs" :key="lang.langCd" type="button" role="tab" class="tab"
                  :aria-selected="contentLang === lang.langCd" @click="contentLang = lang.langCd">
                  {{ lang.langNm }} <span class="mono muted">{{ lang.badge }}</span></button>
              </div>
              <template v-for="lang in code.langs" :key="lang.langCd">
                <HtmlEditor v-if="contentLang === lang.langCd" v-model="form.contents[lang.langCd]!"
                  :editor-locale="lang.editorLocale" height="320px" />
              </template>
              <p class="field-hint">{{ $t('menuAdmin.contentHint') }}</p>
            </div>
          </fieldset>

          <div class="form-actions">
            <button v-if="detail && canDel" type="button" class="btn btn-danger left"
              :disabled="detail.childCnt > 0" :title="detail.childCnt > 0 ? $t('menuAdmin.hasChildren') : ''"
              data-test="menu-delete" @click="confirmDelete = true">{{ $t('common.delete') }}</button>
            <button v-if="creating" type="button" class="btn" @click="creating = false">{{ $t('common.cancel') }}</button>
            <button v-if="editable" type="submit" class="btn btn-primary" :disabled="saving"
              data-test="menu-save">{{ $t('common.save') }}</button>
          </div>
        </form>
      </section>
      <section v-else class="panel">
        <EmptyState :title="$t('menuAdmin.pick')" :description="$t('menuAdmin.pickHint')" />
      </section>
    </div>

    <ConfirmDialog :open="confirmDelete" :title="$t('menuAdmin.deleteTitle')"
      :message="$t('menuAdmin.deleteConfirm', { n: detail?.roleCnt ?? 0 })"
      :confirm-label="$t('common.delete')" danger @close="confirmDelete = false" @confirm="remove" />
  </div>
</template>

<style scoped>
.wide {
  max-width: 1320px;
}

.tree {
  padding: 6px 0;
}

.plain {
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}

.tabs {
  margin-bottom: var(--space-2);
}
</style>
