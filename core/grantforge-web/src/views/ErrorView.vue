<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ShieldAlert, Unplug, MapPinOff, ArrowLeft, LayoutDashboard } from '@lucide/vue'
import UiButton from '@/components/UiButton.vue'
const { status } = defineProps<{ status: '403' | '404' | 'network' }>()
const router = useRouter()
const content = computed(() => ({
  '403': { title: '这扇门，暂时没有为你打开', description: '你还没有访问此页面的权限，请联系管理员分配相应角色。', icon: ShieldAlert },
  '404': { title: '这个页面似乎走丢了', description: '链接可能已失效，或者页面已移动到其他位置。', icon: MapPinOff },
  network: { title: '连接暂时中断', description: '请检查网络和服务状态，然后再试一次。', icon: Unplug },
})[status])
</script>
<template><section class="flex min-h-[65dvh] flex-col items-center justify-center px-5 text-center"><div class="mb-7 flex size-24 items-center justify-center rounded-[28px] border border-brand/10 bg-brand-soft text-brand"><component :is="content.icon" :size="38" :stroke-width="1.4" /></div><p class="mb-3 font-mono text-xs tracking-widest text-muted">{{ status === 'network' ? 'CONNECTION LOST' : `ERROR ${status}` }}</p><h1 class="text-2xl font-semibold tracking-tight">{{ content.title }}</h1><p class="mb-8 mt-4 max-w-sm text-sm leading-7 text-muted">{{ content.description }}</p><div class="flex gap-3"><UiButton variant="secondary" @click="router.back()"><ArrowLeft :size="15" />返回上一页</UiButton><UiButton @click="router.push('/dashboard')"><LayoutDashboard :size="15" />回到概览</UiButton></div></section></template>
