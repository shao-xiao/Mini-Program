import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { expect, test } from 'vitest'
import CellEditor from '../../src/components/CellEditor.vue'
import CustomFieldCellEditor from '../../src/components/workspace/CustomFieldCellEditor.vue'
import WorkspaceDataGrid from '../../src/components/workspace/WorkspaceDataGrid.vue'
import { WORKSPACE_GRID_CONTEXT } from '../../src/components/workspace/workspaceGridContext.js'
import { ElTableStub, elementStubs } from './elementStubs.js'

const rowCounts = [20, 100, 200]

function createRows(count) {
  return Array.from({ length: count }, (_, index) => ({
    id: index + 1,
    name: `设备-${String(index + 1).padStart(3, '0')}`,
    code: `EQ-${String(index + 1).padStart(4, '0')}`,
    status: index % 3 === 0 ? 'FAULT' : 'NORMAL',
    type: index % 2 === 0 ? 'HVAC' : 'ELEVATOR',
    location: `${(index % 20) + 1}F`,
    reading: index * 12.5,
    updatedAt: `2026-07-${String((index % 28) + 1).padStart(2, '0')}`
  }))
}

function createColumns() {
  const statusMap = new Map([
    ['NORMAL', { value: 'NORMAL', label: '正常', tone: 'success' }],
    ['FAULT', { value: 'FAULT', label: '故障', tone: 'danger' }]
  ])
  return [
    { key: 'name', label: '设备名称', type: 'text', editable: true, minWidth: 150 },
    { key: 'code', label: '设备编号', type: 'text', minWidth: 130 },
    { key: 'status', label: '状态', type: 'status', optionMap: statusMap, minWidth: 100 },
    { key: 'type', label: '类型', type: 'text', editable: true, minWidth: 110 },
    { key: 'location', label: '位置', type: 'text', editable: true, minWidth: 110 },
    { key: 'reading', label: '读数', type: 'number', editable: true, minWidth: 100 },
    { key: 'updatedAt', label: '更新时间', type: 'date', minWidth: 120 },
    { key: 'remark', label: '备注', type: 'text', minWidth: 140 }
  ]
}

async function duration(task) {
  const startedAt = performance.now()
  await task()
  await nextTick()
  return Number((performance.now() - startedAt).toFixed(2))
}

test('WorkspaceDataGrid keeps one-editor rendering characteristics at 20, 100 and 200 rows', async () => {
  const results = []

  for (const rowCount of rowCounts) {
    const rows = createRows(rowCount)
    const columns = createColumns()
    const startedAt = performance.now()
    const wrapper = mount(WorkspaceDataGrid, {
      props: {
        rows,
        columns,
        rowKey: 'id',
        showSelection: true,
        canEditCell: () => true,
        density: 'compact'
      },
      global: {
        stubs: elementStubs()
      }
    })
    await nextTick()
    const initialRenderMs = Number((performance.now() - startedAt).toFixed(2))
    const initialEditorCount = wrapper.findAllComponents(CellEditor).length
    const domNodeCount = wrapper.element.querySelectorAll('*').length

    const editMs = await duration(async () => {
      await wrapper.find('.workspace-grid-cell.is-editable').trigger('keydown', { key: 'Enter' })
    })
    const activeEditorCount = wrapper.findAllComponents(CellEditor).length
    await wrapper.find('.workspace-grid-cell.is-active').trigger('keydown', { key: 'Escape' })

    const filterChangeMs = await duration(() => wrapper.setProps({
      rows: rows.filter(row => row.status === 'NORMAL')
    }))
    const sortChangeMs = await duration(() => wrapper.setProps({
      rows: [...rows].reverse(),
      sortState: { key: 'name', order: 'descending' }
    }))
    const fieldDisplayChangeMs = await duration(() => wrapper.setProps({
      columns: columns.slice(0, 6)
    }))
    const selectionSyncMs = await duration(async () => {
      wrapper.findComponent(ElTableStub).vm.$emit('selection-change', rows.slice(0, 10))
    })

    results.push({
      rows: rowCount,
      initialRenderMs,
      editableFields: columns.filter(field => field.editable).length,
      initialEditors: initialEditorCount,
      activeEditors: activeEditorCount,
      editMs,
      filterChangeMs,
      sortChangeMs,
      fieldDisplayChangeMs,
      selectionSyncMs,
      domNodes: domNodeCount
    })

    wrapper.unmount()
  }

  console.table(results)
  expect(results.map(result => result.rows)).toEqual(rowCounts)
  expect(results.every(result => result.initialEditors === 0)).toBe(true)
  expect(results.every(result => result.activeEditors === 1)).toBe(true)
  expect(results.every(result => result.editableFields === 4)).toBe(true)
  expect(results.every(result => Number.isFinite(result.initialRenderMs))).toBe(true)
  expect(results[2].domNodes).toBeGreaterThan(results[1].domNodes)
  expect(results[1].domNodes).toBeGreaterThan(results[0].domNodes)
}, 30000)

test('WorkspaceDataGrid keeps 50 custom fields lazy at 20, 100 and 200 rows', async () => {
  const customColumns = Array.from({ length: 50 }, (_, index) => ({
    id: index + 1,
    key: `custom_field_${index + 1}`,
    fieldCode: `custom_field_${index + 1}`,
    label: `自定义字段 ${index + 1}`,
    type: index % 5 === 0 ? 'number' : 'text',
    fieldType: index % 5 === 0 ? 'NUMBER' : 'TEXT',
    editable: true,
    custom: true,
    platformCustom: true,
    minWidth: 120
  }))
  const results = []

  for (const rowCount of rowCounts) {
    const rows = Array.from({ length: rowCount }, (_, rowIndex) => ({
      id: rowIndex + 1,
      customFields: Object.fromEntries(customColumns.map((field, fieldIndex) => [
        field.key,
        field.type === 'number' ? rowIndex * 100 + fieldIndex : `值-${rowIndex + 1}-${fieldIndex + 1}`
      ]))
    }))
    const startedAt = performance.now()
    const wrapper = mount(WorkspaceDataGrid, {
      props: {
        rows,
        columns: customColumns,
        rowKey: 'id',
        density: 'compact'
      },
      global: {
        provide: {
          [WORKSPACE_GRID_CONTEXT]: {
            provided: true,
            customFields: {
              enabled: true,
              canEditCell: () => true,
              isSaving: () => false,
              saveCell: async () => ({ success: true })
            }
          }
        },
        stubs: elementStubs()
      }
    })
    await nextTick()
    const initialRenderMs = Number((performance.now() - startedAt).toFixed(2))
    const initialEditors = wrapper.findAllComponents(CustomFieldCellEditor).length
    const editMs = await duration(async () => {
      await wrapper.find('.workspace-grid-cell.is-editable').trigger('keydown', { key: 'Enter' })
    })
    const activeEditors = wrapper.findAllComponents(CustomFieldCellEditor).length

    results.push({ rows: rowCount, fields: customColumns.length, initialRenderMs, editMs, initialEditors, activeEditors })
    wrapper.unmount()
  }

  console.table(results)
  expect(results.every(result => result.fields === 50)).toBe(true)
  expect(results.every(result => result.initialEditors === 0)).toBe(true)
  expect(results.every(result => result.activeEditors === 1)).toBe(true)
  expect(results.every(result => Number.isFinite(result.initialRenderMs))).toBe(true)
}, 60000)
