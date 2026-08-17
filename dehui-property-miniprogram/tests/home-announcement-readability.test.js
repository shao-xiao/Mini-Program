const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')

const root = path.resolve(__dirname, '..')

function read(filePath) {
  return fs.readFileSync(path.join(root, filePath), 'utf8')
}

function cssRule(source, selector) {
  const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = source.match(new RegExp(`${escapedSelector}\\s*\\{([^}]*)\\}`, 's'))
  assert.ok(match, `missing CSS rule: ${selector}`)
  return match[1]
}

function loadIndexModule() {
  let pageDefinition
  const filename = path.join(root, 'pages/index/index.js')
  const source = `${fs.readFileSync(filename, 'utf8')}
globalThis.__announcementTestExports = { getBannerSummary, buildBanners }`
  const sandbox = {
    Page(value) {
      pageDefinition = value
    },
    wx: {
      nextTick(callback) {
        callback()
      }
    },
    setTimeout,
    require(request) {
      if (request === '../../utils/request') return {}
      if (request === '../../utils/capabilities') {
        return {
          getUserType: () => 'PUBLIC',
          isTenantProfile: () => false,
          getVisibleHomeFeatures: () => [],
          getFeatureAccessState: () => ({ allowed: true }),
          canAccessFeature: () => true
        }
      }
      if (request === '../../utils/auth') {
        return {
          ensureWechatSession: async () => ({}),
          refreshCurrentProfile: async () => ({})
        }
      }
      throw new Error(`Unexpected dependency: ${request}`)
    }
  }
  vm.runInNewContext(source, sandbox, { filename })
  return {
    presentation: sandbox.__announcementTestExports,
    pageDefinition
  }
}

function loadAnnouncementPresentation() {
  return loadIndexModule().presentation
}

test('announcement summaries preserve 1, 30, 80 and 200 characters for CSS line clamping', () => {
  const { getBannerSummary, buildBanners } = loadAnnouncementPresentation()

  for (const length of [1, 30, 80, 200]) {
    const content = '园'.repeat(length)
    assert.equal(getBannerSummary({ content }), content)
    assert.equal(buildBanners([{
      id: length,
      title: `公告${length}`,
      content
    }])[0].subtitle, content)
  }

  assert.equal(
    getBannerSummary({ content: '第一行\n  第二行\t第三行' }),
    '第一行 第二行 第三行',
    'only whitespace normalization belongs in the data presentation layer'
  )
})

test('home announcement card uses adaptive height and a natural title-summary-footer flow', () => {
  const script = read('pages/index/index.js')
  const markup = read('pages/index/index.wxml')
  const styles = read('pages/index/index.wxss')

  assert.doesNotMatch(script, /HOME_ANNOUNCEMENT_SUMMARY_LIMIT/)
  assert.doesNotMatch(script, /getBannerSummary[\s\S]{0,240}\.slice\(/)
  assert.match(script, /syncAnnouncementSwiperHeight/)
  assert.match(script, /selectAll\(['"]\.banner-item['"]\)/)
  assert.match(markup, /style="height:\s*\{\{announcementSwiperHeight\}\}px;"/)
  assert.match(markup, /bindchange="onBannerChange"/)
  assert.match(markup, /indicator-dots="\{\{banners\.length > 1\}\}"/)
  assert.doesNotMatch(markup, /class="banner-indicators"[\s\S]*?wx:for="\{\{banners\}\}"/)
  assert.match(
    markup,
    /<view class="banner-item">[\s\S]*?<view class="banner-head">[\s\S]*?<view class="banner-title">\{\{item\.title\}\}<\/view>[\s\S]*?<view class="banner-type">\{\{item\.typeText\}\}<\/view>[\s\S]*?<view class="banner-subtitle"[^>]*>\{\{item\.subtitle\}\}<\/view>[\s\S]*?<view class="banner-footer">[\s\S]*?<view class="banner-time"[^>]*>\{\{item\.time\}\}<\/view>[\s\S]*?<view class="banner-link">/
  )

  const swiperRule = cssRule(styles, '.home-swiper')
  assert.match(swiperRule, /min-height:\s*\d+rpx;/)
  assert.match(swiperRule, /max-height:\s*\d+rpx;/)
  assert.doesNotMatch(swiperRule, /(?:^|[\s;])height:\s*\d+rpx;/)

  const cardRule = cssRule(styles, '.banner-item')
  assert.match(cardRule, /height:\s*auto;/)
  assert.match(cardRule, /min-height:\s*\d+rpx;/)
  assert.match(cardRule, /max-height:\s*\d+rpx;/)
  assert.doesNotMatch(cardRule, /height:\s*100%;/)
  assert.match(cardRule, /display:\s*flex;/)
  assert.match(cardRule, /flex-direction:\s*column;/)

  const titleRule = cssRule(styles, '.banner-title')
  assert.match(titleRule, /white-space:\s*nowrap;/)
  assert.match(titleRule, /text-overflow:\s*ellipsis;/)
  assert.match(titleRule, /overflow:\s*hidden;/)

  const summaryRule = cssRule(styles, '.banner-subtitle')
  assert.match(summaryRule, /-webkit-line-clamp:\s*3;/)
  assert.match(summaryRule, /overflow:\s*hidden;/)

  const footerRule = cssRule(styles, '.banner-footer')
  assert.match(footerRule, /display:\s*(?:flex|grid);/)
  assert.match(footerRule, /align-items:\s*center;/)

  for (const selector of ['.banner-time', '.banner-link']) {
    const rule = cssRule(styles, selector)
    assert.doesNotMatch(rule, /position:\s*absolute;/)
    assert.doesNotMatch(rule, /bottom:\s*\d+rpx;/)
  }
  assert.match(cssRule(styles, '.banner-link'), /margin-left:\s*auto;/)

  assert.doesNotMatch(styles, /content:\s*"DHIC DATA SERVICE"/)
})

test('a stale hidden-slide measurement cannot overwrite the current announcement height', () => {
  const { pageDefinition } = loadIndexModule()
  const rectCallbacks = []
  const page = {
    ...pageDefinition,
    _active: true,
    _announcementMeasureSequence: 0,
    data: {
      ...pageDefinition.data,
      banners: [{ id: 1 }, { id: 2 }],
      currentBannerIndex: 0,
      announcementSwiperHeight: 0
    },
    setData(payload, callback) {
      Object.assign(this.data, payload)
      if (callback) callback()
    },
    createSelectorQuery() {
      return {
        selectAll() {
          return this
        },
        boundingClientRect(callback) {
          rectCallbacks.push(callback)
          return this
        },
        exec() {}
      }
    }
  }

  page.syncAnnouncementSwiperHeight(0)
  page.data.currentBannerIndex = 1
  page.syncAnnouncementSwiperHeight(1)
  assert.equal(rectCallbacks.length, 2)

  const rects = [{ height: 224 }, { height: 300 }]
  rectCallbacks[1](rects)
  rectCallbacks[0](rects)

  assert.equal(page.data.announcementSwiperHeight, 300)
})
