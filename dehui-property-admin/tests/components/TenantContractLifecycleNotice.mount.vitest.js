import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TenantContractLifecycleNotice from '../../src/views/tenant/workspace/TenantContractLifecycleNotice.vue'

describe('tenant contract lifecycle notice', () => {
  it('explains why draft and pending contracts are absent from the rental summary', async () => {
    const draft = { id: 17, contractNo: 'HT-DRAFT', status: 'DRAFT' }
    const pending = { id: 19, contractNo: 'HT-PENDING', status: 'PENDING_APPROVAL' }
    const wrapper = mount(TenantContractLifecycleNotice, {
      props: {
        contracts: [
          draft,
          pending,
          { id: 20, contractNo: 'HT-ACTIVE', status: 'ACTIVE' }
        ]
      }
    })

    expect(wrapper.get('[aria-label="待生效合同提示"]').text()).toContain('2 份合同尚未生效')
    expect(wrapper.text()).toContain('草稿 1')
    expect(wrapper.text()).toContain('待审批 1')
    expect(wrapper.text()).toContain('租赁摘要仅统计已审批生效且在当前租期内的合同')

    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('select-contract')).toEqual([[draft]])
  })

  it('does not occupy overview space when there is no actionable inactive contract', () => {
    const wrapper = mount(TenantContractLifecycleNotice, {
      props: {
        contracts: [
          { id: 20, contractNo: 'HT-ACTIVE', status: 'ACTIVE' },
          { id: 21, contractNo: 'HT-CANCELLED', status: 'CANCELLED' }
        ]
      }
    })

    expect(wrapper.find('[aria-label="待生效合同提示"]').exists()).toBe(false)
  })
})
