// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

// Checks the documentation before it is built: every page in the navigation has a file with a title and a
// description, every Markdown file is in the navigation, and every internal link and image points at something
// that exists. English pages live in content/en and must mirror a Chinese page; missing translations are fine
// (they fall back at build time), but an English page must link within /en/. Run with `pnpm check`.
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

function frontMatter(file) {
  return /^---\n([\s\S]*?)\n---/.exec(readFileSync(file, 'utf8'))?.[1] ?? ''
}

function checkLinks(name, text, english) {
  for (const [, image, target] of text.replace(/```[\s\S]*?```/g, '').matchAll(/(!?)\[[^\]]*\]\(([^)\s]+)\)/g)) {
    if (!target.startsWith('/')) continue
    const path = target.split('#')[0]
    if (image || /\.[a-z0-9]+$/i.test(path)) {
      if (!existsSync(join(publicDir, path))) problems.push(`content/${name}: ${target} is not in public/`)
      continue
    }
    let page = path.replace(/^\/|\/$/g, '')
    if (english) {
      if (!path.startsWith('/en/') && path !== '/en') {
        problems.push(`content/${name}: ${target} must stay inside /en/ on an English page`)
        continue
      }
      page = page.replace(/^en\//, '')
    }
    if (page !== '' && !slugs.includes(page)) problems.push(`content/${name}: links to ${target}, which is not a page`)
    else if (!path.endsWith('/')) problems.push(`content/${name}: ${target} should end with a slash, as the exported site does`)
  }
}

for (const slug of slugs) {
  const file = join(content, `${slug}.md`)
  if (!existsSync(file)) {
    problems.push(`${slug}: in the navigation but content/${slug}.md does not exist`)
    continue
  }
  const front = frontMatter(file)
  for (const key of ['title', 'description']) {
    if (!new RegExp(`^${key}:\\s*\\S`, 'm').test(front)) problems.push(`content/${slug}.md: no ${key} in the front matter`)
  }
}

for (const file of markdownFiles(content)) {
  const name = relative(content, file).replace(/\\/g, '/')
  if (name.startsWith('en/')) continue
  const slug = name.replace(/\.md$/, '')
  if (!slugs.includes(slug)) problems.push(`content/${name}: not in the navigation (lib/navigation.ts)`)
  checkLinks(name, readFileSync(file, 'utf8'), false)
}

const englishDir = join(content, 'en')
if (existsSync(englishDir)) {
  for (const file of markdownFiles(englishDir)) {
    const name = relative(content, file).replace(/\\/g, '/')
    const slug = name.replace(/\.md$/, '').replace(/^en\//, '')
    if (!slugs.includes(slug)) {
      problems.push(`content/${name}: translates ${slug}, which is not in the navigation (lib/navigation.ts)`)
      continue
    }
    const front = frontMatter(file)
    for (const key of ['title', 'description']) {
      if (!new RegExp(`^${key}:\\s*\\S`, 'm').test(front)) problems.push(`content/${name}: no ${key} in the front matter`)
    }
    checkLinks(name, readFileSync(file, 'utf8'), true)
  }
}

if (problems.length) {
  console.error(problems.join('\n'))
  console.error(`\n${problems.length} problem(s) in the documentation`)
  process.exit(1)
}
console.log(`${slugs.length} pages, links and images are in order`)
