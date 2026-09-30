import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
// @ts-ignore
import ViewShadcnUI from 'view-shadcn-ui'
import { createIcons } from '@/fontawesome'

const app = createApp(App)
createIcons(app)
app.use(ViewShadcnUI)
app.use(router)
app.mount('#app')
