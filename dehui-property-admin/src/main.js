import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles/dhic-theme.css'

const app = createApp(App)

app.config.errorHandler = (error, _instance, info) => {
  if (error?.requestMessageShown) return
  console.error(`[Vue] ${info}`, error)
}

app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
