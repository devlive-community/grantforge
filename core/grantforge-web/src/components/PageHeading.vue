<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
const { title, description, badge = '' } = defineProps<{ title: string; description: string; badge?: string }>()
</script>
<template>
  <div class="mb-7 flex flex-wrap items-start justify-between gap-4">
    <div><div class="flex items-center gap-3"><h1 class="text-2xl font-semibold tracking-tight">{{ title }}</h1><span v-if="badge" class="badge bg-brand-soft text-brand">{{ badge }}</span></div><p class="mt-2 text-[13px] leading-relaxed text-muted">{{ description }}</p></div>
    <div class="flex flex-wrap items-center gap-2"><slot></slot></div>
  </div>
</template>
