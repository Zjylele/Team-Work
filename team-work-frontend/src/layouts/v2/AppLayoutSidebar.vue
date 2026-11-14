<script setup lang="ts">
import LayoutToggle from '../../components/v2/LayoutToggle.vue'
import { useUiStore } from '../../stores/ui'
import { useLayoutSession } from './useLayoutSession'

const { session, active, items, logout, go } = useLayoutSession()
const ui = useUiStore()
</script>

<template>
  <div class="shell">
    <button
      type="button"
      class="menu-fab"
      aria-label="打开导航菜单"
      @click="ui.mobileNavOpen = true"
    >
      菜单
    </button>
    <div
      v-if="ui.mobileNavOpen"
      class="backdrop"
      role="presentation"
      @click="ui.mobileNavOpen = false"
    />
    <aside class="side" :class="{ open: ui.mobileNavOpen }">
      <div class="brand">
        <div class="mark" aria-hidden="true" />
        <div class="brand-text">
          <div class="name">{{ session.team?.name || '团队' }}</div>
          <div class="role">{{ session.isCreator ? '创建者' : '成员' }}</div>
        </div>
      </div>
      <nav class="nav" aria-label="主导航">
        <button
          v-for="item in items"
          :key="item.path"
          type="button"
          class="nav-item"
          :class="{ on: active === item.path }"
          @click="go(item.path)"
        >
          {{ item.label }}
        </button>
      </nav>
      <div class="tools">
        <LayoutToggle />
      </div>
      <div class="side-foot">
        <div class="user" :title="session.account?.displayName">{{ session.account?.displayName }}</div>
        <button type="button" class="ghost" @click="logout">退出</button>
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
  background: var(--color-shell);
  position: relative;
  gap: 0;
}
.side {
  width: 232px;
  flex-shrink: 0;
  margin: 14px 0 14px 14px;
  background: var(--color-nav);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  display: flex;
  flex-direction: column;
  padding: 18px 14px;
  gap: 8px;
  z-index: 30;
}
.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 4px 8px 12px;
}
.mark {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--color-primary);
}
.name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-nav-foreground);
}
.role {
  font-size: 11px;
  color: var(--color-muted-foreground);
}
.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.nav-item {
  width: 100%;
  text-align: left;
  background: transparent;
  border: 0;
  color: var(--color-nav-foreground);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  transition: background var(--motion-duration) var(--motion-ease);
}
.nav-item.on,
.nav-item:hover {
  background: var(--color-nav-active);
  color: var(--color-foreground);
}
.nav-item:focus-visible {
  outline: 2px solid var(--color-ring);
  outline-offset: 2px;
}
.tools {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px;
}
.side-foot {
  margin-top: auto;
  padding: 12px 8px 4px;
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.user {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-muted-foreground);
}
.ghost {
  background: transparent;
  border: 0;
  color: var(--color-primary);
  cursor: pointer;
  flex-shrink: 0;
}
.ghost:focus-visible {
  outline: 2px solid var(--color-ring);
  outline-offset: 2px;
}
.main {
  flex: 1;
  min-width: 0;
  padding: var(--space-page) clamp(16px, 3vw, 32px);
}
.menu-fab {
  display: none;
}
.backdrop {
  display: none;
}
@media (max-width: 768px) {
  .menu-fab {
    display: block;
    position: fixed;
    top: 12px;
    left: 12px;
    z-index: 40;
    border: 1px solid var(--color-border);
    background: var(--color-card);
    border-radius: 8px;
    padding: 8px 12px;
    cursor: pointer;
  }
  .side {
    position: fixed;
    inset: 0 auto 0 0;
    transform: translateX(-100%);
    transition: transform var(--motion-duration) var(--motion-ease);
    box-shadow: var(--shadow-soft);
  }
  .side.open {
    transform: translateX(0);
  }
  .backdrop {
    display: block;
    position: fixed;
    inset: 0;
    background: rgb(0 0 0 / 40%);
    z-index: 20;
  }
  .main {
    padding-top: 52px;
  }
}
</style>
