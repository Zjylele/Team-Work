<script setup lang="ts">
import { computed } from 'vue'
import { useUiStore } from '../../stores/ui'
import AppLayoutSidebar from './AppLayoutSidebar.vue'
import AppLayoutTopNav from './AppLayoutTopNav.vue'

const ui = useUiStore()
const layout = computed(() => (ui.layout === 'topnav' ? AppLayoutTopNav : AppLayoutSidebar))
</script>

<template>
  <component :is="layout" />
</template>
