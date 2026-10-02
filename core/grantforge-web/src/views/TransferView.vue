<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { reactive, useTemplateRef } from 'vue'
import { CheckCircle2, Download, FileSpreadsheet, FileUp, Network, UsersRound } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { download, errorMessage, request } from '@/lib/api'
import { vPermission } from '@/lib/permission'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'

type Report = components['schemas']['ImportReportResponse']
type Kind = 'users' | 'units'
interface Column { name: string; required: boolean }
interface State { file: File | null; report: Report | null; busy: boolean; error: string }

const { t } = useI18n(), toast = useToast()
const kinds: { kind: Kind; title: 'transfer.users' | 'transfer.units'; hint: 'transfer.usersHint' | 'transfer.unitsHint'; icon: typeof UsersRound;
  base: string; exportCode: string; importCode: string; columns: Column[] }[] = [
  { kind: 'users', title: 'transfer.users', hint: 'transfer.usersHint', icon: UsersRound, base: '/api/v1/users',
    exportCode: 'system.transfer.btn.export-users', importCode: 'system.transfer.btn.import-users', columns: [
    { name: 'username', required: true }, { name: 'password', required: true }, { name: 'displayName', required: false },
    { name: 'email', required: false }, { name: 'primaryUnit', required: false }, { name: 'otherUnits', required: false },
    { name: 'positions', required: false }] },
  { kind: 'units', title: 'transfer.units', hint: 'transfer.unitsHint', icon: Network, base: '/api/v1/org-units',
    exportCode: 'system.transfer.btn.export-org', importCode: 'system.transfer.btn.import-org', columns: [
    { name: 'code', required: true }, { name: 'name', required: true }, { name: 'parentCode', required: false },
    { name: 'sortOrder', required: false }] },
]
const states = reactive<Record<Kind, State>>({
  users: { file: null, report: null, busy: false, error: '' },
  units: { file: null, report: null, busy: false, error: '' },
})
const pickers = useTemplateRef<HTMLInputElement[]>('pickers')

function save(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob), link = document.createElement('a')
  link.href = url; link.download = filename
  document.body.append(link); link.click(); link.remove()
  URL.revokeObjectURL(url)
}
async function exportFile(base: string) {
  try {
    const file = await download(`${base}/export`)
    save(file.blob, file.filename); toast.show(t('transfer.exported'))
  } catch (reason) { toast.show(errorMessage(reason), 'error') }
}
/** A template is just the header row, with the byte order mark spreadsheets need to read UTF-8. */
function template(kind: Kind, columns: Column[]) {
  save(new Blob([`\uFEFF${columns.map(column => column.name).join(',')}\r\n`], { type: 'text/csv;charset=utf-8' }), `${kind}-template.csv`)
}
function choose(index: number) { pickers.value?.[index]?.click() }
function chosen(kind: Kind, event: Event) {
  const input = event.target as HTMLInputElement
  Object.assign(states[kind], { file: input.files?.[0] ?? null, report: null, error: '' })
  input.value = ''
}
async function send(kind: Kind, base: string, apply: boolean) {
  const state = states[kind], file = state.file
  if (!file || state.busy) return
  state.busy = true; state.error = ''
  try {
    const body = new FormData()
    body.append('file', file)
    state.report = await request<Report>(`${base}/import`, { method: 'POST', body, query: { apply: String(apply) } })
    if (state.report.applied) toast.show(t('transfer.applied', { created: state.report.created }))
  } catch (reason) { state.report = null; state.error = errorMessage(reason) } finally { state.busy = false }
}
function step(state: State) { return state.report?.applied ? 3 : state.report && !state.report.problems.length ? 2 : state.file ? 1 : 0 }
</script>
<template>
  <PageHeading :title="t('titles.transfer')" :description="t('transfer.description')" />
  <div class="grid gap-6 xl:grid-cols-2">
    <section v-for="(item, index) in kinds" :key="item.kind" class="panel flex flex-col p-6" :aria-labelledby="`transfer-${item.kind}`">
      <header class="flex flex-wrap items-start justify-between gap-3">
        <div class="flex items-center gap-3"><span class="flex size-9 items-center justify-center rounded-xl bg-brand-soft text-brand"><component :is="item.icon" :size="18" /></span><h2 :id="`transfer-${item.kind}`" class="text-sm font-semibold">{{ t(item.title) }}</h2></div>
        <div class="flex gap-2"><UiButton variant="secondary" @click="template(item.kind, item.columns)"><FileSpreadsheet :size="15" />{{ t('transfer.template') }}</UiButton><UiButton v-permission="item.exportCode" variant="secondary" @click="exportFile(item.base)"><Download :size="15" />{{ t('transfer.export') }}</UiButton></div>
      </header>
      <p class="mt-4 text-xs leading-6 text-muted">{{ t(item.hint) }}</p>
      <div class="mt-4"><p class="field-label">{{ t('transfer.columns') }}</p><div class="flex flex-wrap gap-1.5"><span v-for="column in item.columns" :key="column.name" class="badge font-mono" :class="column.required ? 'bg-brand-soft text-brand' : 'bg-canvas text-muted'">{{ column.name }} · {{ column.required ? t('transfer.required') : t('transfer.optional') }}</span></div></div>
      <ol v-permission="item.importCode" class="mt-6 flex items-center gap-2 text-[11px]">
        <li v-for="(label, position) in [t('transfer.stepChoose'), t('transfer.stepCheck'), t('transfer.stepApply')]" :key="label" class="flex items-center gap-2" :aria-current="step(states[item.kind]) === position ? 'step' : undefined">
          <span class="flex size-5 items-center justify-center rounded-full text-[10px] font-semibold" :class="step(states[item.kind]) > position ? 'bg-emerald-500 text-white' : step(states[item.kind]) === position ? 'bg-brand text-white' : 'bg-canvas text-muted'">{{ position + 1 }}</span><span :class="step(states[item.kind]) === position ? 'font-medium' : 'text-muted'">{{ label }}</span><span v-if="position < 2" class="h-px w-6 bg-line"></span>
        </li>
      </ol>
      <div v-permission="item.importCode" class="mt-4 flex flex-wrap items-center gap-3">
        <input
          ref="pickers"
          type="file"
          accept=".csv,text/csv"
          class="sr-only"
          :aria-label="t('transfer.chooseFileNamed', { kind: t(item.title) })"
          @change="chosen(item.kind, $event)"
        />
        <UiButton variant="secondary" :disabled="states[item.kind].busy" @click="choose(index)"><FileUp :size="15" />{{ t('transfer.chooseFile') }}</UiButton>
        <span class="text-xs" :class="states[item.kind].file ? '' : 'text-muted'">{{ states[item.kind].file?.name ?? t('transfer.noFile') }}</span>
        <UiButton class="ml-auto" :disabled="!states[item.kind].file" :loading="states[item.kind].busy" @click="send(item.kind, item.base, false)">{{ t('transfer.check') }}</UiButton>
      </div>
      <p v-if="states[item.kind].error" class="mt-4 rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ states[item.kind].error }}</p>
      <template v-else-if="states[item.kind].report">
        <p v-if="states[item.kind].report?.applied" class="mt-4 flex items-center gap-2 rounded-lg bg-emerald-50 p-3 text-xs text-emerald-800" role="status"><CheckCircle2 :size="16" />{{ t('transfer.applied', { created: states[item.kind].report?.created ?? 0 }) }}</p>
        <div v-else-if="!states[item.kind].report?.problems.length" class="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-lg bg-emerald-50 p-3 text-xs text-emerald-800" role="status"><span>{{ t('transfer.passed', { rows: states[item.kind].report?.rows ?? 0 }) }}</span><UiButton :loading="states[item.kind].busy" @click="send(item.kind, item.base, true)">{{ t('transfer.apply', { count: states[item.kind].report?.rows ?? 0 }) }}</UiButton></div>
        <div v-else class="mt-4">
          <p class="mb-2 text-xs text-rose-700" role="alert">{{ t('transfer.failed', { count: states[item.kind].report?.problems.length ?? 0 }) }}</p>
          <div class="max-h-72 overflow-auto rounded-xl border border-line">
            <table class="w-full text-left text-xs">
              <thead class="bg-canvas/60"><tr><th scope="col" class="px-3 py-2 font-semibold text-muted">{{ t('transfer.columnRow') }}</th><th scope="col" class="px-3 py-2 font-semibold text-muted">{{ t('transfer.columnColumn') }}</th><th scope="col" class="px-3 py-2 font-semibold text-muted">{{ t('transfer.columnProblem') }}</th></tr></thead>
              <tbody><tr v-for="(problem, position) in states[item.kind].report?.problems" :key="position" class="border-t border-line"><td class="px-3 py-2 font-mono">{{ problem.row }}</td><td class="px-3 py-2 font-mono">{{ problem.column ?? '—' }}</td><td class="px-3 py-2">{{ problem.message }}</td></tr></tbody>
            </table>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>
