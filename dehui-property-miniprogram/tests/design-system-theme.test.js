const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const themePath = path.join(root, 'styles', 'theme.wxss')

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

function listFiles(directory, extension) {
  const result = []
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const absolute = path.join(directory, entry.name)
    if (entry.isDirectory()) result.push(...listFiles(absolute, extension))
    else if (entry.isFile() && entry.name.endsWith(extension)) result.push(absolute)
  }
  return result
}

test('global theme defines the DHIC brand, technology and semantic design tokens', () => {
  const theme = fs.readFileSync(themePath, 'utf8')
  assert.match(theme, /--dhic-brand-primary:\s*#D71920/i)
  assert.match(theme, /--dhic-tech-800:\s*#163B5C/i)
  assert.match(theme, /--dhic-tech-700:\s*#1F4E79/i)
  assert.match(theme, /--dhic-page-bg:\s*#F5F7FA/i)
  assert.match(theme, /--dhic-text-primary:\s*#1F2937/i)
  assert.match(theme, /--dhic-success:/)
  assert.match(theme, /--dhic-warning:/)
  assert.match(theme, /--dhic-purple:/)
  assert.match(theme, /--dhic-orange-strong:\s*#A65313/i)
  assert.match(
    theme,
    /--dhic-action-primary:\s*var\(--dhic-page-action-primary,\s*var\(--dhic-tech-primary\)\)/
  )
  assert.match(
    theme,
    /--dhic-action-soft:\s*var\(--dhic-page-action-soft,\s*var\(--dhic-tech-soft\)\)/
  )
  assert.match(
    theme,
    /--dhic-action-rgb:\s*var\(--dhic-page-action-rgb,\s*var\(--dhic-tech-primary-rgb\)\)/
  )
  assert.match(theme, /--dhic-shadow-card:/)
  assert.match(theme, /--dhic-shadow-action:[^;]*var\(--dhic-action-rgb\)/)
})

test('all WXSS color declarations outside the theme use semantic tokens', () => {
  const offenders = []
  for (const file of listFiles(root, '.wxss')) {
    if (file === themePath) continue
    const source = fs.readFileSync(file, 'utf8')
    const directHex = source.match(/#[0-9a-f]{3,8}\b/gi) || []
    const directFunctional = source.match(/\b(?:rgb|rgba|hsl|hsla)\(\s*\d/gi) || []
    if (directHex.length || directFunctional.length) {
      offenders.push({
        file: path.relative(root, file),
        values: [...directHex, ...directFunctional]
      })
    }
  }
  assert.deepEqual(offenders, [])
})

test('global primitives and isolated components consume the shared theme', () => {
  const appStyle = read('app.wxss')
  const primitives = read('styles/primitives.wxss')
  assert.match(appStyle, /@import\s+"\.\/styles\/theme\.wxss"/)
  assert.match(appStyle, /@import\s+"\.\/styles\/primitives\.wxss"/)
  assert.match(appStyle, /\.card\s*\{[\s\S]*?var\(--dhic-shadow-card\)/)
  assert.match(primitives, /\.ui-card/)
  assert.match(primitives, /\.ui-button--primary/)
  assert.match(primitives, /\.ui-tag--success/)
  assert.match(primitives, /\.ui-empty/)

  const componentStyles = listFiles(path.join(root, 'components'), '.wxss')
  for (const file of componentStyles) {
    const source = fs.readFileSync(file, 'utf8')
    assert.match(
      source,
      /@import\s+"\.\.\/\.\.\/styles\/theme\.wxss"/,
      `${path.relative(root, file)} must import the global theme`
    )
  }
})

test('native navigation and tab bar use the technology palette', () => {
  const appConfig = JSON.parse(read('app.json'))
  const homeConfig = JSON.parse(read('pages/index/index.json'))
  const mineConfig = JSON.parse(read('pages/mine/index.json'))
  assert.equal(appConfig.window.navigationBarBackgroundColor, '#163B5C')
  assert.equal(appConfig.window.navigationBarTextStyle, 'white')
  assert.equal(appConfig.window.backgroundColor, '#F5F7FA')
  assert.equal(appConfig.tabBar.selectedColor, '#1E496F')
  assert.equal(appConfig.tabBar.color, '#64748B')
  assert.equal(homeConfig.navigationBarBackgroundColor, undefined)
  assert.equal(homeConfig.navigationBarTextStyle, undefined)
  assert.equal(homeConfig.navigationBarBackgroundColor ?? appConfig.window.navigationBarBackgroundColor, '#163B5C')
  assert.equal(homeConfig.navigationBarTextStyle ?? appConfig.window.navigationBarTextStyle, 'white')
  assert.equal(mineConfig.navigationBarBackgroundColor ?? appConfig.window.navigationBarBackgroundColor, '#163B5C')
  assert.equal(mineConfig.navigationBarTextStyle ?? appConfig.window.navigationBarTextStyle, 'white')
})

test('native WeChat controls use the current DHIC brand red fallback', () => {
  const nativeUiFiles = [
    'utils/privacy.js',
    'pages/index/index.js',
    'pages/mine/index.js',
    'pages/me/index.wxml',
    'pages/meeting/index.js',
    'pages/meeting/index.wxml',
    'pages/visitors/index.js',
    'pages/workorders/index.js',
    'subPackages/staff/pages/visitors/detail.js'
  ]
  for (const file of nativeUiFiles) {
    const source = read(file)
    assert.doesNotMatch(source, /#(?:E4312B|d93025)\b/i, `${file} uses a legacy red`)
    assert.match(source, /#D71920/i, `${file} must use the native brand fallback`)
  }
})

test('home keeps its structure with one technology header and one module surface', () => {
  const markup = read('pages/index/index.wxml')
  const style = read('pages/index/index.wxss')
  assert.match(markup, /class="card intro-card"/)
  assert.match(markup, /class="brand-logo-column"/)
  assert.match(markup, />德汇创新中心</)
  assert.match(markup, /class="subtitle">旨在为高新技术、科技研发及创新型总部企业提供聚合发展与赋能升级的理想平台。<\/view>/)
  assert.match(markup, /class="intro-address">上海市浦东新区川桥路1089号<\/view>/)
  assert.match(markup, /class="intro-contact-label">合作请致电：<\/text>/)
  assert.match(markup, /class="intro-contact-number">021-65686336<\/text>/)
  assert.ok(
    markup.indexOf('class="intro-address"') < markup.indexOf('class="intro-contact"'),
    'the address should appear before the contact action'
  )
  assert.match(markup, /class="card banner-card"/)
  assert.match(markup, /class="module-grid"/)
  assert.match(
    style,
    /\.intro-card\s*\{[^}]*min-height:\s*288rpx;[^}]*padding:\s*34rpx 38rpx;[^}]*background:\s*var\(--dhic-gradient-tech\);/s
  )
  assert.match(style, /\.brand-logo-column\s*\{[^}]*width:\s*21%;[^}]*flex:\s*0 0 21%;/s)
  assert.match(style, /\.brand-logo\s*\{[^}]*width:\s*68rpx;[^}]*height:\s*78rpx;/s)
  assert.match(
    style,
    /\.banner-card\s*\{[^}]*background:\s*linear-gradient\(160deg,\s*var\(--dhic-info-soft\)\s*0%,\s*var\(--dhic-surface\)\s*72%\);/s
  )
  assert.match(style, /\.title\s*\{[^}]*color:\s*var\(--dhic-text-inverse\);/s)
  assert.match(style, /\.subtitle\s*\{[^}]*color:\s*rgba\(var\(--dhic-white-rgb\),\s*0\.84\);/s)
  assert.match(style, /\.intro-contact-label\s*\{[^}]*font-weight:\s*var\(--dhic-weight-heading\);/s)
  assert.match(
    style,
    /\.intro-contact-number\s*\{[^}]*font-weight:\s*var\(--dhic-weight-title\);[^}]*color:\s*rgba\(var\(--dhic-white-rgb\),\s*0\.98\);/s
  )
  assert.match(
    style,
    /\.intro-address\s*\{[^}]*font-size:\s*22rpx;[^}]*color:\s*rgba\(var\(--dhic-white-rgb\),\s*0\.68\);/s
  )
  assert.match(markup, /class="banner-type">\{\{item\.typeText\}\}<\/view>/)
  assert.doesNotMatch(style, /content:\s*"DHIC DATA SERVICE"/)
  assert.doesNotMatch(style, /\.intro-card::before/)
  assert.doesNotMatch(style, /\.module-item::before/)
  assert.doesNotMatch(style, /\.module-item\[data-key="[^"]+"\]::before/)
  assert.doesNotMatch(style, /\.module-item\[data-key=/)
  assert.doesNotMatch(style, /border-top:\s*4rpx\s+solid/)
  assert.match(
    style,
    /\.module-item\s*\{[^}]*background:\s*linear-gradient\(160deg,\s*var\(--dhic-info-soft\)\s*0%,\s*var\(--dhic-surface\)\s*72%\);[^}]*box-shadow:\s*none;/s
  )
  assert.match(style, /\.module-item-active\s*\{[^}]*opacity:\s*0\.86;[^}]*box-shadow:\s*none;[^}]*transform:\s*scale\(0\.985\);/s)
})

test('page actions inherit the service palette while danger actions stay explicit', () => {
  const pagePalettes = {
    'pages/announcements/index.wxss': '--dhic-info',
    'pages/bills/index.wxss': '--dhic-info',
    'pages/business-detail/index.wxss': '--dhic-info',
    'pages/contracts/index.wxss': '--dhic-tech-700',
    'pages/energy/index.wxss': '--dhic-tech-600',
    'pages/parking/index.wxss': '--dhic-tech-600',
    'pages/meeting/index.wxss': '--dhic-purple',
    'pages/workorders/index.wxss': '--dhic-orange-strong',
    'pages/visitors/index.wxss': '--dhic-success',
    'pages/investment/index.wxss': '--dhic-tech-600',
    'pages/investment/detail/index.wxss': '--dhic-tech-600',
    'pages/me/index.wxss': '--dhic-tech-primary',
    'pages/mine/index.wxss': '--dhic-tech-primary',
    'subPackages/staff/pages/inspections/index.wxss': '--dhic-tech-600',
    'subPackages/staff/pages/inspections/form.wxss': '--dhic-tech-600',
    'subPackages/staff/pages/workorders/index.wxss': '--dhic-orange-strong',
    'subPackages/staff/pages/workorders/process.wxss': '--dhic-orange-strong',
    'subPackages/staff/pages/energy/index.wxss': '--dhic-tech-600',
    'subPackages/staff/pages/energy/reading-form.wxss': '--dhic-tech-600',
    'subPackages/staff/pages/meetings/index.wxss': '--dhic-purple',
    'subPackages/staff/pages/visitors/index.wxss': '--dhic-success',
    'subPackages/staff/pages/investment/index.wxss': '--dhic-tech-700'
  }

  for (const [file, token] of Object.entries(pagePalettes)) {
    const style = read(file)
    assert.ok(
      style.includes(`--dhic-page-action-primary: var(${token});`),
      `${file} must declare ${token} as its page action palette`
    )
  }

  const appStyle = read('app.wxss')
  const primitives = read('styles/primitives.wxss')
  assert.match(appStyle, /\.button-primary\s*\{[^}]*background:\s*var\(--dhic-action-primary\)/s)
  assert.match(appStyle, /\.staff-button\s*\{[^}]*background:\s*var\(--dhic-action-primary\)/s)
  assert.match(appStyle, /\.staff-button\.secondary\s*\{[^}]*background:\s*var\(--dhic-action-soft\)/s)
  assert.match(primitives, /\.ui-button--primary\s*\{[^}]*background:\s*var\(--dhic-action-primary\)/s)
  assert.match(primitives, /\.ui-button--ghost\s*\{[^}]*background:\s*var\(--dhic-action-soft\)/s)

  const contextualComponents = [
    'components/calendar-date-field/index.wxss',
    'components/calendar-time-field/index.wxss',
    'components/dynamic-form/index.wxss'
  ]
  for (const file of contextualComponents) {
    assert.match(read(file), /var\(--dhic-action-(?:primary|soft)\)/, `${file} must inherit the page action palette`)
  }

  const investmentStyle = read('pages/investment/index.wxss')
  assert.match(
    investmentStyle,
    /\.primary-button,\s*\.submit-button\s*\{[^}]*background:\s*var\(--dhic-action-primary\);[^}]*var\(--dhic-shadow-action\);/s
  )
  assert.match(
    investmentStyle,
    /\.investment-news-card\s*\{[^}]*background:\s*linear-gradient\(160deg,\s*var\(--dhic-info-soft\)\s*0%,\s*var\(--dhic-tech-soft\)\s*100%\);/s
  )

  const visitorStyle = read('pages/visitors/index.wxss')
  assert.match(
    visitorStyle,
    /\.tenant-submit-button,\s*\.public-submit-button\s*\{[^}]*background:\s*var\(--dhic-action-primary\);[^}]*var\(--dhic-shadow-action\);/s
  )
  assert.match(
    visitorStyle,
    /\.visitor-data-page \.reservation-history-card,\s*\.visitor-data-page \.tenant-history-panel\s*\{[^}]*background:\s*linear-gradient\(160deg,\s*var\(--dhic-info-soft\)\s*0%,\s*var\(--dhic-tech-soft\)\s*100%\);/s
  )
  assert.match(visitorStyle, /\.cancel-button\s*\{[^}]*color:\s*var\(--dhic-danger\)/s)

  const meetingStyle = read('pages/meeting/index.wxss')
  assert.match(
    meetingStyle,
    /\.submit-button\s*\{[^}]*background:\s*var\(--dhic-action-primary\);[^}]*var\(--dhic-shadow-action\);/s
  )

  const redActionOffenders = []
  for (const file of listFiles(root, '.wxss')) {
    if (file === themePath) continue
    const source = fs.readFileSync(file, 'utf8')
    for (const block of source.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
      if (
        /(?:button|action|link|filter|chip)/i.test(block[1]) &&
        /var\(--dhic-brand-primary\)/.test(block[2])
      ) {
        redActionOffenders.push(`${path.relative(root, file)}: ${block[1].trim()}`)
      }
    }
  }
  assert.deepEqual(redActionOffenders, [])
})

test('account management keeps a compact flat hierarchy without decorative top bars', () => {
  const style = read('pages/me/index.wxss')
  assert.doesNotMatch(style, /\.login-card::before/)
  assert.doesNotMatch(style, /\.account-card::before/)
  assert.match(style, /\.login-card,[\s\S]*?\.state-panel\s*\{[^}]*border:\s*1rpx solid var\(--dhic-border-card\);[^}]*box-shadow:\s*none;/s)
  assert.match(style, /\.account-card \.outline-button\.retry-button\s*\{[^}]*width:\s*100%;/s)
  assert.match(style, /\.account-page > \.outline-button\.retry-button\s*\{[^}]*width:\s*100%;/s)
  assert.match(style, /\.primary-button\s*\{[^}]*box-shadow:\s*none;/s)
})
