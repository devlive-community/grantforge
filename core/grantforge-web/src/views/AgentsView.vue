<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Copy, KeySquare, Plus, RadioTower, RefreshCw, Trash2, XCircle } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { ApiError, errorMessage, request } from '@/lib/api'
import { dateLabel } from '@/lib/format'
import { vPermission } from '@/lib/permission'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiDatePicker from '@/components/UiDatePicker.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'

type Service = components['schemas']['ServiceResponse']
type Agent = components['schemas']['AgentResponse']
type Token = components['schemas']['AgentTokenResponse']
type Issued = components['schemas']['IssuedTokenResponse']
type SigningKey = components['schemas']['SigningKeyResponse']

const { t } = useI18n(), toast = useToast(), route = useRoute(), router = useRouter()
const services = shallowRef<Service[]>([]), agents = shallowRef<Agent[]>([]), tokens = shallowRef<Token[]>([])
const key = shallowRef<SigningKey | null>(null), loading = ref(false), error = ref(''), busy = ref(false)
const issuing = ref(false), form = ref({ name: '', expiresAt: '' }), fieldErrors = ref<Record<string, string>>({}), formError = ref('')
const issued = shallowRef<Issued | null>(null), revoking = shallowRef<Token | null>(null)

const serviceId = computed(() => {
  const asked = typeof route.query.service === 'string' ? route.query.service : ''
  return services.value.some(service => service.id === asked) ? asked : services.value[0]?.id ?? ''
})
const serviceOptions = computed(() => services.value.map(entry => ({ value: entry.id, label: entry.label, description: entry.name })))
const endpoint = computed(() => `${window.location.origin}/api/v1/agent/`)

async function load() {
  loading.value = true; error.value = ''
  try {
    const [found, signing] = await Promise.all([request<Service[]>('/api/v1/services'), request<SigningKey>('/api/v1/policy-signing-key')])
    services.value = found; key.value = signing
    await loadService()
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
async function loadService() {
  if (!serviceId.value) { agents.value = []; tokens.value = []; return }
  const id = encodeURIComponent(serviceId.value)
  const [found, issuedTokens] = await Promise.all([request<Agent[]>(`/api/v1/services/${id}/agents`), request<Token[]>(`/api/v1/services/${id}/agent-tokens`)])
  agents.value = found; tokens.value = issuedTokens
}
async function reload() { try { await loadService() } catch (reason) { error.value = errorMessage(reason) } }
watch(serviceId, (now, before) => { if (before !== undefined && now !== before) void reload() })
// The secret is shown once: closing the dialog drops it.
watch(issuing, open => { if (!open) issued.value = null })
function chooseService(id: string) { void router.replace({ query: { ...route.query, service: id } }) }

function openIssue() { form.value = { name: '', expiresAt: '' }; fieldErrors.value = {}; formError.value = ''; issued.value = null; issuing.value = true }
async function issue() {
  if (busy.value) return
  busy.value = true; fieldErrors.value = {}; formError.value = ''
  try {
    const expiresAt = form.value.expiresAt ? new Date(form.value.expiresAt).toISOString() : undefined
    issued.value = await request<Issued>(`/api/v1/services/${encodeURIComponent(serviceId.value)}/agent-tokens`, {
      method: 'POST', body: { name: form.value.name.trim(), expiresAt } })
    await reload()
  } catch (reason) {
    const problems = reason instanceof ApiError ? reason.problem?.errors ?? [] : []
    fieldErrors.value = Object.fromEntries(problems.map(problem => [problem.field, problem.message]))
    formError.value = errorMessage(reason)
  } finally { busy.value = false }
}
async function copy(text: string) {
  try { await navigator.clipboard.writeText(text); toast.show(t('agents.copied')) } catch { toast.show(t('agents.copyFailed'), 'error') }
}
async function revoke() {
  const token = revoking.value
  if (!token || busy.value) return
  busy.value = true
  try {
    await request(`/api/v1/agent-tokens/${encodeURIComponent(token.id)}/revoke`, { method: 'POST' })
    revoking.value = null; toast.show(t('agents.revoked')); await reload()
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { busy.value = false }
}
async function forget(agent: Agent) {
  try {
    await request(`/api/v1/services/${encodeURIComponent(serviceId.value)}/agents/${encodeURIComponent(agent.id)}`, { method: 'DELETE' })
    toast.show(t('agents.forgotten')); await reload()
  } catch (reason) { toast.show(errorMessage(reason), 'error') }
}
function tokenState(token: Token) {
  if (token.revokedAt) return t('agents.tokenRevoked')
  return token.usable ? t('agents.tokenUsable') : t('agents.tokenExpired')
}
function statusLabel(agent: Agent) {
  if (agent.status === 'CURRENT') return t('agents.statusCurrent')
  return agent.status === 'OUTDATED' ? t('agents.statusOutdated') : t('agents.statusSilent')
}
const statusClass = (agent: Agent) => agent.status === 'CURRENT' ? 'bg-emerald-50 text-emerald-700'
  : agent.status === 'OUTDATED' ? 'bg-amber-50 text-amber-700' : 'bg-rose-50 text-rose-700'
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.agents')" :description="t('agents.description')">
    <UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton>
    <UiButton v-permission="'data.agent.btn.manage'" :disabled="!serviceId" @click="openIssue"><Plus :size="16" />{{ t('agents.issue') }}</UiButton>
  </PageHeading>
  <p v-if="error" class="panel p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <section v-else-if="!loading && !services.length" class="panel flex flex-col items-center p-10 text-center">
    <RadioTower :size="28" class="text-muted" />
    <p class="mt-3 font-medium">{{ t('agents.noServices') }}</p>
    <RouterLink to="/data/services" class="mt-2 text-xs text-brand hover:underline">{{ t('agents.toServices') }}</RouterLink>
  </section>
  <template v-else-if="serviceId">
    <div class="mb-5 w-full max-w-xs"><UiSelect :model-value="serviceId" :label="t('agents.service')" :options="serviceOptions" @update:model-value="chooseService" /></div>
    <div class="grid gap-5 xl:grid-cols-[1fr_22rem]">
      <section class="panel" aria-labelledby="agents-heading">
        <h2 id="agents-heading" class="border-b border-line px-5 py-4 text-sm font-semibold">{{ t('agents.agents') }}</h2>
        <p v-if="!agents.length" class="p-8 text-center text-xs text-muted">{{ t('agents.noAgents') }}</p>
        <div v-for="agent in agents" :key="agent.id" class="flex flex-wrap items-center gap-3 border-b border-line px-5 py-3 last:border-0" :data-agent="agent.instance">
          <div class="min-w-0 flex-1">
            <p class="font-mono text-xs font-medium">{{ agent.instance }}</p>
            <p class="mt-1 text-[11px] text-muted">{{ [agent.host, agent.agentVersion].filter(Boolean).join(' · ') || '—' }}</p>
          </div>
          <span class="text-[11px] text-muted">{{ t('agents.applied', { version: agent.appliedPolicyVersion ?? '—' }) }}</span>
          <span class="text-[11px] text-muted">{{ t('agents.lastSeen', { time: dateLabel(agent.lastSeenAt) }) }}</span>
          <span class="badge" :class="statusClass(agent)">{{ statusLabel(agent) }}</span>
          <button
            v-permission="'data.agent.btn.manage'"
            type="button"
            class="table-action hover:text-rose-600"
            :aria-label="t('agents.forget', { name: agent.instance })"
            @click="forget(agent)"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </section>
      <div class="space-y-5">
        <section class="panel p-5" aria-labelledby="tokens-heading">
          <h2 id="tokens-heading" class="text-sm font-semibold">{{ t('agents.tokens') }}</h2>
          <p class="mt-1 text-[11px] text-muted">{{ t('agents.tokensHint') }}</p>
          <p v-if="!tokens.length" class="mt-4 text-xs text-muted">{{ t('agents.noTokens') }}</p>
          <ul class="mt-3 space-y-2">
            <li v-for="token in tokens" :key="token.id" class="flex items-center gap-2 rounded-xl bg-canvas/60 px-3 py-2" :data-token="token.name">
              <div class="min-w-0 flex-1">
                <p class="truncate text-xs font-medium">{{ token.name }} <span class="ml-1 font-mono text-[10px] text-muted">{{ token.hint }}…</span></p>
                <p class="mt-0.5 text-[10px] text-muted">{{ tokenState(token) }} · {{ t('agents.lastUsed', { time: dateLabel(token.lastUsedAt) }) }}</p>
              </div>
              <button
                v-if="!token.revokedAt"
                v-permission="'data.agent.btn.manage'"
                type="button"
                class="table-action hover:text-rose-600"
                :aria-label="t('agents.revokeNamed', { name: token.name })"
                @click="revoking = token"
              >
                <XCircle :size="14" />
              </button>
            </li>
          </ul>
        </section>
        <section v-if="key" class="panel p-5" aria-labelledby="key-heading">
          <h2 id="key-heading" class="flex items-center gap-2 text-sm font-semibold"><KeySquare :size="15" />{{ t('agents.signingKey') }}</h2>
          <p class="mt-1 text-[11px] text-muted">{{ t('agents.signingKeyHint') }}</p>
          <dl class="mt-3 space-y-2 text-xs">
            <div><dt class="text-muted">{{ t('agents.endpoint') }}</dt><dd class="mt-0.5 break-all font-mono">{{ endpoint }}</dd></div>
            <div><dt class="text-muted">{{ t('agents.keyId') }}</dt><dd class="mt-0.5 font-mono">{{ key.keyId }} · {{ key.algorithm }}</dd></div>
            <div>
              <dt class="text-muted">{{ t('agents.publicKey') }}</dt>
              <dd class="mt-0.5 flex items-start gap-2">
                <code class="min-w-0 flex-1 break-all font-mono text-[11px]">{{ key.publicKey }}</code>
                <button type="button" class="icon-button size-7" :aria-label="t('agents.copyKey')" @click="copy(key.publicKey)"><Copy :size="13" /></button>
              </dd>
            </div>
          </dl>
        </section>
      </div>
    </div>
  </template>

  <UiDialog v-model="issuing" :title="t('agents.issueTitle')" :description="issued ? '' : t('agents.issueDescription')" :busy="busy">
    <div v-if="issued" class="space-y-3" data-issued>
      <p class="rounded-lg bg-amber-50 p-3 text-xs text-amber-800" role="note">{{ t('agents.secretOnce') }}</p>
      <div class="flex items-start gap-2 rounded-xl bg-canvas p-3">
        <code class="min-w-0 flex-1 break-all font-mono text-xs">{{ issued.secret }}</code>
        <button type="button" class="icon-button size-7" :aria-label="t('agents.copySecret')" @click="copy(issued.secret)"><Copy :size="13" /></button>
      </div>
    </div>
    <form
      v-else
      id="token-form"
      class="space-y-5"
      novalidate
      @submit.prevent="issue"
    >
      <UiField
        v-model="form.name"
        :label="t('agents.tokenName')"
        :placeholder="t('agents.tokenNamePlaceholder')"
        :error="fieldErrors.name"
        required
      />
      <UiDatePicker v-model="form.expiresAt" time :label="t('agents.expiresAt')" :error="fieldErrors.expiresAt" />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer>
      <UiButton v-if="issued" @click="issuing = false">{{ t('agents.done') }}</UiButton>
      <template v-else>
        <UiButton variant="secondary" :disabled="busy" @click="issuing = false">{{ t('shared.cancel') }}</UiButton>
        <UiButton type="submit" form="token-form" :loading="busy">{{ t('agents.issue') }}</UiButton>
      </template>
    </template>
  </UiDialog>
  <UiDialog :model-value="Boolean(revoking)" :title="t('agents.revokeTitle')" :busy="busy" @update:model-value="revoking = null">
    <p class="text-xs">{{ t('agents.revokeConfirm', { name: revoking?.name ?? '' }) }}</p>
    <template #footer><UiButton variant="secondary" :disabled="busy" @click="revoking = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="busy" @click="revoke">{{ t('agents.revoke') }}</UiButton></template>
  </UiDialog>
</template>
