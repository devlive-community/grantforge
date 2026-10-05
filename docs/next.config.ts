// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { NextConfig } from 'next'

// A static site: `next build` writes plain files to out/, which GitHub Pages serves as they are.
const config: NextConfig = {
  output: 'export',
  trailingSlash: true,
  images: { unoptimized: true },
  reactStrictMode: true,
}

export default config
