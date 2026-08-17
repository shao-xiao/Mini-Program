import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, describe, expect, test } from 'vitest'
import CustomFieldCellEditor from '../../src/components/workspace/CustomFieldCellEditor.vue'
import CustomFieldCellRenderer from '../../src/components/workspace/CustomFieldCellRenderer.vue'
import CustomFieldFormSection from '../../src/components/workspace/CustomFieldFormSection.vue'
import { elementStubs } from './elementStubs.js'

const wrappers = []
const fields = [
  { key: 'custom_name', fieldCode: 'custom_name', label: 'Name', type: 'text', custom: true, required: true },
  { key: 'custom_budget', fieldCode: 'custom_budget', label: 'Budget', type: 'money', custom: true, config: { scale: 2 } },
  {
    key: 'custom_level',
    fieldCode: 'custom_level',
    label: 'Level',
    type: 'select',
    custom: true,
    options: [{ value: 'A', label: 'Level A' }, { value: 'B', label: 'Level B' }]
  },
  { key: 'custom_reviewed', fieldCode: 'custom_reviewed', label: 'Reviewed', type: 'checkbox', custom: true }
]

function mountComponent(component, options = {}) {
  const wrapper = mount(component, {
    attachTo: document.body,
    global: { stubs: elementStubs() },
    ...options
  })
  wrappers.push(wrapper)
  return wrapper
}

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  document.body.innerHTML = ''
})

describe('custom field value components', () => {
  test('renders option labels through the compiled option map', () => {
    const wrapper = mountComponent(CustomFieldCellRenderer, {
      props: {
        field: fields[2],
        row: { id: 1, customFields: { custom_level: 'A' } },
        optionMap: new Map([['A', { value: 'A', label: 'Level A', tone: 'success' }]])
      }
    })

    expect(wrapper.text()).toContain('Level A')
  })

  test('keeps the editor open on validation failure and emits typed values on save', async () => {
    const required = mountComponent(CustomFieldCellEditor, {
      props: { field: fields[0], row: { id: 1, customFields: { custom_name: 'old' } }, editable: true }
    })
    await nextTick()
    const requiredInput = required.find('input')
    await requiredInput.setValue('')
    await requiredInput.trigger('keyup', { key: 'Enter' })
    expect(required.emitted('save')).toBeUndefined()
    expect(required.text()).toContain('Name')
    expect(required.find('input').exists()).toBe(true)

    const numeric = mountComponent(CustomFieldCellEditor, {
      props: { field: fields[1], row: { id: 2, customFields: { custom_budget: 10 } }, editable: true }
    })
    await nextTick()
    const numberInput = numeric.find('input[type="number"]')
    await numberInput.setValue('25.5')
    await numberInput.trigger('keyup', { key: 'Enter' })
    const emitted = numeric.emitted('save').at(-1)
    expect(emitted[0]).toBe(25.5)
    emitted[1]({ success: true })
    await nextTick()
  })

  test('renders one shared form section for all implemented value types', async () => {
    const wrapper = mountComponent(CustomFieldFormSection, {
      props: {
        fields,
        modelValue: { custom_name: 'alpha', custom_budget: 5, custom_level: 'A', custom_reviewed: false },
        editable: true
      }
    })

    expect(wrapper.findAll('.custom-field-form-item')).toHaveLength(4)
    expect(wrapper.text()).toContain('Name')
    expect(wrapper.text()).toContain('Budget')
    const nameInput = wrapper.find('input[type="text"]')
    await nameInput.setValue('beta')
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({ custom_name: 'beta' })
  })
})
