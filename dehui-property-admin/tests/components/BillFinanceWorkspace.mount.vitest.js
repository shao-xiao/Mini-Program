import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, test } from 'vitest'

import BillDetailDrawer from '../../src/views/tenant/finance/BillDetailDrawer.vue'
import BillTenantDirectoryPane from '../../src/views/tenant/finance/BillTenantDirectoryPane.vue'
import { elementStubs } from './elementStubs.js'

const wrappers = []

function mountComponent(component, props) {
  const wrapper = mount(component, {
    props,
    global: { stubs: elementStubs() }
  })
  wrappers.push(wrapper)
  return wrapper
}

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
})

describe('bill finance workspace components', () => {
  test('tenant directory renders all-tenants and real tenant finance summaries', async () => {
    const tenant = {
      id: 6,
      tenantName: '上海云启科技有限公司',
      tenantCode: 'DEMO-TEN-001',
      contactPerson: '张三',
      contactPhone: '13800010001',
      activeContractCount: 2,
      unpaidBillCount: 3,
      overdueBillCount: 1,
      unpaidAmount: 28500
    }
    const wrapper = mountComponent(BillTenantDirectoryPane, {
      tenants: [tenant],
      selectedId: 6,
      search: ''
    })

    expect(wrapper.text()).toContain('全部租户')
    expect(wrapper.text()).toContain('上海云启科技有限公司')
    expect(wrapper.text()).toContain('合同 2')
    expect(wrapper.text()).toContain('待缴 3')
    expect(wrapper.text()).toContain('逾期 1')
    expect(wrapper.findAll('.tenant-item')[1].classes()).toContain('is-selected')
    expect(wrapper.findAll('.tenant-item')[0].attributes('aria-pressed')).toBe('false')
    expect(wrapper.findAll('.tenant-item')[1].attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('.tenant-summary-line').text()).toContain('未收 ¥28,500.00')

    await wrapper.findAll('.tenant-item')[0].trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual([null])
    await wrapper.findAll('.tenant-item')[1].trigger('click')
    expect(wrapper.emitted('select')?.[1]).toEqual([tenant])
  })

  test('bill detail drawer presents persisted fields without inventing source line items', () => {
    const wrapper = mountComponent(BillDetailDrawer, {
      modelValue: true,
      bill: {
        billNumber: 'BIL-001',
        tenantName: '上海云启科技有限公司',
        contractNumber: 'CON-001',
        billType: 'RENT',
        sourceType: 'MANUAL',
        amount: 1000,
        paidAmount: 200,
        auditStatus: 'APPROVED',
        status: 'UNPAID'
      }
    })

    expect(wrapper.text()).toContain('BIL-001')
    expect(wrapper.text()).toContain('上海云启科技有限公司')
    expect(wrapper.text()).toContain('¥800.00')
    expect(wrapper.text()).toContain('费用明细与来源快照')
  })
})
