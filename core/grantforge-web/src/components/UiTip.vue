<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, useId, watch, type CSSProperties } from 'vue'
import { AlertCircle, CheckCircle2 } from '@lucide/vue'
import { offscreen, placedStyle, placement } from '@/lib/floating'

/**
 * A validation result that floats over the page instead of pushing it around: a tinted bubble under the control it
 * belongs to, lined up with that control's right edge, with a small triangle pointing back up at it. It never takes a
 * pointer event, so nothing underneath it becomes unreachable, and it moves with the page while the control is on
 * screen. It renders inside the control's dialog when the control sits in one, so it stays above the page.
 */
const { message = '', state = 'error', anchor = null, control = '' } = defineProps<{
  message?: string
  state?: 'error' | 'success'
  /** The control the tip hangs off. Without one the tip stays hidden, since it has nowhere to be placed. */
  anchor?: HTMLElement | null
  /** The id of the control the tip describes, so `aria-describedby` can point here. */
  control?: string
}>()
const own = useId()
const tipId = computed(() => `${control || own}-tip`)
const bubble = ref<HTMLElement>()
const host = computed(() => message ? anchor?.closest('dialog') ?? document.body : undefined)
// Hidden until it has been placed, so it never shows for a frame at the end of the page.
const style = ref<CSSProperties>({ visibility: 'hidden' })
const tones = {
  error: { box: 'border-rose-100 bg-rose-50 text-rose-700 dark:border-rose-500/25 dark:bg-rose-500/10 dark:text-rose-300', arrow: 'border-b-rose-50 dark:border-b-rose-500/10', icon: 'text-rose-600 dark:text-rose-400' },
  success: { box: 'border-emerald-100 bg-emerald-50 text-emerald-700 dark:border-emerald-500/25 dark:bg-emerald-500/10 dark:text-emerald-300', arrow: 'border-b-emerald-50 dark:border-b-emerald-500/10', icon: 'text-emerald-600 dark:text-emerald-400' },
}
const tone = computed(() => tones[state])

function place() {
  const opener = anchor, floating = bubble.value
  if (!message || !opener || !floating) return
  const box = opener.getBoundingClientRect(), screen = { width: window.innerWidth, height: window.innerHeight }
  if (offscreen(box, screen)) { style.value = { visibility: 'hidden' }; return }
  // Measured at its natural size, whatever an earlier placement capped it to.
  const at = placement(box, { width: floating.offsetWidth, height: floating.offsetHeight }, screen, { margin: 12, gap: 8, align: 'end' })
  style.value = placedStyle(at, host.value ?? document.body)
}
function listen(on: boolean) {
  const action = on ? 'addEventListener' : 'removeEventListener'
  window[action]('resize', place)
  window[action]('scroll', place, true)
}
watch(() => [message, state, anchor], () => {
  listen(Boolean(message && anchor))
  if (message && anchor) void nextTick(place)
}, { immediate: true })
onBeforeUnmount(() => listen(false))
</script>

<template>
  <Teleport v-if="host" :to="host">
    <div
      :id="tipId"
      ref="bubble"
      :role="state === 'success' ? 'status' : 'alert'"
      :style="style"
      class="pointer-events-none z-40 flex w-max max-w-[min(17rem,calc(100vw-1.5rem))] items-center gap-2 rounded-xl border px-3 py-2 text-xs leading-5"
      :class="tone.box"
    >
      <span aria-hidden="true" class="absolute -top-1.5 right-4 size-0 border-x-[5px] border-b-[6px] border-x-transparent" :class="tone.arrow"></span>
      <component
        :is="state === 'success' ? CheckCircle2 : AlertCircle"
        :size="14"
        aria-hidden="true"
        class="shrink-0"
        :class="tone.icon"
      />
      <span class="min-w-0 break-words">{{ message }}</span>
    </div>
  </Teleport>
</template>
