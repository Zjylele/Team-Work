<script setup lang="ts">

defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <div class="auth">
    <header class="auth-top">
      <div class="brand-mark" aria-hidden="true" />
      <div>
        <div class="brand-name">Team Work</div>
        <div class="brand-tag">团队任务协作</div>
      </div>
    </header>
    <main class="auth-main">
      <article class="v2-card auth-card">
        <h1 class="v2-title">{{ title }}</h1>
        <p v-if="subtitle" class="v2-subtitle">{{ subtitle }}</p>
        <slot />
      </article>
    </main>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  background: var(--color-background);
  display: flex;
  flex-direction: column;
}
.auth-top {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-nav);
}
.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--color-primary);
}
.brand-name {
  font-weight: 600;
  font-size: 15px;
}
.brand-tag {
  font-size: 12px;
  color: var(--color-muted-foreground);
}
.auth-theme {
  margin-left: auto;
}
.auth-main {
  flex: 1;
  display: grid;
  place-items: center;
  padding: 32px 20px;
}
.auth-card {
  width: min(100%, 400px);
  padding: 28px 24px;
}
.auth-card :deep(.el-form-item) {
  margin-bottom: 16px;
}
.auth-card :deep(.el-button--primary) {
  width: 100%;
}
.auth-card :deep(.foot) {
  margin-top: 16px;
  font-size: 13px;
  color: var(--color-muted-foreground);
}
.auth-card :deep(a) {
  color: var(--color-primary);
}
</style>
