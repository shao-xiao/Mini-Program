import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, test } from 'vitest'
import BatchToolbar from '../../src/components/BatchToolbar.vue'
import WorkspaceViewManager from '../../src/components/WorkspaceViewManager.vue'
import { elementStubs } from './elementStubs.js'

const wrappers = []

function mountComponent(component, props) {
  const wrapper = mount(component, {
    props,
    global: {
      stubs: elementStubs()
    }
  })
  wrappers.push(wrapper)
  return wrapper
}

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
})

describe('workspace accessibility announcements', () => {
  test('BatchToolbar announces selection count when it appears', () => {
    const wrapper = mountComponent(BatchToolbar, { selectedCount: 3 })
    const status = wrapper.find('[role="status"][aria-live="polite"]')

    expect(status.exists()).toBe(true)
    expect(status.text()).toContain('已选择 3 项')
    expect(wrapper.find('.workspace-batch-toolbar').attributes('aria-label')).toContain('3 项')
  })

  test('WorkspaceViewManager announces dirty and saved states', async () => {
    const wrapper = mountComponent(WorkspaceViewManager, {
      views: [{ id: 'default', name: '默认视图', system: true }],
      activeViewId: 'default',
      viewDirty: true
    })
    const status = wrapper.find('[role="status"][aria-live="polite"]')

    expect(status.text()).toContain('未保存修改')
    expect(wrapper.find('button[aria-label*="未保存修改"]').exists()).toBe(true)

    await wrapper.setProps({ viewDirty: false })
    expect(status.text()).toContain('已保存')
    expect(wrapper.find('button[aria-label="当前视图已保存"]').attributes('disabled')).toBeDefined()
  })
})
