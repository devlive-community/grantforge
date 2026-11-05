<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { ChevronDown, ChevronRight, Search } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { displayName, resourceTypeKeys, type Resource } from '@/lib/catalog'
import { grantable, MAX_GRANT_CHANGES, useGrantTree } from '@/lib/useGrantTree'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import UiButton from './UiButton.vue'
import UiDialog from './UiDialog.vue'
import UiSelect from './UiSelect.vue'

const open = defineModel<boolean>({ required: true })
const props = defineProps<{ accountId: string; accountName: string }>()
const { t } = useI18n(), toast = useToast()
type Application = components['schemas']['ApplicationResponse']
type DirectGrant = { resourceId: string; expiresAt: string | null }
const applications = shallowRef<Application[]>([]), applicationId = ref(''), resources = shallowRef<Resource[]>([])
const grants = shallowRef<DirectGrant[]>([]), inherited = shallowRef(new Set<string>()), pending = ref(new Map<string, { allowed: boolean; expiresAt: string }>())
const query = ref(''), onlyDirect = ref(false), loading = ref(false), saving = ref(false), confirming = ref(false), error = ref('')
const options = computed(() => applications.value.map(app => ({ value: app.id, label: app.name })))
const stored = computed(() => new Map(grants.value.map(grant => [grant.resourceId, grant])))
const selected = (id: string) => pending.value.get(id)?.allowed ?? stored.value.has(id)
const { children, collapsed, moduleResources, rows: treeRows, toggleRoot, reset: resetTree } = useGrantTree(resources, query)
const rows = computed(() => treeRows.value.filter(item => !onlyDirect.value || selected(item.id)
  || (!item.parentId && (moduleResources.value.get(item.id) ?? []).some(resource => selected(resource.id)))))
function localDate(value: string | null | undefined) {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}
const expiry = (id: string) => pending.value.get(id)?.expiresAt ?? localDate(stored.value.get(id)?.expiresAt)
function changeMany(ids: string[], allowed: boolean) {
  const next = new Map(pending.value)
  for (const id of ids) {
    if (inherited.value.has(id)) continue
    const expiresAt = expiry(id), original = stored.value.get(id)
    if (allowed === !!original && (!allowed || expiresAt === localDate(original?.expiresAt))) next.delete(id)
    else next.set(id, { allowed, expiresAt })
  }
  if (next.size > MAX_GRANT_CHANGES) { error.value = t('grants.bulkLimit'); return }
  pending.value = next; confirming.value = false; error.value = ''
}
function change(id: string, allowed: boolean, expiresAt: string) {
  const next = new Map(pending.value), original = stored.value.get(id)
  if (allowed === !!original && (!allowed || expiresAt === localDate(original?.expiresAt))) next.delete(id)
  else next.set(id, { allowed, expiresAt })
  if (next.size > MAX_GRANT_CHANGES) { error.value = t('grants.bulkLimit'); return }
  pending.value = next; confirming.value = false; error.value = ''
}
async function load() {
  if (!applicationId.value) { resources.value = []; grants.value = []; inherited.value = new Set(); return }
  loading.value = true; error.value = ''
  try {
    const id = applicationId.value
    const [tree, direct, fromRoles] = await Promise.all([
      request<Resource[]>(`/api/v1/applications/${encodeURIComponent(id)}/resources`),
      request<DirectGrant[]>(`/api/v1/users/${encodeURIComponent(props.accountId)}/resource-grants`, { query: { applicationId: id } }),
      request<string[]>(`/api/v1/users/${encodeURIComponent(props.accountId)}/resource-grants/role-resources`, { query: { applicationId: id } }),
    ])
    if (id !== applicationId.value) return
    resources.value = tree; grants.value = direct; inherited.value = new Set(fromRoles); pending.value = new Map(); confirming.value = false
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
watch(open, async visible => {
  if (!visible) return
  error.value = ''; query.value = ''; onlyDirect.value = false; pending.value = new Map(); resetTree()
  try {
    applications.value = (await request<Application[]>('/api/v1/applications')).filter(app => !app.builtin)
    applicationId.value = applications.value[0]?.id ?? ''
    await load()
  } catch (reason) { error.value = errorMessage(reason) }
}, { immediate: true })
watch(applicationId, (value, old) => { if (old && value !== old && open.value) { resetTree(); void load() } })
async function save() {
  if (saving.value || !pending.value.size) return
  saving.value = true; error.value = ''
  try {
    const changes = [...pending.value].map(([resourceId, value]) => ({ resourceId, effect: value.allowed ? 'ALLOW' : null,
      expiresAt: value.allowed && value.expiresAt ? new Date(value.expiresAt).toISOString() : null }))
    grants.value = await request<DirectGrant[]>(`/api/v1/users/${encodeURIComponent(props.accountId)}/resource-grants`, {
      method: 'PUT', body: { applicationId: applicationId.value, changes },
    })
    pending.value = new Map(); confirming.value = false; toast.show(t('grants.saved')); open.value = false
  } catch (reason) { error.value = errorMessage(reason) } finally { saving.value = false }
}
</script>

<template>
  <UiDialog
    v-model="open"
    :title="t('directGrants.title', { name: accountName })"
    :description="t('directGrants.description')"
    :busy="saving"
    wide
  >
    <div class="mb-4 flex flex-wrap items-end gap-3">
      <div class="w-56"><UiSelect v-model="applicationId" :label="t('grants.application')" :options="options" :disabled="loading || saving || pending.size > 0" /></div>
      <div class="flex min-w-48 flex-1 items-center gap-2 rounded-xl border border-line bg-canvas/40 px-3"><Search :size="15" class="text-muted" /><input v-model="query" :aria-label="t('grants.search')" :placeholder="t('grants.searchPlaceholder')" class="w-full bg-transparent py-2.5 text-xs outline-none" /></div>
      <label class="flex items-center gap-2 text-xs"><input v-model="onlyDirect" type="checkbox" />{{ t('directGrants.onlyDirect') }}</label>
    </div>
    <p class="mb-3 text-[11px] text-muted">{{ t('grants.bulkHint') }}</p>
    <p v-if="error" class="mb-3 text-xs text-rose-600" role="alert">{{ error }}</p>
    <p v-if="!applications.length && !loading" class="py-8 text-center text-xs text-muted">{{ t('directGrants.noApps') }}</p>
    <div v-else-if="loading" class="h-40 animate-pulse rounded-lg bg-line"></div>
    <div v-else class="max-h-[50vh] overflow-auto rounded-lg border border-line">
      <table class="w-full min-w-[640px] text-left text-xs">
        <thead class="sticky top-0 bg-surface"><tr class="border-b border-line text-muted"><th class="px-3 py-2">{{ t('grants.columnResource') }}</th><th class="px-3 py-2">{{ t('directGrants.allow') }}</th><th class="px-3 py-2">{{ t('directGrants.expires') }}</th></tr></thead>
        <tbody>
          <tr
            v-for="item in rows"
            :key="item.id"
            class="border-b border-line"
            :data-resource="item.code"
            :class="pending.has(item.id) ? 'bg-amber-50/60' : !item.parentId ? 'bg-sky-50/70 dark:bg-sky-900/20' : ''"
          >
            <td class="px-3 py-2" :style="{ paddingLeft: `${12 + item.depth * 18}px` }">
              <button
                v-if="!item.parentId && children.get(item.id)?.length"
                type="button"
                class="mr-1 inline-flex rounded p-1 text-muted hover:text-current disabled:opacity-50"
                :aria-label="t(collapsed.has(item.id) && !query.trim() ? 'tree.expand' : 'tree.collapse', { name: displayName(item) })"
                :aria-expanded="!collapsed.has(item.id) || !!query.trim()"
                :disabled="!!query.trim()"
                @click="toggleRoot(item.id)"
              >
                <ChevronRight v-if="collapsed.has(item.id) && !query.trim()" :size="14" />
                <ChevronDown v-else :size="14" />
              </button>
              <span class="badge mr-2">{{ t(resourceTypeKeys[item.type]) }}</span>{{ displayName(item) }} <span class="font-mono text-[10px] text-muted">{{ item.code }}</span>
            </td>
            <td class="px-3 py-2">
              <input
                v-if="grantable.has(item.type)"
                type="checkbox"
                :checked="inherited.has(item.id) || selected(item.id)"
                :disabled="saving || inherited.has(item.id)"
                :aria-label="t('directGrants.allowNamed', { name: displayName(item) })"
                @change="change(item.id, ($event.target as HTMLInputElement).checked, expiry(item.id))"
              />
              <span v-if="inherited.has(item.id) && grantable.has(item.type)" class="ml-2 text-[10px] text-muted">{{ t(selected(item.id) ? 'directGrants.roleAndDirect' : 'directGrants.fromRole') }}</span>
              <div
                v-if="item.type === 'MODULE' && !item.parentId && moduleResources.get(item.id)?.length"
                class="inline-flex rounded-lg border border-line p-0.5"
                role="group"
                :aria-label="t('grants.bulkOf', { name: displayName(item) })"
              >
                <button
                  type="button"
                  class="whitespace-nowrap rounded-md px-2 py-1 text-[11px] text-muted hover:text-current disabled:opacity-50"
                  :disabled="saving"
                  :aria-label="t('grants.bulkClearNamed', { name: displayName(item) })"
                  @click="changeMany((moduleResources.get(item.id) ?? []).map(resource => resource.id), false)"
                >
                  {{ t('grants.bulkClear') }}
                </button>
                <button
                  type="button"
                  class="whitespace-nowrap rounded-md px-2 py-1 text-[11px] text-muted hover:text-current disabled:opacity-50"
                  :disabled="saving"
                  :aria-label="t('grants.bulkAllowNamed', { name: displayName(item) })"
                  @click="changeMany((moduleResources.get(item.id) ?? []).map(resource => resource.id), true)"
                >
                  {{ t('grants.bulkAllow') }}
                </button>
              </div>
            </td>
            <td class="px-3 py-2">
              <input
                v-if="grantable.has(item.type) && selected(item.id)"
                type="datetime-local"
                class="rounded-lg border border-line bg-surface px-2 py-1"
                :value="expiry(item.id)"
                :disabled="saving"
                :aria-label="t('directGrants.expiresNamed', { name: displayName(item) })"
                @change="change(item.id, true, ($event.target as HTMLInputElement).value)"
              />
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!rows.length" class="p-6 text-center text-xs text-muted">{{ t('directGrants.noResources') }}</p>
    </div>
    <p v-if="confirming" class="mt-4 rounded-lg bg-amber-50 p-3 text-xs text-amber-800">{{ t('directGrants.confirm', { count: pending.size }) }}</p>
    <template #footer>
      <span class="mr-auto text-xs text-muted">{{ t('grants.pending', { count: pending.size }) }}</span>
      <UiButton variant="secondary" :disabled="saving || !pending.size" @click="load">{{ t('grants.reset') }}</UiButton>
      <UiButton v-if="confirming" :loading="saving" @click="save">{{ t('grants.confirmSave') }}</UiButton>
      <UiButton v-else :disabled="!pending.size" @click="confirming = true">{{ t('grants.save') }}</UiButton>
    </template>
  </UiDialog>
</template>
