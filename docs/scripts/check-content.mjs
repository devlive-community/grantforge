// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

// Checks the documentation before it is built: every page in the navigation has a file with a title and a
// description, every Markdown file is in the navigation, and every internal link and image points at something
// that exists. Run with `pnpm check`.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const root = process.cwd()
const content = join(root, 'content')
const publicDir = join(root, 'public')
const navigation = readFileSync(join(root, 'lib/navigation.ts'), 'utf8')
const slugs = [...navigation.matchAll(/slug: '([^']+)'/g)].map(match => match[1])
const problems = []

function markdownFiles(dir) {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name)
    return statSync(path).isDirectory() ? markdownFiles(path) : name.endsWith('.md') ? [path] : []
  })
}

for (const slug of slugs) {
  const file = join(content, `${slug}.md`)
  if (!existsSync(file)) {
    problems.push(`${slug}: in the navigation but content/${slug}.md does not exist`)
    continue
  }
  const front = /^---\n([\s\S]*?)\n---/.exec(readFileSync(file, 'utf8'))?.[1] ?? ''
  for (const key of ['title', 'description']) {
    if (!new RegExp(`^${key}:\\s*\\S`, 'm').test(front)) problems.push(`content/${slug}.md: no ${key} in the front matter`)
  }
}

for (const file of markdownFiles(content)) {
  const name = relative(content, file).replace(/\\/g, '/')
  const slug = name.replace(/\.md$/, '')
  if (!slugs.includes(slug)) problems.push(`content/${name}: not in the navigation (lib/navigation.ts)`)
  const text = readFileSync(file, 'utf8').replace(/```[\s\S]*?```/g, '')
  for (const [, image, target] of text.matchAll(/(!?)\[[^\]]*\]\(([^)\s]+)\)/g)) {
    if (!target.startsWith('/')) continue
    const path = target.split('#')[0]
    if (image || /\.[a-z0-9]+$/i.test(path)) {
      if (!existsSync(join(publicDir, path))) problems.push(`content/${name}: ${target} is not in public/`)
    }
    else if (path !== '/' && !slugs.includes(path.replace(/^\/|\/$/g, ''))) {
      problems.push(`content/${name}: links to ${target}, which is not a page`)
    }
    else if (!path.endsWith('/')) {
      problems.push(`content/${name}: ${target} should end with a slash, as the exported site does`)
    }
  }
}

if (problems.length) {
  console.error(problems.join('\n'))
  console.error(`\n${problems.length} problem(s) in the documentation`)
  process.exit(1)
}
console.log(`${slugs.length} pages, links and images are in order`)
