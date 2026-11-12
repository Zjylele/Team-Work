import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi, teamApi } from '../api/client'
import { ApiError, ensureCsrf, onUnauthorized, resetCsrf } from '../api/http'
import type { Account, Team, TeamMemberView } from '../api/types'

export const useSessionStore = defineStore('session', () => {
  const account = ref<Account | null>(null)
  const team = ref<Team | null>(null)
  const members = ref<TeamMemberView[]>([])
  const membersFromApi = ref(false)
  const ready = ref(false)
  const loading = ref(false)

  const isCreator = computed(() => team.value?.role === 'CREATOR')
  const isMember = computed(() => team.value != null)

  onUnauthorized(() => {
    account.value = null
    team.value = null
  })

  async function loadMembers() {
    membersFromApi.value = false
    const list = await teamApi.members()
    if (list) {
      members.value = list
      membersFromApi.value = true
      return
    }
    const fallback: TeamMemberView[] = []
    if (account.value) {
      fallback.push({
        id: account.value.id,
        displayName: account.value.displayName,
        role: team.value?.role,
      })
    }
    members.value = fallback
  }

  async function refresh() {
    loading.value = true
    try {
      await ensureCsrf()
      try {
        account.value = await authApi.me()
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          account.value = null
          team.value = null
          members.value = []
          return
        }
        throw error
      }
      team.value = await teamApi.current()
      if (team.value) {
        await loadMembers()
      } else {
        members.value = account.value
          ? [{ id: account.value.id, displayName: account.value.displayName }]
          : []
      }
    } finally {
      loading.value = false
      ready.value = true
    }
  }

  async function register(username: string, password: string, displayName: string) {
    resetCsrf()
    await ensureCsrf()
    account.value = await authApi.register({ username, password, displayName })
    resetCsrf()
    await refresh()
  }

  async function login(username: string, password: string) {
    resetCsrf()
    await ensureCsrf()
    account.value = await authApi.login({ username, password })
    resetCsrf()
    await refresh()
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      account.value = null
      team.value = null
      members.value = []
      resetCsrf()
    }
  }

  async function createTeam(name: string) {
    team.value = await teamApi.create(name)
    await loadMembers()
  }

  async function joinTeam(code: string) {
    team.value = await teamApi.join(code)
    await loadMembers()
  }

  async function regenerateInvite() {
    team.value = await teamApi.regenerateInvite()
  }

  function rememberPerson(person: TeamMemberView) {
    if (members.value.some((item) => item.id === person.id)) {
      return
    }
    members.value = [...members.value, person]
  }

  return {
    account,
    team,
    members,
    membersFromApi,
    ready,
    loading,
    isCreator,
    isMember,
    refresh,
    register,
    login,
    logout,
    createTeam,
    joinTeam,
    regenerateInvite,
    rememberPerson,
  }
})
