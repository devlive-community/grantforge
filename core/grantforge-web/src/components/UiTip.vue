<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, useId, watch, type CSSProperties } from 'vue'
import { AlertCircle, CheckCircle2 } from '@lucide/vue'
import { intersect, outside, placedStyle, placement, viewportBox, type Box } from '@/lib/floating'

/**
 * A validation result that floats over the page instead of pushing it around: a tinted bubble under the control it
 * belongs to, lined up with that control's right edge, with a small triangle pointing back up at it. It never takes a
 * pointer event, so nothing underneath it becomes unreachable, and it moves with the page while the control is on
 * screen. It renders inside the control's dialog when the control sits in one, so it stays above the page, and it keeps
 * to that dialog rather than floating off over the page beside it.
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
// The tip outlives its message for a moment so it can fade out, so the host cannot depend on there being one.
const host = computed(() => anchor?.closest('dialog') ?? document.body)
// Positioned from the first frame, so a tip that is never placed cannot take up room in whatever it renders in.
const style = ref<CSSProperties>({ position: host.value instanceof HTMLDialogElement ? 'absolute' : 'fixed', visibility: 'hidden' })
const tones = {
  error: { box: 'border-rose-100 bg-rose-50 text-rose-700 dark:border-rose-500/25 dark:bg-rose-500/10 dark:text-rose-300', arrow: 'border-b-rose-50 dark:border-b-rose-500/10', icon: 'text-rose-600 dark:text-rose-400' },
  success: { box: 'border-emerald-100 bg-emerald-50 text-emerald-700 dark:border-emerald-500/25 dark:bg-emerald-500/10 dark:text-emerald-300', arrow: 'border-b-emerald-50 dark:border-b-emerald-500/10', icon: 'text-emerald-600 dark:text-emerald-400' },
}
const tone = computed(() => tones[state])

/** The area the tip may occupy: the window, narrowed to the dialog when the control sits in one. */
function areaFor(): Box {
  const viewport = viewportBox()
  const dialog = host.value
  return dialog instanceof HTMLDialogElement ? intersect(viewport, dialog.getBoundingClientRect()) : viewport
}

function place() {
  const opener = anchor, floating = bubble.value
  if (!opener || !floating) return
  const area = areaFor(), box = opener.getBoundingClientRect()
  // Measured at its natural size, whatever an earlier placement capped it to.
  const at = placement(box, { width: floating.offsetWidth, height: floating.offsetHeight }, area, { margin: 12, gap: 8, align: 'end' })
  // A control that has left the area leaves the tip nothing to point at, so it waits rather than floating on.
  style.value = { ...placedStyle(at, host.value), visibility: outside(box, area) ? 'hidden' : 'visible' }
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
  <Teleport :to="host">
    <Transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="-translate-y-1 opacity-0"
      leave-active-class="transition duration-100 ease-in"
      leave-to-class="opacity-0"
    >
      <div
        v-if="message"
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
    </Transition>
  </Teleport>
</template>
