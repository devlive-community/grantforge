<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ScrollText, Search } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { dateLabel } from '@/lib/format'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'

type Service = components['schemas']['ServiceResponse']
type AccessEvent = components['schemas']['AccessEventResponse']
type Page = components['schemas']['AccessPageResponse']

const PAGE = 50
const { t } = useI18n(), route = useRoute(), router = useRouter()
const services = shallowRef<Service[]>([]), events = shallowRef<AccessEvent[]>([]), next = ref<string | undefined>()
const loading = ref(false), more = ref(false), error = ref('')
const filters = ref({ user: '', resource: '', accessType: '', outcome: '', from: '', until: '' })

const serviceId = computed(() => {
  const asked = typeof route.query.service === 'string' ? route.query.service : ''
  return services.value.some(service => service.id === asked) ? asked : services.value[0]?.id ?? ''
})
const serviceOptions = computed(() => services.value.map(entry => ({ value: entry.id, label: entry.label, description: entry.name })))
const outcomes = computed(() => [{ value: '', label: t('accessAudit.anyOutcome') }, { value: 'ALLOWED', label: t('accessAudit.allowed') },
  { value: 'DENIED', label: t('accessAudit.denied') }])

/** The query of a page: the filters that are set, the page size and where to go on from. */
function query(cursor?: string) {
  const { user, resource, accessType, outcome, from, until } = filters.value
  const params = new URLSearchParams({ limit: String(PAGE) })
  for (const [name, value] of Object.entries({ user, resource, accessType, outcome })) if (value.trim()) params.set(name, value.trim())
  if (from) params.set('from', new Date(from).toISOString())
  if (until) params.set('until', new Date(until).toISOString())
  if (cursor) params.set('cursor', cursor)
  return `/api/v1/services/${encodeURIComponent(serviceId.value)}/access-events?${params.toString()}`
}
async function load() {
  loading.value = true; error.value = ''
  try {
    services.value = await request<Service[]>('/api/v1/services')
    await search()
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
async function search() {
  if (!serviceId.value) { events.value = []; next.value = undefined; return }
  const page = await request<Page>(query())
  events.value = page.events; next.value = page.next
}
async function run() {
  loading.value = true; error.value = ''
  try { await search() } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
async function loadMore() {
  if (!next.value || more.value) return
  more.value = true
  try {
    const page = await request<Page>(query(next.value))
    events.value = [...events.value, ...page.events]; next.value = page.next
  } catch (reason) { error.value = errorMessage(reason) } finally { more.value = false }
}
watch(serviceId, (now, before) => { if (before !== undefined && now !== before) void run() })
function chooseService(id: string) { void router.replace({ query: { ...route.query, service: id } }) }
function decidedBy(event: AccessEvent) {
  if (event.enforcer === 'NATIVE') return t('accessAudit.native')
  if (event.policyName) return event.policyName
  return event.policyId ? t('accessAudit.policyGone', { id: event.policyId }) : '—'
}
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.accessAudit')" :description="t('accessAudit.description')" />
  <p v-if="error" class="panel mb-5 p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <section v-if="!loading && !error && !services.length" class="panel flex flex-col items-center p-10 text-center">
    <ScrollText :size="28" class="text-muted" />
    <p class="mt-3 font-medium">{{ t('accessAudit.noServices') }}</p>
    <RouterLink to="/data/services" class="mt-2 text-xs text-brand hover:underline">{{ t('accessAudit.toServices') }}</RouterLink>
  </section>
  <template v-else-if="serviceId">
    <form class="panel mb-5 grid gap-4 p-5 md:grid-cols-3 xl:grid-cols-4" novalidate @submit.prevent="run">
      <UiSelect :model-value="serviceId" :label="t('accessAudit.service')" :options="serviceOptions" @update:model-value="chooseService" />
      <UiField v-model="filters.user" :label="t('accessAudit.user')" />
      <UiField v-model="filters.resource" :label="t('accessAudit.resource')" />
      <UiField v-model="filters.accessType" :label="t('accessAudit.accessType')" />
      <UiSelect v-model="filters.outcome" :label="t('accessAudit.outcome')" :options="outcomes" />
      <UiField v-model="filters.from" type="datetime-local" :label="t('accessAudit.from')" />
      <UiField v-model="filters.until" type="datetime-local" :label="t('accessAudit.until')" />
      <div class="flex items-end"><UiButton type="submit" :loading="loading"><Search :size="15" />{{ t('accessAudit.search') }}</UiButton></div>
    </form>
    <section class="panel overflow-x-auto">
      <p v-if="!events.length && !loading" class="p-10 text-center text-xs text-muted">{{ t('accessAudit.empty') }}</p>
      <table v-else class="w-full min-w-[56rem] text-left text-xs">
        <thead class="border-b border-line text-[11px] text-muted">
          <tr>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('accessAudit.time') }}</th>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('accessAudit.user') }}</th>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('accessAudit.resource') }}</th>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('accessAudit.accessType') }}</th>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('accessAudit.outcome') }}</th>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('accessAudit.decidedBy') }}</th>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('accessAudit.agent') }}</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-line">
          <tr v-for="event in events" :key="event.id" :data-event="event.eventId">
            <td class="whitespace-nowrap px-4 py-3 text-muted">{{ dateLabel(event.occurredAt) }}</td>
            <td class="px-4 py-3"><span class="font-medium">{{ event.user }}</span><span v-if="event.clientIp" class="block text-[10px] text-muted">{{ event.clientIp }}</span></td>
            <td class="max-w-80 px-4 py-3">
              <span class="block truncate font-mono" :title="event.resource">{{ event.resource }}</span>
              <details v-if="event.request" class="mt-1 text-[10px] text-muted"><summary class="cursor-pointer">{{ t('accessAudit.request') }}</summary><code class="block whitespace-pre-wrap break-all">{{ event.request }}</code></details>
            </td>
            <td class="px-4 py-3">{{ event.accessType }}<span v-if="event.action" class="block text-[10px] text-muted">{{ event.action }}</span></td>
            <td class="px-4 py-3"><span class="badge" :class="event.outcome === 'ALLOWED' ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'">{{ event.outcome === 'ALLOWED' ? t('accessAudit.allowed') : t('accessAudit.denied') }}</span></td>
            <td class="px-4 py-3">{{ decidedBy(event) }}</td>
            <td class="px-4 py-3 font-mono text-[11px] text-muted">{{ event.agentInstance }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="next" class="border-t border-line p-4 text-center"><UiButton variant="secondary" :loading="more" @click="loadMore">{{ t('accessAudit.more') }}</UiButton></div>
    </section>
  </template>
</template>
