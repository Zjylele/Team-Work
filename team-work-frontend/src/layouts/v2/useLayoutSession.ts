import { useRouter } from 'vue-router'
import { useSessionStore } from '../../stores/session'
import { APP_NAV_ITEMS, useAppNavActive } from '../../composables/useAppNav'

export function useLayoutSession() {
  const session = useSessionStore()
  const router = useRouter()
  const active = useAppNavActive()

  async function logout() {
    await session.logout()
    await router.push('/login')
  }

  function go(path: string) {
    router.push(path)
  }

  return { session, active, items: APP_NAV_ITEMS, logout, go }
}
