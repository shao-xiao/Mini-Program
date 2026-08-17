import { modulePermissionBundles } from './modulePermissionBundles.js'

const activeModuleRules = modulePermissionBundles.map(module => ({
  path: module.route,
  codes: [...module.readCodes, ...module.writeCodes],
  viewCodes: [...module.readCodes]
}))

const legacyAndSupportingRules = [
  { path: '/buildings', codes: ['building:*', 'building:view', 'building:create', 'building:update', 'building:delete'], viewCodes: ['building:*', 'building:view'] },
  { path: '/floors', codes: ['floor:*', 'floor:view', 'floor:create', 'floor:update', 'floor:delete'], viewCodes: ['floor:*', 'floor:view'] },
  { path: '/rooms', codes: ['room:*', 'room:view', 'room:create', 'room:update', 'room:delete', 'room:lease', 'room:checkout'], viewCodes: ['room:*', 'room:view'] },
  { path: '/equipment', codes: ['asset:view', 'asset:create', 'asset:update', 'asset:delete'], viewCodes: ['asset:view'] },
  { path: '/investment/contents', codes: ['investment-content:*', 'investment-content:view', 'investment-content:create', 'investment-content:update', 'investment-content:delete', 'investment-content:publish'], viewCodes: ['investment-content:*', 'investment-content:view'] },
  { path: '/investment/listings', codes: ['investment-listing:*', 'investment-listing:view', 'investment-listing:create', 'investment-listing:update', 'investment-listing:delete', 'investment-listing:publish', 'investment-listing:image'], viewCodes: ['investment-listing:*', 'investment-listing:view'] },
  {
    path: '/contracts',
    codes: ['contract:view', 'contract:create', 'contract:update', 'contract:approve', 'contract:activate', 'contract:terminate', 'contract:cancel', 'contract:delete'],
    viewCodes: ['contract:view']
  },
  { path: '/tenants', codes: ['tenant:*', 'tenant:view', 'tenant:create', 'tenant:update', 'tenant:delete', 'tenant:portal:view'], viewCodes: ['tenant:*', 'tenant:view'] },
  {
    path: '/leases',
    codes: ['lease:view', 'lease:create', 'lease:checkin', 'lease:checkout', 'lease:renew', 'lease:terminate', 'lease:update', 'lease:delete'],
    viewCodes: ['lease:view']
  },
  { path: '/fee-rules', codes: ['fee-rule:view', 'fee-rule:create', 'fee-rule:update', 'fee-rule:activate'], viewCodes: ['fee-rule:view'] },
  { path: '/billing-plans', codes: ['billing-plan:view', 'billing-plan:create', 'billing-plan:update', 'billing-plan:terminate'], viewCodes: ['billing-plan:view'] },
  { path: '/announcements', codes: ['announcement:view', 'announcement:create', 'announcement:update', 'announcement:delete'], viewCodes: ['announcement:view'] },
  { path: '/parking/spaces', codes: ['parking:view', 'parking:create', 'parking:update', 'parking:delete'], viewCodes: ['parking:view'] },
  { path: '/parking/bills', codes: ['parking-bill:view', 'parking-bill:add', 'parking-bill:update', 'parking-bill:pay'], viewCodes: ['parking-bill:view'] },
  { path: '/meetings/rooms', codes: ['meeting-room:*', 'meeting-room:view', 'meeting-room:create', 'meeting-room:update', 'meeting-room:delete'], viewCodes: ['meeting-room:*', 'meeting-room:view'] },
  { path: '/meetings/bookings', codes: ['meeting-booking:*', 'meeting-booking:view', 'meeting-booking:create', 'meeting-booking:confirm', 'meeting-booking:cancel', 'meeting-booking:complete'], viewCodes: ['meeting-booking:*', 'meeting-booking:view'] },
  { path: '/energy/meters', codes: ['energy:meter:view', 'energy:meter:create', 'energy:meter:update', 'energy:meter:delete'], viewCodes: ['energy:meter:view'] },
  { path: '/energy/records', codes: ['energy:view', 'energy:create', 'energy:reading:update', 'energy:reading:delete'], viewCodes: ['energy:view'] },
  { path: '/energy/stats', codes: ['energy:view'], viewCodes: ['energy:view'] },
  { path: '/payments', codes: ['bill:view'], viewCodes: ['bill:view'] },
  { path: '/invoices', codes: ['bill:view'], viewCodes: ['bill:view'] },
  { path: '/logs/operations', codes: ['system:user:view'], viewCodes: ['system:user:view'] },
  { path: '/logs/logins', codes: ['system:user:view'], viewCodes: ['system:user:view'] }
]

export const pathPermissionRules = [...activeModuleRules, ...legacyAndSupportingRules]

const rulesByPath = new Map(pathPermissionRules.map(rule => [rule.path, rule]))

export function getPathPermissionCodes(path) {
  return rulesByPath.get(path)?.codes || []
}

export function getPathViewPermissionCodes(path) {
  const rule = rulesByPath.get(path)
  return rule?.viewCodes || rule?.codes?.filter(code => code.endsWith(':view') || code.endsWith(':*')) || []
}
