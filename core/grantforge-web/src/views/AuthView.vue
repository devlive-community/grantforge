<script setup lang="ts">
import { computed, onMounted, ref, useId, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, ShieldCheck, UsersRound, KeyRound, Eye, EyeOff, Check, AlertCircle } from '@lucide/vue'
import UiButton from '@/components/UiButton.vue'
import { useAuth } from '@/stores/auth'
import { errorMessage, request } from '@/lib/api'
const { mode = 'login' } = defineProps<{ mode?: 'login' | 'register' }>()
const auth = useAuth(), route = useRoute(), router = useRouter()
const name = ref(auth.username), password = ref(''), confirmation = ref(''), visible = ref(false), busy = ref(false), error = ref(''), registered = ref(false)
const usernameInput = useTemplateRef<HTMLInputElement>('usernameInput')
const id = useId(), register = computed(() => mode === 'register')
onMounted(() => usernameInput.value?.focus())
watch(() => mode, () => { error.value = ''; password.value = ''; confirmation.value = ''; registered.value = false })
async function submit() {
  if (busy.value) return
  error.value = ''
  if (!name.value.trim()) { error.value = '请输入用户名'; usernameInput.value?.focus(); return }
  if (!password.value) { error.value = '请输入密码'; return }
  if (register.value && password.value.length < 8) { error.value = '密码至少需要 8 个字符'; return }
  if (register.value && !confirmation.value) { error.value = '请再次输入密码'; return }
  if (register.value && password.value !== confirmation.value) { error.value = '两次输入的密码不一致'; return }
  busy.value = true
  try {
    if (register.value) {
      await request<number>('/api/v1/user/register', { method: 'POST', anonymous: true, body: { username: name.value.trim(), password: password.value, repassword: confirmation.value } })
      registered.value = true
    } else {
      await auth.login(name.value.trim(), password.value)
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
      <div class="relative my-auto max-w-md py-16"><span class="mb-7 inline-flex items-center gap-2 rounded-full border border-indigo-400/20 bg-indigo-400/8 px-3 py-1.5 text-[11px] text-indigo-200"><span class="size-1.5 rounded-full bg-teal-400"></span>开放、清晰、可掌控</span><h1 class="text-[44px] font-semibold leading-[1.25] tracking-tight xl:text-[50px]">让每一次授权，<br /><span class="text-indigo-300">都有清晰的边界。</span></h1><p class="mt-6 text-sm leading-7 text-slate-400">一个为开发者打造的权限工作空间。连接用户、角色与资源，让访问控制回归简单。</p><div class="mt-12 space-y-5"><div v-for="item in [{ icon: UsersRound, title: '用户与角色', text: '让组织内的职责与权限一目了然' }, { icon: ShieldCheck, title: '精确的访问控制', text: '统一管理菜单与接口授权' }, { icon: KeyRound, title: '一个清晰的工作空间', text: '专注于你的业务，减少管理的复杂度' }]" :key="item.title" class="flex items-center gap-4"><span class="flex size-10 items-center justify-center rounded-xl border border-white/8 bg-white/4 text-indigo-300"><component :is="item.icon" :size="18" /></span><div><p class="text-[13px] font-medium">{{ item.title }}</p><p class="mt-1 text-xs text-slate-500">{{ item.text }}</p></div></div></div></div>
      <div class="relative flex justify-between text-xs text-slate-600"><span>Devlive Community</span><span>开源 · Apache 2.0</span></div>
    </section>
    <main class="flex min-h-dvh flex-col items-center justify-center bg-surface px-6 py-12">
      <div class="mb-10 flex items-center gap-3 lg:hidden"><img src="/static/images/grantforge-logo.png" alt="" class="size-9" /><span class="text-xl font-semibold">GrantForge</span></div>
      <div class="w-full max-w-[380px]">
        <template v-if="registered"><span class="mb-6 flex size-14 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-600"><Check :size="28" /></span><h2 class="text-2xl font-semibold">账号已创建</h2><p class="mt-3 text-sm leading-6 text-muted">请登录你的账号。管理权限由管理员分配。</p><RouterLink to="/auth/login" class="mt-8 block"><UiButton class="w-full">前往登录 <ArrowRight :size="16" /></UiButton></RouterLink></template>
        <template v-else>
          <p class="eyebrow mb-3 text-brand">WELCOME TO GRANTFORGE</p><h2 class="text-[28px] font-semibold tracking-tight">{{ register ? '创建你的账号' : '欢迎回来' }}</h2><p class="mb-9 mt-3 text-[13px] text-muted">{{ register ? '加入工作空间，开启更清晰的权限管理。' : '登录工作空间，继续管理你的访问权限。' }}</p>
          <div v-if="error" class="mb-5 flex items-start gap-2 rounded-xl bg-rose-50 p-3 text-xs leading-5 text-rose-700" role="alert"><AlertCircle :size="16" class="mt-0.5 shrink-0" />{{ error }}</div>
          <form class="space-y-5" novalidate @submit.prevent="submit">
            <div>
              <label :for="`${id}-name`" class="field-label">用户名</label><input
                :id="`${id}-name`"
                ref="usernameInput"
                v-model="name"
                class="field"
                autocomplete="username"
                required
                placeholder="输入用户名"
              />
            </div><div>
              <label :for="`${id}-password`" class="field-label">密码</label><div class="relative">
                <input
                  :id="`${id}-password`"
                  v-model="password"
                  class="field pr-12"
                  :type="visible ? 'text' : 'password'"
                  :autocomplete="register ? 'new-password' : 'current-password'"
                  :minlength="register ? 8 : undefined"
                  required
                  :placeholder="register ? '至少 8 个字符' : '输入密码'"
                /><button type="button" class="icon-button absolute right-1 top-1" :aria-label="visible ? '隐藏密码' : '显示密码'" @click="visible = !visible"><component :is="visible ? EyeOff : Eye" :size="17" /></button>
              </div>
            </div><div v-if="register">
              <label :for="`${id}-confirmation`" class="field-label">确认密码</label><input
                :id="`${id}-confirmation`"
                v-model="confirmation"
                class="field"
                type="password"
                autocomplete="new-password"
                required
                placeholder="再次输入密码"
              />
            </div><UiButton type="submit" class="mt-2 w-full" :loading="busy">{{ register ? '创建账号' : '登录工作空间' }} <ArrowRight :size="16" /></UiButton>
          </form>
          <p class="mt-7 text-center text-xs text-muted">{{ register ? '已经有账号？' : '还没有账号？' }} <RouterLink :to="register ? '/auth/login' : '/auth/register'" class="ml-1 font-medium text-brand hover:underline">{{ register ? '返回登录' : '创建账号' }}</RouterLink></p>
        </template>
      </div><p class="mt-16 text-[10px] text-muted/60">GrantForge · 让权限管理更清晰</p>
    </main>
  </div>
</template>
