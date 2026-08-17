import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const viewsRoot = fileURLToPath(new URL('../src/views/', import.meta.url))
const mainSource = readFileSync(new URL('../src/main.js', import.meta.url), 'utf8')

function vueFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const fullPath = path.join(directory, entry.name)
    if (entry.isDirectory()) return vueFiles(fullPath)
    return entry.isFile() && entry.name.endsWith('.vue') && !entry.name.endsWith('.bak.vue')
      ? [fullPath]
      : []
  })
}

test('active admin views do not leak rejected requests through async form-validation callbacks', () => {
  const offenders = vueFiles(viewsRoot)
    .filter(file => /\.validate\(\s*async\s+/.test(readFileSync(file, 'utf8')))
    .map(file => path.relative(viewsRoot, file))

  assert.deepEqual(offenders, [])
})

test('direct view mutation requests are awaited, returned, or owned by an awaited batch', () => {
  const offenders = []

  for (const file of vueFiles(viewsRoot)) {
    const lines = readFileSync(file, 'utf8').split(/\r?\n/)
    lines.forEach((line, index) => {
      if (!/request\.(post|put|patch|delete)\(/.test(line)) return
      if (/\bawait\b|\breturn\b|Promise\.all(?:Settled)?\(/.test(line)) return
      offenders.push(`${path.relative(viewsRoot, file)}:${index + 1}`)
    })
  }

  assert.deepEqual(offenders, [])
})

test('the Vue error boundary only suppresses request errors already shown to the user', () => {
  assert.match(mainSource, /if \(error\?\.requestMessageShown\) return/)
  assert.match(mainSource, /console\.error\(`\[Vue\] \$\{info\}`/)
})
