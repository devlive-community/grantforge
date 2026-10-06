// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * Splits a page or a query into the tokens the client-side search index stores.
 * Chinese has no spaces, so its single characters are the tokens; Hangul, Cyrillic and
 * Latin words are separated by spaces and stay whole, which the prefix option then extends.
 */
export function searchTokens(text: string): string[] {
  return text.toLowerCase().match(/[a-z0-9][a-z0-9_.-]*|[一-鿿]|[\uAC00-\uD7A3]+|[\u0400-\u04FF]+/g) ?? []
}
