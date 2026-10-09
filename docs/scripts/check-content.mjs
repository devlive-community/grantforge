// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

// Checks the documentation before it is built: every page in the navigation has a file with a title and a
// description, every Markdown file is in the navigation, and every internal link and image points at something
// that exists. Every language has a directory below content/, Chinese included; a translated page must mirror
// the Chinese one and link within its own language, while a missing translation is fine (it falls back at
// build time). Run with `pnpm check`.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const root = process.cwd()
const content = join(root, 'content')
const publicDir = join(root, 'public')
// The directory the Chinese source pages live in, and the languages published next to them, each with its own
// directory and URL prefix.
const SOURCE = 'zh'
const TRANSLATED = ['zh-tw', 'en', 'ru', 'ko', 'ja', 'de', 'fr', 'es', 'pt-br', 'it']
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

function checkLinks(name, text, locale) {
  for (const [, image, target] of text.replace(/```[\s\S]*?```/g, '').matchAll(/(!?)\[[^\]]*\]\(([^)\s]+)\)/g)) {
    if (!target.startsWith('/')) continue
    const path = target.split('#')[0]
    if (image || /\.[a-z0-9]+$/i.test(path)) {
      if (!existsSync(join(publicDir, path))) problems.push(`content/${name}: ${target} is not in public/`)
      continue
    }
    let page = path.replace(/^\/|\/$/g, '')
    if (locale) {
      if (!path.startsWith(`/${locale}/`) && path !== `/${locale}`) {
        problems.push(`content/${name}: ${target} must stay inside /${locale}/ on a ${locale} page`)
        continue
      }
      page = page.replace(new RegExp(`^${locale}/`), '')
    }
    if (page !== '' && !slugs.includes(page)) problems.push(`content/${name}: links to ${target}, which is not a page`)
    else if (!path.endsWith('/')) problems.push(`content/${name}: ${target} should end with a slash, as the exported site does`)
  }
}

for (const slug of slugs) {
  const file = join(content, SOURCE, `${slug}.md`)
  if (!existsSync(file)) {
    problems.push(`${slug}: in the navigation but content/${SOURCE}/${slug}.md does not exist`)
    continue
  }
  const front = frontMatter(file)
  for (const key of ['title', 'description']) {
    if (!new RegExp(`^${key}:\\s*\\S`, 'm').test(front)) problems.push(`content/${SOURCE}/${slug}.md: no ${key} in the front matter`)
  }
}

for (const file of markdownFiles(join(content, SOURCE))) {
  const name = relative(content, file).replace(/\\/g, '/')
  const slug = name.replace(/\.md$/, '').replace(new RegExp(`^${SOURCE}/`), '')
  if (!slugs.includes(slug)) problems.push(`content/${name}: not in the navigation (lib/navigation.ts)`)
  checkLinks(name, readFileSync(file, 'utf8'), false)
}

for (const locale of TRANSLATED) {
  const dir = join(content, locale)
  if (!existsSync(dir)) continue
  for (const file of markdownFiles(dir)) {
    const name = relative(content, file).replace(/\\/g, '/')
    const slug = name.replace(/\.md$/, '').replace(new RegExp(`^${locale}/`), '')
    if (!slugs.includes(slug)) {
      problems.push(`content/${name}: translates ${slug}, which is not in the navigation (lib/navigation.ts)`)
      continue
    }
    const front = frontMatter(file)
    for (const key of ['title', 'description']) {
      if (!new RegExp(`^${key}:\\s*\\S`, 'm').test(front)) problems.push(`content/${name}: no ${key} in the front matter`)
    }
    checkLinks(name, readFileSync(file, 'utf8'), locale)
  }
}

if (problems.length) {
  console.error(problems.join('\n'))
  console.error(`\n${problems.length} problem(s) in the documentation`)
  process.exit(1)
}
console.log(`${slugs.length} pages, links and images are in order`)
