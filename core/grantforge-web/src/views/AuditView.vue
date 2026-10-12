<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { Download, Search } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { download, errorMessage, request } from '@/lib/api'
import { auditActions } from '@/lib/auditActions'
import { dateLabel } from '@/lib/format'
import { vPermission } from '@/lib/permission'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageControls from '@/components/PageControls.vue'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import UiDatePicker from '@/components/UiDatePicker.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'

type Page = components['schemas']['PageResultAuditEventResponse']

const { t } = useI18n(), toast = useToast()
const result = shallowRef<Page>({ items: [], page: 1, size: 20, total: 0 })
const events = computed(() => result.value.items)
const loading = ref(false), exporting = ref(false), error = ref('')
const filters = ref({ action: '', outcome: '', actor: '', target: '', from: '', until: '' })
// Pages go through what the last search asked for, not what has been typed into the filters since.
const applied = shallowRef(parameters())
const page = ref(1), size = ref(20)
const pages = computed(() => Math.ceil(result.value.total / size.value))

const actionOptions = computed(() => [{ value: '', label: t('audit.anyAction') }, ...auditActions.map(action => ({ value: action, label: action }))])
const outcomes = computed(() => [{ value: '', label: t('audit.anyOutcome') }, { value: 'SUCCESS', label: t('audit.success') },
  { value: 'FAILURE', label: t('audit.failure') }])

/** The filters that are set, as query parameters. */
function parameters() {
  const { action, outcome, actor, target, from, until } = filters.value
  return { action: action || undefined, outcome: outcome || undefined, actor: actor.trim() || undefined, target: target.trim() || undefined,
    from: from ? new Date(from).toISOString() : undefined, until: until ? new Date(until).toISOString() : undefined }
}
async function load() {
  loading.value = true; error.value = ''
  try {
    result.value = await request<Page>('/api/v1/audit-events/pages', { query: { ...applied.value, page: page.value, size: size.value } })
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
/** Searches with the filters as they are now, from the first page. */
function run() { applied.value = parameters(); page.value = 1; void load() }
function goTo(value: number) { page.value = value; void load() }
function setSize(value: number) { size.value = value; page.value = 1; void load() }
async function exportEvents() {
  exporting.value = true
  try {
    const file = await download('/api/v1/audit-events/export', parameters())
    const url = URL.createObjectURL(file.blob), link = document.createElement('a')
    link.href = url; link.download = file.filename
    document.body.append(link); link.click(); link.remove()
    URL.revokeObjectURL(url)
    toast.show(t('audit.exported'))
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { exporting.value = false }
}
onMounted(run)
</script>

<template>
  <PageHeading :title="t('titles.audit')" :description="t('audit.description')">
    <UiButton v-permission="'system.audit.btn.export'" variant="secondary" :loading="exporting" @click="exportEvents">
      <Download :size="15" />{{ t('audit.export') }}
    </UiButton>
  </PageHeading>
  <form class="panel mb-5 grid gap-4 p-5 md:grid-cols-3 xl:grid-cols-4" novalidate @submit.prevent="run">
    <UiSelect v-model="filters.action" :label="t('audit.action')" :options="actionOptions" />
    <UiSelect v-model="filters.outcome" :label="t('audit.outcome')" :options="outcomes" />
    <UiField v-model="filters.actor" :label="t('audit.actor')" />
    <UiField v-model="filters.target" :label="t('audit.target')" />
    <UiDatePicker v-model="filters.from" time :max="filters.until" :label="t('audit.from')" />
    <UiDatePicker v-model="filters.until" time :min="filters.from" :label="t('audit.until')" />
    <div class="flex items-end">
      <UiButton type="submit" :loading="loading"><Search :size="15" />{{ t('audit.search') }}</UiButton>
    </div>
  </form>
  <p v-if="error" class="panel mb-5 p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <section v-else class="panel overflow-hidden">
    <p v-if="!events.length && !loading" class="p-10 text-center text-xs text-muted">{{ t('audit.empty') }}</p>
    <div v-else class="overflow-x-auto">
      <table class="w-full min-w-[56rem] text-left text-xs">
        <thead class="border-b border-line text-[11px] text-muted">
          <tr>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('audit.time') }}</th>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('audit.action') }}</th>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('audit.outcome') }}</th>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('audit.actor') }}</th>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('audit.target') }}</th>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('audit.reason') }}</th>
            <th scope="col" class="px-4 py-3 font-medium">{{ t('audit.origin') }}</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-line">
          <tr v-for="event in events" :key="event.id" :data-event="event.id">
            <td class="whitespace-nowrap px-4 py-3 text-muted">{{ dateLabel(event.occurredAt) }}</td>
            <td class="px-4 py-3 font-mono text-[11px]">{{ event.action }}</td>
            <td class="px-4 py-3">
              <span class="badge" :class="event.outcome === 'SUCCESS' ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'">
                {{ event.outcome === 'SUCCESS' ? t('audit.success') : t('audit.failure') }}
              </span>
            </td>
            <td class="px-4 py-3">{{ event.actorName ?? '—' }}</td>
            <td class="max-w-56 truncate px-4 py-3 font-mono text-[11px]" :title="event.targetId">{{ event.targetId ?? '—' }}</td>
            <td class="max-w-56 truncate px-4 py-3" :title="event.reason">{{ event.reason ?? '—' }}</td>
            <td class="px-4 py-3 text-[10px] text-muted">
              <span v-if="event.clientIp" class="block">{{ event.clientIp }}</span>
              <span v-if="event.userAgent" class="block max-w-48 truncate" :title="event.userAgent">{{ event.userAgent }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <PageControls
      :page="page"
      :size="size"
      :total="result.total"
      :pages="pages"
      :loading="loading"
      @update:page="goTo"
      @size="setSize"
    />
  </section>
</template>
