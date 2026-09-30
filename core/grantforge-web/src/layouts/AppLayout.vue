<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LayoutDashboard, UsersRound, ShieldCheck, PanelsTopLeft, Braces, ArrowLeftRight, Search, Sun, Moon, Menu, LogOut, ExternalLink, Command, ChevronRight } from '@lucide/vue'
import { useAuth } from '@/stores/auth'
import { initials } from '@/lib/format'
import UiDialog from '@/components/UiDialog.vue'
const auth = useAuth(), route = useRoute(), router = useRouter()
const mobile = ref(false), commandOpen = ref(false), query = ref('')
const dark = ref(localStorage.getItem('GrantForgeTheme') === 'dark')
const navigation = [
  { path: '/dashboard', title: '概览', icon: LayoutDashboard, group: '工作空间' },
  { path: '/admin/users', title: '用户管理', icon: UsersRound, group: '访问控制' },
  { path: '/admin/roles', title: '角色管理', icon: ShieldCheck, group: '访问控制' },
  { path: '/admin/menus', title: '菜单管理', icon: PanelsTopLeft, group: '访问控制' },
  { path: '/admin/methods', title: '请求方式', icon: ArrowLeftRight, group: '访问控制' },
  { path: '/json/pretty', title: 'JSON 工作台', icon: Braces, group: '开发工具' },
]
const visible = computed(() => navigation.filter(item => auth.canVisit(item.path)))
const matches = computed(() => visible.value.filter(item => item.title.toLowerCase().includes(query.value.toLowerCase())))
function theme() { document.documentElement.classList.toggle('dark', dark.value); localStorage.setItem('GrantForgeTheme', dark.value ? 'dark' : 'light') }
function toggleTheme() { dark.value = !dark.value; theme() }
function keydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); commandOpen.value = !commandOpen.value }
  if (event.key === 'Escape') mobile.value = false
}
function navigate(path: string) { commandOpen.value = false; query.value = ''; mobile.value = false; void router.push(path) }
function logout() { auth.logout(); void router.replace('/auth/login') }
onMounted(() => { theme(); window.addEventListener('keydown', keydown) })
onBeforeUnmount(() => window.removeEventListener('keydown', keydown))
</script>
<template>
  <div class="min-h-dvh">
    <a href="#main-content" class="fixed left-4 top-4 z-100 -translate-y-20 rounded-lg bg-brand px-4 py-2 text-white focus:translate-y-0">跳转到内容</a>
    <button
      v-if="mobile"
      type="button"
      aria-label="关闭导航"
      class="fixed inset-0 z-30 bg-slate-950/55 lg:hidden"
      @click="mobile = false"
    ></button>
    <aside class="fixed inset-y-0 left-0 z-40 flex w-[244px] flex-col border-r border-white/5 bg-[#101828] text-white transition-transform lg:translate-x-0" :class="mobile ? 'translate-x-0' : '-translate-x-full'">
      <RouterLink to="/dashboard" class="flex items-center gap-3 px-6 pb-7 pt-8" @click="mobile = false"><img src="/static/images/grantforge-logo.png" alt="" class="size-9" /><div><span class="text-[19px] font-semibold tracking-tight">GrantForge</span><p class="mt-0.5 text-[10px] tracking-widest text-slate-500">ACCESS, WITH CONFIDENCE.</p></div></RouterLink>
      <div class="mx-5 mb-6 flex items-center gap-3 rounded-xl border border-white/8 bg-white/4 p-3"><div class="flex size-8 items-center justify-center rounded-lg bg-brand/20 text-indigo-300"><ShieldCheck :size="18" /></div><div><p class="text-xs font-medium">权限工作空间</p><p class="mt-0.5 text-[10px] text-slate-500">Devlive Community</p></div><span class="ml-auto size-1.5 rounded-full bg-emerald-400"></span></div>
      <nav aria-label="主导航" class="flex-1 overflow-y-auto px-4">
        <template v-for="group in ['工作空间', '访问控制', '开发工具']" :key="group">
          <p v-if="visible.some(item => item.group === group)" class="mb-2 mt-5 px-3 text-[10px] font-medium tracking-widest text-slate-500">{{ group }}</p>
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
      <div class="mx-5 mb-5 mt-6 rounded-xl border border-white/8 bg-gradient-to-br from-white/4 to-transparent p-4"><p class="text-xs font-medium text-slate-200">构建更清晰的权限边界</p><p class="mt-2 text-[11px] leading-5 text-slate-500">从用户到角色，让每一次授权都有据可依。</p><a href="https://authx.devlive.org" target="_blank" rel="noreferrer" class="mt-3 inline-flex items-center gap-2 text-[11px] text-indigo-300 hover:text-white">阅读使用文档 <ExternalLink :size="12" /></a></div>
      <div class="flex items-center justify-between border-t border-white/8 px-6 py-4 text-[10px] text-slate-600"><span>© 2026 Devlive</span><span class="font-mono">v2026.0.0</span></div>
    </aside>
    <div class="lg:ml-[244px]">
      <header class="sticky top-0 z-20 flex h-[76px] items-center justify-between border-b border-line bg-surface/95 px-5 backdrop-blur-lg sm:px-8 xl:px-10">
        <div class="flex items-center gap-3"><button type="button" class="icon-button lg:hidden" aria-label="打开导航" @click="mobile = true"><Menu :size="20" /></button><span class="hidden text-xs text-muted sm:inline">工作空间</span><ChevronRight :size="13" class="hidden text-muted/50 sm:block" /><span class="text-xs font-medium">{{ route.meta.title || 'GrantForge' }}</span></div>
        <div class="flex items-center gap-2 sm:gap-4"><button type="button" class="hidden items-center gap-2 rounded-xl border border-line bg-canvas/50 px-3 py-2 text-xs text-muted sm:flex" @click="commandOpen = true"><Search :size="14" /><span class="mr-7">快速跳转</span><kbd class="flex items-center gap-0.5 rounded border border-line px-1.5 py-0.5 text-[10px]"><Command :size="10" />K</kbd></button><button type="button" class="icon-button" :aria-label="dark ? '切换浅色主题' : '切换深色主题'" @click="toggleTheme"><component :is="dark ? Sun : Moon" :size="18" /></button><div class="hidden h-6 w-px bg-line sm:block"></div><button type="button" class="flex items-center gap-2.5 rounded-lg py-1" @click="logout"><span class="flex size-8 items-center justify-center rounded-xl bg-brand-soft text-[11px] font-semibold text-brand">{{ initials(auth.user?.name || auth.username) }}</span><span class="hidden text-left sm:block"><span class="block text-xs font-medium">{{ auth.user?.name || auth.username }}</span><span class="mt-0.5 block text-[10px] text-muted">退出登录</span></span><LogOut :size="15" class="text-muted" /></button></div>
      </header>
      <main id="main-content" class="mx-auto max-w-[1500px] p-5 sm:p-8 xl:p-10">
        <div v-if="auth.navigationError" class="mb-5 flex items-center justify-between rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800"><span>{{ auth.navigationError }}</span><button type="button" class="font-semibold underline" @click="auth.loadNavigation()">重试</button></div>
        <RouterView v-slot="{ Component }"><component :is="Component" :key="route.path" /></RouterView>
      </main>
    </div>
    <UiDialog v-model="commandOpen" title="快速跳转" description="搜索工作空间中的页面">
      <div class="flex items-center gap-3 rounded-xl border border-line px-4">
        <Search :size="18" class="text-muted" /><input
          v-model="query"
          aria-label="搜索页面"
          placeholder="输入页面名称…"
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
        </button><p v-if="!matches.length" class="py-8 text-center text-muted">没有匹配的页面</p>
      </div>
    </UiDialog>
  </div>
</template>
