<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, useTemplateRef } from 'vue'
import { Braces, WandSparkles, Minimize2, Copy, Download, Trash2, CheckCircle2, ShieldCheck, ArrowRight } from '@lucide/vue'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import { useI18n } from 'vue-i18n'
import { useToast } from '@/stores/toast'
const input = ref(''), output = ref(''), error = ref(''), formatted = ref(false)
const editor = useTemplateRef<HTMLTextAreaElement>('editor')
const toast = useToast(), { t } = useI18n()
const bytes = computed(() => new Blob([input.value]).size)
const lines = computed(() => input.value ? input.value.split('\n').length : 0)
function format(compact = false) {
  error.value = ''
  try { output.value = JSON.stringify(JSON.parse(input.value) as unknown, null, compact ? undefined : 2); formatted.value = true }
  catch (reason) { error.value = reason instanceof Error ? reason.message : t('json.invalid'); output.value = ''; formatted.value = false }
}
function sample() {
  input.value = JSON.stringify({ project: 'GrantForge', access: { user: 'alex', roles: ['developer'], resources: ['project:read', 'project:write'] }, enabled: true }, null, 2)
  output.value = ''; error.value = ''; formatted.value = false; editor.value?.focus()
}
async function copy() { try { await navigator.clipboard.writeText(output.value); toast.show(t('json.copied')) } catch { toast.show(t('json.copyFailed'), 'error') } }
function download() {
  const url = URL.createObjectURL(new Blob([output.value], { type: 'application/json' }))
  const link = document.createElement('a'); link.href = url; link.download = 'grantforge.json'; link.click(); URL.revokeObjectURL(url)
}
function clear() { input.value = ''; output.value = ''; error.value = ''; formatted.value = false; editor.value?.focus() }
</script>
<template>
  <PageHeading :title="t('titles.json')" :description="t('json.description')"><UiButton variant="secondary" @click="sample"><Braces :size="15" />{{ t('json.sample') }}</UiButton></PageHeading>
  <div class="mb-5 flex flex-wrap items-center justify-between gap-3"><div class="flex flex-wrap gap-2"><UiButton :disabled="!input.trim()" @click="format()"><WandSparkles :size="15" />{{ t('json.format') }}</UiButton><UiButton variant="secondary" :disabled="!input.trim()" @click="format(true)"><Minimize2 :size="15" />{{ t('json.compact') }}</UiButton><UiButton variant="ghost" :disabled="!input" @click="clear"><Trash2 :size="15" />{{ t('json.clear') }}</UiButton></div><span class="flex items-center gap-1.5 text-[11px] text-muted"><ShieldCheck :size="14" class="text-emerald-500" />{{ t('json.localOnly') }}</span></div>
  <div class="grid gap-5 xl:grid-cols-2">
    <section class="panel overflow-hidden">
      <header class="flex items-center justify-between border-b border-line px-5 py-4"><div class="flex items-center gap-2"><span class="size-2 rounded-full bg-amber-400"></span><h2 class="text-xs font-semibold">{{ t('json.input') }}</h2></div><span class="font-mono text-[10px] text-muted">{{ t('json.stats', { lines, bytes }) }}</span></header><textarea
        ref="editor"
        v-model="input"
        :aria-label="t('json.inputLabel')"
        :placeholder="t('json.inputPlaceholder')"
        spellcheck="false"
        class="min-h-[440px] w-full resize-y border-0 bg-transparent p-5 font-mono text-[12px] leading-7 text-ink outline-none"
      ></textarea>
    </section>
    <section class="panel overflow-hidden">
      <header class="flex items-center justify-between border-b border-line px-5 py-3">
        <div class="flex items-center gap-2"><span class="size-2 rounded-full" :class="formatted ? 'bg-emerald-400' : 'bg-slate-300'"></span><h2 class="text-xs font-semibold">{{ t('json.output') }}</h2><span v-if="formatted" class="ml-1 text-[10px] text-emerald-600">{{ t('json.valid') }}</span></div><div class="flex gap-1">
          <button
            type="button"
            class="icon-button"
            :aria-label="t('json.copy')"
            :disabled="!output"
            @click="copy"
          >
            <Copy :size="15" />
          </button><button
            type="button"
            class="icon-button"
            :aria-label="t('json.download')"
            :disabled="!output"
            @click="download"
          >
            <Download :size="15" />
          </button>
        </div>
      </header><textarea
        v-if="output"
        :value="output"
        :aria-label="t('json.outputLabel')"
        readonly
        spellcheck="false"
        class="min-h-[440px] w-full resize-y border-0 bg-transparent p-5 font-mono text-[12px] leading-7 text-ink outline-none"
      ></textarea><div v-else class="flex min-h-[440px] flex-col items-center justify-center p-8 text-center"><span class="mb-4 flex size-14 items-center justify-center rounded-2xl bg-canvas text-muted"><Braces :size="28" :stroke-width="1.4" /></span><p class="text-sm font-medium">{{ t('json.emptyTitle') }}</p><p class="mt-2 text-xs leading-6 text-muted">{{ t('json.emptyDescription') }}</p><span class="mt-5 flex items-center gap-2 text-[10px] text-brand">{{ t('json.stepInput') }} <ArrowRight :size="12" />{{ t('json.stepValidate') }} <ArrowRight :size="12" />{{ t('json.stepOutput') }}</span></div>
    </section>
  </div>
  <p v-if="error" class="mt-5 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 font-mono text-xs leading-6 text-rose-700" role="alert">{{ error }}</p><p v-else-if="formatted" class="mt-5 flex items-center gap-2 text-xs text-emerald-600"><CheckCircle2 :size="15" />{{ t('json.passed') }}</p>
</template>
