// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { errorOf, GrantForgeError } from './errors.js'

/** What the signed-in user may do in this application, as GrantForge answers it. */
export interface UserAuthorization {
  application: string
  accountId: string
  tenantId: string
  username: string
  version: number
  roles: string[]
  resources: string[]
  permissions: string[]
  computedAt: string
}

/** How the application asks GrantForge about its users. */
export interface ClientOptions {
  /** GrantForge's address. */
  baseUrl: string
  /** Returns the user's access token, such as GrantForgeAuth.accessToken; null when nobody is signed in. */
  accessToken: () => Promise<string | null>
  /** How long an answer is used before it is revalidated with its ETag, in milliseconds; 30 seconds by default. */
  ttl?: number
  /** The fetch to call GrantForge with. */
  fetch?: typeof fetch
  /** The current time in milliseconds. */
  now?: () => number
}

interface Kept { token: string, answer: UserAuthorization, etag: string, checkedAt: number }

/**
 * Asks GrantForge's open API what the signed-in user may do. The answer is kept for the TTL, then revalidated with
 * If-None-Match, which costs GrantForge a 304 while nothing changed.
 */
export class GrantForgeClient {
  private readonly ttl: number
  private readonly fetcher: typeof fetch
  private readonly now: () => number
  private kept: Kept | null = null
  private pending: Promise<UserAuthorization> | null = null

  constructor(private readonly options: ClientOptions) {
    this.ttl = options.ttl ?? 30_000
    this.fetcher = options.fetch ?? ((input, init) => fetch(input, init))
    this.now = options.now ?? (() => Date.now())
  }

  /** Returns what the user may do; throws a GrantForgeError when nobody is signed in or GrantForge refuses. */
  authorization(): Promise<UserAuthorization> {
    this.pending ??= this.load().finally(() => { this.pending = null })
    return this.pending
  }

  /** Returns whether the user may call an API of this application. */
  async can(permission: string): Promise<boolean> {
    return (await this.authorization()).permissions.includes(permission)
  }

  /** Returns whether the user may use a resource of this application, such as a page or a button. */
  async hasResource(resource: string): Promise<boolean> {
    return (await this.authorization()).resources.includes(resource)
  }

  /** Forgets the kept answer, as when the user signs out or another user signs in. */
  forget(): void {
    this.kept = null
  }

  private async load(): Promise<UserAuthorization> {
    const token = await this.options.accessToken()
    if (!token) {
      this.kept = null
      throw new GrantForgeError('unauthenticated', 'Nobody is signed in')
    }
    const kept = this.kept?.token === token ? this.kept : null
    if (kept && kept.checkedAt + this.ttl > this.now()) return kept.answer
    const headers: Record<string, string> = { Authorization: `Bearer ${token}`, Accept: 'application/json' }
    if (kept) headers['If-None-Match'] = kept.etag
    let response: Response
    try {
      response = await this.fetcher(`${this.options.baseUrl.replace(/\/+$/, '')}/api/v1/open/me/authorization`, { headers })
    } catch (failure) {
      throw new GrantForgeError('unavailable', `GrantForge did not answer: ${String(failure)}`)
    }
    if (response.status === 304 && kept) {
      this.kept = { ...kept, checkedAt: this.now() }
      return kept.answer
    }
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) this.kept = null
      throw errorOf(response, 'The open API')
    }
    const answer = await response.json() as UserAuthorization
    this.kept = { token, answer, etag: response.headers.get('ETag') ?? `"${answer.version}"`, checkedAt: this.now() }
    return answer
  }
}
