<script setup lang="ts">
import { useUiStore } from '../../stores/ui'

const ui = useUiStore()
</script>

<template>
  <button type="button" class="toggle" :aria-pressed="ui.theme === 'dark'" @click="ui.toggleTheme()">
    <span class="sr-only">切换浅色/深色主题</span>
    <span aria-hidden="true">{{ ui.theme === 'dark' ? '深色' : '浅色' }}</span>
  </button>
</template>

<style scoped>
.toggle {
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-card-foreground);
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
  transition: border-color var(--motion-duration) var(--motion-ease);
}
.toggle:hover {
  border-color: var(--color-primary);
}
.toggle:focus-visible {
  outline: 2px solid var(--color-ring);
  outline-offset: 2px;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}
</style>
