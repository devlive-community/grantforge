<script setup lang="ts">
const { active = true, locked = false } = defineProps<{ active?: boolean; locked?: boolean }>()
</script>
<template><span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-medium" :class="locked ? 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400' : active ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400' : 'bg-canvas text-muted'"><span class="size-1.5 rounded-full bg-current"></span>{{ locked ? '已锁定' : active ? '正常' : '已停用' }}</span></template>
