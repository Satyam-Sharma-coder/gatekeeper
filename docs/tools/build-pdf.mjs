import { execFileSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { Marked } from 'marked'

const here = dirname(fileURLToPath(import.meta.url))
const docsDir = resolve(here, '..')
const outputPdf = join(docsDir, 'Gatekeeper-Documentation.pdf')

const SECTIONS = [
  'README.md',
  '01-system-overview.md',
  '02-getting-started.md',
  '03-backend-api-reference.md',
  '04-data-model.md',
  '05-security-and-auth.md',
  '06-frontend.md',
  '07-end-to-end-flows.md',
  '08-known-issues-and-roadmap.md',
  '09-testing.md',
]

function sectionId(file) {
  return 'doc-' + file.replace(/\.md$/, '').toLowerCase()
}

function slugify(text) {
  return text
    .toLowerCase()
    .replace(/<[^>]*>/g, '')
    .replace(/[^\w\s-]/g, '')
    .trim()
    .replace(/\s+/g, '-')
}

function escapeHtml(value) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

const seen = new Map()
const toc = []

function uniqueId(base) {
  const count = seen.get(base) ?? 0
  seen.set(base, count + 1)
  return count === 0 ? base : `${base}-${count}`
}

const marked = new Marked({
  gfm: true,
  renderer: {
    heading({ tokens, depth, text }) {
      const html = this.parser.parseInline(tokens)
      const id = uniqueId(slugify(text))
      if (depth <= 2) toc.push({ depth, id, title: escapeHtml(text.replace(/`/g, '')) })
      return `<h${depth} id="${id}">${html}</h${depth}>\n`
    },
    link({ href, title, tokens }) {
      const inner = this.parser.parseInline(tokens)
      if (/(^|\/)(index\.html|[^/]+\.pdf)(#.*)?$/.test(href)) return inner
      const local = /^([\w.-]+\.md)(#(.+))?$/.exec(href)
      if (local) {
        const target = local[3] ? local[3] : sectionId(local[1])
        return `<a href="#${target}">${inner}</a>`
      }
      const t = title ? ` title="${escapeHtml(title)}"` : ''
      return `<a href="${href}"${t}>${inner}</a>`
    },
  },
})

const body = SECTIONS.map((file) => {
  const source = readFileSync(join(docsDir, file), 'utf8')
  return `<section class="doc" id="${sectionId(file)}">\n${marked.parse(source)}\n</section>`
}).join('\n')

const generated = new Date().toISOString().slice(0, 10)

const tocHtml = toc
  .filter((entry) => entry.depth === 1 || entry.depth === 2)
  .map((entry) => `<li class="toc-${entry.depth}"><a href="#${entry.id}">${entry.title}</a></li>`)
  .join('\n')

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Gatekeeper Documentation</title>
<style>
  @page { size: A4; margin: 18mm 15mm 20mm 15mm; @bottom-center { content: counter(page); font: 9pt "Segoe UI", sans-serif; color: #64748b; } }
  @page :first { @bottom-center { content: none; } }
  * { box-sizing: border-box; }
  body { font: 10pt/1.5 "Segoe UI", Roboto, Arial, sans-serif; color: #0f172a; margin: 0; }
  h1, h2, h3, h4 { color: #0f172a; line-height: 1.25; break-after: avoid; }
  h1 { font-size: 22pt; border-bottom: 2px solid #4f46e5; padding-bottom: 6pt; margin: 0 0 14pt; }
  h2 { font-size: 15pt; margin: 22pt 0 8pt; color: #312e81; }
  h3 { font-size: 12pt; margin: 16pt 0 6pt; }
  h4 { font-size: 10.5pt; margin: 12pt 0 4pt; }
  p, li { orphans: 3; widows: 3; }
  a { color: #4338ca; text-decoration: none; }
  code { font: 8.6pt Consolas, "Cascadia Mono", monospace; background: #f1f5f9; padding: 1pt 3pt; border-radius: 3pt; }
  pre { background: #0f172a; color: #e2e8f0; padding: 8pt 10pt; border-radius: 5pt; font: 7.6pt/1.35 Consolas, "Cascadia Mono", monospace; white-space: pre; overflow: hidden; break-inside: avoid; }
  pre code { background: none; padding: 0; font: inherit; color: inherit; }
  table { border-collapse: collapse; width: 100%; margin: 8pt 0 12pt; font-size: 8.8pt; break-inside: auto; }
  tr { break-inside: avoid; }
  th, td { border: 1px solid #cbd5e1; padding: 4pt 6pt; text-align: left; vertical-align: top; }
  th { background: #eef2ff; color: #1e1b4b; }
  blockquote { margin: 8pt 0; padding: 6pt 10pt; border-left: 3px solid #6366f1; background: #f8fafc; color: #334155; }
  blockquote p { margin: 0; }
  section.doc { break-before: page; }
  .cover { height: 245mm; display: flex; flex-direction: column; justify-content: center; }
  .cover .eyebrow { color: #4f46e5; font-weight: 600; letter-spacing: .12em; text-transform: uppercase; font-size: 10pt; }
  .cover h1 { font-size: 38pt; border: 0; margin: 6pt 0 10pt; }
  .cover p { font-size: 13pt; color: #475569; max-width: 140mm; }
  .cover .meta { margin-top: 28pt; font-size: 10pt; color: #64748b; }
  .toc { break-before: page; }
  .toc h1 { font-size: 20pt; }
  .toc ul { list-style: none; padding: 0; margin: 0; }
  .toc li.toc-1 { font-weight: 600; margin-top: 8pt; }
  .toc li.toc-2 { margin-left: 14pt; font-size: 9.2pt; }
  .toc-two { columns: 1; }
</style>
</head>
<body>
<div class="cover">
  <div class="eyebrow">Project documentation</div>
  <h1>Gatekeeper</h1>
  <p>A multi-tenant API gateway platform: control plane, admin console and data-plane scaffolds. How it is built, how to run it, and how a change flows through it.</p>
  <div class="meta">Generated ${generated} from the Markdown files in the docs folder.<br>Reflects the state of the source code on 2026-10-29.</div>
</div>
<div class="toc">
  <h1 id="contents">Contents</h1>
  <ul>
${tocHtml}
  </ul>
</div>
${body}
</body>
</html>`

const workDir = mkdtempSync(join(tmpdir(), 'gatekeeper-docs-'))
const htmlPath = join(workDir, 'combined.html')
writeFileSync(htmlPath, html, 'utf8')

function findBrowser() {
  const candidates = [
    process.env.CHROME_PATH,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    '/usr/bin/google-chrome',
    '/usr/bin/chromium',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  ]
  return candidates.find((candidate) => candidate && existsSync(candidate))
}

const browser = findBrowser()
if (!browser) {
  console.error('No Chrome or Edge found. Set CHROME_PATH to the browser executable.')
  process.exit(1)
}

execFileSync(
  browser,
  [
    '--headless=new',
    '--disable-gpu',
    '--no-pdf-header-footer',
    `--user-data-dir=${join(workDir, 'profile')}`,
    `--print-to-pdf=${outputPdf}`,
    pathToFileURL(htmlPath).href,
  ],
  { stdio: 'inherit', timeout: 120000 },
)

console.log(`Wrote ${outputPdf}`)
