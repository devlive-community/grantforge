<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue'
import { Plug, Power, PowerOff, RefreshCw } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { vPermission } from '@/lib/permission'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'

type Plugin = components['schemas']['PluginResponse']

const { t } = useI18n(), toast = useToast()
const plugins = shallowRef<Plugin[]>([]), loading = ref(false), error = ref(''), busy = ref('')

// Literal keys, so the message checker sees every one in use.
const statusKeys = { ACTIVE: 'plugins.statusActive', DISABLED: 'plugins.statusDisabled', INCOMPATIBLE: 'plugins.statusIncompatible',
  FAILED: 'plugins.statusFailed' } as const
const statusColors = { ACTIVE: 'bg-emerald-50 text-emerald-700', DISABLED: 'bg-canvas text-muted', INCOMPATIBLE: 'bg-amber-50 text-amber-700',
  FAILED: 'bg-rose-50 text-rose-700' } as const
const sourceKeys = { BUILTIN: 'plugins.sourceBuiltin', EXTERNAL: 'plugins.sourceExternal' } as const

async function load() {
  loading.value = true; error.value = ''
  try { plugins.value = await request<Plugin[]>('/api/v1/plugins') } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
async function act(key: string, action: () => Promise<unknown>, done: string) {
  if (busy.value) return
  busy.value = key
  try { await action(); toast.show(done); await load() } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { busy.value = '' }
}
function toggle(plugin: Plugin) {
  const on = plugin.status === 'DISABLED'
  void act(plugin.id, () => request(`/api/v1/plugins/${encodeURIComponent(plugin.id)}/${on ? 'enable' : 'disable'}`, { method: 'POST' }),
    on ? t('plugins.enabled') : t('plugins.disabled'))
}
function rescan() {
  void act('rescan', () => request('/api/v1/plugins/rescan', { method: 'POST' }), t('plugins.rescanned'))
}
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.plugins')" :description="t('plugins.description')" :badge="t('plugins.count', { count: plugins.length })">
    <UiButton v-permission="'platform.plugin.btn.rescan'" variant="secondary" :loading="busy === 'rescan'" @click="rescan"><RefreshCw :size="15" />{{ t('plugins.rescan') }}</UiButton>
  </PageHeading>
  <p v-if="error" class="panel p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <div v-else-if="loading && !plugins.length" class="space-y-3"><div v-for="index in 3" :key="index" class="h-24 animate-pulse rounded-xl bg-line"></div></div>
  <section v-else-if="!plugins.length" class="panel flex flex-col items-center p-10 text-center">
    <Plug :size="28" class="text-muted" />
    <p class="mt-3 font-medium">{{ t('plugins.empty') }}</p>
    <p class="mt-1 text-xs text-muted">{{ t('plugins.emptyHint') }}</p>
  </section>
  <div v-else class="space-y-4">
    <section v-for="plugin in plugins" :key="plugin.source + plugin.id + plugin.location" class="panel p-5" :data-plugin="plugin.id">
      <header class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div class="flex flex-wrap items-center gap-2">
            <h2 class="text-sm font-semibold">{{ plugin.name }}</h2>
            <span class="font-mono text-[10px] text-muted">{{ plugin.id }}</span>
            <span class="badge" :class="statusColors[plugin.status]">{{ t(statusKeys[plugin.status]) }}</span>
            <span class="badge">{{ t(sourceKeys[plugin.source]) }}</span>
          </div>
          <p v-if="plugin.description" class="mt-1 text-xs text-muted">{{ plugin.description }}</p>
          <p class="mt-1 text-[11px] text-muted">{{ t('plugins.details', { version: plugin.version ?? '—', api: plugin.apiVersion ?? '—', location: plugin.location }) }}</p>
        </div>
        <UiButton
          v-if="plugin.status === 'ACTIVE' || plugin.status === 'DISABLED'"
          v-permission="'platform.plugin.btn.toggle'"
          variant="secondary"
          :loading="busy === plugin.id"
          :aria-label="plugin.status === 'DISABLED' ? t('plugins.enableNamed', { name: plugin.name }) : t('plugins.disableNamed', { name: plugin.name })"
          @click="toggle(plugin)"
        >
          <component :is="plugin.status === 'DISABLED' ? Power : PowerOff" :size="15" />{{ plugin.status === 'DISABLED' ? t('plugins.enable') : t('plugins.disable') }}
        </UiButton>
      </header>
      <p v-if="plugin.problem" class="mt-3 rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="note">{{ plugin.problem }}</p>
      <ul v-if="plugin.serviceTypes.length" class="mt-4 grid gap-3 md:grid-cols-2">
        <li v-for="type in plugin.serviceTypes" :key="type.name" class="rounded-lg border border-line p-3 text-xs" :data-service-type="type.name">
          <p class="font-medium">{{ type.label }} <span class="ml-1 font-mono text-[10px] text-muted">{{ type.name }} · v{{ type.version }}</span></p>
          <p v-if="type.description" class="mt-1 text-muted">{{ type.description }}</p>
          <p class="mt-2"><span class="text-muted">{{ t('plugins.resources') }}</span> {{ type.resources.join(' / ') }}</p>
          <p class="mt-1"><span class="text-muted">{{ t('plugins.accessTypes') }}</span> {{ type.accessTypes.join(t('roles.listSeparator')) }}</p>
          <p v-if="type.dataMask || type.rowFilter" class="mt-1 text-muted">
            <span v-if="type.dataMask" class="badge mr-1">{{ t('plugins.dataMask') }}</span><span v-if="type.rowFilter" class="badge">{{ t('plugins.rowFilter') }}</span>
          </p>
        </li>
      </ul>
    </section>
  </div>
</template>
