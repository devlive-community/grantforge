<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { AlertTriangle, Copy, Pencil, PlugZap, Plus, RefreshCw, RefreshCcw, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { vPermission } from '@/lib/permission'
import { dateLabel } from '@/lib/format'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiSwitch from '@/components/UiSwitch.vue'

type Source = components['schemas']['IdentitySourceResponse']
type Report = components['schemas']['SyncReportResponse']
type Kind = Source['type']

// Directories and providers the tenant's users sign in with (D-72). Directories can be tested and synced; providers
// need the callback shown here registered as a redirect URI of their client.
const { t } = useI18n(), toast = useToast()
const sources = shallowRef<Source[]>([]), loading = ref(false), error = ref('')
const editing = ref<'create' | 'edit' | null>(null), deleting = shallowRef<Source | null>(null), target = shallowRef<Source | null>(null)
const saving = ref(false), formError = ref(''), busy = ref('')
const blankLdap = () => ({ url: '', baseDn: '', bindDn: '', userFilter: '', usernameAttribute: '', displayNameAttribute: '', emailAttribute: '',
  idAttribute: '', disableMissing: false })
const blankOidc = () => ({ issuer: '', clientId: '', scopes: '', usernameClaim: '', displayNameClaim: '', emailClaim: '' })
const form = ref({ code: '', name: '', type: 'LDAP' as Kind, enabled: true, provisioning: true, secret: '', interval: '', ldap: blankLdap(), oidc: blankOidc() })
const types = computed(() => [{ value: 'LDAP', label: t('identitySources.typeLdap'), description: t('identitySources.typeLdapText') },
  { value: 'OIDC', label: t('identitySources.typeOidc'), description: t('identitySources.typeOidcText') }])
const callback = (code: string) => `${window.location.origin}/api/v1/auth/federated/callback/${code || '<code>'}`

async function load() {
  loading.value = true; error.value = ''
  try { sources.value = await request<Source[]>('/api/v1/identity-sources') } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
function openCreate() {
  form.value = { code: '', name: '', type: 'LDAP', enabled: true, provisioning: true, secret: '', interval: '', ldap: blankLdap(), oidc: blankOidc() }
  target.value = null; formError.value = ''; editing.value = 'create'
}
function openEdit(source: Source) {
  const ldap = source.ldap, oidc = source.oidc
  form.value = { code: source.code, name: source.name, type: source.type, enabled: source.enabled, provisioning: source.provisioning, secret: '',
    interval: source.syncIntervalMinutes ? String(source.syncIntervalMinutes) : '',
    ldap: ldap ? { ...ldap, bindDn: ldap.bindDn ?? '' } : blankLdap(), oidc: oidc ? { ...oidc } : blankOidc() }
  target.value = source; formError.value = ''; editing.value = 'edit'
}
function missing(): string {
  const value = form.value
  if (!value.code.trim()) return t('identitySources.enterCode')
  if (!value.name.trim()) return t('identitySources.enterName')
  if (value.type === 'LDAP' && (!value.ldap.url.trim() || !value.ldap.baseDn.trim())) return t('identitySources.enterDirectory')
  if (value.type === 'OIDC' && (!value.oidc.issuer.trim() || !value.oidc.clientId.trim())) return t('identitySources.enterProvider')
  if (value.interval && !/^\d+$/.test(value.interval.trim())) return t('identitySources.enterInterval')
  return ''
}
async function save() {
  if (saving.value) return
  formError.value = missing()
  if (formError.value) return
  const value = form.value
  // A blank secret keeps the stored one.
  const body = { code: value.code.trim(), name: value.name.trim(), type: value.type, enabled: value.enabled, provisioning: value.provisioning,
    secret: value.secret || undefined, syncIntervalMinutes: value.type === 'LDAP' && value.interval ? Number(value.interval) : undefined,
    ldap: value.type === 'LDAP' ? value.ldap : undefined, oidc: value.type === 'OIDC' ? value.oidc : undefined }
  saving.value = true
  try {
    const source = target.value
    if (source) await request<Source>(`/api/v1/identity-sources/${encodeURIComponent(source.id)}`, { method: 'PUT', body })
    else await request<Source>('/api/v1/identity-sources', { method: 'POST', body })
    editing.value = null; toast.show(t(source ? 'identitySources.saved' : 'identitySources.created')); await load()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function test(source: Source) {
  busy.value = `test-${source.id}`
  try { await request<null>(`/api/v1/identity-sources/${encodeURIComponent(source.id)}/test`, { method: 'POST' }); toast.show(t('identitySources.tested')) }
  catch (reason) { toast.show(errorMessage(reason), 'error') } finally { busy.value = '' }
}
async function sync(source: Source) {
  busy.value = `sync-${source.id}`
  try {
    const report = await request<Report>(`/api/v1/identity-sources/${encodeURIComponent(source.id)}/sync`, { method: 'POST' })
    toast.show(t('identitySources.synced', { created: report.created, updated: report.updated, disabled: report.disabled })); await load()
  } catch (reason) { toast.show(errorMessage(reason), 'error'); await load() } finally { busy.value = '' }
}
async function remove() {
  const source = deleting.value
  if (!source || saving.value) return
  saving.value = true; formError.value = ''
  try {
    await request<null>(`/api/v1/identity-sources/${encodeURIComponent(source.id)}`, { method: 'DELETE' })
    deleting.value = null; toast.show(t('identitySources.deleted')); await load()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function copy(text: string) {
  try { await navigator.clipboard.writeText(text); toast.show(t('identitySources.copied')) } catch { toast.show(t('identitySources.copyFailed'), 'error') }
}
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.identitySources')" :description="t('identitySources.description')">
    <UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton>
    <UiButton v-permission="'system.identity-source.btn.create'" @click="openCreate"><Plus :size="16" />{{ t('identitySources.create') }}</UiButton>
  </PageHeading>
  <p v-if="error" class="panel p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <div v-else-if="!loading && !sources.length" class="panel p-10 text-center"><p class="text-sm font-semibold">{{ t('identitySources.emptyTitle') }}</p><p class="mt-2 text-xs text-muted">{{ t('identitySources.emptyText') }}</p></div>
  <div v-else class="grid gap-5 xl:grid-cols-2">
    <section v-for="source in sources" :key="source.id" class="panel p-5" :data-source="source.code">
      <header class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 class="flex items-center gap-2 text-sm font-semibold">{{ source.name }}<span class="badge bg-brand-soft text-brand">{{ source.type === 'LDAP' ? t('identitySources.typeLdap') : t('identitySources.typeOidc') }}</span></h2>
          <p class="mt-1 font-mono text-[11px] text-muted">{{ source.code }}</p>
        </div>
        <StatusBadge :active="source.enabled" />
      </header>
      <dl class="mt-4 grid gap-3 text-xs sm:grid-cols-2">
        <div><dt class="text-muted">{{ t('identitySources.where') }}</dt><dd class="mt-0.5 break-all font-mono">{{ source.ldap?.url ?? source.oidc?.issuer }}</dd></div>
        <div><dt class="text-muted">{{ t('identitySources.accounts') }}</dt><dd class="mt-0.5">{{ t('identitySources.accountCount', { count: source.accounts }) }} · {{ source.provisioning ? t('identitySources.provisioningOn') : t('identitySources.provisioningOff') }}</dd></div>
        <div v-if="source.callbackPath" class="sm:col-span-2">
          <dt class="text-muted">{{ t('identitySources.callback') }}</dt>
          <dd class="mt-0.5 flex items-start gap-2"><code class="min-w-0 flex-1 break-all font-mono" data-callback>{{ callback(source.code) }}</code><button type="button" class="icon-button size-7" :aria-label="t('identitySources.copyCallback')" @click="copy(callback(source.code))"><Copy :size="13" /></button></dd>
        </div>
        <div v-if="source.type === 'LDAP'" class="sm:col-span-2">
          <dt class="text-muted">{{ t('identitySources.lastSync') }}</dt>
          <dd class="mt-0.5" data-last-sync>{{ source.lastSyncedAt ? `${dateLabel(source.lastSyncedAt)} · ${source.lastSyncSummary}` : t('identitySources.neverSynced') }}<span v-if="source.syncIntervalMinutes" class="text-muted"> · {{ t('identitySources.every', { minutes: source.syncIntervalMinutes }) }}</span></dd>
        </div>
      </dl>
      <div class="mt-4 flex flex-wrap justify-end gap-1 border-t border-line pt-3">
        <template v-if="source.type === 'LDAP'">
          <button
            v-permission="'system.identity-source.btn.update'"
            type="button"
            class="table-action"
            :disabled="busy !== ''"
            :aria-label="t('identitySources.testNamed', { name: source.name })"
            @click="test(source)"
          >
            <PlugZap :size="14" />{{ t('identitySources.test') }}
          </button><button
            v-permission="'system.identity-source.btn.sync'"
            type="button"
            class="table-action"
            :disabled="busy !== ''"
            :aria-label="t('identitySources.syncNamed', { name: source.name })"
            @click="sync(source)"
          >
            <RefreshCcw :size="14" />{{ t('identitySources.sync') }}
          </button>
        </template><button
          v-permission="'system.identity-source.btn.update'"
          type="button"
          class="table-action"
          :aria-label="t('identitySources.editNamed', { name: source.name })"
          @click="openEdit(source)"
        >
          <Pencil :size="14" />{{ t('identitySources.edit') }}
        </button><button
          v-permission="'system.identity-source.btn.delete'"
          type="button"
          class="table-action hover:text-rose-600"
          :aria-label="t('identitySources.deleteNamed', { name: source.name })"
          @click="deleting = source; formError = ''"
        >
          <Trash2 :size="14" />{{ t('identitySources.delete') }}
        </button>
      </div>
    </section>
  </div>
  <UiDialog
    :model-value="editing !== null"
    :title="editing === 'create' ? t('identitySources.create') : t('identitySources.editTitle')"
    wide
    :busy="saving"
    @update:model-value="editing = null"
  >
    <form id="identity-source" class="space-y-5" novalidate @submit.prevent="save">
      <UiSelect v-if="editing === 'create'" v-model="form.type" :label="t('identitySources.type')" :options="types" />
      <div class="grid gap-5 sm:grid-cols-2">
        <UiField
          v-model="form.code"
          :label="t('identitySources.code')"
          :placeholder="t('identitySources.codePlaceholder')"
          :disabled="editing === 'edit'"
          required
        /><UiField v-model="form.name" :label="t('identitySources.name')" :placeholder="t('identitySources.namePlaceholder')" required />
      </div>
      <div class="flex flex-wrap gap-6"><UiSwitch v-model="form.enabled" :label="t('identitySources.enabled')" /><UiSwitch v-model="form.provisioning" :label="t('identitySources.provisioning')" /></div>
      <template v-if="form.type === 'LDAP'">
        <UiField v-model="form.ldap.url" :label="t('identitySources.url')" placeholder="ldaps://ldap.example.com" required />
        <div class="grid gap-5 sm:grid-cols-2">
          <UiField v-model="form.ldap.baseDn" :label="t('identitySources.baseDn')" placeholder="ou=people,dc=example,dc=com" required /><UiField v-model="form.ldap.bindDn" :label="t('identitySources.bindDn')" placeholder="cn=reader,dc=example,dc=com" />
        </div>
        <UiField
          v-model="form.secret"
          :label="t('identitySources.bindPassword')"
          type="password"
          autocomplete="new-password"
          :placeholder="target?.secretSet ? t('identitySources.secretKept') : ''"
        />
        <details class="rounded-xl border border-line p-4">
          <summary class="cursor-pointer text-xs font-medium">{{ t('identitySources.attributes') }}</summary>
          <p class="mt-2 text-[11px] text-muted">{{ t('identitySources.attributesHint') }}</p>
          <div class="mt-4 grid gap-5 sm:grid-cols-2">
            <UiField v-model="form.ldap.userFilter" :label="t('identitySources.userFilter')" placeholder="(&(objectClass=person)(uid={0}))" /><UiField v-model="form.ldap.usernameAttribute" :label="t('identitySources.usernameAttribute')" placeholder="uid" />
            <UiField v-model="form.ldap.displayNameAttribute" :label="t('identitySources.displayNameAttribute')" placeholder="cn" /><UiField v-model="form.ldap.emailAttribute" :label="t('identitySources.emailAttribute')" placeholder="mail" />
            <UiField v-model="form.ldap.idAttribute" :label="t('identitySources.idAttribute')" placeholder="entryUUID" />
          </div>
        </details>
        <div class="grid items-end gap-5 sm:grid-cols-2">
          <UiField v-model="form.interval" :label="t('identitySources.interval')" :placeholder="t('identitySources.intervalPlaceholder')" /><UiSwitch v-model="form.ldap.disableMissing" :label="t('identitySources.disableMissing')" />
        </div>
      </template>
      <template v-else>
        <UiField v-model="form.oidc.issuer" :label="t('identitySources.issuer')" placeholder="https://login.example.com/realms/acme" required />
        <div class="grid gap-5 sm:grid-cols-2">
          <UiField v-model="form.oidc.clientId" :label="t('identitySources.clientId')" required /><UiField
            v-model="form.secret"
            :label="t('identitySources.clientSecret')"
            type="password"
            autocomplete="new-password"
            :placeholder="target?.secretSet ? t('identitySources.secretKept') : ''"
          />
        </div>
        <p class="text-[11px] text-muted">{{ t('identitySources.callbackHint', { url: callback(form.code.trim()) }) }}</p>
        <details class="rounded-xl border border-line p-4">
          <summary class="cursor-pointer text-xs font-medium">{{ t('identitySources.claims') }}</summary>
          <div class="mt-4 grid gap-5 sm:grid-cols-2">
            <UiField v-model="form.oidc.scopes" :label="t('identitySources.scopes')" placeholder="openid profile email" /><UiField v-model="form.oidc.usernameClaim" :label="t('identitySources.usernameClaim')" placeholder="preferred_username" />
            <UiField v-model="form.oidc.displayNameClaim" :label="t('identitySources.displayNameClaim')" placeholder="name" /><UiField v-model="form.oidc.emailClaim" :label="t('identitySources.emailClaim')" placeholder="email" />
          </div>
        </details>
      </template>
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="editing = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="identity-source" :loading="saving">{{ editing === 'create' ? t('identitySources.create') : t('identitySources.save') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="deleting !== null" :title="t('identitySources.deleteTitle')" :busy="saving" @update:model-value="deleting = null">
    <p class="flex gap-3 text-xs leading-6"><AlertTriangle :size="18" class="shrink-0 text-amber-500" />{{ t('identitySources.deleteWarning', { name: deleting?.name }) }}</p>
    <p v-if="formError" class="mt-4 rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="deleting = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('identitySources.delete') }}</UiButton></template>
  </UiDialog>
</template>
