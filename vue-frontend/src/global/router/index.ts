import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/auth/store'
import type { Action } from '@/auth/types'
import { bootstrapSession } from '@/global/session'
import AppShell from '@/global/layout/AppShell.vue'

declare module 'vue-router' {
  interface RouteMeta {
    /** 로그인 없이 접근 */
    public?: boolean
    /** 필요한 권한 리소스 (SCOPE_{resource}.{action}) */
    resource?: string
    /** 필요한 작업 (기본 list) */
    action?: Action
    /** 시스템관리자 전용 */
    sysAdmin?: boolean
    /** 문서 제목 i18n 키 */
    title?: string
  }
}

/**
 * 라우트.
 *  - 메뉴 URL(TB_MENU.MENU_URL)과 같은 경로로 화면을 등록한다. (예: /support/notices)
 *  - 화면이 아직 없는 메뉴 URL 은 마지막 catch-all 에서 "준비 중" 으로 표시한다.
 *  - 권한 검사(meta.resource / action)는 버튼·화면 노출용이고 실제 인가는 서버가 한다.
 *  - 숫자 파라미터는 ([0-9]+) 로 쓴다. (vue-router 5 는 정규식의 역슬래시 이스케이프(\d)를 매칭하지 못함)
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/global/views/LoginView.vue'),
    meta: { public: true, title: 'login.title' },
  },
  {
    path: '/',
    component: AppShell,
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/global/views/HomeView.vue'),
        meta: { title: 'home.title' },
      },
      {
        path: 'support/notices',
        name: 'notice-list',
        component: () => import('@/notice/views/NoticeListView.vue'),
        meta: { resource: 'board', title: 'notice.title' },
      },
      {
        path: 'support/notices/new',
        name: 'notice-new',
        component: () => import('@/notice/views/NoticeFormView.vue'),
        meta: { resource: 'board', action: 'add', title: 'notice.form.createTitle' },
      },
      {
        path: 'support/notices/:id([0-9]+)',
        name: 'notice-detail',
        component: () => import('@/notice/views/NoticeDetailView.vue'),
        props: (route) => ({ id: Number(route.params.id) }),
        meta: { resource: 'board', title: 'notice.title' },
      },
      {
        path: 'support/notices/:id([0-9]+)/edit',
        name: 'notice-edit',
        component: () => import('@/notice/views/NoticeFormView.vue'),
        props: (route) => ({ id: Number(route.params.id) }),
        meta: { resource: 'board', action: 'edit', title: 'notice.form.editTitle' },
      },
      {
        path: 'birthdays',
        name: 'calendar',
        component: () => import('@/calendar/views/CalendarView.vue'),
        meta: { resource: 'calendar', title: 'calendar.title' },
      },
      {
        path: 'tx/trades/list',
        name: 'sample-list',
        component: () => import('@/sample/views/SampleListView.vue'),
        meta: { resource: 'sample', title: 'sample.title' },
      },
      {
        path: 'system/codes',
        name: 'code-admin',
        component: () => import('@/code/views/CodeAdminView.vue'),
        meta: { resource: 'code', title: 'codeAdmin.title' },
      },
      {
        path: 'system/menus',
        name: 'menu-admin',
        component: () => import('@/menu/views/MenuAdminView.vue'),
        meta: { resource: 'menu', title: 'menuAdmin.title' },
      },
      {
        path: 'system/roles',
        name: 'role-admin',
        component: () => import('@/role/views/RoleAdminView.vue'),
        meta: { resource: 'role', title: 'roleAdmin.title' },
      },
      {
        path: 'org',
        name: 'org',
        component: () => import('@/user/views/OrgView.vue'),
        meta: { title: 'org.title' },
      },
      {
        path: 'me',
        name: 'profile',
        component: () => import('@/auth/views/ProfileView.vue'),
        meta: { title: 'profile.title' },
      },
      {
        path: 'frame/:menuId([0-9]+)',
        name: 'frame',
        component: () => import('@/global/views/FrameView.vue'),
        props: (route) => ({ menuId: Number(route.params.menuId) }),
      },
      {
        path: 'content/:menuId([0-9]+)',
        name: 'content',
        component: () => import('@/global/views/ContentMenuView.vue'),
        props: (route) => ({ menuId: Number(route.params.menuId) }),
      },
      {
        path: 'forbidden',
        name: 'forbidden',
        component: () => import('@/global/views/ForbiddenView.vue'),
        meta: { title: 'error.forbidden.title' },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'menu-pending',
        component: () => import('@/global/views/MenuPendingView.vue'),
      },
    ],
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (_to, _from, saved) => saved ?? { top: 0 },
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    return true
  }
  const loggedIn = await bootstrapSession()
  if (!loggedIn) {
    return { name: 'login', query: { returnTo: to.fullPath } }
  }
  const auth = useAuthStore()
  const denied = (to.meta.sysAdmin && !auth.isSysAdmin)
    || (to.meta.resource && !auth.can(to.meta.resource, to.meta.action ?? 'list'))
  if (denied) {
    return { name: 'forbidden', query: { from: to.fullPath } }
  }
  return true
})
