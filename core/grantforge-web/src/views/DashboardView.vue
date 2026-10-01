<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, shallowRef } from 'vue'
import { ArrowUpRight, ArrowRight, UsersRound, ShieldCheck, RefreshCw, Network, Users, BriefcaseBusiness, BookOpen, Braces, ExternalLink } from '@lucide/vue'
import { request } from '@/lib/api'
import { dateLabel, initials } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useI18n } from 'vue-i18n'
import { currentLocale } from '@/i18n'
type User = components['schemas']['UserResponse']
type UserPage = components['schemas']['PageResultUserResponse']
type OrgUnit = components['schemas']['OrgUnitResponse']
const auth = useAuth(), loading = ref(true), partial = ref(false), { t } = useI18n()
const totals = ref<Record<string, number | null>>({}), recent = shallowRef<User[]>([]), updated = ref('')
let controller: AbortController | undefined
const greeting = computed(() => new Date().getHours() < 12 ? t('dashboard.morning') : new Date().getHours() < 18 ? t('dashboard.afternoon') : t('dashboard.evening'))
/** One card per console page with a count; only the pages the user may open are shown and loaded. */
const cards = computed(() => [
  { title: t('dashboard.cardUsers'), icon: UsersRound, path: '/admin/users', caption: t('dashboard.cardUsersCaption'), color: 'text-indigo-500 bg-indigo-50 dark:bg-indigo-500/10' },
  { title: t('dashboard.cardUnits'), icon: Network, path: '/admin/org', caption: t('dashboard.cardUnitsCaption'), color: 'text-emerald-600 bg-emerald-50 dark:bg-emerald-500/10' },
  { title: t('dashboard.cardGroups'), icon: Users, path: '/admin/groups', caption: t('dashboard.cardGroupsCaption'), color: 'text-amber-600 bg-amber-50 dark:bg-amber-500/10' },
  { title: t('dashboard.cardPositions'), icon: BriefcaseBusiness, path: '/admin/positions', caption: t('dashboard.cardPositionsCaption'), color: 'text-blue-500 bg-blue-50 dark:bg-blue-500/10' },
].filter(card => auth.canVisit(card.path)))
const steps = computed(() => [
  { title: t('dashboard.stepUsers'), text: t('dashboard.stepUsersText') },
  { title: t('dashboard.stepRoles'), text: t('dashboard.stepRolesText') },
  { title: t('dashboard.stepResources'), text: t('dashboard.stepResourcesText') },
])
const counters: Record<string, (signal: AbortSignal) => Promise<number>> = {
  '/admin/org': async signal => (await request<OrgUnit[]>('/api/v1/org-units', { signal })).length,
  '/admin/groups': async signal => (await request<{ total: number }>('/api/v1/groups', { signal, query: { page: 1, size: 1 } })).total,
  '/admin/positions': async signal => (await request<{ total: number }>('/api/v1/positions', { signal, query: { page: 1, size: 1 } })).total,
}
async function load() {
  controller?.abort(); controller = new AbortController()
  const signal = controller.signal
  loading.value = true
  const paths = cards.value.map(card => card.path)
  const users = paths.includes('/admin/users') ? request<UserPage>('/api/v1/users', { signal, query: { page: 1, size: 5 } }) : undefined
  const counts = paths.map(path => {
    const counter = counters[path]
    return counter ? counter(signal) : users ? users.then(page => page.total) : Promise.reject(new Error(path))
  })
  const results = await Promise.allSettled(counts)
  const members = users ? await users.catch(() => undefined) : undefined
  if (signal.aborted) return
  totals.value = Object.fromEntries(results.map((result, index) => [paths[index], result.status === 'fulfilled' ? result.value : null]))
  recent.value = members?.items ?? []
  partial.value = results.some(result => result.status === 'rejected')
  updated.value = new Intl.DateTimeFormat(currentLocale(), { hour: '2-digit', minute: '2-digit' }).format(new Date())
  loading.value = false
}
onMounted(() => void load())
onBeforeUnmount(() => controller?.abort())
</script>
<template>
  <PageHeading :title="t('dashboard.title')" :description="t('dashboard.description')"><span v-if="updated" class="mr-1 hidden text-[11px] text-muted sm:block">{{ t('dashboard.updatedAt', { time: updated }) }}</span><UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />{{ t('dashboard.refresh') }}</UiButton></PageHeading>
  <section class="panel relative mb-7 overflow-hidden border-brand/10 bg-gradient-to-r from-brand-soft via-surface to-surface p-6 sm:p-8"><div class="absolute right-16 top-0 hidden size-72 translate-y-[-30%] rounded-full border-[45px] border-brand/4 sm:block"></div><div class="relative flex items-center justify-between gap-5"><div><span class="eyebrow text-brand">A CLEARER VIEW OF ACCESS</span><h2 class="mt-3 text-[25px] font-semibold tracking-tight">{{ t('dashboard.greeting', { greeting, name: auth.user?.name || auth.username }) }} <span class="ml-1">✦</span></h2><p class="mt-3 max-w-lg text-[13px] leading-6 text-muted">{{ t('dashboard.intro') }}</p><div class="mt-5 flex flex-wrap gap-2"><RouterLink v-if="auth.canVisit('/admin/users')" to="/admin/users"><UiButton><UsersRound :size="15" />{{ t('dashboard.manageUsers') }} <ArrowRight :size="14" /></UiButton></RouterLink><RouterLink v-if="auth.canVisit('/admin/org')" to="/admin/org"><UiButton variant="secondary"><Network :size="15" />{{ t('dashboard.viewOrg') }}</UiButton></RouterLink></div></div><div class="hidden flex-col items-center gap-3 pr-6 sm:flex"><div class="flex size-24 items-center justify-center rounded-[26px] border border-brand/10 bg-surface shadow-[0_8px_30px_#635bff10]"><img src="/static/images/grantforge-logo.png" alt="GrantForge" class="size-17" /></div><span class="rounded-full border border-brand/10 bg-surface px-3 py-1 text-[10px] font-medium tracking-wider text-brand">RBAC WORKSPACE</span></div></div></section>
  <div v-if="partial" class="mb-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800" role="alert">{{ t('dashboard.partial') }}</div>
  <div class="mb-7 grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><div v-for="card in cards" :key="card.path" class="panel p-5"><div class="flex items-center justify-between"><span class="flex size-10 items-center justify-center rounded-xl" :class="card.color"><component :is="card.icon" :size="20" :stroke-width="1.7" /></span><RouterLink :to="card.path" :aria-label="t('dashboard.viewCard', { title: card.title })" class="icon-button size-7"><ArrowUpRight :size="17" /></RouterLink></div><p class="mt-5 text-xs text-muted">{{ card.title }}</p><div v-if="loading" class="my-3 h-8 w-16 animate-pulse rounded bg-line"></div><p v-else class="mt-2 text-[30px] font-semibold leading-none tracking-tight">{{ totals[card.path] ?? '—' }}</p><p class="mt-4 text-[10px] text-muted/80">{{ card.caption }}</p></div></div>
  <div class="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
    <section class="panel overflow-hidden"><header class="flex items-center justify-between border-b border-line px-5 py-5"><div><h3 class="text-sm font-semibold">{{ t('dashboard.members') }}</h3><p class="mt-1 text-[11px] text-muted">{{ t('dashboard.membersCaption') }}</p></div><RouterLink v-if="auth.canVisit('/admin/users')" to="/admin/users" class="flex items-center gap-1 text-[11px] font-medium text-brand">{{ t('dashboard.viewAll') }} <ArrowUpRight :size="13" /></RouterLink></header><div v-if="loading" class="space-y-6 p-6"><div v-for="index in 3" :key="index" class="h-10 animate-pulse rounded-lg bg-line"></div></div><div v-else-if="recent.length" class="divide-y divide-line"><div v-for="user in recent" :key="user.id" class="flex flex-wrap items-center justify-between gap-3 px-5 py-4"><div class="flex items-center gap-3"><span class="flex size-9 items-center justify-center rounded-xl bg-brand-soft text-[11px] font-semibold text-brand">{{ initials(user.displayName || user.username) }}</span><div><p class="text-[13px] font-medium">{{ user.displayName || user.username }}</p><p class="mt-1 text-[10px] text-muted">{{ user.primaryUnitName || t('users.noUnit') }}</p></div></div><div class="flex items-center gap-5"><span class="hidden text-[10px] text-muted sm:block">{{ dateLabel(user.createdAt) }}</span><StatusBadge :active="user.status === 'ACTIVE'" :locked="!!user.lockedUntil" /></div></div></div><p v-else class="px-6 py-12 text-center text-xs text-muted">{{ t('dashboard.noMembers') }}</p></section>
    <div class="space-y-6">
      <section class="panel p-5"><div class="mb-5 flex items-center justify-between"><h3 class="text-sm font-semibold">{{ t('dashboard.startTitle') }}</h3><ShieldCheck :size="17" class="text-brand" /></div><div class="space-y-4"><div v-for="(step, index) in steps" :key="step.title" class="flex items-center gap-3"><span class="flex size-7 shrink-0 items-center justify-center rounded-full bg-brand-soft font-mono text-[10px] font-medium text-brand">0{{ index + 1 }}</span><div><p class="text-xs font-medium">{{ step.title }}</p><p class="mt-1 text-[10px] text-muted">{{ step.text }}</p></div></div></div></section>
      <section class="panel p-5"><h3 class="mb-4 text-sm font-semibold">{{ t('dashboard.tools') }}</h3><RouterLink to="/json/pretty" class="flex items-center gap-3 rounded-xl bg-canvas p-3 transition hover:bg-brand-soft"><span class="flex size-9 items-center justify-center rounded-lg bg-surface text-brand"><Braces :size="19" /></span><div><p class="text-xs font-medium">{{ t('titles.json') }}</p><p class="mt-1 text-[10px] text-muted">{{ t('dashboard.jsonCaption') }}</p></div><ArrowUpRight :size="15" class="ml-auto text-muted" /></RouterLink><a href="https://authx.devlive.org" target="_blank" rel="noreferrer" class="mt-3 flex items-center gap-3 px-3 py-2 text-xs text-muted hover:text-brand"><BookOpen :size="15" />{{ t('dashboard.docs') }} <ExternalLink :size="12" class="ml-auto" /></a></section>
    </div>
  </div>
  <div class="mt-7 flex flex-wrap items-center justify-between gap-3 text-[10px] text-muted/65"><span>{{ t('dashboard.footer') }}</span><span class="flex items-center gap-1.5"><span class="size-1.5 rounded-full" :class="partial ? 'bg-amber-400' : 'bg-emerald-400'"></span>{{ loading ? t('dashboard.loading') : partial ? t('dashboard.partialStatus') : t('dashboard.upToDate') }}</span></div>
</template>
