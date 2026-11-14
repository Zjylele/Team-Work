<script setup lang="ts">
import { useRouter } from 'vue-router'
import { APP_NAV_ITEMS, useAppNavActive } from '../composables/useAppNav'
import { useSessionStore } from '../stores/session'

const session = useSessionStore()
const router = useRouter()

const items = APP_NAV_ITEMS
const active = useAppNavActive()

async function logout() {
  await session.logout()
  await router.push('/login')
}
</script>

<template>
  <div class="shell">
    <aside class="side">
      <div class="brand">
        <div class="dot" />
        <div>
          <div class="name">{{ session.team?.name || '团队' }}</div>
          <div class="role">{{ session.isCreator ? '创建者' : '成员' }}</div>
        </div>
      </div>
      <nav>
        <button
          v-for="item in items"
          :key="item.path"
          class="nav-item"
          :class="{ on: active === item.path }"
          @click="router.push(item.path)"
        >
          {{ item.label }}
        </button>
      </nav>
      <div class="side-foot">
        <div class="user">{{ session.account?.displayName }}</div>
        <button class="ghost" @click="logout">退出</button>
      </div>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  background: #f4f5f7;
}
.side {
  width: 220px;
  flex-shrink: 0;
  background: #1c1d22;
  color: #ececec;
  display: flex;
  flex-direction: column;
  padding: 16px 12px;
}
.brand {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 4px 8px 16px;
}
.dot {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: #3f76ff;
}
.name {
  font-size: 14px;
  font-weight: 600;
}
.role {
  font-size: 11px;
  color: #9aa0a6;
}
.nav-item {
  width: 100%;
  text-align: left;
  background: transparent;
  border: 0;
  color: #c9cdd3;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.nav-item.on,
.nav-item:hover {
  background: #2a2b32;
  color: #fff;
}
.side-foot {
  margin-top: auto;
  padding: 10px 8px 0;
  border-top: 1px solid #2e2f36;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}
.ghost {
  background: transparent;
  border: 0;
  color: #9aa0a6;
  cursor: pointer;
}
.main {
  flex: 1;
  min-width: 0;
  padding: 24px 28px;
}
</style>
