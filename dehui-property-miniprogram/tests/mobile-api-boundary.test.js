const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function javascriptFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const absolutePath = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === 'tests' || entry.name === 'node_modules') return []
      return javascriptFiles(absolutePath)
    }
    return entry.isFile() && entry.name.endsWith('.js') ? [absolutePath] : []
  })
}

function requestPaths(source) {
  const paths = []
  const methodCall = /api\.(?:get|post|put|delete|download|upload)\(\s*['"`]([^'"`]+)['"`]/g
  const requestObject = /api\.request\(\s*\{[\s\S]*?url:\s*['"`]([^'"`]+)['"`]/g

  for (const pattern of [methodCall, requestObject]) {
    for (const match of source.matchAll(pattern)) paths.push(match[1])
  }
  return paths
}

test('all miniprogram backend requests use the mobile API namespace', () => {
  const violations = []

  for (const file of javascriptFiles(root)) {
    const source = fs.readFileSync(file, 'utf8')
    for (const requestPath of requestPaths(source)) {
      if (!requestPath.startsWith('/mobile/')) {
        violations.push(`${path.relative(root, file)}: ${requestPath}`)
      }
    }
  }

  assert.deepEqual(violations, [])
})
