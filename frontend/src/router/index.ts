import { createRouter, createWebHistory, type RouterHistory, type LocationQueryValue } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    guestOnly?: boolean
    layout?: 'auth'
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
        meta: { title: 'Home', requiresAuth: true },
      },
      {
        path: '/events',
        name: 'events',
        component: () => import('@/views/EventsView.vue'),
        meta: { title: 'Events', requiresAuth: true },
      },
      {
        path: '/events/:id',
        name: 'event',
        component: () => import('@/views/EventDetailView.vue'),
        meta: { title: 'Event', requiresAuth: true },
      },
      {
        path: '/event-types',
        name: 'event-types',
        component: () => import('@/views/EventTypesView.vue'),
        meta: { title: 'Event types', requiresAuth: true },
      },
      {
        path: '/members',
        name: 'members',
        component: () => import('@/views/MembersView.vue'),
        meta: { title: 'Members', requiresAuth: true },
      },
      {
        path: '/login',
        name: 'login',
        component: () => import('@/views/LoginView.vue'),
        meta: { title: 'Sign in', guestOnly: true, layout: 'auth' },
      },
      {
        path: '/signup',
        name: 'signup',
        component: () => import('@/views/SignupView.vue'),
        meta: { title: 'Create your group', guestOnly: true, layout: 'auth' },
      },
      {
        path: '/:pathMatch(.*)*',
        name: 'not-found',
        component: () => import('@/views/NotFoundView.vue'),
        meta: { title: 'Page not found' },
      },
    ],
  })

  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    await auth.restore()
    if (to.meta.requiresAuth && !auth.isAuthenticated) return loginLocation(to.fullPath)
    if (to.meta.guestOnly && auth.isAuthenticated) return safeRedirect(to.query.redirect)
    return true
  })

  router.afterEach((to) => {
    document.title = to.meta.title ? `${to.meta.title} · GroupLoom` : 'GroupLoom'
  })

  return router
}

export default createAppRouter()
