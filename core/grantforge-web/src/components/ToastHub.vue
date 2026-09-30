<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { CheckCircle2, AlertCircle, X } from '@lucide/vue'
import { useToast } from '@/stores/toast'
const toast = useToast()
</script>
<template>
  <div class="fixed right-4 bottom-5 z-50 flex w-[min(380px,calc(100%-2rem))] flex-col gap-3" aria-live="polite" aria-atomic="false">
    <div v-for="item in toast.items" :key="item.id" class="panel flex items-start gap-3 p-4 shadow-lg">
      <component :is="item.kind === 'success' ? CheckCircle2 : AlertCircle" :size="19" :class="item.kind === 'success' ? 'text-emerald-500' : 'text-rose-500'" class="mt-0.5 shrink-0" />
      <p class="flex-1 text-sm leading-relaxed">{{ item.message }}</p>
      <button class="icon-button -m-1 size-7" type="button" aria-label="关闭提示" @click="toast.remove(item.id)"><X :size="14" /></button>
    </div>
  </div>
</template>
