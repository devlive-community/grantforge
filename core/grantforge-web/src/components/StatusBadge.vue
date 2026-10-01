<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { active = true, locked = false } = defineProps<{ active?: boolean; locked?: boolean }>()
const { t } = useI18n()
</script>
<template><span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-medium" :class="locked ? 'bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400' : active ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400' : 'bg-canvas text-muted'"><span class="size-1.5 rounded-full bg-current"></span>{{ locked ? t('status.locked') : active ? t('status.active') : t('status.inactive') }}</span></template>
