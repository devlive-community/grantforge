<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LayoutDashboard, UsersRound, ShieldCheck, PanelsTopLeft, Braces, ArrowLeftRight, Search, Sun, Moon, Menu, LogOut, ExternalLink, Command, ChevronRight, Languages, MonitorSmartphone, Building2, Network, Users } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { currentLocale, setLocale } from '@/i18n'
import { useAuth } from '@/stores/auth'
import { initials } from '@/lib/format'
import UiDialog from '@/components/UiDialog.vue'
const auth = useAuth(), route = useRoute(), router = useRouter(), { t } = useI18n()
const mobile = ref(false), commandOpen = ref(false), query = ref('')
const dark = ref(localStorage.getItem('GrantForgeTheme') === 'dark')
const navigation = [
  { path: '/dashboard', titleKey: 'titles.dashboard', icon: LayoutDashboard, group: 'layout.groupWorkspace' },
  { path: '/admin/users', titleKey: 'titles.users', icon: UsersRound, group: 'layout.groupAccess' },
  { path: '/admin/org', titleKey: 'titles.org', icon: Network, group: 'layout.groupAccess' },
  { path: '/admin/groups', titleKey: 'titles.groups', icon: Users, group: 'layout.groupAccess' },
  { path: '/admin/roles', titleKey: 'titles.roles', icon: ShieldCheck, group: 'layout.groupAccess' },
  { path: '/admin/menus', titleKey: 'titles.menus', icon: PanelsTopLeft, group: 'layout.groupAccess' },
  { path: '/admin/methods', titleKey: 'titles.methods', icon: ArrowLeftRight, group: 'layout.groupAccess' },
  { path: '/admin/sessions', titleKey: 'titles.sessions', icon: MonitorSmartphone, group: 'layout.groupAccess' },
  { path: '/platform/tenants', titleKey: 'titles.tenants', icon: Building2, group: 'layout.groupPlatform' },
  { path: '/json/pretty', titleKey: 'titles.json', icon: Braces, group: 'layout.groupTools' },
]
const groups = ['layout.groupWorkspace', 'layout.groupAccess', 'layout.groupPlatform', 'layout.groupTools'] as const
const visible = computed(() => navigation.filter(item => auth.canVisit(item.path)).map(item => ({ ...item, title: t(item.titleKey) })))
const matches = computed(() => visible.value.filter(item => item.title.toLowerCase().includes(query.value.toLowerCase())))
function theme() { document.documentElement.classList.toggle('dark', dark.value); localStorage.setItem('GrantForgeTheme', dark.value ? 'dark' : 'light') }
function toggleTheme() { dark.value = !dark.value; theme() }
function toggleLocale() { setLocale(currentLocale() === 'zh-CN' ? 'en-US' : 'zh-CN') }
function keydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); commandOpen.value = !commandOpen.value }
  if (event.key === 'Escape') mobile.value = false
}
function navigate(path: string) { commandOpen.value = false; query.value = ''; mobile.value = false; void router.push(path) }
async function logout() { await auth.logout(); void router.replace('/auth/login') }
onMounted(() => { theme(); window.addEventListener('keydown', keydown) })
onBeforeUnmount(() => window.removeEventListener('keydown', keydown))
</script>
<template>
  <div class="min-h-dvh">
    <a href="#main-content" class="fixed left-4 top-4 z-100 -translate-y-20 rounded-lg bg-brand px-4 py-2 text-white focus:translate-y-0">{{ t('layout.skipToContent') }}</a>
    <button
      v-if="mobile"
      type="button"
      :aria-label="t('layout.closeNavigation')"
      class="fixed inset-0 z-30 bg-slate-950/55 lg:hidden"
      @click="mobile = false"
    ></button>
    <aside class="fixed inset-y-0 left-0 z-40 flex w-[244px] flex-col border-r border-white/5 bg-[#101828] text-white transition-transform lg:translate-x-0" :class="mobile ? 'translate-x-0' : '-translate-x-full'">
      <RouterLink to="/dashboard" class="flex items-center gap-3 px-6 pb-7 pt-8" @click="mobile = false"><img src="/static/images/grantforge-logo.png" alt="" class="size-9" /><div><span class="text-[19px] font-semibold tracking-tight">GrantForge</span><p class="mt-0.5 text-[10px] tracking-widest text-slate-500">ACCESS, WITH CONFIDENCE.</p></div></RouterLink>
      <div class="mx-5 mb-6 flex items-center gap-3 rounded-xl border border-white/8 bg-white/4 p-3"><div class="flex size-8 items-center justify-center rounded-lg bg-brand/20 text-indigo-300"><ShieldCheck :size="18" /></div><div><p class="text-xs font-medium">{{ t('layout.workspaceName') }}</p><p class="mt-0.5 text-[10px] text-slate-500">Devlive Community</p></div><span class="ml-auto size-1.5 rounded-full bg-emerald-400"></span></div>
      <nav :aria-label="t('layout.mainNavigation')" class="flex-1 overflow-y-auto px-4">
        <template v-for="group in groups" :key="group">
          <p v-if="visible.some(item => item.group === group)" class="mb-2 mt-5 px-3 text-[10px] font-medium tracking-widest text-slate-500">{{ t(group) }}</p>
          <RouterLink
            v-for="item in visible.filter(item => item.group === group)"
            :key="item.path"
            :to="item.path"
            class="mb-1 flex items-center gap-3 rounded-xl px-3 py-3 text-[13px] transition"
            :class="route.path === item.path ? 'bg-brand/18 text-indigo-200' : 'text-slate-400 hover:bg-white/5 hover:text-white'"
            @click="mobile = false"
          >
            <component :is="item.icon" :size="18" :stroke-width="1.8" /><span>{{ item.title }}</span><span v-if="route.path === item.path" class="ml-auto size-1.5 rounded-full bg-indigo-300"></span>
          </RouterLink>
        </template>
      </nav>
      <div class="mx-5 mb-5 mt-6 rounded-xl border border-white/8 bg-gradient-to-br from-white/4 to-transparent p-4"><p class="text-xs font-medium text-slate-200">{{ t('layout.promoTitle') }}</p><p class="mt-2 text-[11px] leading-5 text-slate-500">{{ t('layout.promoText') }}</p><a href="https://authx.devlive.org" target="_blank" rel="noreferrer" class="mt-3 inline-flex items-center gap-2 text-[11px] text-indigo-300 hover:text-white">{{ t('layout.readDocs') }} <ExternalLink :size="12" /></a></div>
      <div class="flex items-center justify-between border-t border-white/8 px-6 py-4 text-[10px] text-slate-600"><span>© 2026 Devlive</span><span class="font-mono">v2026.0.0</span></div>
    </aside>
    <div class="lg:ml-[244px]">
      <header class="sticky top-0 z-20 flex h-[76px] items-center justify-between border-b border-line bg-surface/95 px-5 backdrop-blur-lg sm:px-8 xl:px-10">
        <div class="flex items-center gap-3"><button type="button" class="icon-button lg:hidden" :aria-label="t('layout.openNavigation')" @click="mobile = true"><Menu :size="20" /></button><span class="hidden text-xs text-muted sm:inline">{{ t('layout.breadcrumbRoot') }}</span><ChevronRight :size="13" class="hidden text-muted/50 sm:block" /><span class="text-xs font-medium">{{ route.meta.titleKey ? t(route.meta.titleKey) : 'GrantForge' }}</span></div>
        <div class="flex items-center gap-2 sm:gap-4"><button type="button" class="hidden items-center gap-2 rounded-xl border border-line bg-canvas/50 px-3 py-2 text-xs text-muted sm:flex" @click="commandOpen = true"><Search :size="14" /><span class="mr-7">{{ t('layout.quickJump') }}</span><kbd class="flex items-center gap-0.5 rounded border border-line px-1.5 py-0.5 text-[10px]"><Command :size="10" />K</kbd></button><button type="button" class="icon-button" :aria-label="t('layout.switchLanguage')" @click="toggleLocale"><Languages :size="18" /></button><button type="button" class="icon-button" :aria-label="dark ? t('layout.lightTheme') : t('layout.darkTheme')" @click="toggleTheme"><component :is="dark ? Sun : Moon" :size="18" /></button><div class="hidden h-6 w-px bg-line sm:block"></div><RouterLink to="/account" class="flex items-center gap-2.5 rounded-lg py-1"><span class="flex size-8 items-center justify-center rounded-xl bg-brand-soft text-[11px] font-semibold text-brand">{{ initials(auth.user?.name || auth.username) }}</span><span class="hidden text-left sm:block"><span class="block text-xs font-medium">{{ auth.user?.name || auth.username }}</span><span class="mt-0.5 block text-[10px] text-muted">{{ t('titles.account') }}</span></span></RouterLink><button type="button" class="icon-button" @click="logout"><LogOut :size="17" /><span class="sr-only">{{ t('layout.logout') }}</span></button></div>
      </header>
      <main id="main-content" class="mx-auto max-w-[1500px] p-5 sm:p-8 xl:p-10">
        <div v-if="auth.authorizationError" class="mb-5 flex items-center justify-between rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800"><span>{{ auth.authorizationError }}</span><button type="button" class="font-semibold underline" @click="auth.loadAuthorization()">{{ t('common.retry') }}</button></div>
        <RouterView v-slot="{ Component }"><component :is="Component" :key="route.path" /></RouterView>
      </main>
    </div>
    <UiDialog v-model="commandOpen" :title="t('layout.quickJump')" :description="t('layout.quickJumpDescription')">
      <div class="flex items-center gap-3 rounded-xl border border-line px-4">
        <Search :size="18" class="text-muted" /><input
          v-model="query"
          :aria-label="t('layout.searchPages')"
          :placeholder="t('layout.searchPlaceholder')"
          class="w-full bg-transparent py-3 outline-none"
          autofocus
        />
      </div>
      <div class="mt-3 space-y-1">
        <button
          v-for="item in matches"
          :key="item.path"
          type="button"
          class="flex w-full items-center gap-3 rounded-xl p-3 text-left hover:bg-brand-soft"
          @click="navigate(item.path)"
        >
          <component :is="item.icon" :size="17" class="text-brand" /><span>{{ item.title }}</span><ChevronRight :size="15" class="ml-auto text-muted" />
        </button><p v-if="!matches.length" class="py-8 text-center text-muted">{{ t('layout.noMatchingPages') }}</p>
      </div>
    </UiDialog>
  </div>
</template>
