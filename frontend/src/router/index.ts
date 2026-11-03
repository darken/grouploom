import { watch } from 'vue'
import { createRouter, createWebHistory, type RouterHistory, type LocationQueryValue } from 'vue-router'

import { i18n, type MessageSchema } from '@/i18n'
import { useAuthStore } from '@/stores/auth'

type TitleKey = `page.title.${keyof MessageSchema['page']['title']}`

declare module 'vue-router' {
  interface RouteMeta {
    titleKey?: TitleKey
    requiresAuth?: boolean
    guestOnly?: boolean
    /** Reachable by anyone; the guard skips session restore and every auth redirect. */
    public?: boolean
    layout?: 'auth' | 'member'
  }
}

/** Only same-site paths are accepted as post-login targets, which blocks open redirects. */
export function safeRedirect(value: LocationQueryValue | LocationQueryValue[] | undefined): string {
  const target = Array.isArray(value) ? value[0] : value
  if (typeof target !== 'string' || !target.startsWith('/') || target.startsWith('//') || target.includes('\\')) {
    return '/'
  }
  return target
}

export function loginLocation(fullPath: string) {
  return fullPath === '/' ? { name: 'login' } : { name: 'login', query: { redirect: fullPath } }
}

export function createAppRouter(history: RouterHistory = createWebHistory(import.meta.env.BASE_URL)) {
  const router = createRouter({
    history,
    routes: [
      {
        path: '/',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { titleKey: 'page.title.home', requiresAuth: true },
      },
      {
        path: '/events',
        name: 'events',
        component: () => import('@/views/EventsView.vue'),
        meta: { titleKey: 'page.title.events', requiresAuth: true },
      },
      {
        path: '/events/:id',
        name: 'event',
        component: () => import('@/views/EventDetailView.vue'),
        meta: { titleKey: 'page.title.event', requiresAuth: true },
      },
      {
        path: '/event-types',
        name: 'event-types',
        component: () => import('@/views/EventTypesView.vue'),
        meta: { titleKey: 'page.title.eventTypes', requiresAuth: true },
      },
      {
        path: '/members',
        name: 'members',
        component: () => import('@/views/MembersView.vue'),
        meta: { titleKey: 'page.title.members', requiresAuth: true },
      },
      {
        path: '/login',
        name: 'login',
        component: () => import('@/views/LoginView.vue'),
        meta: { titleKey: 'page.title.login', guestOnly: true, layout: 'auth' },
      },
      {
        path: '/signup',
        name: 'signup',
        component: () => import('@/views/SignupView.vue'),
        meta: { titleKey: 'page.title.signup', guestOnly: true, layout: 'auth' },
      },
      {
        path: '/m/preferences',
        name: 'member-preferences',
        component: () => import('@/views/MemberPreferencesView.vue'),
        meta: { titleKey: 'page.title.memberPreferences', public: true, layout: 'member' },
      },
      {
        path: '/m/attend',
        name: 'member-attendance',
        component: () => import('@/views/MemberAttendanceView.vue'),
        meta: { titleKey: 'page.title.memberAttendance', public: true, layout: 'member' },
      },
      {
        path: '/:pathMatch(.*)*',
        name: 'not-found',
        component: () => import('@/views/NotFoundView.vue'),
        meta: { titleKey: 'page.title.notFound' },
      },
    ],
  })

  router.beforeEach(async (to) => {
    // Members open their links in whatever browser they have, possibly one an organizer is signed in to.
    if (to.meta.public) return true
    const auth = useAuthStore()
    await auth.restore()
    if (to.meta.requiresAuth && !auth.isAuthenticated) return loginLocation(to.fullPath)
    if (to.meta.guestOnly && auth.isAuthenticated) return safeRedirect(to.query.redirect)
    return true
  })

  const updateTitle = () => {
    const { titleKey } = router.currentRoute.value.meta
    document.title = titleKey ? `${i18n.global.t(titleKey)} · GroupLoom` : 'GroupLoom'
  }
  router.afterEach(updateTitle)
  watch(i18n.global.locale, updateTitle)

  return router
}

export default createAppRouter()
