// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuth } from './stores/auth'
import { onAuthorizationVersion, onUnauthorized } from './lib/api'
import { currentLocale, i18n } from './i18n'
import './assets/main.css'

const app = createApp(App)
app.use(createPinia())
app.use(i18n)
document.documentElement.lang = currentLocale()
onUnauthorized(() => {
  // The server ended the session (expired or revoked): forget the user and sign in again.
  useAuth().reset()
  if (router.currentRoute.value.name !== 'login') void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
})
// Permissions changed since they were loaded (a grant, an assignment): show the console as they are now.
onAuthorizationVersion(version => useAuth().observeVersion(version))
app.use(router)
app.mount('#app')
