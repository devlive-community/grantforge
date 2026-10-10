<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useId, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, ShieldCheck, UsersRound, KeyRound, Eye, EyeOff, Check, AlertCircle } from '@lucide/vue'
import UiButton from '@/components/UiButton.vue'
import UiTip from '@/components/UiTip.vue'
import LocalePicker from '@/components/LocalePicker.vue'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/stores/auth'
import { useBootstrap } from '@/stores/bootstrap'
import { authorizeTarget, continueAuthorization, federatedSignIn } from '@/lib/authorize'
import { ApiError, errorMessage, request } from '@/lib/api'
import type { components } from '@/api/schema'
const { mode = 'login' } = defineProps<{ mode?: 'login' | 'register' | 'setup' }>()
const auth = useAuth(), bootstrap = useBootstrap(), route = useRoute(), router = useRouter(), { t } = useI18n()
const name = ref(auth.username), password = ref(''), confirmation = ref(''), visible = ref(false), busy = ref(false), formError = ref(''), fieldErrors = ref<Record<string, string>>({}), registered = ref(false)
const token = ref(''), tenantName = ref('')
// An account with two-step sign-in gives a code after the password.
const secondStep = ref(false), code = ref('')
const usernameInput = useTemplateRef<HTMLInputElement>('usernameInput'), tokenInput = useTemplateRef<HTMLInputElement>('tokenInput')
const passwordInput = useTemplateRef<HTMLInputElement>('passwordInput'), confirmationInput = useTemplateRef<HTMLInputElement>('confirmationInput')
const codeInput = useTemplateRef<HTMLInputElement>('codeInput')
const id = useId(), register = computed(() => mode === 'register'), setup = computed(() => mode === 'setup')
// Register and setup both create an account, so both ask for the password twice.
const newAccount = computed(() => mode !== 'login')
// A provider's sign-in comes back here for the second step, or with the code of its refusal.
const federatedErrors: Record<string, 'auth.federatedConflict' | 'auth.federatedUnknown' | 'auth.federatedLocked' | 'auth.federatedDisabled'> = {
  'GF-IDENTITY-116': 'auth.federatedConflict', 'GF-IDENTITY-117': 'auth.federatedUnknown', 'GF-IDENTITY-021': 'auth.federatedLocked',
  'GF-IDENTITY-024': 'auth.federatedLocked', 'GF-IDENTITY-022': 'auth.federatedDisabled', 'GF-IDENTITY-023': 'auth.federatedDisabled' }
onMounted(() => {
  const refused = route.query.federatedError
  if (typeof refused === 'string') formError.value = t(federatedErrors[refused] ?? 'auth.federatedFailed')
  if (mode === 'login' && route.query.mfa === '1') { secondStep.value = true; void nextTick(() => codeInput.value?.focus()); return }
  ;(setup.value ? tokenInput : usernameInput).value?.focus()
})
function signInWith(code: string) {
  const redirect = route.query.redirect
  continueAuthorization(federatedSignIn(code, authorizeTarget(route.query.authorize), typeof redirect === 'string' ? redirect : null))
}
watch(() => mode, () => { formError.value = ''; fieldErrors.value = {}; password.value = ''; confirmation.value = ''; registered.value = false; secondStep.value = false })
/** Where a completed sign-in goes: back to an application's authorization, or into the console. */
async function proceed() {
  // An application sent the user here to sign in: back to the authorization server, a page of its own.
  const target = authorizeTarget(route.query.authorize)
  if (target) { continueAuthorization(target); return }
  const next = route.query.redirect
  await router.replace(typeof next === 'string' && next.startsWith('/') && !next.startsWith('//') ? next : '/dashboard')
}
async function verify() {
  if (busy.value) return
  formError.value = ''
  fieldErrors.value = {}
  if (!code.value.trim()) { fieldErrors.value = { code: t('mfa.enterCode') }; codeInput.value?.focus(); return }
  busy.value = true
  try {
    await auth.completeSecondFactor(code.value.trim())
    await proceed()
  } catch (reason) {
    formError.value = errorMessage(reason)
    // Only a wrong code may be tried again; otherwise the sign-in starts over.
    if (!(reason instanceof ApiError && reason.problem?.code === 'GF-IDENTITY-100')) back(false)
  } finally { busy.value = false }
}
function back(clearError = true) {
  secondStep.value = false; code.value = ''
  if (clearError) formError.value = ''
  fieldErrors.value = {}
  void nextTick(() => usernameInput.value?.focus())
}
/** Every field that fails its check, with the control it belongs to, so one click shows all of them. */
function problems() {
  const found: { input: HTMLInputElement | null; field: string; message: string }[] = []
  if (setup.value && !token.value.trim()) found.push({ input: tokenInput.value, field: 'token', message: t('auth.enterSetupToken') })
  if (!name.value.trim()) found.push({ input: usernameInput.value, field: 'name', message: t('auth.enterUsername') })
  if (!password.value) found.push({ input: passwordInput.value, field: 'password', message: t('auth.enterPassword') })
  if (newAccount.value) {
    if (!confirmation.value) found.push({ input: confirmationInput.value, field: 'confirmation', message: t('auth.repeatPassword') })
    else if (password.value !== confirmation.value) found.push({ input: confirmationInput.value, field: 'confirmation', message: t('auth.passwordMismatch') })
  }
  return found
}
async function submit() {
  if (busy.value) return
  formError.value = ''
  const found = problems()
  fieldErrors.value = Object.fromEntries(found.map(problem => [problem.field, problem.message]))
  // Every failed field shows its message at once; the first of them takes the focus.
  if (found.length) { found[0]?.input?.focus(); return }
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
      if (await auth.login(name.value.trim(), password.value) === 'secondFactor') {
        secondStep.value = true; password.value = ''; code.value = ''
        void nextTick(() => codeInput.value?.focus())
        return
      }
      await proceed()
    }
  } catch (reason) { formError.value = errorMessage(reason) } finally { busy.value = false }
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
    <main class="relative flex min-h-dvh flex-col items-center justify-center bg-surface px-6 py-12">
      <div class="absolute right-5 top-5 xl:right-8 xl:top-8"><LocalePicker /></div>
      <div class="mb-10 flex items-center gap-3 lg:hidden"><img src="/static/images/grantforge-logo.png" alt="" class="size-9" /><span class="text-xl font-semibold">GrantForge</span></div>
      <div class="w-full max-w-[380px]">
        <template v-if="registered"><span class="mb-6 flex size-14 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-600"><Check :size="28" /></span><h2 class="text-2xl font-semibold">{{ setup ? t('auth.setupDone') : t('auth.registered') }}</h2><p class="mt-3 text-sm leading-6 text-muted">{{ setup ? t('auth.setupDoneText') : t('auth.registeredText') }}</p><RouterLink to="/auth/login" class="mt-8 block"><UiButton class="w-full">{{ t('auth.goToLogin') }} <ArrowRight :size="16" /></UiButton></RouterLink></template>
        <template v-else>
          <p class="eyebrow mb-3 text-brand">WELCOME TO GRANTFORGE</p><h2 class="text-[28px] font-semibold tracking-tight">{{ secondStep ? t('mfa.signInTitle') : setup ? t('auth.setupTitle') : register ? t('auth.registerTitle') : t('auth.loginTitle') }}</h2><p class="mb-9 mt-3 text-[13px] text-muted">{{ secondStep ? t('mfa.signInSubtitle') : setup ? t('auth.setupSubtitle') : register ? t('auth.registerSubtitle') : t('auth.loginSubtitle') }}</p>
          <div v-if="formError" class="mb-5 flex items-start gap-2 rounded-xl bg-rose-50 p-3 text-xs leading-5 text-rose-700" role="alert"><AlertCircle :size="16" class="mt-0.5 shrink-0" />{{ formError }}</div>
          <form
            v-if="secondStep"
            class="space-y-5"
            novalidate
            data-second-step
            @submit.prevent="verify"
          >
            <div>
              <label :for="`${id}-code`" class="field-label">{{ t('mfa.code') }}</label><input
                :id="`${id}-code`"
                ref="codeInput"
                v-model="code"
                class="field"
                autocomplete="one-time-code"
                spellcheck="false"
                maxlength="64"
                required
                :placeholder="t('mfa.codePlaceholder')"
                :aria-invalid="Boolean(fieldErrors.code)"
                :aria-describedby="fieldErrors.code ? `${id}-code-tip` : undefined"
              />
              <UiTip v-if="fieldErrors.code" :message="fieldErrors.code" :anchor="codeInput" :control="`${id}-code`" />
              <p class="mt-2 text-[11px] leading-5 text-muted">{{ t('mfa.signInHint') }}</p>
            </div><UiButton type="submit" class="mt-2 w-full" :loading="busy">{{ t('mfa.verify') }} <ArrowRight :size="16" /></UiButton>
            <button type="button" class="w-full text-center text-xs text-muted hover:text-brand" :disabled="busy" @click="back()">{{ t('mfa.back') }}</button>
          </form>
          <form v-else class="space-y-5" novalidate @submit.prevent="submit">
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
                  :aria-invalid="Boolean(fieldErrors.token)"
                  :aria-describedby="fieldErrors.token ? `${id}-token-tip` : undefined"
                />
                <UiTip v-if="fieldErrors.token" :message="fieldErrors.token" :anchor="tokenInput" :control="`${id}-token`" />
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
                :aria-invalid="Boolean(fieldErrors.name)"
                :aria-describedby="fieldErrors.name ? `${id}-name-tip` : undefined"
              />
              <UiTip v-if="fieldErrors.name" :message="fieldErrors.name" :anchor="usernameInput" :control="`${id}-name`" />
            </div><div>
              <label :for="`${id}-password`" class="field-label">{{ t('auth.password') }}</label><div class="relative">
                <input
                  :id="`${id}-password`"
                  ref="passwordInput"
                  v-model="password"
                  class="field pr-12"
                  :type="visible ? 'text' : 'password'"
                  :autocomplete="newAccount ? 'new-password' : 'current-password'"
                  :minlength="register ? 8 : undefined"
                  required
                  :placeholder="setup ? t('auth.setupPasswordPlaceholder') : register ? t('auth.newPasswordPlaceholder') : t('auth.passwordPlaceholder')"
                  :aria-invalid="Boolean(fieldErrors.password)"
                  :aria-describedby="fieldErrors.password ? `${id}-password-tip` : undefined"
                /><button type="button" class="icon-button absolute right-1 top-1" :aria-label="visible ? t('auth.hidePassword') : t('auth.showPassword')" @click="visible = !visible"><component :is="visible ? EyeOff : Eye" :size="17" /></button>
              </div>
              <UiTip v-if="fieldErrors.password" :message="fieldErrors.password" :anchor="passwordInput" :control="`${id}-password`" />
            </div><div v-if="newAccount">
              <label :for="`${id}-confirmation`" class="field-label">{{ t('auth.confirmPassword') }}</label><input
                :id="`${id}-confirmation`"
                ref="confirmationInput"
                v-model="confirmation"
                class="field"
                type="password"
                autocomplete="new-password"
                required
                :placeholder="t('auth.confirmPlaceholder')"
                :aria-invalid="Boolean(fieldErrors.confirmation)"
                :aria-describedby="fieldErrors.confirmation ? `${id}-confirmation-tip` : undefined"
              />
              <UiTip v-if="fieldErrors.confirmation" :message="fieldErrors.confirmation" :anchor="confirmationInput" :control="`${id}-confirmation`" />
            </div><UiButton type="submit" class="mt-2 w-full" :loading="busy">{{ setup ? t('auth.setup') : register ? t('auth.register') : t('auth.login') }} <ArrowRight :size="16" /></UiButton>
          </form>
          <div v-if="!secondStep && mode === 'login' && bootstrap.signInSources.length" class="mt-7" data-providers>
            <p class="mb-3 flex items-center gap-3 text-[11px] text-muted"><span class="h-px flex-1 bg-line"></span>{{ t('auth.orSignInWith') }}<span class="h-px flex-1 bg-line"></span></p>
            <div class="space-y-2">
              <UiButton
                v-for="source in bootstrap.signInSources"
                :key="source.code"
                variant="secondary"
                class="w-full"
                @click="signInWith(source.code)"
              >
                {{ t('auth.signInWith', { name: source.name }) }}
              </UiButton>
            </div>
          </div>
          <p v-if="!secondStep && (register || (!setup && bootstrap.registrationEnabled))" class="mt-7 text-center text-xs text-muted">{{ register ? t('auth.hasAccount') : t('auth.noAccount') }} <RouterLink :to="register ? '/auth/login' : '/auth/register'" class="ml-1 font-medium text-brand hover:underline">{{ register ? t('auth.backToLogin') : t('auth.register') }}</RouterLink></p>
        </template>
      </div><p class="mt-16 text-[10px] text-muted/60">{{ t('auth.footer') }}</p>
    </main>
  </div>
</template>
