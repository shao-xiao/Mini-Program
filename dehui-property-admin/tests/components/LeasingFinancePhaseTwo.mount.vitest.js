import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'

import BillingPlanList from '../../src/views/tenant/BillingPlanList.vue'
import FeeRuleList from '../../src/views/tenant/FeeRuleList.vue'
import { elementStubs } from './elementStubs.js'

const api = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn()
}))

vi.mock('../../src/utils/request', () => ({ default: api }))
vi.mock('../../src/utils/permission', () => ({ hasPermission: () => true }))

const wrappers = []

const SlotStub = defineComponent({
  name: 'SlotStub',
  setup(_props, { slots }) {
    return () => h('div', [slots.header?.(), slots.default?.(), slots.footer?.()])
  }
})

function mountPage(component) {
  const wrapper = mount(component, {
    global: {
      stubs: {
        ...elementStubs(),
        ElAlert: SlotStub,
        ElCard: SlotStub,
        ElDialog: SlotStub,
        ElEmpty: SlotStub,
        ElForm: SlotStub,
        ElFormItem: SlotStub,
        ElPagination: SlotStub,
        ListSummaryGrid: SlotStub
      }
    }
  })
  wrappers.push(wrapper)
  return wrapper
}

beforeEach(() => {
  api.get.mockImplementation(url => {
    if (url === '/fee-rules/search' || url === '/billing-plans/search') {
      return Promise.resolve({ records: [], total: 0, page: 1, pageSize: 20 })
    }
    if (url === '/fee-rules/summary') {
      return Promise.resolve({ totalCount: 8, draftCount: 2, activeCount: 5, disabledCount: 1 })
    }
    if (url === '/billing-plans/summary') {
      return Promise.resolve({
        totalCount: 6,
        draftCount: 1,
        activeCount: 4,
        terminatedCount: 1,
        estimatedPeriodAmount: 12000
      })
    }
    return Promise.resolve([])
  })
})

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
})

describe('phase-two leasing finance pages', () => {
  test('FeeRuleList mounts and requests independent server list and summary data', async () => {
    const wrapper = mountPage(FeeRuleList)
    await flushPromises()

    expect(wrapper.exists()).toBe(true)
    expect(api.get).toHaveBeenCalledWith('/fee-rules/search', expect.objectContaining({
      params: expect.objectContaining({ page: 1, pageSize: 20 }),
      silent: true
    }))
    expect(api.get).toHaveBeenCalledWith('/fee-rules/summary', expect.objectContaining({ silent: true }))
  })

  test('BillingPlanList mounts and requests independent server list and summary data', async () => {
    const wrapper = mountPage(BillingPlanList)
    await flushPromises()

    expect(wrapper.exists()).toBe(true)
    expect(api.get).toHaveBeenCalledWith('/billing-plans/search', expect.objectContaining({
      params: expect.objectContaining({ page: 1, pageSize: 20 }),
      silent: true
    }))
    expect(api.get).toHaveBeenCalledWith('/billing-plans/summary', expect.objectContaining({ silent: true }))
  })
})
