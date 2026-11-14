<script setup lang="ts">
import { computed } from 'vue'
import { Grid, Menu, Bell, SwitchButton } from '@element-plus/icons-vue'
import LayoutToggle from '../../components/v2/LayoutToggle.vue'
import { useUiStore } from '../../stores/ui'
import { useLayoutSession } from './useLayoutSession'

const { session, active, items, logout, go } = useLayoutSession()
const ui = useUiStore()
const current = computed(() => items.find(item => item.path === active.value) ?? items[0])
function navigate(path: string) {
  ui.mobileNavOpen = false
  go(path)
}
</script>

<template>
  <div class="shell">
    <button v-if="ui.mobileNavOpen" class="backdrop" type="button" aria-label="关闭导航" @click="ui.mobileNavOpen = false" />
    <aside class="side workspace-sidebar" :class="{ open: ui.mobileNavOpen }">
      <div class="brand">
        <span class="mark"><el-icon><Grid /></el-icon></span>
        <div>
          <strong>{{ session.team?.name || '团队' }}</strong>
          <small>Team-Work</small>
        </div>
      </div>
      <nav aria-label="主导航">
        <button v-for="item in items" :key="item.path" type="button" class="nav-item"
          :class="{ on: active === item.path }" :aria-current="active === item.path ? 'page' : undefined"
          @click="navigate(item.path)">
          <el-icon><component :is="item.icon" /></el-icon>{{ item.label }}
        </button>
      </nav>
      <div class="side-foot">
        <LayoutToggle />
        <button type="button" class="icon-button" title="退出登录" aria-label="退出登录" @click="logout">
          <el-icon><SwitchButton /></el-icon>
        </button>
      </div>
    </aside>
    <div class="surface">
      <header class="toolbar">
        <button type="button" class="menu-button icon-button" aria-label="打开导航" :aria-expanded="ui.mobileNavOpen" @click="ui.mobileNavOpen = !ui.mobileNavOpen">
          <el-icon><Menu /></el-icon>
        </button>
        <span class="section-icon"><el-icon><component :is="current.icon" /></el-icon></span>
        <strong>{{ current.label }}</strong>
        <div class="toolbar-actions">
          <RouterLink class="icon-button" to="/notifications" aria-label="通知"><el-icon><Bell /></el-icon></RouterLink>
          <RouterLink class="avatar" to="/settings" :aria-label="'个人设置 · ' + (session.account?.displayName ?? '')">
            {{ session.account?.displayName?.slice(0, 2) }}
          </RouterLink>
        </div>
      </header>
      <main class="main"><router-view /></main>
    </div>
  </div>
</template>

<style scoped>
.shell { display: grid; grid-template-columns: 204px minmax(0, 1fr); min-height: 100dvh; }
.side {
  position: sticky; top: 0; height: 100dvh; display: flex; flex-direction: column;
  padding: 0 10px 12px; background: #303a48; z-index: 30;
}
.brand { display: flex; align-items: center; gap: 10px; min-height: 68px; padding: 10px; }
.brand > div { min-width: 0; }
.brand strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.brand small { display: block; margin-top: 3px; opacity: .8; }
.mark, .section-icon, .avatar { display: grid; place-items: center; flex: none; }
.mark { width: 32px; height: 32px; background: #486adc; border-radius: 8px; }
.nav-item {
  display: flex; align-items: center; gap: 11px; width: 100%; min-height: 40px; margin-bottom: 3px;
  padding: 0 11px; border: 0; border-radius: 6px; background: transparent; text-align: left; cursor: pointer;
}
.nav-item:hover { background: #3b4758; }
.nav-item.on { background: #465772; box-shadow: inset 3px 0 #9fb8ff; font-weight: 650; }
.side-foot { display: flex; align-items: center; justify-content: space-between; margin-top: auto; border-top: 1px solid #536073; padding: 12px 8px 0; }
.surface { min-width: 0; }
.toolbar {
  position: sticky; top: 0; z-index: 15; height: 53px; display: flex; align-items: center; gap: 10px;
  padding: 0 20px; border-bottom: 1px solid var(--color-border); background: #fff;
}
.section-icon { width: 28px; height: 28px; background: #edf2ff; border-radius: 6px; }
.toolbar-actions { margin-left: auto; display: flex; align-items: center; gap: 10px; }
.icon-button { display: grid; place-items: center; width: 32px; height: 32px; background: transparent; border: 0; text-decoration: none; cursor: pointer; }
.avatar { width: 30px; height: 30px; background: #d9f3e6; border-radius: 50%; text-decoration: none; font-size: 11px; font-weight: 650; }
.main { min-width: 0; padding: 18px 20px; }
.menu-button, .backdrop { display: none; }
@media (max-width: 760px) {
  .shell { display: block; }
  .main { padding: 12px; }
  .toolbar { padding: 0 12px; }
  .menu-button { display: grid; }
  .side { position: fixed; inset: 0 auto 0 0; width: min(220px, 85vw); visibility: hidden; transform: translateX(-100%); }
  .side.open { visibility: visible; transform: none; }
  .backdrop { display: block; position: fixed; inset: 0; width: 100%; border: 0; background: #1b233672; z-index: 25; }
}
</style>
