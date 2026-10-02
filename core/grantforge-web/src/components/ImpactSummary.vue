<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { components } from '@/api/schema'

type Impact = components['schemas']['ImpactReportResponse']

/** What a change would do, shown before it is made: the roles it reaches, what they gain and lose, and their holders. */
const { impact, tenants = false } = defineProps<{ impact: Impact; tenants?: boolean }>()
const { t } = useI18n()
const SHOWN = 12
const lostShown = computed(() => impact.lost.slice(0, SHOWN))
const gainedShown = computed(() => impact.gained.slice(0, SHOWN))
</script>
<template>
  <section class="rounded-lg border p-3 text-xs" :class="impact.lost.length ? 'border-amber-200 bg-amber-50/60' : 'border-line bg-canvas/40'" role="status" data-impact>
    <p v-if="!impact.roles.length" class="text-muted">{{ t('impact.none') }}</p>
    <template v-else>
      <p class="font-medium">{{ t('impact.summary', { roles: impact.roles.length, accounts: impact.accounts }) }}</p>
      <p v-if="impact.lost.length" class="mt-2 text-amber-800">{{ t('impact.lost') }} <span v-for="code in lostShown" :key="code" class="badge ml-1 bg-white font-mono text-[10px]">{{ code }}</span><span v-if="impact.lost.length > SHOWN" class="ml-1">{{ t('impact.more', { count: impact.lost.length - SHOWN }) }}</span></p>
      <p v-if="impact.gained.length" class="mt-2 text-emerald-800">{{ t('impact.gained') }} <span v-for="code in gainedShown" :key="code" class="badge ml-1 bg-white font-mono text-[10px]">{{ code }}</span><span v-if="impact.gained.length > SHOWN" class="ml-1">{{ t('impact.more', { count: impact.gained.length - SHOWN }) }}</span></p>
      <ul class="mt-2 max-h-40 space-y-1 overflow-auto">
        <li v-for="role in impact.roles" :key="role.roleId + (role.tenantCode ?? '')" class="flex flex-wrap items-center gap-2" :data-impact-role="role.code">
          <span v-if="tenants && role.tenantCode" class="badge">{{ role.tenantCode }}</span>
          <span class="font-medium">{{ role.name }}</span><span class="font-mono text-[10px] text-muted">{{ role.code }}</span>
          <span v-if="role.gained" class="text-emerald-700">+{{ role.gained }}</span><span v-if="role.lost" class="text-rose-700">−{{ role.lost }}</span>
        </li>
      </ul>
    </template>
  </section>
</template>
