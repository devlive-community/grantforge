<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import type { components } from '@/api/schema'

type Usage = components['schemas']['FieldUsageResponse']

const { resourceId } = defineProps<{ resourceId: string }>()
const { t } = useI18n()
const usages = shallowRef<Usage[]>([]), loading = ref(false), error = ref('')
// Literal keys, so the message checker sees every one in use.
const directionKeys = { READ: 'fieldUsages.read', WRITE: 'fieldUsages.write' } as const

async function load() {
  loading.value = true; error.value = ''
  try {
    usages.value = await request<Usage[]>(`/api/v1/resources/${encodeURIComponent(resourceId)}/field-usages`)
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
watch(() => resourceId, load, { immediate: true })
</script>

<template>
  <section class="mt-8 border-t border-line pt-5" :aria-label="t('fieldUsages.title')">
    <h3 class="text-sm font-semibold">{{ t('fieldUsages.title') }}</h3>
    <p class="mb-3 mt-1 text-[11px] text-muted">{{ t('fieldUsages.caption') }}</p>
    <p v-if="error" class="text-xs text-rose-600" role="alert">{{ error }}</p>
    <div v-else-if="loading" class="h-10 animate-pulse rounded-lg bg-line"></div>
    <p v-else-if="!usages.length" class="text-xs text-muted">{{ t('fieldUsages.none') }}</p>
    <ul v-else class="divide-y divide-line rounded-lg border border-line">
      <li v-for="usage in usages" :key="`${usage.httpMethod} ${usage.pathPattern} ${usage.direction}`" class="flex flex-wrap items-center gap-3 px-3 py-2.5 text-xs" :data-usage="`${usage.httpMethod} ${usage.pathPattern}`">
        <span class="badge font-mono">{{ usage.httpMethod }}</span>
        <span class="min-w-0 flex-1 break-all font-mono">{{ usage.pathPattern }}</span>
        <span class="badge">{{ t(directionKeys[usage.direction]) }}</span>
      </li>
    </ul>
  </section>
</template>
