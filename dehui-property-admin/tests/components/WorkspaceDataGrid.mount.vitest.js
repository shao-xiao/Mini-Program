import { mount } from '@vue/test-utils'
import { h, nextTick } from 'vue'
import { afterEach, describe, expect, test } from 'vitest'
import CellEditor from '../../src/components/CellEditor.vue'
import CustomFieldCellEditor from '../../src/components/workspace/CustomFieldCellEditor.vue'
import WorkspaceDataGrid from '../../src/components/workspace/WorkspaceDataGrid.vue'
import { WORKSPACE_GRID_CONTEXT } from '../../src/components/workspace/workspaceGridContext.js'
import { ElTableStub, elementStubs } from './elementStubs.js'

const mountedWrappers = []

function mountGrid(props = {}, slots = {}, provide = {}) {
  const wrapper = mount(WorkspaceDataGrid, {
    attachTo: document.body,
    props: {
      rows: [{ id: 1, name: '循环泵', status: 'NORMAL' }],
      columns: [
        { key: 'name', label: '设备名称', type: 'text', editable: true, sortable: true },
        {
          key: 'status',
          label: '状态',
          type: 'status',
          optionMap: new Map([['NORMAL', { value: 'NORMAL', label: '正常', tone: 'success' }]]),
          query: { sort: 'server' },
          sortable: true
        }
      ],
      rowKey: 'id',
      dataMode: 'server',
      canEditCell: () => true,
      ...props
    },
    slots,
    global: {
      stubs: elementStubs(),
      provide
    }
  })
  mountedWrappers.push(wrapper)
  return wrapper
}

afterEach(() => {
  mountedWrappers.splice(0).forEach(wrapper => wrapper.unmount())
  document.body.innerHTML = ''
})

describe('WorkspaceDataGrid mounted behavior', () => {
  test('renders dynamic fields, compiled enum labels and business slots', () => {
    const wrapper = mountGrid({}, {
      'cell-name': ({ row }) => h('strong', { class: 'custom-name-cell' }, `自定义:${row.name}`),
      operations: ({ row }) => h('button', { class: 'row-operation' }, `编辑:${row.id}`)
    })

    expect(wrapper.find('[data-column-key="name"]').exists()).toBe(true)
    expect(wrapper.find('.custom-name-cell').text()).toBe('自定义:循环泵')
    expect(wrapper.text()).toContain('正常')
    expect(wrapper.find('.row-operation').text()).toBe('编辑:1')
  })

  test('exposes loading, empty and retryable error states', async () => {
    const wrapper = mountGrid({ rows: [], loading: true })
    expect(wrapper.find('.el-table-stub').attributes('data-loading')).toBe('true')

    await wrapper.setProps({ loading: false, emptyText: '当前筛选无结果' })
    expect(wrapper.find('.workspace-grid-empty').text()).toContain('当前筛选无结果')
    expect(wrapper.find('.workspace-grid-empty').attributes('role')).toBe('status')

    await wrapper.setProps({ errorText: '设备加载失败', retryable: true })
    expect(wrapper.find('.workspace-grid-empty').classes()).toContain('is-error')
    expect(wrapper.find('.workspace-grid-empty').attributes('role')).toBe('alert')
    await wrapper.find('.workspace-grid-empty button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  test('emits stable selection keys and declared sort scope', async () => {
    const group = { __group: true, key: 'NORMAL', label: '正常', count: 1 }
    const row = { id: 7, name: '风机', status: 'NORMAL' }
    const wrapper = mountGrid({ rows: [group, row], showSelection: true })
    const table = wrapper.findComponent(ElTableStub)

    table.vm.$emit('selection-change', [group, row])
    table.vm.$emit('sort-change', { prop: 'status', order: 'descending' })
    await nextTick()

    expect(wrapper.emitted('update:selectedRowKeys').at(-1)).toEqual([[7]])
    expect(wrapper.emitted('selection-change').at(-1)).toEqual([[row]])
    expect(wrapper.emitted('sort-change').at(-1)[0]).toMatchObject({
      field: 'status',
      direction: 'descending',
      scope: 'server'
    })
  })

  test('mounts no editor initially and at most one active editor by keyboard', async () => {
    const wrapper = mountGrid()
    expect(wrapper.findAllComponents(CellEditor)).toHaveLength(0)

    const trigger = wrapper.find('.workspace-grid-cell.is-editable')
    expect(trigger.attributes('role')).toBe('button')
    expect(trigger.attributes('aria-keyshortcuts')).toContain('F2')
    expect(trigger.attributes('tabindex')).toBe('0')

    await trigger.trigger('keydown', { key: 'Enter' })
    expect(wrapper.findAllComponents(CellEditor)).toHaveLength(1)

    await trigger.trigger('keydown', { key: 'Escape' })
    expect(wrapper.findAllComponents(CellEditor)).toHaveLength(0)
    expect(wrapper.emitted('cell-cancel')).toHaveLength(1)
  })

  test('routes custom fields through the custom field editor without changing native fields', async () => {
    const wrapper = mountGrid({
      rows: [{ id: 1, name: 'native', customFields: { custom_note: 'old' } }],
      columns: [
        { key: 'name', label: 'Name', type: 'text', editable: true },
        { key: 'custom_note', label: 'Note', type: 'text', editable: true, custom: true }
      ]
    })
    const cells = wrapper.findAll('.workspace-grid-cell.is-editable')

    await cells[0].trigger('keydown', { key: 'Enter' })
    expect(wrapper.findAllComponents(CellEditor)).toHaveLength(1)
    expect(wrapper.findAllComponents(CustomFieldCellEditor)).toHaveLength(0)
    await cells[0].trigger('keydown', { key: 'Escape' })

    await cells[1].trigger('keydown', { key: 'Enter' })
    expect(wrapper.findAllComponents(CellEditor)).toHaveLength(0)
    expect(wrapper.findAllComponents(CustomFieldCellEditor)).toHaveLength(1)
  })

  test('routes registered platform values through the local workspace controller', async () => {
    const saves = []
    const customField = {
      id: 21,
      key: 'custom_note',
      fieldCode: 'custom_note',
      label: 'Note',
      type: 'text',
      editable: true,
      custom: true,
      platformCustom: true
    }
    const wrapper = mountGrid({
      rows: [{ id: 1, customFields: { custom_note: 'old' } }],
      columns: [customField],
      canEditCell: () => false
    }, {}, {
      [WORKSPACE_GRID_CONTEXT]: {
        provided: true,
        customFields: {
          enabled: true,
          canEditCell: () => true,
          isSaving: () => false,
          saveCell: async (row, field, value) => {
            saves.push({ row, field, value })
            row.customFields[field.key] = value
            return { success: true }
          }
        }
      }
    })

    const trigger = wrapper.find('.workspace-grid-cell.is-editable')
    await trigger.trigger('keydown', { key: 'Enter' })
    const input = wrapper.find('input[type="text"]')
    await input.setValue('new')
    await input.trigger('keyup', { key: 'Enter' })
    await nextTick()
    await nextTick()

    expect(saves).toHaveLength(1)
    expect(saves[0]).toMatchObject({ value: 'new' })
    expect(wrapper.emitted('cell-save')).toBeUndefined()
    expect(wrapper.emitted('custom-field-save')).toHaveLength(1)
    expect(wrapper.findAllComponents(CustomFieldCellEditor)).toHaveLength(0)
  })

  test('keeps failed edits mounted and focused while readonly cells never enter edit mode', async () => {
    const wrapper = mountGrid()
    const trigger = wrapper.find('.workspace-grid-cell.is-editable')
    await trigger.trigger('keydown', { key: 'F2' })

    const input = wrapper.find('.cell-editor input')
    await input.setValue('新名称')
    await input.trigger('keyup', { key: 'Enter' })
    const savePayload = wrapper.emitted('cell-save').at(-1)[0]
    savePayload.complete({ success: false, message: '保存失败' })
    await nextTick()
    await nextTick()

    expect(wrapper.findAllComponents(CellEditor)).toHaveLength(1)
    expect(wrapper.text()).toContain('保存失败')
    expect(document.activeElement).toBe(wrapper.find('.cell-editor input').element)

    const readonly = mountGrid({ canEditCell: () => false })
    const readonlyCell = readonly.find('.workspace-grid-cell')
    expect(readonlyCell.attributes('tabindex')).toBeUndefined()
    expect(readonlyCell.attributes('role')).toBeUndefined()
    await readonlyCell.trigger('dblclick')
    expect(readonly.findAllComponents(CellEditor)).toHaveLength(0)
  })

  test('group rows cannot be selected or edited', () => {
    const wrapper = mountGrid({
      rows: [
        { __group: true, key: 'FAULT', label: '故障', count: 1 },
        { id: 9, name: '电梯', status: 'NORMAL' }
      ],
      showSelection: true
    })
    const controls = wrapper.findAll('.row-selection-control')

    expect(controls[0].attributes('disabled')).toBeDefined()
    expect(controls[1].attributes('disabled')).toBeUndefined()
    expect(wrapper.findAll('.workspace-grid-cell.is-editable')).toHaveLength(1)
  })
})
