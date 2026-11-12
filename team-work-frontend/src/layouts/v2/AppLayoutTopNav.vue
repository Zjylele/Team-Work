<script setup lang="ts">
import ThemeToggle from '../../components/v2/ThemeToggle.vue'
import LayoutToggle from '../../components/v2/LayoutToggle.vue'
import { useLayoutSession } from './useLayoutSession'

const { session, active, items, logout, go } = useLayoutSession()
</script>

<template>
  <div class="shell">
    <header class="top">
      <div class="brand">
        <div class="mark" aria-hidden="true" />
        <div>
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
          {{ item.short }}
        </button>
      </nav>
      <div class="actions">
        <LayoutToggle />
        <ThemeToggle />
        <span class="user">{{ session.account?.displayName }}</span>
        <button type="button" class="ghost" @click="logout">退出</button>
      </div>
    </header>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  background: var(--color-shell);
  display: flex;
  flex-direction: column;
}
.top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;
  padding: 14px clamp(16px, 3vw, 28px);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-nav);
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 140px;
}
.mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--color-primary);
}
.name {
  font-size: 14px;
  font-weight: 600;
}
.role {
  font-size: 11px;
  color: var(--color-muted-foreground);
}
.nav {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
}
.nav-item {
  border: 0;
  background: transparent;
  color: var(--color-nav-foreground);
  padding: 8px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  transition: background var(--motion-duration) var(--motion-ease);
}
.nav-item.on,
.nav-item:hover {
  background: var(--color-nav-active);
}
.nav-item:focus-visible {
  outline: 2px solid var(--color-ring);
  outline-offset: 2px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.user {
  font-size: 12px;
  color: var(--color-muted-foreground);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ghost {
  border: 0;
  background: transparent;
  color: var(--color-primary);
  cursor: pointer;
  font-size: 13px;
}
.main {
  flex: 1;
  padding: var(--space-page) clamp(16px, 3vw, 32px);
}
@media (max-width: 640px) {
  .nav {
    width: 100%;
    order: 3;
  }
  .actions {
    width: 100%;
    order: 2;
    margin-left: 0;
    justify-content: flex-end;
  }
}
</style>
