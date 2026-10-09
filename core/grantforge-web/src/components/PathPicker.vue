<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { ArrowUp, ChevronRight, File, Folder, RotateCw } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { crumbs, ordered, parentOf, sizeLabel, type BrowseEntry, type BrowsePage } from '@/lib/browse'
import { dateLabel } from '@/lib/format'
import UiButton from '@/components/UiButton.vue'
import UiCheckbox from '@/components/UiCheckbox.vue'
import UiDialog from '@/components/UiDialog.vue'

/**
 * Picks paths of a service by browsing its directories from the level's starting directory down, page by page. Files
 * and directories can be chosen, also across directories, or the directory being looked at as a whole. A newer listing
 * cancels an older one, so a slow answer never replaces the directory the user moved to.
 */
const open = defineModel<boolean>({ required: true })
const { serviceId, resource, label } = defineProps<{ serviceId: string; resource: string; label: string }>()
const emit = defineEmits<{ pick: [values: string[]] }>()
const { t } = useI18n()

/** Entries asked for at a time. */
const PAGE_SIZE = 100

const root = ref(''), directory = ref(''), entries = shallowRef<BrowseEntry[]>([]), next = ref<string | null>(null)
const loading = ref(false), failure = ref(''), chosen = ref<string[]>([])
const shown = computed(() => ordered(entries.value))
const trail = computed(() => root.value ? crumbs(root.value, directory.value) : [])
const parent = computed(() => root.value ? parentOf(root.value, directory.value) : undefined)
let ticket = 0, pending: AbortController | undefined, last: { target: string; cursor?: string } = { target: '' }

async function load(target: string, cursor?: string) {
  pending?.abort()
  last = { target, cursor }
  const controller = new AbortController(), mine = ++ticket
  pending = controller
  loading.value = true; failure.value = ''
  if (!cursor) { entries.value = []; next.value = null }
  try {
    const page = await request<BrowsePage>(`/api/v1/services/${encodeURIComponent(serviceId)}/browse`, {
      method: 'POST', body: { resource, directory: target, cursor, pageSize: PAGE_SIZE }, signal: controller.signal })
    if (mine !== ticket) return
    root.value = page.root; directory.value = page.directory
    entries.value = cursor ? [...entries.value, ...page.entries] : page.entries
    next.value = page.nextCursor ?? null
  } catch (reason) {
    if (mine !== ticket) return
    failure.value = errorMessage(reason)
  } finally {
    if (mine === ticket) loading.value = false
  }
}
/** Asks again for what failed: the directory being opened, or the page being added. */
function retry() { void load(last.target, last.cursor) }
function toggle(value: string, on: boolean) {
  chosen.value = on ? [...chosen.value.filter(other => other !== value), value] : chosen.value.filter(other => other !== value)
}
function pick(values: string[]) {
  if (!values.length) return
  emit('pick', values)
  open.value = false
}

watch(open, value => {
  if (value) {
    chosen.value = []; root.value = ''; directory.value = ''
    void load('')
  } else {
    ticket++; pending?.abort(); pending = undefined
  }
}, { immediate: true })
</script>

<template>
  <UiDialog v-model="open" :title="t('picker.title', { level: label })" :description="t('picker.description')" wide>
    <div class="flex flex-wrap items-center gap-2">
      <button
        type="button"
        class="icon-button"
        :disabled="!parent || loading"
        :aria-label="t('picker.up')"
        :title="t('picker.up')"
        @click="parent && load(parent)"
      >
        <ArrowUp :size="16" aria-hidden="true" />
      </button>
      <nav :aria-label="t('picker.path')" class="min-w-0 flex-1">
        <ol class="flex flex-wrap items-center gap-1 font-mono text-xs">
          <li v-for="(crumb, index) in trail" :key="crumb.path" class="flex items-center gap-1">
            <ChevronRight v-if="index > 0" :size="12" class="text-muted" aria-hidden="true" />
            <button
              v-if="index < trail.length - 1"
              type="button"
              class="rounded-md px-1.5 py-0.5 text-brand hover:bg-brand-soft"
              @click="load(crumb.path)"
            >
              {{ crumb.name }}
            </button>
            <span v-else class="px-1.5 py-0.5 font-semibold" aria-current="location">{{ crumb.name }}</span>
          </li>
        </ol>
      </nav>
    </div>

    <div class="mt-4 max-h-[50vh] overflow-auto rounded-lg border border-line" :aria-busy="loading">
      <table class="w-full min-w-[560px] text-left text-xs">
        <thead class="sticky top-0 bg-surface">
          <tr class="border-b border-line text-[11px] text-muted">
            <th class="px-3 py-2">{{ t('picker.name') }}</th>
            <th class="px-3 py-2">{{ t('picker.owner') }}</th>
            <th class="px-3 py-2">{{ t('picker.permission') }}</th>
            <th class="px-3 py-2 text-right">{{ t('picker.size') }}</th>
            <th class="px-3 py-2">{{ t('picker.modified') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in shown" :key="entry.value" class="border-b border-line last:border-0" :data-entry="entry.value">
            <td class="px-3 py-1.5">
              <div class="flex items-center gap-2">
                <UiCheckbox
                  :checked="chosen.includes(entry.value)"
                  :label="entry.name"
                  @update:checked="toggle(entry.value, $event)"
                />
                <Folder v-if="entry.directory" :size="14" class="shrink-0 text-amber-500" aria-hidden="true" />
                <File v-else :size="14" class="shrink-0 text-muted" aria-hidden="true" />
                <button
                  v-if="entry.directory"
                  type="button"
                  class="ml-auto rounded-md p-1 text-muted hover:bg-canvas hover:text-ink"
                  :aria-label="t('picker.open', { name: entry.name })"
                  :title="t('picker.open', { name: entry.name })"
                  @click="load(entry.value)"
                >
                  <ChevronRight :size="14" aria-hidden="true" />
                </button>
              </div>
            </td>
            <td class="whitespace-nowrap px-3 py-1.5 text-muted">{{ [entry.owner, entry.group].filter(Boolean).join(' : ') || '—' }}</td>
            <td class="px-3 py-1.5 font-mono text-[11px] text-muted">{{ entry.permission ?? '—' }}</td>
            <td class="whitespace-nowrap px-3 py-1.5 text-right text-muted">{{ entry.directory ? '—' : sizeLabel(entry.size) }}</td>
            <td class="whitespace-nowrap px-3 py-1.5 text-muted">{{ dateLabel(entry.modifiedAt ?? undefined) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="loading" class="px-3 py-3 text-xs text-muted" role="status">{{ t('picker.loading') }}</p>
      <p v-else-if="!failure && !shown.length" class="px-3 py-6 text-center text-xs text-muted" role="status">{{ t('picker.empty') }}</p>
      <div v-if="failure" class="flex items-center gap-2 px-3 py-3 text-xs text-rose-600" role="alert">
        <span class="min-w-0 flex-1 break-words">{{ t('picker.failed', { reason: failure }) }}</span>
        <button type="button" class="inline-flex items-center gap-1 rounded-lg px-2 py-1 font-medium text-brand hover:bg-brand-soft" @click="retry">
          <RotateCw :size="12" aria-hidden="true" />{{ t('picker.retry') }}
        </button>
      </div>
      <div v-if="next && !loading && !failure" class="border-t border-line p-2 text-center">
        <UiButton variant="ghost" @click="load(directory, next)">{{ t('picker.loadMore') }}</UiButton>
      </div>
    </div>

    <template #footer>
      <span class="mr-auto text-[11px] text-muted">{{ t('picker.chosen', { count: chosen.length }) }}</span>
      <UiButton variant="secondary" @click="open = false">{{ t('picker.cancel') }}</UiButton>
      <UiButton variant="secondary" :disabled="!directory || loading" @click="pick([directory])">{{ t('picker.pickDirectory') }}</UiButton>
      <UiButton :disabled="!chosen.length" @click="pick(chosen)">{{ t('picker.pickChosen', { count: chosen.length }) }}</UiButton>
    </template>
  </UiDialog>
</template>
