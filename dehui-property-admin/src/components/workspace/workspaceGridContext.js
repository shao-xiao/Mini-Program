import { inject, provide } from 'vue'

export const WORKSPACE_GRID_CONTEXT = Symbol('dhic-workspace-grid')

const noop = () => false
const noopAsync = async () => ({ success: false })

export function provideWorkspaceGrid(controller) {
  const resolved = { provided: true, ...controller }
  provide(WORKSPACE_GRID_CONTEXT, resolved)
  return resolved
}

export function useWorkspaceGrid() {
  return inject(WORKSPACE_GRID_CONTEXT, {
    provided: false,
    customFields: {
      enabled: false,
      canEditCell: noop,
      cellKey: () => '',
      isSaving: noop,
      saveCell: noopAsync
    }
  })
}
