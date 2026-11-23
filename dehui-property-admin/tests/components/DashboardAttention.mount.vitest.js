import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import DashboardAttention from '../../src/components/dashboard/DashboardAttention.vue'

it('distinguishes unavailable data and successful zero counts from actionable records', async () => {
  const wrapper = mount(DashboardAttention, {
    props: {
      overdueBills: [],
      expiringContracts: [],
      billHint: '账单加载失败',
      contractHint: '正在加载合同…'
    }
  })
  expect(wrapper.text()).toContain('账单加载失败')
  expect(wrapper.text()).toContain('正在加载合同…')
  expect(wrapper.text()).not.toMatch(/0 [笔份]/)
  await wrapper.setProps({ billHint: '', contractHint: '' })
  expect(wrapper.findAll('button')).toHaveLength(0)
  expect(wrapper.text()).toContain('0 笔')
  expect(wrapper.text()).toContain('0 份')

  await wrapper.setProps({
    expiringContracts: [
      { id: 7, contractNumber: 'C-07', tenantId: 12, tenantName: '甲方租户', endDate: '2026-12-01' },
      { id: 8, contractNumber: 'C-08', tenantId: 24, endDate: '2026-12-02' }
    ]
  })
  const toggle = wrapper.get('button')
  expect(toggle.attributes('aria-expanded')).toBe('false')
  await toggle.trigger('click')
  expect(toggle.attributes('aria-expanded')).toBe('true')
  expect(wrapper.get('tbody').text()).toContain('C-07')
  expect(wrapper.get('tbody').text()).toContain('2026-12-01')
  expect(wrapper.get('tbody').text()).toContain('甲方租户')
  expect(wrapper.get('tbody').text()).toContain('ID 24')
  await toggle.trigger('click')
  expect(wrapper.find('tbody').exists()).toBe(false)
})
