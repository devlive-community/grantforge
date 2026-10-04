// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { discover, type Endpoints } from './discovery.js'
import { errorOf, GrantForgeError } from './errors.js'
import { challengeOf, claimsOf, randomToken } from './pkce.js'

/** How a browser application signs its users in through GrantForge. */
export interface AuthOptions {
  /** GrantForge's address, the issuer of its tokens, such as https://grantforge.example.com. */
  issuer: string
  /** The application's public client ID, registered in GrantForge's resource catalog. */
  clientId: string
  /** Where GrantForge sends users back to; one of the client's redirect URIs. */
  redirectUri: string
  /** The scopes to ask for; openid and permissions by default. */
  scope?: string
  /** Where tokens and pending sign-ins are kept; the session storage by default, so they end with the tab. */
  storage?: Storage
  /** The fetch to call GrantForge with. */
  fetch?: typeof fetch
  /** Navigates the browser; location.assign by default. */
  navigate?: (url: string) => void
  /** The current time in milliseconds. */
  now?: () => number
}

/** The tokens of the signed-in user. */
export interface Tokens {
  accessToken: string
  refreshToken: string | null
  idToken: string | null
  /** When the access token expires, in milliseconds. */
  expiresAt: number
}

interface Pending { verifier: string, nonce: string, returnTo: string | null }

const TOKENS = 'grantforge.tokens'
const PENDING = 'grantforge.pending.'
/** Access tokens are renewed this long before they expire, so calls do not race their expiry. */
const EARLY = 30_000

/**
 * Signs users in through GrantForge with the authorization code flow and PKCE, as a public client, and keeps their tokens
 * fresh. Refresh tokens rotate on every use and GrantForge revokes everything when a replaced one comes back, so one
 * refresh runs at a time however many calls need a token.
 */
export class GrantForgeAuth {
  private readonly scope: string
  private readonly storage: Storage
  private readonly fetcher: typeof fetch
  private readonly navigate: (url: string) => void
  private readonly now: () => number
  private endpoints: Promise<Endpoints> | null = null
  private refreshing: Promise<Tokens | null> | null = null

  constructor(private readonly options: AuthOptions) {
    this.scope = options.scope ?? 'openid permissions'
    this.storage = options.storage ?? sessionStorage
    this.fetcher = options.fetch ?? ((input, init) => fetch(input, init))
    this.navigate = options.navigate ?? (url => window.location.assign(url))
    this.now = options.now ?? (() => Date.now())
  }

  /** Sends the browser to GrantForge to sign in; it comes back to the redirect URI, where handleRedirect takes over. */
  async login(returnTo: string | null = null): Promise<void> {
    const endpoints = await this.discovered()
    const state = randomToken(), verifier = randomToken(), nonce = randomToken()
    this.storage.setItem(PENDING + state, JSON.stringify({ verifier, nonce, returnTo } satisfies Pending))
    const query = new URLSearchParams({ response_type: 'code', client_id: this.options.clientId, redirect_uri: this.options.redirectUri,
      scope: this.scope, state, nonce, code_challenge: await challengeOf(verifier), code_challenge_method: 'S256' })
    this.navigate(`${endpoints.authorization}?${query}`)
  }

  /**
   * Finishes a sign-in at the redirect URI: exchanges the code for tokens and checks the ID token's nonce.
   *
   * @returns where the user was when login was called, if given
   */
  async handleRedirect(url: string = window.location.href): Promise<{ returnTo: string | null }> {
    const params = new URL(url).searchParams
    const error = params.get('error')
    if (error) throw new GrantForgeError(error === 'access_denied' ? 'forbidden' : 'invalid', `GrantForge refused the sign-in: ${error}`)
    const code = params.get('code'), state = params.get('state')
    const stored = state ? this.storage.getItem(PENDING + state) : null
    if (!code || !state || !stored) throw new GrantForgeError('invalid', 'No sign-in of this browser waits for this answer')
    this.storage.removeItem(PENDING + state)
    const pending = JSON.parse(stored) as Pending
    const tokens = await this.tokenRequest({ grant_type: 'authorization_code', code, redirect_uri: this.options.redirectUri,
      code_verifier: pending.verifier })
    if (tokens.idToken && claimsOf(tokens.idToken).nonce !== pending.nonce) {
      this.storage.removeItem(TOKENS)
      throw new GrantForgeError('invalid', 'The ID token does not answer this sign-in')
    }
    return { returnTo: pending.returnTo }
  }

  /** Returns a valid access token, refreshing it when it is about to expire; null if the user must sign in again. */
  async accessToken(): Promise<string | null> {
    const tokens = this.stored()
    if (!tokens) return null
    if (tokens.expiresAt - EARLY > this.now()) return tokens.accessToken
    if (!tokens.refreshToken) return null
    const refreshed = await this.refresh(tokens.refreshToken)
    return refreshed?.accessToken ?? null
  }

  /** Returns whether a user is signed in in this browser. */
  get signedIn(): boolean {
    return this.stored() !== null
  }

  /** Returns the claims of the ID token: sub, preferred_username, name, email, tid. */
  user(): Record<string, unknown> | null {
    const idToken = this.stored()?.idToken
    return idToken ? claimsOf(idToken) : null
  }

  /** Forgets the user's tokens in this browser and asks GrantForge to revoke the refresh token. */
  async logout(): Promise<void> {
    const tokens = this.stored()
    this.storage.removeItem(TOKENS)
    if (!tokens?.refreshToken) return
    try {
      const endpoints = await this.discovered()
      if (endpoints.revocation) {
        await this.fetcher(endpoints.revocation, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({ token: tokens.refreshToken, token_type_hint: 'refresh_token', client_id: this.options.clientId }) })
      }
    } catch {
      // Signing out locally does not wait on GrantForge; the token expires anyway.
    }
  }

  /** Forgets the tokens without telling GrantForge, as when it refused them. */
  forget(): void {
    this.storage.removeItem(TOKENS)
  }

  private refresh(refreshToken: string): Promise<Tokens | null> {
    this.refreshing ??= this.tokenRequest({ grant_type: 'refresh_token', refresh_token: refreshToken })
      .then(tokens => tokens as Tokens | null)
      .catch(failure => {
        // A refused refresh token will not work again: the user signs in anew.
        if (failure instanceof GrantForgeError && failure.reason !== 'unavailable') {
          this.storage.removeItem(TOKENS)
          return null
        }
        throw failure
      })
      .finally(() => { this.refreshing = null })
    return this.refreshing
  }

  private async tokenRequest(form: Record<string, string>): Promise<Tokens> {
    const endpoints = await this.discovered()
    let response: Response
    try {
      response = await this.fetcher(endpoints.token, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ ...form, client_id: this.options.clientId }) })
    } catch (failure) {
      throw new GrantForgeError('unavailable', `GrantForge did not answer: ${String(failure)}`)
    }
    if (!response.ok) {
      // OAuth answers refused grants with 400 invalid_grant: the user must sign in again.
      throw errorOf(response.status === 400 ? new Response(null, { status: 401 }) : response, 'The token endpoint')
    }
    const answer = await response.json() as Record<string, unknown>
    if (typeof answer.access_token !== 'string') throw new GrantForgeError('unavailable', 'The token endpoint answered without a token')
    const previous = this.stored()
    const tokens: Tokens = {
      accessToken: answer.access_token,
      refreshToken: typeof answer.refresh_token === 'string' ? answer.refresh_token : previous?.refreshToken ?? null,
      idToken: typeof answer.id_token === 'string' ? answer.id_token : previous?.idToken ?? null,
      expiresAt: this.now() + (typeof answer.expires_in === 'number' ? answer.expires_in : 300) * 1000,
    }
    this.storage.setItem(TOKENS, JSON.stringify(tokens))
    return tokens
  }

  private stored(): Tokens | null {
    const stored = this.storage.getItem(TOKENS)
    if (!stored) return null
    try {
      return JSON.parse(stored) as Tokens
    } catch {
      return null
    }
  }

  private discovered(): Promise<Endpoints> {
    this.endpoints ??= discover(this.options.issuer, this.fetcher).catch(failure => {
      this.endpoints = null
      throw failure
    })
    return this.endpoints
  }
}
