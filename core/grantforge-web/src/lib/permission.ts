// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { watchEffect, type Directive, type WatchStopHandle } from 'vue'
import { translate } from '@/i18n'
import { useAuth } from '@/stores/auth'

interface Applied { stop: WatchStopHandle; code: string; display: string }
const applied = new WeakMap<HTMLElement, Applied>()

function apply(el: HTMLElement, code: string, disable: boolean, display: string) {
  const allowed = useAuth().can(code)
  if (!disable) {
    el.style.display = allowed ? display : 'none'
    return
  }
  if (el instanceof HTMLButtonElement || el instanceof HTMLInputElement) el.disabled = el.disabled || !allowed
  el.toggleAttribute('aria-disabled', !allowed)
  if (allowed) el.removeAttribute('title'); else el.title = translate('permissions.denied')
}
function start(el: HTMLElement, code: string, disable: boolean) {
  const display = applied.get(el)?.display ?? el.style.display
  applied.get(el)?.stop()
  applied.set(el, { stop: watchEffect(() => apply(el, code, disable, display)), code, display })
}

/**
 * `v-permission="'system.user.btn.create'"` hides the element unless the user may use that console resource;
 * `v-permission:disable="…"` disables it instead. It follows the permissions as they change. Hiding is a
 * convenience: the server checks every call.
 */
export const vPermission: Directive<HTMLElement, string> = {
  mounted(el, binding) { start(el, binding.value, binding.arg === 'disable') },
  updated(el, binding) {
    const current = applied.get(el)
    if (current?.code !== binding.value) start(el, binding.value, binding.arg === 'disable')
    // A re-render may have reset `disabled`; apply the permission again.
    else apply(el, binding.value, binding.arg === 'disable', current.display)
  },
  unmounted(el) { applied.get(el)?.stop(); applied.delete(el) },
}
