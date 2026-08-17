import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import { createAuthGuard } from './authGuard.js'

function createDependencies(overrides = {}) {
  let token = 'token'
  const calls = { refresh: 0 }
  return {
    calls,
    setToken(value) {
      token = value
    },
    dependencies: {
      readToken: () => token,
      refreshAccess: async () => {
        calls.refresh += 1
      },
      readRoles: () => ['ENGINEER'],
      firstAccessibleRoute: () => '/workorders',
      canAccess: (_roles, path) => path === '/workorders',
      ...overrides
    }
  }
}

test('auth guard keeps login public and does not refresh access there', async () => {
  const context = createDependencies()
  const guard = createAuthGuard(context.dependencies)

  assert.equal(await guard({ path: '/login' }), true)
  assert.equal(context.calls.refresh, 0)
})

test('auth guard redirects unauthenticated navigation to login', async () => {
  const context = createDependencies({ readToken: () => '' })
  const guard = createAuthGuard(context.dependencies)

  assert.equal(await guard({ path: '/workorders' }), '/login')
  assert.equal(context.calls.refresh, 0)
})

test('auth guard refreshes access and resolves the first accessible home route', async () => {
  const context = createDependencies()
  const guard = createAuthGuard(context.dependencies)

  assert.equal(await guard({ path: '/' }), '/workorders')
  assert.equal(context.calls.refresh, 1)
})

test('auth guard preserves accessible pages and sends denied manual URLs to 403', async () => {
  const context = createDependencies()
  const guard = createAuthGuard(context.dependencies)

  assert.equal(await guard({ path: '/workorders' }), true)
  assert.equal(await guard({ path: '/dashboard' }), '/403')
})

test('auth guard routes access refresh failures without creating a 403 loop', async () => {
  const context = createDependencies({
    refreshAccess: async () => {
      throw new Error('access unavailable')
    }
  })
  const guard = createAuthGuard(context.dependencies)

  assert.equal(await guard({ path: '/workorders' }), '/403')
  assert.equal(await guard({ path: '/403' }), true)
})

test('auth guard returns to login when a failed refresh clears the token', async () => {
  const context = createDependencies()
  context.dependencies.refreshAccess = async () => {
    context.setToken('')
    throw new Error('unauthorized')
  }
  const guard = createAuthGuard(context.dependencies)

  assert.equal(await guard({ path: '/workorders' }), '/login')
})

test('router installs the return-style guard without a next callback', () => {
  const source = fs.readFileSync(new URL('./index.js', import.meta.url), 'utf8')
  assert.match(source, /router\.beforeEach\(createAuthGuard\(\)\)/)
  assert.doesNotMatch(source, /beforeEach\s*\([^)]*\bnext\b|\bnext\s*\(/)
})
