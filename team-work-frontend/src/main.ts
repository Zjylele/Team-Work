import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './style.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/v2/tokens.css'
import './styles/v2/base.css'
import { UI_V2 } from './config/ui'
import { useUiStore } from './stores/ui'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

if (UI_V2) {
  useUiStore().init()
}

app.mount('#app')
