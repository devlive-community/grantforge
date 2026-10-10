<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { History, KeyRound, LogOut, MonitorSmartphone, ShieldAlert, ShieldCheck, UserRound } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { useFieldErrors, type FieldErrors } from '@/lib/fieldErrors'
import { agentLabel, dateLabel } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import AccountMfa from '@/components/AccountMfa.vue'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import UiField from '@/components/UiField.vue'

type Me = components['schemas']['MeResponse']
type Session = components['schemas']['SessionResponse']
type LoginHistory = components['schemas']['PageResultLoginHistoryResponse']
type LoginEntry = LoginHistory['items'][number]

const { t } = useI18n(), auth = useAuth(), toast = useToast(), router = useRouter(), route = useRoute()
// One tab per concern, each at its own address, so a reload, the back button or a shared link opens the same one.
const tabs = [
  { id: 'profile', icon: UserRound, label: 'account.tabProfile' },
  { id: 'security', icon: ShieldCheck, label: 'account.tabSecurity' },
  { id: 'devices', icon: MonitorSmartphone, label: 'account.tabDevices' },
] as const
type Tab = typeof tabs[number]['id']
const tab = computed<Tab>(() => {
  // A password that must be changed first leaves nothing else to show.
  if (auth.passwordChangeRequired) return 'security'
  const asked = route.params.tab
  return tabs.find(item => item.id === asked)?.id ?? 'profile'
})
const displayName = ref(''), email = ref('')
watch(() => auth.me, value => { displayName.value = value?.displayName ?? ''; email.value = value?.email ?? '' }, { immediate: true })
const savingProfile = ref(false), profileError = ref('')
const current = ref(''), next = ref(''), confirm = ref(''), changing = ref(false), passwordError = ref('')
const { errors: fieldErrors, invalid } = useFieldErrors(() => [current.value, next.value, confirm.value], problems)
const sessions = shallowRef<Session[]>([]), sessionsLoading = ref(false), sessionsError = ref(''), ending = ref('')
const history = shallowRef<LoginEntry[]>([]), historyError = ref('')
const actions = { LOGIN_SUCCEEDED: 'account.historySucceeded', LOGIN_FAILED: 'account.historyFailed',
  ACCOUNT_LOCKED: 'account.historyLocked', LOGOUT: 'account.historyLoggedOut', MFA_ENABLED: 'account.historyMfaEnabled',
  MFA_DISABLED: 'account.historyMfaDisabled', MFA_RECOVERY_CODES_RENEWED: 'account.historyCodesRenewed',
  MFA_RECOVERY_CODE_USED: 'account.historyCodeUsed', MFA_STEP_UP: 'account.historyStepUp' } as const
const reasons: Record<string, 'account.reasonWrongPassword' | 'account.reasonLocked' | 'account.reasonLockedByAdmin' | 'account.reasonDisabled'
  | 'account.reasonSuspended' | 'account.reasonWrongCode'> = {
  'GF-IDENTITY-020': 'account.reasonWrongPassword', 'GF-IDENTITY-021': 'account.reasonLocked',
  'GF-IDENTITY-022': 'account.reasonDisabled', 'GF-IDENTITY-023': 'account.reasonSuspended',
  'GF-IDENTITY-024': 'account.reasonLockedByAdmin', 'GF-IDENTITY-100': 'account.reasonWrongCode' }
/** Names a sign-in event, with the refusal reason when the console knows it (the raw code otherwise). */
function describe(entry: LoginEntry) {
  const action = t(actions[entry.action as keyof typeof actions] ?? 'account.historyFailed')
  if (!entry.reason) return action
  const reason = reasons[entry.reason]
  return `${action} · ${reason ? t(reason) : entry.reason}`
}

async function loadSessions() {
  // Until the password is changed the server answers nothing else.
  if (auth.passwordChangeRequired) return
  sessionsLoading.value = true; sessionsError.value = ''
  try { sessions.value = await request<Session[]>('/api/v1/me/sessions') } catch (reason) { sessionsError.value = errorMessage(reason) } finally { sessionsLoading.value = false }
  try { history.value = (await request<LoginHistory>('/api/v1/me/login-history', { query: { page: 1, size: 10 } })).items; historyError.value = '' }
  catch (reason) { historyError.value = errorMessage(reason) }
}
async function saveProfile() {
  if (savingProfile.value) return
  savingProfile.value = true; profileError.value = ''
  try {
    const me = await request<Me>('/api/v1/me', { method: 'PUT', body: { displayName: displayName.value, email: email.value } })
    auth.updated(me)
    toast.show(t('account.saved'))
  } catch (reason) { profileError.value = errorMessage(reason) } finally { savingProfile.value = false }
}
function problems(): FieldErrors {
  const found: FieldErrors = {}
  if (!current.value) found.current = t('account.enterCurrent')
  if (!next.value) found.next = t('account.enterNew')
  if (next.value !== confirm.value) found.confirm = t('account.passwordMismatch')
  return found
}
async function changePassword() {
  if (changing.value) return
  passwordError.value = ''
  if (invalid()) return
  changing.value = true; passwordError.value = ''
  try {
    await request<null>('/api/v1/me/password', { method: 'POST', body: { currentPassword: current.value, newPassword: next.value } })
    current.value = ''; next.value = ''; confirm.value = ''
    auth.updated(await request<Me>('/api/v1/me'))
    toast.show(t('account.passwordChanged'))
  } catch (reason) { passwordError.value = errorMessage(reason) } finally { changing.value = false }
}
async function end(session: Session) {
  ending.value = session.id
  try {
    await request<null>(`/api/v1/me/sessions/${encodeURIComponent(session.id)}`, { method: 'DELETE' })
    if (session.current) { auth.reset(); void router.replace('/auth/login'); return }
    toast.show(t('sessions.ended')); void loadSessions()
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { ending.value = '' }
}
// The devices tab loads its lists when it is opened, rather than every time the account page is.
watch(tab, value => { if (value === 'devices') void loadSessions() }, { immediate: true })
</script>
<template>
  <PageHeading :title="t('titles.account')" :description="t('account.description')" />
  <div v-if="auth.passwordChangeRequired" class="mb-6 flex gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-900" role="status"><ShieldAlert :size="20" class="mt-0.5 shrink-0" /><div><p class="text-sm font-semibold">{{ t('account.forcedTitle') }}</p><p class="mt-1 text-xs leading-5">{{ t('account.forcedText') }}</p></div></div>
  <!--
    Scrolls sideways on a narrow screen, without a bar. Its baseline is an inset shadow rather than a border the tabs
    overlap: overflowing it by a pixel would make it scroll down as well.
  -->
  <nav
    v-else
    :aria-label="t('account.sections')"
    class="mb-6 flex gap-1 overflow-x-auto overflow-y-hidden shadow-[inset_0_-1px_0_var(--color-line)] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
    data-account-tabs
  >
    <RouterLink
      v-for="item in tabs"
      :key="item.id"
      :to="`/account/${item.id}`"
      class="flex shrink-0 items-center gap-2 border-b-2 px-4 py-3 text-[13px] font-medium transition"
      :class="tab === item.id ? 'border-brand text-brand' : 'border-transparent text-muted hover:border-line hover:text-ink'"
      :aria-current="tab === item.id ? 'page' : undefined"
      :data-tab="item.id"
    >
      <component :is="item.icon" :size="16" aria-hidden="true" />{{ t(item.label) }}
    </RouterLink>
  </nav>

  <div v-if="tab === 'profile'" class="space-y-6" data-tab-panel="profile">
    <section class="panel p-6">
      <header class="mb-6 flex items-center gap-3"><span class="flex size-9 items-center justify-center rounded-xl bg-brand-soft text-brand"><UserRound :size="18" /></span><div><h2 class="text-sm font-semibold">{{ t('account.profile') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('account.profileCaption') }}</p></div></header>
      <dl class="mb-6 grid gap-4 rounded-xl bg-canvas/60 p-4 sm:grid-cols-2"><div><dt class="text-[11px] text-muted">{{ t('account.username') }}</dt><dd class="mt-1 font-mono text-sm">{{ auth.me?.username }}</dd></div><div><dt class="text-[11px] text-muted">{{ t('account.organization') }}</dt><dd class="mt-1 text-sm">{{ auth.me?.tenantName }}</dd></div></dl>
      <form id="profile" novalidate @submit.prevent="saveProfile">
        <div class="grid gap-5 md:grid-cols-2">
          <UiField v-model="displayName" :label="t('account.displayName')" :placeholder="t('account.displayNamePlaceholder')" autocomplete="name" /><UiField
            v-model="email"
            :label="t('account.email')"
            type="email"
            :placeholder="t('account.emailPlaceholder')"
            autocomplete="email"
          />
        </div>
        <p v-if="profileError" class="mt-5 rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ profileError }}</p>
        <div class="mt-6 flex justify-end border-t border-line pt-5"><UiButton type="submit" :loading="savingProfile">{{ t('account.save') }}</UiButton></div>
      </form>
    </section>
  </div>

  <div v-else-if="tab === 'security'" class="space-y-6" data-tab-panel="security">
    <section class="panel p-6">
      <header class="mb-6 flex items-center gap-3"><span class="flex size-9 items-center justify-center rounded-xl bg-brand-soft text-brand"><KeyRound :size="18" /></span><div><h2 class="text-sm font-semibold">{{ t('account.password') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('account.passwordCaption') }}</p></div></header>
      <p v-if="auth.me?.identitySource" class="rounded-lg bg-canvas/60 p-4 text-xs leading-6" data-external-password>{{ t('account.externalPassword', { source: auth.me.identitySource }) }}</p>
      <form
        v-else
        id="password"
        novalidate
        @submit.prevent="changePassword"
      >
        <div class="grid gap-5 md:grid-cols-3">
          <UiField
            v-model="current"
            :label="t('account.currentPassword')"
            type="password"
            autocomplete="current-password"
            required
            :error="fieldErrors.current"
          /><UiField
            v-model="next"
            :label="t('account.newPassword')"
            type="password"
            autocomplete="new-password"
            required
            :error="fieldErrors.next"
          /><UiField
            v-model="confirm"
            :label="t('account.confirmPassword')"
            type="password"
            autocomplete="new-password"
            required
            :error="fieldErrors.confirm"
          />
        </div>
        <p v-if="passwordError" class="mt-5 rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ passwordError }}</p>
        <div class="mt-6 flex justify-end border-t border-line pt-5"><UiButton type="submit" :loading="changing">{{ t('account.changePassword') }}</UiButton></div>
      </form>
    </section>
    <AccountMfa v-if="!auth.passwordChangeRequired" />
  </div>

  <div v-else class="space-y-6" data-tab-panel="devices">
    <section class="panel overflow-hidden">
      <header class="flex items-center gap-3 border-b border-line px-6 py-5"><span class="flex size-9 items-center justify-center rounded-xl bg-brand-soft text-brand"><MonitorSmartphone :size="18" /></span><div><h2 class="text-sm font-semibold">{{ t('account.sessions') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('account.sessionsCaption') }}</p></div></header>
      <p v-if="sessionsError" class="px-6 py-8 text-center text-xs text-rose-600" role="alert">{{ sessionsError }}</p>
      <div v-else-if="sessionsLoading && !sessions.length" class="space-y-4 p-6"><div v-for="index in 2" :key="index" class="h-10 animate-pulse rounded-lg bg-line"></div></div>
      <ul v-else class="divide-y divide-line">
        <li v-for="session in sessions" :key="session.id" class="flex flex-wrap items-center justify-between gap-3 px-6 py-4">
          <div><p class="text-sm font-medium">{{ agentLabel(session.userAgent) }}<span v-if="session.current" class="badge ml-2 bg-emerald-50 text-emerald-700">{{ t('sessions.current') }}</span></p><p class="mt-1 text-[11px] text-muted"><span class="font-mono">{{ session.clientIp || '—' }}</span> · {{ t('account.signedInAt', { time: dateLabel(session.signedInAt) }) }} · {{ t('account.lastSeenAt', { time: dateLabel(session.lastSeenAt) }) }}</p></div>
          <button
            type="button"
            class="table-action hover:text-rose-600"
            :disabled="ending === session.id"
            :aria-label="t('account.endSessionNamed', { client: agentLabel(session.userAgent) })"
            :data-tooltip="t('sessions.end')"
            @click="end(session)"
          >
            <LogOut :size="14" />
          </button>
        </li>
      </ul>
    </section>
    <section class="panel overflow-hidden">
      <header class="flex items-center gap-3 border-b border-line px-6 py-5"><span class="flex size-9 items-center justify-center rounded-xl bg-brand-soft text-brand"><History :size="18" /></span><div><h2 class="text-sm font-semibold">{{ t('account.history') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('account.historyCaption') }}</p></div></header>
      <p v-if="historyError" class="px-6 py-8 text-center text-xs text-rose-600" role="alert">{{ historyError }}</p>
      <p v-else-if="!history.length" class="px-6 py-8 text-center text-xs text-muted">{{ t('account.historyEmpty') }}</p>
      <ul v-else class="divide-y divide-line">
        <li v-for="(entry, index) in history" :key="index" class="flex flex-wrap items-center justify-between gap-3 px-6 py-3.5">
          <div class="flex items-center gap-3"><span class="size-2 rounded-full" :class="entry.outcome === 'SUCCESS' && entry.action !== 'ACCOUNT_LOCKED' ? 'bg-emerald-400' : 'bg-rose-400'"></span><div><p class="text-xs font-medium">{{ describe(entry) }}</p><p class="mt-1 text-[11px] text-muted">{{ agentLabel(entry.userAgent) }} · <span class="font-mono">{{ entry.clientIp || '—' }}</span></p></div></div>
          <span class="text-[11px] text-muted">{{ dateLabel(entry.occurredAt) }}</span>
        </li>
      </ul>
    </section>
  </div>
</template>
