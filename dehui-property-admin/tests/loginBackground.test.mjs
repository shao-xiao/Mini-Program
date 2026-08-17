import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const loginViewPath = resolve(root, 'src/views/Login.vue')
const backgroundPath = resolve(root, 'src/assets/login-background.jpg')
const logoPath = resolve(root, 'src/assets/dehui-logo.png')

const loginView = readFileSync(loginViewPath, 'utf8')

assert.ok(existsSync(backgroundPath), 'login background asset should exist')
assert.ok(existsSync(logoPath), 'login logo asset should exist')
assert.match(loginView, /login-background\.jpg/, 'Login.vue should import the login background asset')
assert.match(loginView, /dehui-logo\.png/, 'Login.vue should import the DHIC logo asset')
assert.match(loginView, /--login-bg-url/, 'Login.vue should expose the background image as a CSS variable')
assert.match(loginView, /background:[\s\S]*var\(--login-bg-url\)/, 'login page should use the background asset in CSS')
assert.match(loginView, /class="login-brand"/, 'login page should have a branded header area')
assert.match(loginView, /class="login-logo"/, 'login page should render the DHIC logo in the login card')
assert.match(loginView, /class="login-subtitle"/, 'login page should include a product subtitle')
assert.match(loginView, /class="login-meta"/, 'login page should include a compact platform footer')
assert.match(loginView, /class="login-card-accent"/, 'login page should include a refined brand accent')
assert.match(loginView, /class="login-system-tag"/, 'login page should include a compact system tag')
assert.match(loginView, /class="login-security-line"/, 'login page should include a concise security status line')
assert.match(loginView, /class="[^"]*\blogin-shell\b[^"]*"/, 'login page should use a composed login shell layout')
assert.match(loginView, /class="[^"]*\blogin-visual-panel\b[^"]*"/, 'login page should include a branded visual panel')
assert.match(loginView, /class="[^"]*\blogin-form-panel\b[^"]*"/, 'login page should keep the form in a dedicated panel')
assert.match(loginView, /class="[^"]*\blogin-page-brand\b[^"]*"/, 'login page should include a persistent page brand mark')
assert.match(loginView, /class="[^"]*\blogin-form-mark\b[^"]*"/, 'login form should include a refined brand mark')
assert.match(loginView, /class="[^"]*\blogin-visual-divider\b[^"]*"/, 'visual panel should include a structured divider')
assert.match(loginView, /class="[^"]*\blogin-card-frame\b[^"]*"/, 'login shell should include a refined structural frame')
assert.match(loginView, /class="[^"]*\blogin-form-divider\b[^"]*"/, 'login form should include a subtle section divider')
assert.match(loginView, /class="[^"]*\blogin-field-label\b[^"]*"/, 'login inputs should have explicit field labels')
assert.match(loginView, /class="[^"]*\blogin-page-footnote\b[^"]*"/, 'login page should include a refined platform footnote')

console.log('login background checks passed')
