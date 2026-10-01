// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * Console pages and the resource code that grants each one. Pages missing here, such as the dashboard, are
 * open to every signed-in user. Hiding a page is a convenience; the server still checks every API call.
 */
export const pageResources: Readonly<Record<string, string>> = {
  '/admin/users': 'system.user',
  '/admin/org': 'system.org',
  '/admin/groups': 'system.group',
  '/admin/positions': 'system.position',
  '/admin/transfer': 'system.transfer',
  '/admin/sessions': 'system.session',
  '/platform/tenants': 'platform.tenant',
  '/platform/resources': 'platform.resource',
  '/platform/apis': 'platform.api',
}

/** Returns the resource code that grants the page at `path`, or undefined when every signed-in user may open it. */
export function pageResource(path: string): string | undefined {
  return Object.hasOwn(pageResources, path) ? pageResources[path] : undefined
}
