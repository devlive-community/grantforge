<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, useId, useTemplateRef, watch, type CSSProperties } from 'vue'
import { autoUpdate, computePosition, flip, hide, offset, shift, type Middleware } from '@floating-ui/dom'
import { AlertCircle, CheckCircle2 } from '@lucide/vue'

/**
 * A validation result that floats over the page instead of pushing it around: a tinted bubble below the control it
 * belongs to, lined up with that control's right edge, with a small triangle pointing back at it. It never takes a
 * pointer event, so nothing underneath it becomes unreachable. It renders inside the control's dialog when the control
 * sits in one, so it stays above the page.
 *
 * Floating UI places it: below the control when there is room for it there, above it when there is not, slid sideways
 * to stay on screen, and hidden while the control is scrolled out of view. The room is what the control itself is
 * clipped by — the window, and any scroller between it and the page, such as a dialog's body — so a tip never covers a
 * dialog's heading or hangs past the edge of the part of a form you can see. It follows the control as the page
 * scrolls, resizes or shifts under it.
 */
const { message = '', state = 'error', anchor = null, control = '' } = defineProps<{
  message?: string
  state?: 'error' | 'success'
  /** The control the tip points at: the input or button itself, not the field around it with its label. */
  anchor?: HTMLElement | null
  /** The id of the control the tip describes, so `aria-describedby` can point here. */
  control?: string
}>()
const own = useId()
const tipId = computed(() => `${control || own}-tip`)
const bubble = useTemplateRef<HTMLElement>('bubble')
// The tip outlives its message for a moment so it can fade out, so the host cannot depend on there being one.
const host = computed(() => anchor?.closest('dialog') ?? document.body)
// A modal dialog sits in the top layer and is the containing block of what renders inside it.
const strategy = computed(() => host.value instanceof HTMLDialogElement ? 'absolute' : 'fixed')
// Which side of its control the tip ended up on, so its triangle points back at it rather than off into the page.
const side = ref<'top' | 'bottom'>('bottom')
// Positioned and hidden from the first frame, so a tip that is not placed yet cannot take up room or flash elsewhere.
const style = ref<CSSProperties>({ position: strategy.value, left: '0px', top: '0px', visibility: 'hidden' })
const tailStyle = ref<CSSProperties>({})
const tones = {
  error: {
    box: 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-500/40 dark:bg-[color-mix(in_oklab,var(--color-rose-500)_16%,var(--color-surface))] dark:text-rose-200',
    icon: 'text-rose-600 dark:text-rose-400',
  },
  success: {
    box: 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-500/40 dark:bg-[color-mix(in_oklab,var(--color-emerald-500)_16%,var(--color-surface))] dark:text-emerald-200',
    icon: 'text-emerald-600 dark:text-emerald-400',
  },
}
const tone = computed(() => tones[state])

// The triangle is 10px square, turned 45°, and keeps this far from the bubble's rounded corners.
const TAIL = 10, CORNER = 12
/**
 * Where the triangle goes along the bubble: under the control's right end, where the bubble lines up, rather than the
 * control's centre, which on a wide input lies far beyond a short bubble. Kept clear of the bubble's corners, so it
 * still points into the control once the bubble had to slide sideways to stay on screen.
 */
const pointer: Middleware = {
  name: 'pointer',
  fn({ x, rects: { reference, floating } }) {
    const target = reference.x + reference.width - Math.min(20, reference.width / 2)
    const left = Math.min(Math.max(target - x - TAIL / 2, CORNER), floating.width - CORNER - TAIL)
    return { data: { left } }
  },
}

async function place() {
  const opener = anchor, floating = bubble.value
  if (!opener || !floating) return
  const middleware = [
    offset(10),
    // altBoundary measures against what clips the control rather than what clips the tip in its host.
    flip({ fallbackPlacements: ['top-end'], altBoundary: true, padding: 8 }),
    shift({ altBoundary: true, padding: 8 }),
    pointer,
    hide(),
  ]
  const at = await computePosition(opener, floating, { placement: 'bottom-end', strategy: strategy.value, middleware })
  side.value = at.placement.startsWith('top') ? 'top' : 'bottom'
  style.value = {
    position: at.strategy,
    left: `${at.x}px`,
    top: `${at.y}px`,
    // A control scrolled out of view leaves the tip nothing to point at, so it waits rather than floating on.
    visibility: at.middlewareData.hide?.referenceHidden ? 'hidden' : 'visible',
  }
  tailStyle.value = { left: `${(at.middlewareData.pointer as { left: number }).left}px` }
}

let stop: (() => void) | undefined
watch([() => anchor, bubble], ([opener, floating]) => {
  stop?.()
  stop = opener && floating ? autoUpdate(opener, floating, () => void place()) : undefined
}, { flush: 'post' })
// A new message may take a different size, and nothing else tells the placement when the browser cannot observe it.
watch(() => [message, state], () => void place(), { flush: 'post' })
onBeforeUnmount(() => stop?.())
</script>

<template>
  <Teleport :to="host">
    <Transition
      enter-active-class="transition-opacity duration-150 ease-out"
      enter-from-class="opacity-0"
      leave-active-class="transition-opacity duration-100 ease-in"
      leave-to-class="opacity-0"
    >
      <div
        v-if="message"
        :id="tipId"
        ref="bubble"
        :role="state === 'success' ? 'status' : 'alert'"
        :style="style"
        class="pointer-events-none z-40 flex w-max max-w-[min(17rem,calc(100vw-1.5rem))] items-center gap-2 rounded-lg border px-2.5 py-1.5 text-xs leading-5 shadow-[0_4px_12px_-4px_rgb(15_23_42/0.16)]"
        :class="tone.box"
      >
        <span
          aria-hidden="true"
          :style="tailStyle"
          class="absolute size-2.5 rotate-45"
          :class="[tone.box, side === 'top' ? '-bottom-[5px] border-b border-r' : '-top-[5px] border-l border-t']"
        ></span>
        <component
          :is="state === 'success' ? CheckCircle2 : AlertCircle"
          :size="14"
          aria-hidden="true"
          class="relative shrink-0"
          :class="tone.icon"
        />
        <span class="relative min-w-0 break-words">{{ message }}</span>
      </div>
    </Transition>
  </Teleport>
</template>
