<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { Copy, KeyRound, Pencil, Plus, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import type { components } from '@/api/schema'
import { ApiError, errorMessage, request } from '@/lib/api'
import { dateLabel } from '@/lib/format'
import { useToast } from '@/stores/toast'
import UiButton from '@/components/UiButton.vue'
import UiCheckbox from '@/components/UiCheckbox.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiSwitch from '@/components/UiSwitch.vue'
import UiTagInput from '@/components/UiTagInput.vue'

type Client = components['schemas']['ClientResponse']
type Issued = components['schemas']['IssuedClientResponse']
type Grant = Client['grants'][number]
type ClientType = Client['type']
type Mode = 'list' | 'create' | 'edit' | 'issued' | 'rotate' | 'delete'

const open = defineModel<boolean>({ required: true })
const { applicationId, applicationName } = defineProps<{ applicationId: string; applicationName: string }>()
const { t } = useI18n(), toast = useToast()

const grants: Grant[] = ['AUTHORIZATION_CODE', 'REFRESH_TOKEN', 'CLIENT_CREDENTIALS']
const scopes = ['openid', 'profile', 'email', 'permissions']
// Literal keys, so the message checker sees every one in use.
const grantKeys = { AUTHORIZATION_CODE: 'clients.grantCode', REFRESH_TOKEN: 'clients.grantRefresh', CLIENT_CREDENTIALS: 'clients.grantCredentials' } as const
const typeKeys = { CONFIDENTIAL: 'clients.confidential', PUBLIC: 'clients.public' } as const

const clients = shallowRef<Client[]>([]), loading = ref(false), error = ref(''), busy = ref(false)
const mode = ref<Mode>('list'), current = shallowRef<Client | null>(null), issued = shallowRef<Issued | null>(null)
const form = ref(blank()), graceHours = ref('24'), fieldErrors = ref<Record<string, string>>({}), formError = ref('')

const typeOptions = computed(() => (['CONFIDENTIAL', 'PUBLIC'] as const).map(value => ({ value, label: t(typeKeys[value]) })))
const title = computed(() => {
  if (mode.value === 'create') return t('clients.createTitle')
  if (mode.value === 'edit') return t('clients.editTitle', { name: current.value?.name ?? '' })
  if (mode.value === 'issued') return t('clients.issuedTitle')
  if (mode.value === 'rotate') return t('clients.rotateTitle', { name: current.value?.name ?? '' })
  if (mode.value === 'delete') return t('clients.deleteTitle')
  return t('clients.title', { name: applicationName })
})

function blank() {
  return { name: '', type: 'CONFIDENTIAL' as ClientType, redirectUris: [] as string[], scopes: ['openid'], grants: ['AUTHORIZATION_CODE', 'REFRESH_TOKEN'] as Grant[],
    accessTokenMinutes: '15', refreshTokenHours: '720', enabled: true }
}
async function load() {
  loading.value = true; error.value = ''
  try { clients.value = await request<Client[]>(`/api/v1/applications/${encodeURIComponent(applicationId)}/clients`) } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
// The secret is shown once: closing the dialog drops it.
watch(open, value => { mode.value = 'list'; issued.value = null; if (value) void load() }, { immediate: true })

function reset(next: Mode, client: Client | null = null) { mode.value = next; current.value = client; fieldErrors.value = {}; formError.value = '' }
function openCreate() { form.value = blank(); reset('create') }
function openEdit(client: Client) {
  form.value = { name: client.name, type: client.type, redirectUris: [...client.redirectUris], scopes: [...client.scopes], grants: [...client.grants],
    accessTokenMinutes: String(client.accessTokenMinutes), refreshTokenHours: String(client.refreshTokenHours), enabled: client.enabled }
  reset('edit', client)
}
function openRotate(client: Client) { graceHours.value = '24'; reset('rotate', client) }
function toggle<T>(list: T[], value: T, on: boolean) { return on ? [...list.filter(item => item !== value), value] : list.filter(item => item !== value) }

/** Field issues of nested lists and of the create request's settings belong to the field that shows them. */
function fieldOf(path: string) { return path.replace(/^settings\./, '').replace(/\[\d+\]$/, '') }
function fail(reason: unknown) {
  const problems = reason instanceof ApiError ? reason.problem?.errors ?? [] : []
  fieldErrors.value = Object.fromEntries(problems.map(problem => [fieldOf(problem.field), problem.message]))
  formError.value = errorMessage(reason)
}
async function act(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; fieldErrors.value = {}; formError.value = ''
  try { await action() } catch (reason) { fail(reason) } finally { busy.value = false }
}
function settings() {
  const value = form.value
  return { name: value.name.trim(), redirectUris: value.redirectUris, scopes: value.scopes, grants: value.grants,
    accessTokenMinutes: Number(value.accessTokenMinutes), refreshTokenHours: Number(value.refreshTokenHours), enabled: value.enabled }
}
function save() {
  void act(async () => {
    if (mode.value === 'create') {
      issued.value = await request<Issued>(`/api/v1/applications/${encodeURIComponent(applicationId)}/clients`, { method: 'POST', body: { type: form.value.type, settings: settings() } })
      mode.value = 'issued'; toast.show(t('clients.created'))
    } else if (current.value) {
      await request<Client>(`/api/v1/clients/${encodeURIComponent(current.value.id)}`, { method: 'PUT', body: settings() })
      mode.value = 'list'; toast.show(t('clients.updated'))
    }
    await load()
  })
}
function rotate() {
  const client = current.value
  if (!client) return
  void act(async () => {
    issued.value = await request<Issued>(`/api/v1/clients/${encodeURIComponent(client.id)}/rotate-secret`, { method: 'POST', body: { graceHours: Number(graceHours.value) } })
    mode.value = 'issued'; toast.show(t('clients.rotated')); await load()
  })
}
function remove() {
  const client = current.value
  if (!client) return
  void act(async () => {
    await request(`/api/v1/clients/${encodeURIComponent(client.id)}`, { method: 'DELETE' })
    mode.value = 'list'; toast.show(t('clients.deleted')); await load()
  })
}
async function copy(text: string) {
  try { await navigator.clipboard.writeText(text); toast.show(t('clients.copied')) } catch { toast.show(t('clients.copyFailed'), 'error') }
}
function back() { issued.value = null; reset('list') }
</script>
<template>
  <UiDialog
    v-model="open"
    :title="title"
    :description="mode === 'list' ? t('clients.description') : ''"
    :busy="busy"
    wide
  >
    <template v-if="mode === 'list'">
      <p v-if="error" class="py-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
      <p v-else-if="loading && !clients.length" class="py-6 text-center text-xs text-muted">{{ t('clients.loading') }}</p>
      <p v-else-if="!clients.length" class="py-6 text-center text-xs text-muted">{{ t('clients.empty') }}</p>
      <ul v-else class="space-y-2">
        <li v-for="client in clients" :key="client.id" class="flex flex-wrap items-center gap-3 rounded-xl bg-canvas/60 px-3 py-2.5" :data-client="client.name">
          <div class="min-w-0 flex-1">
            <p class="text-xs font-medium">
              {{ client.name }}
              <span class="badge ml-1 bg-brand-soft text-brand">{{ t(typeKeys[client.type]) }}</span>
              <span v-if="!client.enabled" class="badge ml-1 bg-rose-50 text-rose-700">{{ t('clients.disabled') }}</span>
            </p>
            <p class="mt-1 flex items-center gap-1 font-mono text-[11px] text-muted">
              {{ client.clientId }}
              <button type="button" class="icon-button size-6" :aria-label="t('clients.copyId', { name: client.name })" @click="copy(client.clientId)"><Copy :size="12" /></button>
            </p>
            <p class="mt-0.5 text-[11px] text-muted">
              {{ client.grants.map(grant => t(grantKeys[grant])).join(' · ') }}
              <template v-if="client.previousSecretExpiresAt"> · {{ t('clients.graceUntil', { time: dateLabel(client.previousSecretExpiresAt) }) }}</template>
            </p>
          </div>
          <button type="button" class="table-action" :aria-label="t('clients.edit', { name: client.name })" @click="openEdit(client)"><Pencil :size="14" /></button>
          <button
            v-if="client.type === 'CONFIDENTIAL'"
            type="button"
            class="table-action"
            :aria-label="t('clients.rotate', { name: client.name })"
            @click="openRotate(client)"
          >
            <KeyRound :size="14" />
          </button>
          <button type="button" class="table-action hover:text-rose-600" :aria-label="t('clients.delete', { name: client.name })" @click="reset('delete', client)"><Trash2 :size="14" /></button>
        </li>
      </ul>
    </template>

    <form
      v-else-if="mode === 'create' || mode === 'edit'"
      id="client-form"
      class="space-y-4"
      novalidate
      @submit.prevent="save"
    >
      <UiField v-model="form.name" :label="t('clients.name')" :error="fieldErrors.name" required />
      <UiSelect v-if="mode === 'create'" v-model="form.type" :label="t('clients.type')" :options="typeOptions" />
      <p v-if="mode === 'create'" class="-mt-2 text-[11px] text-muted">{{ form.type === 'CONFIDENTIAL' ? t('clients.confidentialHint') : t('clients.publicHint') }}</p>
      <fieldset>
        <legend class="mb-2 text-xs font-medium">{{ t('clients.grants') }}</legend>
        <div class="flex flex-wrap gap-4">
          <UiCheckbox
            v-for="grant in grants"
            :key="grant"
            :checked="form.grants.includes(grant)"
            :label="t(grantKeys[grant])"
            @update:checked="on => form.grants = toggle(form.grants, grant, on)"
          />
        </div>
        <p v-if="fieldErrors.grants" class="mt-1 text-[11px] text-rose-600">{{ fieldErrors.grants }}</p>
      </fieldset>
      <UiTagInput
        v-model="form.redirectUris"
        :label="t('clients.redirectUris')"
        :placeholder="t('clients.redirectPlaceholder')"
        :hint="t('clients.redirectHint')"
        :error="fieldErrors.redirectUris"
      />
      <fieldset>
        <legend class="mb-2 text-xs font-medium">{{ t('clients.scopes') }}</legend>
        <div class="flex flex-wrap gap-4">
          <UiCheckbox
            v-for="scope in scopes"
            :key="scope"
            :checked="form.scopes.includes(scope)"
            :label="scope"
            @update:checked="on => form.scopes = toggle(form.scopes, scope, on)"
          />
        </div>
        <p v-if="fieldErrors.scopes" class="mt-1 text-[11px] text-rose-600">{{ fieldErrors.scopes }}</p>
      </fieldset>
      <div class="grid gap-4 sm:grid-cols-2">
        <UiField
          v-model="form.accessTokenMinutes"
          type="number"
          min="1"
          :label="t('clients.accessTokenMinutes')"
          :error="fieldErrors.accessTokenMinutes"
        />
        <UiField
          v-model="form.refreshTokenHours"
          type="number"
          min="1"
          :label="t('clients.refreshTokenHours')"
          :error="fieldErrors.refreshTokenHours"
        />
      </div>
      <UiSwitch v-model="form.enabled" :label="t('clients.enabled')" />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>

    <div v-else-if="mode === 'issued' && issued" class="space-y-3" data-issued>
      <dl class="space-y-2 text-xs">
        <div><dt class="text-muted">{{ t('clients.clientId') }}</dt><dd class="mt-0.5 font-mono">{{ issued.client.clientId }}</dd></div>
      </dl>
      <template v-if="issued.secret">
        <p class="rounded-lg bg-amber-50 p-3 text-xs text-amber-800" role="note">{{ t('clients.secretOnce') }}</p>
        <div class="flex items-start gap-2 rounded-xl bg-canvas p-3">
          <code class="min-w-0 flex-1 break-all font-mono text-xs">{{ issued.secret }}</code>
          <button type="button" class="icon-button size-7" :aria-label="t('clients.copySecret')" @click="copy(issued.secret)"><Copy :size="13" /></button>
        </div>
      </template>
      <p v-else class="text-xs text-muted">{{ t('clients.noSecret') }}</p>
    </div>

    <form
      v-else-if="mode === 'rotate'"
      id="rotate-form"
      class="space-y-4"
      novalidate
      @submit.prevent="rotate"
    >
      <p class="text-xs">{{ t('clients.rotateHint') }}</p>
      <UiField
        v-model="graceHours"
        type="number"
        min="0"
        :label="t('clients.graceHours')"
        :error="fieldErrors.graceHours"
      />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>

    <div v-else-if="mode === 'delete'" class="space-y-3">
      <p class="text-xs">{{ t('clients.deleteConfirm', { name: current?.name ?? '' }) }}</p>
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </div>

    <template #footer>
      <template v-if="mode === 'list'">
        <UiButton variant="secondary" @click="open = false">{{ t('clients.close') }}</UiButton>
        <UiButton @click="openCreate"><Plus :size="15" />{{ t('clients.create') }}</UiButton>
      </template>
      <UiButton v-else-if="mode === 'issued'" @click="back">{{ t('clients.done') }}</UiButton>
      <template v-else>
        <UiButton variant="secondary" :disabled="busy" @click="back">{{ t('shared.cancel') }}</UiButton>
        <UiButton v-if="mode === 'create' || mode === 'edit'" type="submit" form="client-form" :loading="busy">{{ mode === 'create' ? t('clients.create') : t('clients.save') }}</UiButton>
        <UiButton v-else-if="mode === 'rotate'" type="submit" form="rotate-form" :loading="busy">{{ t('clients.rotateAction') }}</UiButton>
        <UiButton v-else variant="danger" :loading="busy" @click="remove">{{ t('clients.deleteAction') }}</UiButton>
      </template>
    </template>
  </UiDialog>
</template>
