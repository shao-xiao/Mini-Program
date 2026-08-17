const FALLBACK_CAPABILITIES = Object.freeze({
  PUBLIC: Object.freeze([]),
  TENANT: Object.freeze([]),
  STAFF: Object.freeze([])
})

const FEATURE_DEFINITIONS = Object.freeze({
  announcement: Object.freeze({
    key: 'announcement',
    title: '园区公告',
    description: '查看园区通知与活动公告',
    url: '/pages/announcements/index',
    capability: 'announcement.view',
    publicRoute: true
  }),
  investment: Object.freeze({
    key: 'investment',
    title: '招商中心',
    description: '查看真实招商房源',
    url: '/pages/investment/index',
    capability: 'investment.view',
    publicRoute: true
  }),
  meetingPublic: Object.freeze({
    key: 'meetingPublic',
    title: '会议预约',
    description: '查看会议室，手机号快捷登录后预约',
    url: '/pages/meeting/index?mode=public',
    capability: 'meeting.public.view',
    publicRoute: true
  }),
  visitorGuide: Object.freeze({
    key: 'visitorGuide',
    title: '访客预约',
    description: '无需登录即可提交访客预约',
    url: '/pages/visitors/index?mode=public',
    publicRoute: true
  }),
  investmentBooking: Object.freeze({
    key: 'investmentBooking',
    title: '预约看房',
    description: '提交并查看看房预约',
    url: '/pages/investment/index',
    capability: 'investment.booking.create'
  }),
  bill: Object.freeze({
    key: 'bill',
    title: '账单中心',
    description: '查看企业账单',
    url: '/pages/bills/index',
    capability: 'bill.view',
    tenantOnly: true
  }),
  contract: Object.freeze({
    key: 'contract',
    title: '合同信息',
    description: '查看当前企业合同',
    url: '/pages/contracts/index',
    capability: 'contract.view',
    tenantOnly: true
  }),
  rooms: Object.freeze({
    key: 'rooms',
    title: '房间信息',
    description: '查看当前企业租赁房间',
    url: '/pages/rooms/index',
    capability: 'contract.view',
    tenantOnly: true
  }),
  myBusiness: Object.freeze({
    key: 'myBusiness',
    title: '我的业务',
    description: '汇总当前身份可查看的业务',
    url: '/pages/my-business/index',
    capability: 'my-business.view',
    tenantOnly: true
  }),
  workorder: Object.freeze({
    key: 'workorder',
    title: '报修工单',
    description: '提交报修并查看处理进度',
    url: '/pages/workorders/index',
    capability: 'workorder.view',
    tenantOnly: true
  }),
  meeting: Object.freeze({
    key: 'meeting',
    title: '会议预约',
    description: '预约会议室并查看本人记录',
    url: '/pages/meeting/index',
    capability: 'meeting.book',
    tenantOnly: true
  }),
  energy: Object.freeze({
    key: 'energy',
    title: '能耗查询',
    description: '查看后台账单形成的用量与费用',
    url: '/pages/energy/index',
    capabilities: Object.freeze(['energy.company.view', 'energy.staff.view']),
    tenantOnly: true,
    staffAllowed: true
  }),
  parkingCompany: Object.freeze({
    key: 'parkingCompany',
    title: '企业车位',
    description: '查看企业有效车位',
    url: '/pages/parking/index?scope=company',
    capability: 'parking.company.view',
    tenantOnly: true
  }),
  parkingMine: Object.freeze({
    key: 'parkingMine',
    title: '我的车位',
    description: '查看本人车位与车辆',
    url: '/pages/parking/index?scope=mine',
    capability: 'parking.mine.view',
    tenantOnly: true
  }),
  visitor: Object.freeze({
    key: 'visitor',
    title: '访客预约',
    description: '提交预约并查看审核与到访状态',
    url: '/pages/visitors/index',
    capability: 'visitor.create',
    tenantOnly: true
  }),
  staffWorkbench: Object.freeze({
    key: 'staffWorkbench',
    title: '物业移动工作台',
    description: '处理巡检、工单、能耗与园区预约',
    url: '/subPackages/staff/pages/workbench/index',
    capabilities: Object.freeze([
      'inspection.staff.view',
      'workorder.staff.view',
      'energy.staff.view',
      'meeting.staff.view',
      'visitor.staff.view',
      'investment.lead.staff.view'
    ]),
    staffOnly: true
  }),
  staffInspection: Object.freeze({
    key: 'staffInspection',
    title: '巡检记录',
    description: '巡检记录、异常处理与现场照片',
    url: '/subPackages/staff/pages/inspections/index',
    capability: 'inspection.staff.view',
    staffOnly: true
  }),
  staffWorkorder: Object.freeze({
    key: 'staffWorkorder',
    title: '报修工单',
    description: '处理指派给当前员工的工单',
    url: '/subPackages/staff/pages/workorders/index',
    capability: 'workorder.staff.view',
    staffOnly: true
  }),
  staffEnergy: Object.freeze({
    key: 'staffEnergy',
    title: '能耗统计',
    description: '企业能耗、表具历史与移动抄表',
    url: '/subPackages/staff/pages/energy/index',
    capability: 'energy.staff.view',
    staffOnly: true
  }),
  staffMeeting: Object.freeze({
    key: 'staffMeeting',
    title: '会议预约',
    description: '查看园区真实会议预约记录',
    url: '/subPackages/staff/pages/meetings/index',
    capability: 'meeting.staff.view',
    staffOnly: true
  }),
  staffVisitor: Object.freeze({
    key: 'staffVisitor',
    title: '访客预约',
    description: '查看预约并核验到访与离场',
    url: '/subPackages/staff/pages/visitors/index',
    capability: 'visitor.staff.view',
    staffOnly: true
  }),
  staffInvestment: Object.freeze({
    key: 'staffInvestment',
    title: '看房预约',
    description: '跟进小程序招商线索与看房状态',
    url: '/subPackages/staff/pages/investment/index',
    capability: 'investment.lead.staff.view',
    staffOnly: true
  })
})

const FEATURE_ALIASES = Object.freeze({
  announcements: 'announcement',
  investmentLead: 'investmentBooking',
  investmentBookingCreate: 'investmentBooking',
  bills: 'bill',
  contracts: 'contract',
  workorders: 'workorder',
  meetings: 'meeting',
  energySummary: 'energy',
  parking: 'parkingMine',
  visitors: 'visitor'
})

const PUBLIC_HOME_ORDER = Object.freeze(['announcement', 'investment', 'meetingPublic', 'visitorGuide'])
const TENANT_HOME_ORDER = Object.freeze([
  'announcement',
  'investment',
  'myBusiness',
  'bill',
  'rooms',
  'energy',
  'parkingCompany',
  'meeting',
  'workorder',
  'visitor'
])
const STAFF_HOME_ORDER = Object.freeze(['announcement', 'investment', 'staffWorkbench'])
const MINE_ORDER = Object.freeze([
  'myBusiness',
  'bill',
  'rooms',
  'contract',
  'energy',
  'parkingCompany',
  'parkingMine',
  'meeting',
  'workorder',
  'visitor'
])

function readStoredProfile() {
  try {
    const auth = require('./auth')
    return typeof auth.getStoredProfile === 'function' ? auth.getStoredProfile() : null
  } catch (error) {
    return null
  }
}

function unwrapProfile(profile) {
  const source = typeof profile === 'undefined' ? readStoredProfile() : profile
  if (!source || typeof source !== 'object') return {}
  if (source.userType || Array.isArray(source.capabilities)) return source
  if (source.profile && typeof source.profile === 'object') {
    return { ...source, ...source.profile }
  }
  return source
}

function getUserType(profile) {
  const source = unwrapProfile(profile)
  const value = source.userType || source.identity || source.role || 'PUBLIC'
  const normalized = String(value).trim().toUpperCase()
  if (normalized === 'TENANT') return 'TENANT'
  if (normalized === 'INTERNAL' || normalized === 'STAFF') return 'STAFF'
  return 'PUBLIC'
}

function isTenantProfile(profile) {
  return getUserType(profile) === 'TENANT'
}

function getCapabilities(profile) {
  const source = unwrapProfile(profile)
  if (!Array.isArray(source.capabilities)) return []
  return Array.from(new Set(source.capabilities
    .map(item => String(item || '').trim())
    .filter(Boolean)))
}

function hasCapability(code, profile) {
  const normalized = String(code || '').trim()
  return Boolean(normalized && getCapabilities(profile).includes(normalized))
}

function canonicalFeatureKey(featureKey) {
  const key = String(featureKey || '').trim()
  return FEATURE_ALIASES[key] || key
}

function getFeatureAccessState(featureKey, profile) {
  const key = canonicalFeatureKey(featureKey)
  const feature = FEATURE_DEFINITIONS[key]
  const userType = getUserType(profile)
  if (!feature) {
    return {
      featureKey: key,
      userType,
      allowed: false,
      loginRequired: false,
      forbidden: true,
      reason: 'unknown-feature'
    }
  }
  if (feature.publicRoute) {
    return {
      featureKey: key,
      userType,
      allowed: true,
      loginRequired: false,
      forbidden: false,
      reason: 'public-route'
    }
  }
  const identityAllowed = Boolean(
    feature.staffOnly
      ? userType === 'STAFF'
      : (!feature.tenantOnly ||
        userType === 'TENANT' ||
        (feature.staffAllowed && userType === 'STAFF'))
  )
  const capabilityCodes = feature.capabilities || [feature.capability]
  const allowed = Boolean(identityAllowed && capabilityCodes.some(code => hasCapability(code, profile)))
  const loginRequired = !allowed && feature.tenantOnly && userType === 'PUBLIC'
  return {
    featureKey: key,
    capability: feature.capability || capabilityCodes[0],
    userType,
    allowed,
    loginRequired,
    forbidden: !allowed && !loginRequired,
    reason: allowed ? 'allowed' : (loginRequired ? 'login-required' : 'forbidden')
  }
}

function canAccessFeature(featureKey, profile) {
  return getFeatureAccessState(featureKey, profile).allowed
}

function visibleFeatures(keys, profile) {
  return keys
    .filter(key => canAccessFeature(key, profile))
    .map(key => ({ ...FEATURE_DEFINITIONS[key] }))
}

function getVisibleHomeFeatures(profile) {
  const userType = getUserType(profile)
  const order = userType === 'TENANT'
    ? TENANT_HOME_ORDER
    : (userType === 'STAFF' ? STAFF_HOME_ORDER : PUBLIC_HOME_ORDER)
  return visibleFeatures(order, profile)
}

function getVisibleMineFeatures(profile) {
  const userType = getUserType(profile)
  if (userType === 'TENANT') return visibleFeatures(MINE_ORDER, profile)
  if (userType === 'STAFF') return visibleFeatures(['staffWorkbench'], profile)
  return []
}

module.exports = {
  getUserType,
  isTenantProfile,
  getCapabilities,
  hasCapability,
  canAccessFeature,
  getVisibleHomeFeatures,
  getVisibleMineFeatures,
  getFeatureAccessState,
  FALLBACK_CAPABILITIES,
  FEATURE_DEFINITIONS,
  STAFF_FEATURE_KEYS: Object.freeze([
    'staffInspection',
    'staffWorkorder',
    'staffEnergy',
    'staffMeeting',
    'staffVisitor',
    'staffInvestment'
  ])
}
