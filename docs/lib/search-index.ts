// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * Splits a page or a query into the tokens the client-side search index stores. Chinese and Japanese share the
 * Han characters, which carry no spaces, so each one is a token of its own; Hangul, Cyrillic, Latin words and
 * Japanese kana runs stay whole, which the prefix option then extends. Accents are folded away first, so a
 * query typed without them still matches a French word written with them; recomposing afterwards puts Hangul
 * back together, because breaking it apart would leave no syllable to match.
 */
export function searchTokens(text: string): string[] {
  const plain = text.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').normalize('NFC')
  return plain.match(/[a-z0-9][a-z0-9_.-]*|[一-鿿]|[\uAC00-\uD7A3]+|[\u0400-\u04FF]+|[\u3040-\u30FF]+/g) ?? []
}
