import { ref } from 'vue'
import { defineStore } from 'pinia'
import { UI_V2 } from '../config/ui'

export type UiLayout = 'sidebar' | 'topnav'

const LAYOUT_KEY = 'team-work:layout'

function readLayout(): UiLayout {
  const saved = localStorage.getItem(LAYOUT_KEY)
  return saved === 'topnav' ? 'topnav' : 'sidebar'
}

function applyDocument() {
  const root = document.documentElement
  if (UI_V2) {
    root.setAttribute('data-ui-v2', '')
    root.setAttribute('data-theme', 'light')
    root.classList.remove('dark')
  } else {
    root.removeAttribute('data-ui-v2')
    root.removeAttribute('data-theme')
    root.classList.remove('dark')
  }
}

export const useUiStore = defineStore('ui', () => {
  const layout = ref<UiLayout>('sidebar')
  const mobileNavOpen = ref(false)

  function init() {
    if (!UI_V2) return
    layout.value = readLayout()
    applyDocument()
  }

  function setLayout(next: UiLayout) {
    layout.value = next
    localStorage.setItem(LAYOUT_KEY, next)
    mobileNavOpen.value = false
  }

  function toggleLayout() {
    setLayout(layout.value === 'sidebar' ? 'topnav' : 'sidebar')
  }


  return {
    layout,
    mobileNavOpen,
    init,
    setLayout,
    toggleLayout,
  }
})
