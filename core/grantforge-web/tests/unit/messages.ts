// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/** Flattens a message dictionary into dotted keys and their messages. */
export function flatten(messages: object, prefix = ''): Record<string, string> {
  return Object.entries(messages).reduce<Record<string, string>>((all, [key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key
    return typeof value === 'string' ? { ...all, [path]: value } : { ...all, ...flatten(value as object, path) }
  }, {})
}
