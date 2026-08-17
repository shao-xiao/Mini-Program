import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, test, vi } from 'vitest'
import FieldManager from '../../src/components/FieldManager.vue'
import FilterBuilderPopover from '../../src/components/workspace/FilterBuilderPopover.vue'
import GroupBuilderPopover from '../../src/components/workspace/GroupBuilderPopover.vue'
import RowDensityMenu from '../../src/components/workspace/RowDensityMenu.vue'
import SortBuilderPopover from '../../src/components/workspace/SortBuilderPopover.vue'
import { WORKSPACE_TOOLBAR_CONTEXT } from '../../src/components/workspace/workspaceToolbarContext.js'
import { elementStubs } from './elementStubs.js'

const mountedWrappers = []

function createController() {
  const columns = [
    {
      key: 'status',
      label: '状态',
      type: 'select',
      sortable: true,
      groupable: true,
      options: [{ label: '正常', value: 'NORMAL' }],
      query: {
        mode: 'server',
        param: 'status',
        operators: ['equals'],
        sort: 'server',
        group: 'page'
      }
    },
    {
      key: 'name',
      label: '名称',
      type: 'text',
      required: true,
      sortable: true,
      query: {
        mode: 'server',
        param: 'keyword',
        operators: ['contains'],
        sort: 'server',
        group: 'page'
      }
    }
  ]
  return {
    provided: true,
    query: {
      dataMode: 'server',
      filterOptions: columns,
      filters: [],
      filterLogic: 'and',
      filterLogics: ['and', 'or'],
      sortOptions: columns,
      sorts: [],
      sortKey: '',
      sortOrder: '',
      groupOptions: columns,
      groups: [],
      groupKey: '',
      activeFilterCount: 0,
      filterItems: [],
      updateFilters: vi.fn(),
      updateFilterLogic: vi.fn(),
      updateSorts: vi.fn(),
      updateGroups: vi.fn()
    },
    fields: {
      columns,
      visibleColumns: ['status', 'name'],
      columnOrder: ['status', 'name'],
      fixedColumns: {},
      customColumns: [],
      customFieldDefinitions: [],
      canManageCustomFields: false,
      customFieldLoading: false,
      customFieldSaving: false,
      customFieldError: '',
      allowCustomFields: false,
      allowColumnLayout: false,
      saveable: true,
      dirty: true,
      updateVisibleColumns: vi.fn(),
      updateColumnOrder: vi.fn(),
      updateFixedColumns: vi.fn(),
      updateCustomColumns: vi.fn(),
      createCustomColumn: vi.fn(),
      createCustomField: vi.fn(),
      updateCustomField: vi.fn(),
      changeCustomFieldStatus: vi.fn(),
      deleteCustomField: vi.fn(),
      reorderCustomFields: vi.fn(),
      retryCustomFields: vi.fn(),
      save: vi.fn()
    },
    view: {
      rowDensity: 'standard',
      updateRowDensity: vi.fn(),
      reset: vi.fn()
    },
    transfer: {
      workspaceConfig: null,
      loadingActionIds: [],
      disabledActionIds: [],
      runAction: vi.fn(),
      selectFile: vi.fn()
    }
  }
}

function mountControl(component, controller = createController()) {
  const wrapper = mount(component, {
    global: {
      provide: {
        [WORKSPACE_TOOLBAR_CONTEXT]: controller
      },
      stubs: elementStubs()
    }
  })
  mountedWrappers.push(wrapper)
  return { wrapper, controller }
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find(button => button.text().includes(text))
}

afterEach(() => {
  mountedWrappers.splice(0).forEach(wrapper => wrapper.unmount())
})

describe('workspace toolbar mounted controls', () => {
  test('FilterBuilderPopover adds declared conditions and changes AND/OR logic', async () => {
    const { wrapper, controller } = mountControl(FilterBuilderPopover)

    await buttonByText(wrapper, '添加条件').trigger('click')
    expect(controller.query.updateFilters).toHaveBeenCalledWith([
      { key: 'status', operator: 'equals', value: '' }
    ])

    await buttonByText(wrapper, '满足任一').trigger('click')
    expect(controller.query.updateFilterLogic).toHaveBeenCalledWith('or')
  })

  test('SortBuilderPopover adds a unique server sort', async () => {
    const { wrapper, controller } = mountControl(SortBuilderPopover)

    await buttonByText(wrapper, '添加排序').trigger('click')
    expect(controller.query.updateSorts).toHaveBeenCalledWith([
      { key: 'status', order: 'ascending' }
    ])
  })

  test('GroupBuilderPopover exposes current-page scope and adds a unique group', async () => {
    const controller = createController()
    controller.query.groups = [{ key: 'status', order: 'ascending' }]
    const { wrapper } = mountControl(GroupBuilderPopover, controller)

    expect(wrapper.text()).toContain('仅当前页')
    await buttonByText(wrapper, '添加分组').trigger('click')
    expect(controller.query.updateGroups).toHaveBeenCalledWith([
      { key: 'status', order: 'ascending' },
      { key: 'name', order: 'ascending' }
    ])
  })

  test('FieldManager updates visibility and saves through the local controller', async () => {
    const { wrapper, controller } = mountControl(FieldManager)
    const statusToggle = wrapper.find('input[aria-label="隐藏状态"]')

    expect(statusToggle.exists()).toBe(true)
    await statusToggle.setValue(false)
    expect(controller.fields.updateVisibleColumns).toHaveBeenCalledWith(['name'])

    await buttonByText(wrapper, '保存到当前视图').trigger('click')
    expect(controller.fields.save).toHaveBeenCalledOnce()
  })

  test('FieldManager creates a persisted shared field without mutating legacy local columns', async () => {
    const controller = createController()
    controller.fields.allowCustomFields = true
    controller.fields.canManageCustomFields = true
    controller.fields.createCustomField = vi.fn(async payload => ({
      id: 9,
      fieldCode: 'custom_customer_level',
      fieldName: payload.fieldName,
      fieldType: payload.fieldType
    }))
    const { wrapper } = mountControl(FieldManager, controller)

    await buttonByText(wrapper, '新增字段').trigger('click')
    await wrapper.find('input[aria-label="字段名称"]').setValue('客户等级')
    await buttonByText(wrapper, '创建字段').trigger('click')

    expect(controller.fields.createCustomField).toHaveBeenCalledOnce()
    expect(controller.fields.createCustomField.mock.calls[0][0]).toMatchObject({
      fieldName: '客户等级',
      fieldType: 'TEXT'
    })
    expect(controller.fields.updateCustomColumns).not.toHaveBeenCalled()
  })

  test('FieldManager duplicates and disables shared definitions through platform actions', async () => {
    const controller = createController()
    controller.fields.allowCustomFields = true
    controller.fields.canManageCustomFields = true
    controller.fields.customFieldDefinitions = [{
      id: 9,
      key: 'custom_customer_level',
      fieldCode: 'custom_customer_level',
      fieldName: '客户等级',
      label: '客户等级',
      fieldType: 'SINGLE_SELECT',
      type: 'select',
      status: 'ENABLED',
      options: [{ optionCode: 'A', label: 'A级', color: '#1570EF', status: 'ENABLED' }]
    }]
    controller.fields.createCustomField = vi.fn(async payload => ({ id: 10, ...payload }))
    controller.fields.changeCustomFieldStatus = vi.fn(async () => undefined)
    const { wrapper } = mountControl(FieldManager, controller)

    await wrapper.find('button[aria-label="复制字段 客户等级"]').trigger('click')
    expect(wrapper.find('input[aria-label="字段名称"]').element.value).toBe('客户等级 副本')
    await buttonByText(wrapper, '创建字段').trigger('click')

    expect(controller.fields.createCustomField).toHaveBeenCalledOnce()
    expect(controller.fields.createCustomField.mock.calls[0][0]).toMatchObject({
      fieldName: '客户等级 副本',
      fieldType: 'SINGLE_SELECT'
    })
    expect(controller.fields.createCustomField.mock.calls[0][0].options).toHaveLength(1)

    await wrapper.find('button[aria-label="停用字段 客户等级"]').trigger('click')
    expect(controller.fields.changeCustomFieldStatus).toHaveBeenCalledWith({ fieldId: 9, status: 'DISABLED' })
  })

  test('RowDensityMenu publishes the selected density', async () => {
    const { wrapper, controller } = mountControl(RowDensityMenu)

    expect(wrapper.find('[role="menuitemradio"][aria-checked="true"]').text()).toContain('默认')
    await buttonByText(wrapper, '紧凑').trigger('click')
    expect(controller.view.updateRowDensity).toHaveBeenCalledWith('compact')
  })
})
