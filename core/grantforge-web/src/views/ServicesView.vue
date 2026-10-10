<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { Database, KeyRound, Pencil, Plug, Plus, RefreshCw, Trash2, Zap } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { ApiError, errorMessage, request } from '@/lib/api'
import { useFieldErrors, type FieldErrors } from '@/lib/fieldErrors'
import { vPermission } from '@/lib/permission'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiSwitch from '@/components/UiSwitch.vue'

type Service = components['schemas']['ServiceResponse']
type ServiceType = components['schemas']['ServiceTypeResponse']
type Field = ServiceType['configFields'][number]
type Connection = components['schemas']['ConnectionResponse']

const { t } = useI18n(), toast = useToast()
const services = shallowRef<Service[]>([]), types = shallowRef<ServiceType[]>([]), loading = ref(false), error = ref('')
const dialog = ref<'create' | 'edit' | 'delete' | null>(null), target = shallowRef<Service | null>(null), saving = ref(false)
const form = ref({ serviceType: '', name: '', label: '', description: '', enabled: true, values: {} as Record<string, string>, flags: {} as Record<string, boolean> })
const formError = ref(''), testing = ref(''), tested = shallowRef<Connection | null>(null)
/** What the server last rejected, kept beside the local checks so a message outlives an edit to another setting. */
const rejected = ref<FieldErrors>({})
const { errors: fieldErrors, invalid } = useFieldErrors(() => form.value, problems)

const typeOptions = computed(() => types.value.map(type => ({ value: type.name, label: type.label })))
const chosenType = computed(() => types.value.find(type => type.name === form.value.serviceType) ?? null)
const fieldsOf = (field: Field) => field.options.map(option => ({ value: option, label: option }))

async function load() {
  loading.value = true; error.value = ''
  try {
    const [found, available] = await Promise.all([request<Service[]>('/api/v1/services'), request<ServiceType[]>('/api/v1/service-types')])
    services.value = found; types.value = available
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
function open(kind: 'create' | 'edit' | 'delete', service: Service | null = null) {
  target.value = service; dialog.value = kind; formError.value = ''; rejected.value = {}; tested.value = null
  const type = service?.serviceType ?? types.value[0]?.name ?? ''
  form.value = { serviceType: type, name: service?.name ?? '', label: service?.label ?? '', description: service?.description ?? '',
    enabled: service?.enabled ?? true, values: { ...service?.values }, flags: {} }
  for (const field of types.value.find(candidate => candidate.name === type)?.configFields ?? []) {
    if (field.type === 'BOOLEAN') form.value.flags[field.name] = (service?.values[field.name] ?? field.defaultValue) === 'true'
  }
}
/** The settings as the server takes them: switches as true or false, empty text left out. */
function values(): Record<string, string> {
  const result: Record<string, string> = {}
  for (const field of chosenType.value?.configFields ?? []) {
    const value = field.type === 'BOOLEAN' ? String(form.value.flags[field.name] ?? false) : form.value.values[field.name]?.trim()
    if (value) result[field.name] = value
  }
  return result
}
/** Every setting the plugin insists on, answered here rather than only by the server after a round trip. */
function problems(): FieldErrors {
  const found: FieldErrors = {}
  for (const field of chosenType.value?.configFields ?? []) {
    // A switch always has an answer, and a secret the server already holds still counts as filled in.
    if (!field.mandatory || field.type === 'BOOLEAN') continue
    if (field.type === 'SECRET' && target.value?.secretsSet.includes(field.name)) continue
    if (!form.value.values[field.name]?.trim()) found[field.name] = t('services.enterField', { name: field.label })
  }
  return { ...found, ...rejected.value }
}
function showProblems(reason: unknown) {
  const problems = reason instanceof ApiError ? reason.problem?.errors ?? [] : []
  rejected.value = Object.fromEntries(problems.map(problem => [problem.field, problem.message]))
  fieldErrors.value = { ...fieldErrors.value, ...rejected.value }
  formError.value = errorMessage(reason)
}
async function save() {
  if (saving.value) return
  formError.value = ''
  // The settings the plugin insists on are answered here, so a missing one is marked on its own field.
  rejected.value = {}
  if (invalid()) return
  saving.value = true
  const { serviceType, name, label, description, enabled } = form.value
  const body = { serviceType, name, label, description, enabled, values: values() }
  const service = target.value
  try {
    if (dialog.value === 'edit' && service) await request(`/api/v1/services/${encodeURIComponent(service.id)}`, { method: 'PUT', body })
    else await request('/api/v1/services', { method: 'POST', body })
    toast.show(dialog.value === 'edit' ? t('services.saved') : t('services.created'))
    dialog.value = null
    await load()
  } catch (reason) { showProblems(reason) } finally { saving.value = false }
}
async function testForm() {
  testing.value = 'form'; tested.value = null; formError.value = ''; rejected.value = {}
  try {
    tested.value = await request<Connection>('/api/v1/services/test', { method: 'POST', body: { serviceType: form.value.serviceType,
      serviceId: target.value?.id, name: form.value.name || undefined, values: values() } })
  } catch (reason) { showProblems(reason) } finally { testing.value = '' }
}
async function testService(service: Service) {
  testing.value = service.id
  try {
    const result = await request<Connection>('/api/v1/services/test', { method: 'POST', body: { serviceType: service.serviceType,
      serviceId: service.id, name: service.name, values: service.values } })
    toast.show(connectionText(result), result.status === 'SUCCEEDED' ? 'success' : 'error')
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { testing.value = '' }
}
function connectionText(result: Connection) {
  if (result.status === 'SUCCEEDED') return t('services.connected')
  if (result.status === 'UNSUPPORTED') return t('services.testUnsupported')
  return t('services.notConnected', { message: result.message ?? '' })
}
async function remove() {
  const service = target.value
  if (!service || saving.value) return
  saving.value = true
  try {
    await request(`/api/v1/services/${encodeURIComponent(service.id)}`, { method: 'DELETE' })
    dialog.value = null; toast.show(t('services.deleted')); await load()
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.services')" :description="t('services.description')" :badge="t('services.count', { count: services.length })">
    <UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton>
    <UiButton v-permission="'data.service.btn.create'" :disabled="!types.length" @click="open('create')"><Plus :size="16" />{{ t('services.create') }}</UiButton>
  </PageHeading>
  <p v-if="error" class="panel p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <div v-else-if="loading && !services.length" class="space-y-3"><div v-for="index in 3" :key="index" class="h-16 animate-pulse rounded-xl bg-line"></div></div>
  <section v-else-if="!services.length" class="panel flex flex-col items-center p-10 text-center">
    <component :is="types.length ? Database : Plug" :size="28" class="text-muted" />
    <p class="mt-3 font-medium">{{ t('services.empty') }}</p>
    <p class="mt-1 text-xs text-muted">{{ types.length ? t('services.emptyHint') : t('services.noTypes') }}</p>
  </section>
  <section v-else class="panel divide-y divide-line">
    <div v-for="service in services" :key="service.id" class="flex flex-wrap items-center gap-3 px-5 py-4" :data-service="service.name">
      <span class="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-brand"><Database :size="16" /></span>
      <div class="min-w-0 flex-1">
        <p class="font-medium">{{ service.label }} <span class="ml-1 font-mono text-[10px] text-muted">{{ service.name }}</span></p>
        <p class="mt-1 text-[11px] text-muted">{{ service.description || service.values.url || '—' }}</p>
      </div>
      <span class="badge" :class="service.available ? '' : 'bg-amber-50 text-amber-700'">{{ service.serviceTypeLabel ?? t('services.typeUnavailable', { type: service.serviceType }) }}</span>
      <span class="badge" :class="service.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-canvas text-muted'">{{ service.enabled ? t('services.enabled') : t('services.disabled') }}</span>
      <div class="flex gap-0.5">
        <RouterLink
          v-permission="'data.policy'"
          :to="{ path: '/data/policies', query: { service: service.id } }"
          class="table-action"
          :aria-label="t('services.policiesNamed', { name: service.label })"
        >
          <KeyRound :size="14" />
        </RouterLink>
        <button
          v-permission="'data.service.btn.test'"
          type="button"
          class="table-action"
          :disabled="!service.available || testing === service.id"
          :aria-label="t('services.testNamed', { name: service.label })"
          @click="testService(service)"
        >
          <Zap :size="14" />
        </button>
        <button
          v-permission="'data.service.btn.edit'"
          type="button"
          class="table-action"
          :disabled="!service.available"
          :aria-label="t('services.editNamed', { name: service.label })"
          @click="open('edit', service)"
        >
          <Pencil :size="14" />
        </button>
        <button
          v-permission="'data.service.btn.delete'"
          type="button"
          class="table-action hover:text-rose-600"
          :aria-label="t('services.deleteNamed', { name: service.label })"
          @click="open('delete', service)"
        >
          <Trash2 :size="14" />
        </button>
      </div>
    </div>
  </section>

  <UiDialog
    :model-value="dialog === 'create' || dialog === 'edit'"
    :title="dialog === 'edit' ? t('services.editTitle') : t('services.createTitle')"
    :description="t('services.formDescription')"
    :busy="saving"
    wide
    @update:model-value="dialog = null"
  >
    <form id="service-form" class="grid gap-5 sm:grid-cols-2" novalidate @submit.prevent="save">
      <UiSelect
        v-if="dialog === 'create'"
        v-model="form.serviceType"
        :label="t('services.type')"
        :options="typeOptions"
        required
        @update:model-value="open('create')"
      />
      <UiField
        v-model="form.name"
        :label="t('services.name')"
        :placeholder="t('services.namePlaceholder')"
        :error="fieldErrors.name"
        required
      />
      <UiField v-model="form.label" :label="t('services.label')" :error="fieldErrors.label" required />
      <div class="sm:col-span-2"><UiField v-model="form.description" :label="t('services.descriptionLabel')" :error="fieldErrors.description" textarea /></div>
      <div class="sm:col-span-2"><UiSwitch v-model="form.enabled" :label="t('services.enabledSwitch')" /></div>
      <h3 class="text-xs font-semibold sm:col-span-2">{{ t('services.settings') }}</h3>
      <template v-for="field in chosenType?.configFields ?? []" :key="field.name">
        <div :class="field.type === 'TEXT' ? 'sm:col-span-2' : ''" :data-field="field.name">
          <UiSwitch v-if="field.type === 'BOOLEAN'" :model-value="form.flags[field.name] ?? false" :label="field.label" @update:model-value="form.flags[field.name] = $event" />
          <UiSelect
            v-else-if="field.type === 'ENUM'"
            :model-value="form.values[field.name] ?? ''"
            :label="field.label"
            :options="fieldsOf(field)"
            :placeholder="field.defaultValue ?? ''"
            :required="field.mandatory"
            :error="fieldErrors[field.name]"
            @update:model-value="form.values[field.name] = $event"
          />
          <UiField
            v-else
            :model-value="form.values[field.name] ?? ''"
            :label="field.label"
            :type="field.type === 'SECRET' ? 'password' : field.type === 'INTEGER' ? 'number' : 'text'"
            :textarea="field.type === 'TEXT'"
            :placeholder="field.type === 'SECRET' && target?.secretsSet.includes(field.name) ? t('services.secretKept') : field.defaultValue ?? ''"
            :required="field.mandatory && !(field.type === 'SECRET' && target?.secretsSet.includes(field.name))"
            :autocomplete="field.type === 'SECRET' ? 'new-password' : 'off'"
            :error="fieldErrors[field.name]"
            @update:model-value="form.values[field.name] = $event"
          />
          <p v-if="field.description" class="mt-1 text-[11px] text-muted">{{ field.description }}</p>
          <p v-if="fieldErrors[field.name] && field.type === 'BOOLEAN'" class="mt-1 text-xs text-rose-600">{{ fieldErrors[field.name] }}</p>
        </div>
      </template>
      <p v-if="tested" class="rounded-lg p-3 text-xs sm:col-span-2" :class="tested.status === 'SUCCEEDED' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-800'" role="status">{{ connectionText(tested) }}</p>
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700 sm:col-span-2" role="alert">{{ formError }}</p>
    </form>
    <template #footer>
      <UiButton
        v-permission="'data.service.btn.test'"
        variant="secondary"
        class="mr-auto"
        :loading="testing === 'form'"
        :disabled="!chosenType"
        @click="testForm"
      >
        <Zap :size="15" />{{ t('services.test') }}
      </UiButton>
      <UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton>
      <UiButton type="submit" form="service-form" :loading="saving" :disabled="!chosenType">{{ dialog === 'edit' ? t('services.save') : t('services.create') }}</UiButton>
    </template>
  </UiDialog>
  <UiDialog :model-value="dialog === 'delete'" :title="t('services.deleteTitle')" :busy="saving" @update:model-value="dialog = null">
    <p class="text-xs">{{ t('services.deleteConfirm', { name: target?.label ?? '' }) }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('services.delete') }}</UiButton></template>
  </UiDialog>
</template>
