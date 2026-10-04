// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/** Where GrantForge and the samples run, and who signs in. */
export const GRANTFORGE = process.env.GRANTFORGE_BASE_URL || 'http://127.0.0.1:19080'
export const SHOP = 'http://127.0.0.1:19081'
export const NOTES = 'http://127.0.0.1:19082'
// The full-stack suite, which runs first, ends with the administrator's password changed to this one.
export const ADMIN = { username: 'admin', password: process.env.GRANTFORGE_ADMIN_PASSWORD || 'a brand new long password' }
export const SAM = { username: 'sam', password: 'a long password of his own' }
