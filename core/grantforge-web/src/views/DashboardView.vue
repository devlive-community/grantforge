<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, shallowRef } from 'vue'
import { ArrowUpRight, ArrowRight, UsersRound, ShieldCheck, PanelsTopLeft, ArrowLeftRight, RefreshCw, KeyRound, BookOpen, Braces, ExternalLink } from '@lucide/vue'
import { request } from '@/lib/api'
import { dateLabel, initials } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import type { OverviewItem, Page, Role, Menu, Method, User } from '@/types/api'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import StatusBadge from '@/components/StatusBadge.vue'
const auth = useAuth(), loading = ref(true), partial = ref(false)
const totals = ref<(number | null)[]>([null, null, null, null]), recent = shallowRef<User[]>([]), updated = ref('')
let controller: AbortController | undefined
const greeting = computed(() => new Date().getHours() < 12 ? '早上好' : new Date().getHours() < 18 ? '下午好' : '晚上好')
const cards = [
  { title: '注册账号', icon: UsersRound, path: '/admin/users', caption: '包含系统账号', color: 'text-indigo-500 bg-indigo-50 dark:bg-indigo-500/10' },
  { title: '角色', icon: ShieldCheck, path: '/admin/roles', caption: '清晰定义访问职责', color: 'text-emerald-600 bg-emerald-50 dark:bg-emerald-500/10' },
  { title: '菜单资源', icon: PanelsTopLeft, path: '/admin/menus', caption: '统一组织访问目录', color: 'text-amber-600 bg-amber-50 dark:bg-amber-500/10' },
  { title: '请求方式', icon: ArrowLeftRight, path: '/admin/methods', caption: '区分不同操作范围', color: 'text-blue-500 bg-blue-50 dark:bg-blue-500/10' },
]
async function load() {
  controller?.abort(); controller = new AbortController()
  const signal = controller.signal
  loading.value = true
  const results = await Promise.allSettled([
    request<OverviewItem[]>('/api/v1/overview', { signal }),
    request<Page<Role>>('/api/v1/role', { signal, query: { page: 1, size: 1 } }),
    request<Page<Menu>>('/api/v1/menu', { signal, query: { page: 1, size: 1 } }),
    request<Page<Method>>('/api/v1/method', { signal, query: { page: 1, size: 1 } }),
    request<Page<User>>('/api/v1/user', { signal, query: { page: 1, size: 5 } }),
  ])
  if (signal.aborted) return
  const [users, roles, menus, methods, list] = results
  totals.value = [users.status === 'fulfilled' ? users.value[0]?.value ?? null : null, roles.status === 'fulfilled' ? roles.value.totalElements : null, menus.status === 'fulfilled' ? menus.value.totalElements : null, methods.status === 'fulfilled' ? methods.value.totalElements : null]
  recent.value = list.status === 'fulfilled' ? list.value.content : []
  partial.value = results.some(result => result.status === 'rejected')
  updated.value = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date())
  loading.value = false
}
onMounted(() => void load())
onBeforeUnmount(() => controller?.abort())
</script>
<template>
  <PageHeading title="工作空间概览" description="你的用户、角色和资源，都在这里井然有序。"><span v-if="updated" class="mr-1 hidden text-[11px] text-muted sm:block">{{ updated }} 更新</span><UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />刷新概览</UiButton></PageHeading>
  <section class="panel relative mb-7 overflow-hidden border-brand/10 bg-gradient-to-r from-brand-soft via-surface to-surface p-6 sm:p-8"><div class="absolute right-16 top-0 hidden size-72 translate-y-[-30%] rounded-full border-[45px] border-brand/4 sm:block"></div><div class="relative flex items-center justify-between gap-5"><div><span class="eyebrow text-brand">A CLEARER VIEW OF ACCESS</span><h2 class="mt-3 text-[25px] font-semibold tracking-tight">{{ greeting }}，{{ auth.user?.name || auth.username }} <span class="ml-1">✦</span></h2><p class="mt-3 max-w-lg text-[13px] leading-6 text-muted">每一个角色，连接一份职责。让权限配置更清晰，让团队协作更从容。</p><div class="mt-5 flex flex-wrap gap-2"><RouterLink v-if="auth.canVisit('/admin/users')" to="/admin/users"><UiButton><UsersRound :size="15" />管理用户 <ArrowRight :size="14" /></UiButton></RouterLink><RouterLink v-if="auth.canVisit('/admin/roles')" to="/admin/roles"><UiButton variant="secondary"><KeyRound :size="15" />查看角色</UiButton></RouterLink></div></div><div class="hidden flex-col items-center gap-3 pr-6 sm:flex"><div class="flex size-24 items-center justify-center rounded-[26px] border border-brand/10 bg-surface shadow-[0_8px_30px_#635bff10]"><img src="/static/images/grantforge-logo.png" alt="GrantForge" class="size-17" /></div><span class="rounded-full border border-brand/10 bg-surface px-3 py-1 text-[10px] font-medium tracking-wider text-brand">RBAC WORKSPACE</span></div></div></section>
  <div v-if="partial" class="mb-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800" role="alert">部分数据暂时不可用，可能是网络或访问权限限制。你可以刷新重试。</div>
  <div class="mb-7 grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><div v-for="(card, index) in cards" :key="card.title" class="panel p-5"><div class="flex items-center justify-between"><span class="flex size-10 items-center justify-center rounded-xl" :class="card.color"><component :is="card.icon" :size="20" :stroke-width="1.7" /></span><RouterLink v-if="auth.canVisit(card.path)" :to="card.path" :aria-label="`查看${card.title}`" class="icon-button size-7"><ArrowUpRight :size="17" /></RouterLink></div><p class="mt-5 text-xs text-muted">{{ card.title }}</p><div v-if="loading" class="my-3 h-8 w-16 animate-pulse rounded bg-line"></div><p v-else class="mt-2 text-[30px] font-semibold leading-none tracking-tight">{{ totals[index] ?? '—' }}</p><p class="mt-4 text-[10px] text-muted/80">{{ card.caption }}</p></div></div>
  <div class="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
    <section class="panel overflow-hidden"><header class="flex items-center justify-between border-b border-line px-5 py-5"><div><h3 class="text-sm font-semibold">工作空间成员</h3><p class="mt-1 text-[11px] text-muted">一起构建清晰的访问边界</p></div><RouterLink v-if="auth.canVisit('/admin/users')" to="/admin/users" class="flex items-center gap-1 text-[11px] font-medium text-brand">查看全部 <ArrowUpRight :size="13" /></RouterLink></header><div v-if="loading" class="space-y-6 p-6"><div v-for="index in 3" :key="index" class="h-10 animate-pulse rounded-lg bg-line"></div></div><div v-else-if="recent.length" class="divide-y divide-line"><div v-for="user in recent" :key="user.id" class="flex flex-wrap items-center justify-between gap-3 px-5 py-4"><div class="flex items-center gap-3"><span class="flex size-9 items-center justify-center rounded-xl bg-brand-soft text-[11px] font-semibold text-brand">{{ initials(user.name) }}</span><div><p class="text-[13px] font-medium">{{ user.name }}</p><p class="mt-1 text-[10px] text-muted">{{ user.roles?.map(role => role.name).join(' · ') || '尚未分配角色' }}</p></div></div><div class="flex items-center gap-5"><span class="hidden text-[10px] text-muted sm:block">{{ dateLabel(user.createTime) }}</span><StatusBadge :active="user.active" :locked="user.locked" /></div></div></div><p v-else class="px-6 py-12 text-center text-xs text-muted">暂时没有可显示的成员</p></section>
    <div class="space-y-6">
      <section class="panel p-5"><div class="mb-5 flex items-center justify-between"><h3 class="text-sm font-semibold">访问控制，从这里开始</h3><ShieldCheck :size="17" class="text-brand" /></div><div class="space-y-4"><div v-for="(step, index) in [{ title: '添加用户', text: '建立工作空间成员目录' }, { title: '定义角色', text: '根据职责组织访问权限' }, { title: '关联资源', text: '为角色分配菜单与接口' }]" :key="step.title" class="flex items-center gap-3"><span class="flex size-7 shrink-0 items-center justify-center rounded-full bg-brand-soft font-mono text-[10px] font-medium text-brand">0{{ index + 1 }}</span><div><p class="text-xs font-medium">{{ step.title }}</p><p class="mt-1 text-[10px] text-muted">{{ step.text }}</p></div></div></div></section>
      <section class="panel p-5"><h3 class="mb-4 text-sm font-semibold">开发者工具</h3><RouterLink to="/json/pretty" class="flex items-center gap-3 rounded-xl bg-canvas p-3 transition hover:bg-brand-soft"><span class="flex size-9 items-center justify-center rounded-lg bg-surface text-brand"><Braces :size="19" /></span><div><p class="text-xs font-medium">JSON 工作台</p><p class="mt-1 text-[10px] text-muted">格式化、压缩与语法校验</p></div><ArrowUpRight :size="15" class="ml-auto text-muted" /></RouterLink><a href="https://authx.devlive.org" target="_blank" rel="noreferrer" class="mt-3 flex items-center gap-3 px-3 py-2 text-xs text-muted hover:text-brand"><BookOpen :size="15" />使用文档 <ExternalLink :size="12" class="ml-auto" /></a></section>
    </div>
  </div>
  <div class="mt-7 flex flex-wrap items-center justify-between gap-3 text-[10px] text-muted/65"><span>GrantForge · 让权限管理更清晰</span><span class="flex items-center gap-1.5"><span class="size-1.5 rounded-full" :class="partial ? 'bg-amber-400' : 'bg-emerald-400'"></span>{{ loading ? '正在获取工作空间数据' : partial ? '部分数据不可用' : '工作空间数据已更新' }}</span></div>
</template>
