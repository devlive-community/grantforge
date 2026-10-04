// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { inject, shallowRef, watchEffect, type App, type Directive, type InjectionKey, type ShallowRef } from 'vue'
import type { GrantForgeClient, UserAuthorization } from './client.js'
import { GrantForgeError } from './errors.js'

/** The signed-in user's permissions, reactive. */
export interface GrantForgeState {
  /** What the user may do, or null before it is known and when nobody is signed in. */
  authorization: ShallowRef<UserAuthorization | null>
  /** Why the last attempt to know failed, or null. */
  error: ShallowRef<GrantForgeError | null>
  /** Asks GrantForge again, as after signing in or out. */
  refresh: () => Promise<void>
  /** Returns whether the user may call an API; false until known. */
  can: (permission: string) => boolean
  /** Returns whether the user may use a resource, such as a button; false until known. */
  hasResource: (resource: string) => boolean
}

const KEY: InjectionKey<GrantForgeState> = Symbol('grantforge')
const HIDDEN = Symbol('grantforge.display')

type Guarded = HTMLElement & { [HIDDEN]?: string, [STOP]?: () => void }
const STOP = Symbol('grantforge.stop')

/**
 * The Vue plugin: provides the user's permissions (useGrantForge) and the directives v-permission and v-resource, which
 * hide an element unless the user holds all the codes given (a code or a list of codes); with the .disable modifier they
 * disable it instead. Hiding is a convenience: the application's APIs must check permissions too.
 */
export function createGrantForge(client: GrantForgeClient) {
  const authorization = shallowRef<UserAuthorization | null>(null)
  const error = shallowRef<GrantForgeError | null>(null)
  const state: GrantForgeState = {
    authorization,
    error,
    async refresh() {
      try {
        authorization.value = await client.authorization()
        error.value = null
      } catch (failure) {
        authorization.value = null
        error.value = failure instanceof GrantForgeError ? failure : new GrantForgeError('unavailable', String(failure))
      }
    },
    can: permission => authorization.value?.permissions.includes(permission) ?? false,
    hasResource: resource => authorization.value?.resources.includes(resource) ?? false,
  }
  return {
    state,
    install(app: App) {
      app.provide(KEY, state)
      app.directive('permission', guard(codes => codes.every(state.can)))
      app.directive('resource', guard(codes => codes.every(state.hasResource)))
      void state.refresh()
    },
  }
}

/** Returns the user's permissions inside components of an application with the plugin installed. */
export function useGrantForge(): GrantForgeState {
  const state = inject(KEY, null)
  if (!state) throw new Error('useGrantForge needs the plugin of createGrantForge installed')
  return state
}

function guard(allowed: (codes: string[]) => boolean): Directive<HTMLElement, string | string[]> {
  return {
    mounted(element, binding) {
      const target = element as Guarded
      target[HIDDEN] = target.style.display
      target[STOP] = watchEffect(() => apply(target, allowed(codesOf(binding.value)), binding.modifiers.disable === true))
    },
    updated(element, binding) {
      const target = element as Guarded
      target[STOP]?.()
      target[STOP] = watchEffect(() => apply(target, allowed(codesOf(binding.value)), binding.modifiers.disable === true))
    },
    unmounted(element) {
      (element as Guarded)[STOP]?.()
    },
  }
}

function apply(element: Guarded, allowed: boolean, disable: boolean) {
  if (disable) {
    if (allowed) element.removeAttribute('disabled'); else element.setAttribute('disabled', '')
    element.setAttribute('aria-disabled', String(!allowed))
    return
  }
  element.style.display = allowed ? element[HIDDEN] ?? '' : 'none'
}

function codesOf(value: string | string[] | undefined): string[] {
  if (value === undefined) return []
  return Array.isArray(value) ? value : [value]
}
