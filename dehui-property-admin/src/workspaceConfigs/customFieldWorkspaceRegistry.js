const MAXIMUM_CUSTOM_FIELDS = 50

const definitions = [
  entry('buildings', 'buildings', 'building', 'building:view', 'building:update', 'client'),
  entry('floors', 'floors', 'building_floor', 'floor:view', 'floor:update', 'client'),
  entry('rooms', 'rooms', 'building_room', 'room:view', 'room:update', 'server'),
  entry('equipment', 'equipment', 'equipment', 'asset:view', 'asset:update', 'server'),
  entry('tenants', 'tenants', 'tenant', 'tenant:view', 'tenant:update', 'client'),
  entry('leases', 'leases', 'lease', 'lease:view', 'lease:update', 'server'),
  entry('fee-rules', 'fee-rules', 'fee_rule', 'fee-rule:view', 'fee-rule:update', 'server'),
  entry('billing-plans', 'billing-plans', 'billing_plan', 'billing-plan:view', 'billing-plan:update', 'server'),
  entry('investment-leads', 'investment-leads', 'investment_lead', 'investment-lead:view', 'investment-lead:follow', 'server'),
  entry('investment-contents', 'investment-contents', 'investment_content', 'investment-content:view', 'investment-content:update', 'server'),
  entry('investment-listings', 'investment-listings', 'investment_listing', 'investment-listing:view', 'investment-listing:update', 'server'),
  entry('contracts', 'contracts', 'contract', 'contract:view', 'contract:update', 'client'),
  entry('finance-bills', 'bills', 'bill', 'bill:view', 'bill:update', 'server'),
  entry('parking-spaces', 'parking-spaces', 'parking_space', 'parking:view', 'parking:update', 'server'),
  entry('parking-assignments', 'parking-assignments', 'parking_assignment', 'parking:view', 'parking:update', 'client'),
  entry('parking-bills', 'parking-bills', 'parking_bill', 'parking-bill:view', 'parking-bill:update', 'client'),
  entry('meeting-rooms', 'meeting-rooms', 'meeting_room', 'meeting-room:view', 'meeting-room:update', 'client'),
  entry('meeting-bookings', 'meeting-bookings', 'meeting_booking', 'meeting-booking:view', 'meeting-booking:confirm', 'client'),
  entry('workorders', 'workorders', 'work_order', 'workorder:view', 'workorder:update', 'server'),
  entry('visitors', 'visitors', 'visitor_appointment', 'visitor:view', 'visitor:update', 'client'),
  entry('inspections', 'inspections', 'inspection_record', 'inspection:view', 'inspection:update', 'server'),
  entry('announcements', 'announcements', 'notice', 'announcement:view', 'announcement:update', 'client'),
  entry('energy-meters', 'energy-meters', 'energy_meter', 'energy:meter:view', 'energy:meter:update', 'server'),
  entry('energy-readings', 'energy-records', 'energy_reading', 'energy:view', 'energy:reading:update', 'server'),
  entry('system-users', 'system-users', 'system_user', 'system:user:view', 'system:user:update', 'client'),
  entry('system-mobile-users', 'mobile-users', 'mobile_user', 'mobile:view', 'mobile:update', 'server'),
  entry('system-roles', 'system-roles', 'system_role', 'system:role:view', 'system:role:update', 'client')
]

export const CUSTOM_FIELD_WORKSPACE_REGISTRATIONS = Object.freeze(definitions)

const registrationsByConfigId = new Map(definitions.map(item => [item.configId, item]))

export function customFieldWorkspaceRegistration(configId) {
  return registrationsByConfigId.get(String(configId || '').trim()) || null
}

function entry(configId, workspaceKey, entityType, viewPermission, valueUpdatePermission, queryMode) {
  return Object.freeze({
    configId,
    workspaceKey,
    entityType,
    viewPermission,
    valueUpdatePermission,
    queryMode,
    valueSource: queryMode === 'server' ? 'projected' : 'batch',
    maximumCustomFields: MAXIMUM_CUSTOM_FIELDS
  })
}
