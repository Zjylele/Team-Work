import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { UI_V2 } from '../config/ui'

export type UiTheme = 'light' | 'dark'
export type UiLayout = 'sidebar' | 'topnav'

const THEME_KEY = 'team-work:theme'
const LAYOUT_KEY = 'team-work:layout'

function readTheme(): UiTheme {
  const saved = localStorage.getItem(THEME_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  if (window.matchMedia('(prefers-color-scheme: dark)').matches) return 'dark'
  return 'light'
}

function readLayout(): UiLayout {
  const saved = localStorage.getItem(LAYOUT_KEY)
  return saved === 'topnav' ? 'topnav' : 'sidebar'
}

function applyDocument(theme: UiTheme) {
  const root = document.documentElement
  if (UI_V2) {
    root.setAttribute('data-ui-v2', '')
    root.setAttribute('data-theme', theme)
    root.classList.toggle('dark', theme === 'dark')
  } else {
    root.removeAttribute('data-ui-v2')
    root.removeAttribute('data-theme')
    root.classList.remove('dark')
  }
}

export const useUiStore = defineStore('ui', () => {
  const theme = ref<UiTheme>('light')
  const layout = ref<UiLayout>('sidebar')
  const mobileNavOpen = ref(false)

  function init() {
    if (!UI_V2) return
    theme.value = readTheme()
    layout.value = readLayout()
    applyDocument(theme.value)
  }

  function setTheme(next: UiTheme) {
    theme.value = next
    localStorage.setItem(THEME_KEY, next)
    applyDocument(next)
  }

  function toggleTheme() {
    setTheme(theme.value === 'light' ? 'dark' : 'light')
  }

  function setLayout(next: UiLayout) {
    layout.value = next
    localStorage.setItem(LAYOUT_KEY, next)
    mobileNavOpen.value = false
  }

  function toggleLayout() {
    setLayout(layout.value === 'sidebar' ? 'topnav' : 'sidebar')
  }

  watch(theme, (value) => applyDocument(value))

  return {
    theme,
    layout,
    mobileNavOpen,
    init,
    setTheme,
    toggleTheme,
    setLayout,
    toggleLayout,
  }
})
