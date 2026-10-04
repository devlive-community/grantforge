<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, useId, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, ShieldCheck, UsersRound, KeyRound, Eye, EyeOff, Check, AlertCircle } from '@lucide/vue'
import UiButton from '@/components/UiButton.vue'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/stores/auth'
import { useBootstrap } from '@/stores/bootstrap'
import { authorizeTarget, continueAuthorization } from '@/lib/authorize'
import { errorMessage, request } from '@/lib/api'
import type { components } from '@/api/schema'
const { mode = 'login' } = defineProps<{ mode?: 'login' | 'register' | 'setup' }>()
const auth = useAuth(), bootstrap = useBootstrap(), route = useRoute(), router = useRouter(), { t } = useI18n()
const name = ref(auth.username), password = ref(''), confirmation = ref(''), visible = ref(false), busy = ref(false), error = ref(''), registered = ref(false)
const token = ref(''), tenantName = ref('')
const usernameInput = useTemplateRef<HTMLInputElement>('usernameInput'), tokenInput = useTemplateRef<HTMLInputElement>('tokenInput')
const id = useId(), register = computed(() => mode === 'register'), setup = computed(() => mode === 'setup')
// Register and setup both create an account, so both ask for the password twice.
const newAccount = computed(() => mode !== 'login')
onMounted(() => (setup.value ? tokenInput : usernameInput).value?.focus())
watch(() => mode, () => { error.value = ''; password.value = ''; confirmation.value = ''; registered.value = false })
async function submit() {
  if (busy.value) return
  error.value = ''
  if (setup.value && !token.value.trim()) { error.value = t('auth.enterSetupToken'); tokenInput.value?.focus(); return }
  if (!name.value.trim()) { error.value = t('auth.enterUsername'); usernameInput.value?.focus(); return }
  if (!password.value) { error.value = t('auth.enterPassword'); return }
  if (newAccount.value && !confirmation.value) { error.value = t('auth.repeatPassword'); return }
  if (newAccount.value && password.value !== confirmation.value) { error.value = t('auth.passwordMismatch'); return }
  busy.value = true
  try {
    if (setup.value) {
      // The server checks the password policy and answers with a localized reason.
      const result = await request<components['schemas']['SetupResponse']>('/api/v1/setup', { method: 'POST', anonymous: true, body: {
        token: token.value.trim(), tenantName: tenantName.value.trim() || undefined, username: name.value.trim(), password: password.value } })
      bootstrap.setupCompleted(); name.value = result.username; registered.value = true
    } else if (register.value) {
      // The server checks the password policy and answers with a localized reason.
      await request<components['schemas']['RegistrationResponse']>('/api/v1/register', { method: 'POST', anonymous: true,
        body: { username: name.value.trim(), password: password.value } })
      registered.value = true
    } else {
      await auth.login(name.value.trim(), password.value)
      // An application sent the user here to sign in: back to the authorization server, a page of its own.
      const target = authorizeTarget(route.query.authorize)
      if (target) { continueAuthorization(target); return }
      const next = route.query.redirect
      await router.replace(typeof next === 'string' && next.startsWith('/') && !next.startsWith('//') ? next : '/dashboard')
    }
  } catch (reason) { error.value = errorMessage(reason) } finally { busy.value = false }
}
</script>
<template>
  <div class="grid min-h-dvh lg:grid-cols-[1.08fr_1fr]">
    <section class="relative hidden overflow-hidden bg-[#101828] p-14 text-white lg:flex lg:flex-col xl:p-20">
      <div class="absolute -right-40 top-32 size-[600px] rounded-full bg-indigo-600/10 blur-3xl"></div><div class="absolute -left-40 bottom-0 size-[500px] rounded-full bg-teal-600/8 blur-3xl"></div>
      <div class="relative flex items-center gap-3"><img src="/static/images/grantforge-logo.png" alt="" class="size-10" /><span class="text-2xl font-semibold tracking-tight">GrantForge</span></div>
      <div class="relative my-auto max-w-md py-16"><span class="mb-7 inline-flex items-center gap-2 rounded-full border border-indigo-400/20 bg-indigo-400/8 px-3 py-1.5 text-[11px] text-indigo-200"><span class="size-1.5 rounded-full bg-teal-400"></span>{{ t('auth.tagline') }}</span><h1 class="text-[44px] font-semibold leading-[1.25] tracking-tight xl:text-[50px]">{{ t('auth.headlineStart') }}<br /><span class="text-indigo-300">{{ t('auth.headlineEnd') }}</span></h1><p class="mt-6 text-sm leading-7 text-slate-400">{{ t('auth.intro') }}</p><div class="mt-12 space-y-5"><div v-for="item in [{ icon: UsersRound, title: t('auth.featureUsers'), text: t('auth.featureUsersText') }, { icon: ShieldCheck, title: t('auth.featureAccess'), text: t('auth.featureAccessText') }, { icon: KeyRound, title: t('auth.featureWorkspace'), text: t('auth.featureWorkspaceText') }]" :key="item.title" class="flex items-center gap-4"><span class="flex size-10 items-center justify-center rounded-xl border border-white/8 bg-white/4 text-indigo-300"><component :is="item.icon" :size="18" /></span><div><p class="text-[13px] font-medium">{{ item.title }}</p><p class="mt-1 text-xs text-slate-500">{{ item.text }}</p></div></div></div></div>
      <div class="relative flex justify-between text-xs text-slate-600"><span>Devlive Community</span><span>{{ t('auth.license') }}</span></div>
    </section>
    <main class="flex min-h-dvh flex-col items-center justify-center bg-surface px-6 py-12">
      <div class="mb-10 flex items-center gap-3 lg:hidden"><img src="/static/images/grantforge-logo.png" alt="" class="size-9" /><span class="text-xl font-semibold">GrantForge</span></div>
      <div class="w-full max-w-[380px]">
        <template v-if="registered"><span class="mb-6 flex size-14 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-600"><Check :size="28" /></span><h2 class="text-2xl font-semibold">{{ setup ? t('auth.setupDone') : t('auth.registered') }}</h2><p class="mt-3 text-sm leading-6 text-muted">{{ setup ? t('auth.setupDoneText') : t('auth.registeredText') }}</p><RouterLink to="/auth/login" class="mt-8 block"><UiButton class="w-full">{{ t('auth.goToLogin') }} <ArrowRight :size="16" /></UiButton></RouterLink></template>
        <template v-else>
          <p class="eyebrow mb-3 text-brand">WELCOME TO GRANTFORGE</p><h2 class="text-[28px] font-semibold tracking-tight">{{ setup ? t('auth.setupTitle') : register ? t('auth.registerTitle') : t('auth.loginTitle') }}</h2><p class="mb-9 mt-3 text-[13px] text-muted">{{ setup ? t('auth.setupSubtitle') : register ? t('auth.registerSubtitle') : t('auth.loginSubtitle') }}</p>
          <div v-if="error" class="mb-5 flex items-start gap-2 rounded-xl bg-rose-50 p-3 text-xs leading-5 text-rose-700" role="alert"><AlertCircle :size="16" class="mt-0.5 shrink-0" />{{ error }}</div>
          <form class="space-y-5" novalidate @submit.prevent="submit">
            <template v-if="setup">
              <div>
                <label :for="`${id}-token`" class="field-label">{{ t('auth.setupToken') }}</label><input
                  :id="`${id}-token`"
                  ref="tokenInput"
                  v-model="token"
                  class="field"
                  autocomplete="off"
                  spellcheck="false"
                  required
                  :placeholder="t('auth.setupTokenPlaceholder')"
                />
              </div><div>
                <label :for="`${id}-tenant`" class="field-label">{{ t('auth.tenantName') }}</label><input
                  :id="`${id}-tenant`"
                  v-model="tenantName"
                  class="field"
                  autocomplete="organization"
                  maxlength="128"
                  :placeholder="t('auth.tenantNamePlaceholder')"
                />
              </div>
            </template>
            <div>
              <label :for="`${id}-name`" class="field-label">{{ t('auth.username') }}</label><input
                :id="`${id}-name`"
                ref="usernameInput"
                v-model="name"
                class="field"
                autocomplete="username"
                required
                :placeholder="t('auth.usernamePlaceholder')"
              />
            </div><div>
              <label :for="`${id}-password`" class="field-label">{{ t('auth.password') }}</label><div class="relative">
                <input
                  :id="`${id}-password`"
                  v-model="password"
                  class="field pr-12"
                  :type="visible ? 'text' : 'password'"
                  :autocomplete="newAccount ? 'new-password' : 'current-password'"
                  :minlength="register ? 8 : undefined"
                  required
                  :placeholder="setup ? t('auth.setupPasswordPlaceholder') : register ? t('auth.newPasswordPlaceholder') : t('auth.passwordPlaceholder')"
                /><button type="button" class="icon-button absolute right-1 top-1" :aria-label="visible ? t('auth.hidePassword') : t('auth.showPassword')" @click="visible = !visible"><component :is="visible ? EyeOff : Eye" :size="17" /></button>
              </div>
            </div><div v-if="newAccount">
              <label :for="`${id}-confirmation`" class="field-label">{{ t('auth.confirmPassword') }}</label><input
                :id="`${id}-confirmation`"
                v-model="confirmation"
                class="field"
                type="password"
                autocomplete="new-password"
                required
                :placeholder="t('auth.confirmPlaceholder')"
              />
            </div><UiButton type="submit" class="mt-2 w-full" :loading="busy">{{ setup ? t('auth.setup') : register ? t('auth.register') : t('auth.login') }} <ArrowRight :size="16" /></UiButton>
          </form>
          <p v-if="register || (!setup && bootstrap.registrationEnabled)" class="mt-7 text-center text-xs text-muted">{{ register ? t('auth.hasAccount') : t('auth.noAccount') }} <RouterLink :to="register ? '/auth/login' : '/auth/register'" class="ml-1 font-medium text-brand hover:underline">{{ register ? t('auth.backToLogin') : t('auth.register') }}</RouterLink></p>
        </template>
      </div><p class="mt-16 text-[10px] text-muted/60">{{ t('auth.footer') }}</p>
    </main>
  </div>
</template>
