// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuth } from './stores/auth'
import { onUnauthorized } from './lib/api'
import { currentLocale, i18n } from './i18n'
import './assets/main.css'

const app = createApp(App)
app.use(createPinia())
app.use(i18n)
document.documentElement.lang = currentLocale()
onUnauthorized(() => {
  useAuth().logout()
  if (router.currentRoute.value.name !== 'login') void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
})
app.use(router)
app.mount('#app')
