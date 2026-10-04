// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

// The shop's front end: signs users in through GrantForge (public client, PKCE), asks GrantForge what they may do to
// show only the buttons they may use, and calls the shop's API with their token. The API checks again.
import { GrantForgeAuth, GrantForgeClient, GrantForgeError } from '/sdk/index.js'

const config = await (await fetch('/config.json')).json()
const auth = new GrantForgeAuth({ issuer: config.issuer, clientId: config.clientId, redirectUri: `${location.origin}/` })
const grantForge = new GrantForgeClient({ baseUrl: config.issuer, accessToken: () => auth.accessToken(), ttl: 0 })
const element = id => document.getElementById(id)

function fail(failure) {
  element('error').textContent = failure instanceof GrantForgeError ? `${failure.reason}: ${failure.message}` : String(failure)
}

async function api(path, options = {}) {
  const token = await auth.accessToken()
  const response = await fetch(path, { ...options, headers: { ...options.headers, Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } })
  if (!response.ok) throw new Error(`The shop answered ${response.status}`)
  return response.status === 204 ? null : response.json()
}

async function render() {
  const signedIn = auth.signedIn
  element('sign-in').hidden = signedIn
  element('sign-out').hidden = !signedIn
  element('shop').hidden = !signedIn
  element('user').textContent = signedIn ? String(auth.user()?.preferred_username ?? '') : ''
  if (!signedIn) return
  const user = await grantForge.authorization()
  const orders = await api('/api/orders')
  element('orders').replaceChildren(...orders.map(order => {
    const row = document.createElement('tr')
    row.dataset.order = order.title
    row.innerHTML = '<td></td><td></td><td></td><td><button data-resource="shop.orders.btn.delete">Delete</button></td>'
    row.cells[0].textContent = order.title
    row.cells[1].textContent = order.owner
    row.cells[2].textContent = String(order.total)
    row.querySelector('button').addEventListener('click', () => api(`/api/orders/${order.id}`, { method: 'DELETE' }).then(render).catch(fail))
    return row
  }))
  // Buttons the user may not use are not shown.
  for (const guarded of document.querySelectorAll('[data-resource]')) guarded.hidden = !user.resources.includes(guarded.dataset.resource)
}

element('sign-in').addEventListener('click', () => auth.login().catch(fail))
element('sign-out').addEventListener('click', () => auth.logout().then(() => { grantForge.forget(); return render() }).catch(fail))
element('new-order').addEventListener('submit', event => {
  event.preventDefault()
  const form = new FormData(event.target)
  api('/api/orders', { method: 'POST', body: JSON.stringify({ title: form.get('title'), total: Number(form.get('total')) }) })
    .then(() => { event.target.reset(); return render() }).catch(fail)
})

try {
  if (new URLSearchParams(location.search).has('code') || new URLSearchParams(location.search).has('error')) {
    await auth.handleRedirect()
    history.replaceState(null, '', '/')
  }
  await render()
} catch (failure) {
  fail(failure)
}
