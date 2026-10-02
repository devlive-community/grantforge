<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { CircleCheck, RefreshCw } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { dateLabel } from '@/lib/format'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import UiSelect from '@/components/UiSelect.vue'

type Application = components['schemas']['ApplicationResponse']
type Report = components['schemas']['HealthReportResponse']
type Finding = Report['findings'][number]
type Issue = Finding['issue']

const { t } = useI18n()
const applications = shallowRef<Application[]>([]), applicationId = ref(''), report = shallowRef<Report | null>(null)
const loading = ref(false), error = ref('')

// Literal keys, so the message checker sees every one in use; the order is the order of the report.
const issues = {
  GRANT_ON_DISABLED: { title: 'health.grantOnDisabled', hint: 'health.grantOnDisabledHint' },
  GRANT_ON_RETIRED_API: { title: 'health.grantOnRetiredApi', hint: 'health.grantOnRetiredApiHint' },
  GRANT_EXPIRED: { title: 'health.grantExpired', hint: 'health.grantExpiredHint' },
  ACTION_WITHOUT_API: { title: 'health.actionWithoutApi', hint: 'health.actionWithoutApiHint' },
  UNUSED_API: { title: 'health.unusedApi', hint: 'health.unusedApiHint' },
  DEPENDENCY_ON_DISABLED: { title: 'health.dependencyOnDisabled', hint: 'health.dependencyOnDisabledHint' },
  DEPENDENCY_ON_RETIRED_API: { title: 'health.dependencyOnRetiredApi', hint: 'health.dependencyOnRetiredApiHint' },
} as const satisfies Record<Issue, { title: string; hint: string }>

const applicationOptions = computed(() => applications.value.map(item => ({ value: item.id, label: item.name })))
/** The findings by issue, only issues that have some. */
const groups = computed(() => {
  const found = new Map<Issue, Finding[]>()
  for (const finding of report.value?.findings ?? []) found.set(finding.issue, [...found.get(finding.issue) ?? [], finding])
  return (Object.keys(issues) as Issue[]).filter(issue => found.has(issue)).map(issue => ({ issue, findings: found.get(issue) ?? [] }))
})

async function check() {
  if (!applicationId.value) return
  loading.value = true; error.value = ''
  try {
    report.value = await request<Report>(`/api/v1/applications/${encodeURIComponent(applicationId.value)}/health`)
  } catch (reason) { report.value = null; error.value = errorMessage(reason) } finally { loading.value = false }
}
async function load() {
  loading.value = true; error.value = ''
  try {
    applications.value = await request<Application[]>('/api/v1/applications')
    applicationId.value = applications.value[0]?.id ?? ''
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
function detail(finding: Finding): string {
  if (finding.relatedCode) return t('health.needs', { code: finding.relatedCode })
  if (finding.roleCode) return t('health.grantedTo', { tenant: finding.tenantCode ?? '—', role: finding.roleCode })
  return ''
}
watch(applicationId, () => void check())
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.health')" :description="t('health.description')">
    <div class="w-52"><UiSelect v-model="applicationId" :label="t('health.application')" :options="applicationOptions" /></div>
    <UiButton variant="secondary" :loading="loading" :disabled="!applicationId" @click="check"><RefreshCw :size="15" />{{ t('health.run') }}</UiButton>
  </PageHeading>
  <p v-if="error" class="panel p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <div v-else-if="loading && !report" class="space-y-3"><div v-for="index in 3" :key="index" class="h-20 animate-pulse rounded-xl bg-line"></div></div>
  <template v-else-if="report">
    <p class="mb-4 text-xs text-muted">{{ t('health.summary', { count: report.findings.length, time: dateLabel(report.checkedAt) }) }}</p>
    <section v-if="!groups.length" class="panel flex flex-col items-center p-10 text-center">
      <CircleCheck :size="28" class="text-emerald-500" />
      <p class="mt-3 font-medium">{{ t('health.healthy') }}</p>
      <p class="mt-1 text-xs text-muted">{{ t('health.healthyHint') }}</p>
    </section>
    <div v-else class="space-y-5">
      <section v-for="group in groups" :key="group.issue" class="panel overflow-hidden" :data-issue="group.issue">
        <header class="flex flex-wrap items-baseline justify-between gap-2 border-b border-line px-5 py-4">
          <div><h2 class="text-sm font-semibold">{{ t(issues[group.issue].title) }}</h2><p class="mt-1 text-[11px] text-muted">{{ t(issues[group.issue].hint) }}</p></div>
          <span class="badge bg-amber-50 text-amber-700">{{ t('health.count', { count: group.findings.length }) }}</span>
        </header>
        <ul class="divide-y divide-line">
          <li v-for="(finding, index) in group.findings" :key="index" class="flex flex-wrap items-center justify-between gap-2 px-5 py-3 text-xs">
            <span class="font-mono">{{ finding.resourceCode }}</span>
            <span class="text-muted">{{ detail(finding) }}</span>
          </li>
        </ul>
      </section>
    </div>
  </template>
</template>
