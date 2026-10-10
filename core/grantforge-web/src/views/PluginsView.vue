<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { AlertTriangle, Plug, Power, PowerOff, RefreshCw } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { vPermission } from '@/lib/permission'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'

type Plugin = components['schemas']['PluginResponse']
type Impact = components['schemas']['PluginImpactResponse']

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
/** Runs an action on a plugin and reloads the list; answers whether it went through. */
async function act(key: string, action: () => Promise<unknown>, done: string): Promise<boolean> {
  if (busy.value) return false
  busy.value = key
  try { await action(); toast.show(done); await load(); return true } catch (reason) { toast.show(errorMessage(reason), 'error'); return false } finally { busy.value = '' }
}
// Disabling asks first: the console shows what the plugin's service types are used by across every tenant, and the
// administrator types the plugin's id once they have read it.
const disabling = shallowRef<Plugin | null>(null), impact = shallowRef<Impact | null>(null), impactError = ref(''), typed = ref('')
const confirmed = computed(() => impact.value !== null && disabling.value !== null && typed.value.trim() === disabling.value.id)
const totals = computed(() => {
  const services = impact.value?.services ?? []
  return { tenants: new Set(services.map(service => service.tenantCode)).size, services: services.length,
    policies: services.reduce((sum, service) => sum + service.policies, 0), agents: services.reduce((sum, service) => sum + service.agents, 0) }
})
async function loadImpact() {
  const plugin = disabling.value
  if (!plugin) return
  impact.value = null; impactError.value = ''
  try {
    const found = await request<Impact>(`/api/v1/plugins/${encodeURIComponent(plugin.id)}/impact`)
    if (disabling.value === plugin) impact.value = found
  } catch (reason) { if (disabling.value === plugin) impactError.value = errorMessage(reason) }
}
function toggle(plugin: Plugin) {
  if (plugin.status === 'DISABLED') {
    void act(plugin.id, () => request(`/api/v1/plugins/${encodeURIComponent(plugin.id)}/enable`, { method: 'POST' }), t('plugins.enabled'))
    return
  }
  disabling.value = plugin; typed.value = ''
  void loadImpact()
}
async function disable() {
  const plugin = disabling.value
  if (!plugin || !confirmed.value || busy.value) return
  // A refusal keeps the dialog open, with what was typed, to try again or cancel.
  if (await act(plugin.id, () => request(`/api/v1/plugins/${encodeURIComponent(plugin.id)}/disable`, { method: 'POST' }), t('plugins.disabled'))) {
    disabling.value = null
  }
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
  <UiDialog
    :model-value="disabling !== null"
    :title="t('plugins.disableTitle')"
    :description="t('plugins.disableDescription')"
    :busy="busy !== ''"
    wide
    @update:model-value="disabling = null"
  >
    <div v-if="disabling" class="space-y-5 text-xs" data-plugin-impact>
      <div>
        <p class="font-medium">{{ t('plugins.impactTypes', { name: disabling.name }) }}</p>
        <p class="mt-2 flex flex-wrap gap-1.5"><span v-for="type in disabling.serviceTypes" :key="type.name" class="badge">{{ type.label }} <span class="font-mono text-[10px]">{{ type.name }}</span></span></p>
      </div>
      <div v-if="impactError" class="flex items-center justify-between gap-3 rounded-lg bg-rose-50 p-3 text-rose-700 dark:bg-rose-500/10 dark:text-rose-300" role="alert">
        <span>{{ t('plugins.impactFailed', { message: impactError }) }}</span>
        <UiButton variant="secondary" @click="loadImpact">{{ t('plugins.impactRetry') }}</UiButton>
      </div>
      <div v-else-if="!impact" class="space-y-2" role="status" :aria-label="t('plugins.impactLoading')"><div v-for="index in 3" :key="index" class="h-8 animate-pulse rounded-lg bg-line"></div></div>
      <p v-else-if="!impact.services.length" class="rounded-lg bg-canvas p-3 text-muted" role="status">{{ t('plugins.impactNone') }}</p>
      <template v-else>
        <div class="flex gap-2.5 rounded-lg bg-amber-50 p-3 text-amber-800 dark:bg-amber-500/10 dark:text-amber-200" role="status">
          <AlertTriangle :size="16" class="mt-px shrink-0" aria-hidden="true" />
          <div>
            <p class="font-medium">{{ t('plugins.impactSummary', totals) }}</p>
            <p class="mt-2">{{ t('plugins.impactAfter') }}</p>
            <ul class="mt-1 list-disc space-y-0.5 pl-4">
              <li>{{ t('plugins.consequenceServices') }}</li>
              <li>{{ t('plugins.consequencePolicies') }}</li>
              <li>{{ t('plugins.consequenceAgents') }}</li>
            </ul>
          </div>
        </div>
        <div class="max-h-64 overflow-auto rounded-lg border border-line">
          <table class="w-full text-left">
            <thead class="sticky top-0 bg-canvas text-[11px] text-muted">
              <tr><th class="px-3 py-2 font-medium">{{ t('plugins.columnTenant') }}</th><th class="px-3 py-2 font-medium">{{ t('plugins.columnService') }}</th><th class="px-3 py-2 text-right font-medium">{{ t('plugins.columnPolicies') }}</th><th class="px-3 py-2 text-right font-medium">{{ t('plugins.columnAgents') }}</th></tr>
            </thead>
            <tbody class="divide-y divide-line">
              <tr v-for="service in impact.services" :key="service.tenantCode + service.id" :data-affected="service.name">
                <td class="px-3 py-2">{{ service.tenantName }} <span class="font-mono text-[10px] text-muted">{{ service.tenantCode }}</span></td>
                <td class="px-3 py-2">{{ service.label }} <span class="font-mono text-[10px] text-muted">{{ service.name }}</span><span v-if="!service.enabled" class="badge ml-2">{{ t('services.disabled') }}</span></td>
                <td class="px-3 py-2 text-right tabular-nums">{{ service.policies }}</td>
                <td class="px-3 py-2 text-right tabular-nums">{{ service.agents }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
      <form id="plugin-disable" novalidate @submit.prevent="disable">
        <UiField v-model="typed" :label="t('plugins.confirmLabel', { id: disabling.id })" :placeholder="disabling.id" :disabled="!impact" />
      </form>
    </div>
    <template #footer>
      <UiButton variant="secondary" :disabled="busy !== ''" @click="disabling = null">{{ t('shared.cancel') }}</UiButton>
      <UiButton
        type="submit"
        form="plugin-disable"
        variant="danger"
        :disabled="!confirmed"
        :loading="busy !== '' && busy === disabling?.id"
      >
        <PowerOff :size="15" />{{ t('plugins.confirmDisable') }}
      </UiButton>
    </template>
  </UiDialog>
</template>
