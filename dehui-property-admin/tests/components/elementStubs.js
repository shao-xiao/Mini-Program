import { computed, defineComponent, h, inject, provide, ref } from 'vue'

const GRID_ROWS = Symbol('workspace-grid-test-rows')

export const ElButtonStub = defineComponent({
  name: 'ElButton',
  inheritAttrs: false,
  props: {
    disabled: Boolean,
    loading: Boolean
  },
  emits: ['click'],
  setup(props, { attrs, emit, slots }) {
    return () => h('button', {
      ...attrs,
      type: 'button',
      disabled: props.disabled || props.loading,
      onClick: event => emit('click', event)
    }, slots.default?.())
  }
})

export const ElPopoverStub = defineComponent({
  name: 'ElPopover',
  setup(_props, { slots }) {
    return () => h('div', { class: 'el-popover-stub' }, [
      h('div', { class: 'el-popover-reference' }, slots.reference?.()),
      h('div', { class: 'el-popover-content' }, slots.default?.())
    ])
  }
})

export const ElTooltipStub = defineComponent({
  name: 'ElTooltip',
  setup(_props, { slots }) {
    return () => h('span', { class: 'el-tooltip-stub' }, slots.default?.())
  }
})

export const ElTagStub = defineComponent({
  name: 'ElTag',
  setup(_props, { attrs, slots }) {
    return () => h('span', { ...attrs, class: ['el-tag-stub', attrs.class] }, slots.default?.())
  }
})

export const ElImageStub = defineComponent({
  name: 'ElImage',
  inheritAttrs: false,
  props: {
    src: String,
    alt: String,
    fit: String,
    previewSrcList: Array
  },
  setup(props, { attrs }) {
    return () => h('img', {
      ...attrs,
      class: ['el-image-stub', attrs.class],
      src: props.src,
      alt: props.alt || ''
    })
  }
})

export const ElProgressStub = defineComponent({
  name: 'ElProgress',
  props: {
    percentage: Number,
    strokeWidth: Number
  },
  setup(props, { attrs }) {
    return () => h('progress', {
      ...attrs,
      class: ['el-progress-stub', attrs.class],
      max: 100,
      value: props.percentage || 0
    })
  }
})

export const ElDialogStub = defineComponent({
  name: 'ElDialog',
  props: {
    modelValue: Boolean,
    title: String
  },
  emits: ['update:modelValue', 'closed'],
  setup(props, { attrs, emit, slots }) {
    return () => props.modelValue
      ? h('section', { ...attrs, class: ['el-dialog-stub', attrs.class], role: 'dialog', 'aria-label': props.title }, [
          h('h2', props.title),
          slots.default?.(),
          h('footer', slots.footer?.()),
          h('button', {
            type: 'button',
            'aria-label': '关闭对话框',
            onClick: () => {
              emit('update:modelValue', false)
              emit('closed')
            }
          }, '关闭')
        ])
      : null
  }
})

export const ElDrawerStub = defineComponent({
  name: 'ElDrawer',
  props: {
    modelValue: Boolean,
    title: String
  },
  emits: ['update:modelValue', 'open'],
  setup(props, { attrs, emit, slots }) {
    return () => props.modelValue
      ? h('aside', { ...attrs, class: ['el-drawer-stub', attrs.class], role: 'dialog', 'aria-label': props.title }, [
          h('h2', props.title),
          slots.default?.(),
          h('footer', slots.footer?.()),
          h('button', { type: 'button', onClick: () => emit('update:modelValue', false) }, 'Close')
        ])
      : null
  }
})

export const ElFormItemStub = defineComponent({
  name: 'ElFormItem',
  props: {
    label: String,
    error: String,
    required: Boolean
  },
  setup(props, { attrs, slots }) {
    return () => h('label', { ...attrs, class: ['el-form-item-stub', attrs.class] }, [
      h('span', { class: 'el-form-item-label' }, props.label),
      slots.default?.(),
      props.error ? h('span', { class: 'el-form-item-error', role: 'alert' }, props.error) : null
    ])
  }
})

export const ElIconStub = defineComponent({
  name: 'ElIcon',
  setup(_props, { attrs, slots }) {
    return () => h('span', { ...attrs, class: ['el-icon-stub', attrs.class] }, slots.default?.())
  }
})

export const ElAvatarStub = defineComponent({
  name: 'ElAvatar',
  setup(_props, { slots }) {
    return () => h('span', { class: 'el-avatar-stub' }, slots.default?.())
  }
})

export const ElOptionStub = defineComponent({
  name: 'ElOption',
  props: {
    label: [String, Number],
    value: [String, Number, Boolean],
    disabled: Boolean
  },
  setup(props, { slots }) {
    return () => h('option', {
      value: props.value,
      disabled: props.disabled
    }, slots.default?.() || String(props.label ?? props.value ?? ''))
  }
})

export const ElSelectStub = defineComponent({
  name: 'ElSelect',
  inheritAttrs: false,
  props: {
    modelValue: [String, Number, Boolean, Array],
    disabled: Boolean,
    multiple: Boolean
  },
  emits: ['update:modelValue', 'change', 'visible-change', 'keyup'],
  setup(props, { attrs, emit, expose, slots }) {
    const control = ref(null)
    expose({ focus: () => control.value?.focus() })
    return () => h('select', {
      ...attrs,
      ref: control,
      value: props.modelValue,
      disabled: props.disabled,
      multiple: props.multiple,
      onChange: event => {
        const value = props.multiple
          ? [...event.target.selectedOptions].map(option => option.value)
          : event.target.value
        emit('update:modelValue', value)
        emit('change', value)
      },
      onKeyup: event => emit('keyup', event)
    }, slots.default?.())
  }
})

export const ElInputStub = defineComponent({
  name: 'ElInput',
  inheritAttrs: false,
  props: {
    modelValue: [String, Number],
    disabled: Boolean
  },
  emits: ['update:modelValue', 'input', 'keyup', 'blur'],
  setup(props, { attrs, emit, expose }) {
    const control = ref(null)
    expose({ focus: () => control.value?.focus() })
    return () => h('input', {
      ...attrs,
      ref: control,
      value: props.modelValue ?? '',
      disabled: props.disabled,
      onInput: event => {
        emit('update:modelValue', event.target.value)
        emit('input', event.target.value)
      },
      onKeyup: event => emit('keyup', event),
      onBlur: event => emit('blur', event)
    })
  }
})

export const ElInputNumberStub = defineComponent({
  name: 'ElInputNumber',
  inheritAttrs: false,
  props: {
    modelValue: Number,
    disabled: Boolean
  },
  emits: ['update:modelValue', 'change', 'keyup'],
  setup(props, { attrs, emit, expose }) {
    const control = ref(null)
    expose({ focus: () => control.value?.focus() })
    return () => h('input', {
      ...attrs,
      ref: control,
      type: 'number',
      value: props.modelValue ?? '',
      disabled: props.disabled,
      onChange: event => {
        const value = event.target.value === '' ? null : Number(event.target.value)
        emit('update:modelValue', value)
        emit('change', value)
      },
      onKeyup: event => emit('keyup', event)
    })
  }
})

export const ElDatePickerStub = defineComponent({
  name: 'ElDatePicker',
  inheritAttrs: false,
  props: {
    modelValue: [String, Array],
    disabled: Boolean
  },
  emits: ['update:modelValue', 'change'],
  setup(props, { attrs, emit, expose }) {
    const control = ref(null)
    expose({ focus: () => control.value?.focus() })
    return () => h('input', {
      ...attrs,
      ref: control,
      type: 'date',
      value: Array.isArray(props.modelValue) ? props.modelValue[0] : props.modelValue,
      disabled: props.disabled,
      onChange: event => {
        emit('update:modelValue', event.target.value)
        emit('change', event.target.value)
      }
    })
  }
})

export const ElSwitchStub = defineComponent({
  name: 'ElSwitch',
  props: {
    modelValue: Boolean,
    disabled: Boolean
  },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    return () => h('input', {
      type: 'checkbox',
      checked: props.modelValue,
      disabled: props.disabled,
      onChange: event => {
        emit('update:modelValue', event.target.checked)
        emit('change', event.target.checked)
      }
    })
  }
})

export const ElCheckboxStub = defineComponent({
  name: 'ElCheckbox',
  inheritAttrs: false,
  props: {
    modelValue: Boolean,
    disabled: Boolean
  },
  emits: ['update:modelValue', 'change'],
  setup(props, { attrs, emit }) {
    return () => h('input', {
      ...attrs,
      type: 'checkbox',
      checked: props.modelValue,
      disabled: props.disabled,
      onChange: event => {
        emit('update:modelValue', event.target.checked)
        emit('change', event.target.checked)
      }
    })
  }
})

export const ElSegmentedStub = defineComponent({
  name: 'ElSegmented',
  props: {
    modelValue: [String, Number, Boolean],
    options: { type: Array, default: () => [] }
  },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    return () => h('div', { class: 'el-segmented-stub' }, props.options.map(option => {
      const normalized = option && typeof option === 'object' ? option : { label: option, value: option }
      return h('button', {
        type: 'button',
        'aria-pressed': props.modelValue === normalized.value,
        onClick: () => {
          emit('update:modelValue', normalized.value)
          emit('change', normalized.value)
        }
      }, String(normalized.label))
    }))
  }
})

export const ElTabsStub = defineComponent({
  name: 'ElTabs',
  setup(_props, { slots }) {
    return () => h('div', { class: 'el-tabs-stub' }, slots.default?.())
  }
})

export const ElTabPaneStub = defineComponent({
  name: 'ElTabPane',
  setup(_props, { slots }) {
    return () => h('section', { class: 'el-tab-pane-stub' }, slots.default?.())
  }
})

export const ElTableStub = defineComponent({
  name: 'ElTable',
  props: {
    data: { type: Array, default: () => [] },
    emptyText: String
  },
  emits: ['selection-change', 'header-dragend', 'sort-change', 'row-click', 'cell-click'],
  setup(props, { attrs, expose, slots }) {
    provide(GRID_ROWS, computed(() => props.data))
    expose({
      clearSelection() {},
      toggleRowSelection() {}
    })
    return () => h('div', {
      ...attrs,
      class: ['el-table-stub', attrs.class],
      'data-row-count': props.data.length
    }, [
      h('div', { class: 'el-table-columns' }, slots.default?.()),
      props.data.length === 0 ? slots.empty?.() : null,
      slots.append?.()
    ])
  }
})

export const ElTableColumnStub = defineComponent({
  name: 'ElTableColumn',
  props: {
    type: String,
    columnKey: String,
    prop: String,
    label: String,
    selectable: Function,
    sortable: [String, Boolean]
  },
  setup(props, { slots }) {
    const rows = inject(GRID_ROWS, computed(() => []))
    return () => h('section', {
      class: ['el-table-column-stub', props.type ? `is-${props.type}` : ''],
      'data-column-key': props.columnKey || props.prop || props.type || props.label,
      'data-sortable': String(props.sortable || false)
    }, [
      slots.header?.({ column: props }),
      ...rows.value.map((row, index) => props.type === 'selection'
        ? h('input', {
            class: 'row-selection-control',
            type: 'checkbox',
            disabled: props.selectable ? !props.selectable(row, index) : false,
            'data-row-index': index
          })
        : slots.default?.({ row, $index: index, column: props }))
    ])
  }
})

export function elementStubs() {
  return {
    ElAvatar: ElAvatarStub,
    ElButton: ElButtonStub,
    ElCheckbox: ElCheckboxStub,
    ElDatePicker: ElDatePickerStub,
    ElDialog: ElDialogStub,
    ElDrawer: ElDrawerStub,
    ElFormItem: ElFormItemStub,
    ElIcon: ElIconStub,
    ElImage: ElImageStub,
    ElInput: ElInputStub,
    ElInputNumber: ElInputNumberStub,
    ElOption: ElOptionStub,
    ElPopover: ElPopoverStub,
    ElProgress: ElProgressStub,
    ElSegmented: ElSegmentedStub,
    ElSelect: ElSelectStub,
    ElSwitch: ElSwitchStub,
    ElTabPane: ElTabPaneStub,
    ElTable: ElTableStub,
    ElTableColumn: ElTableColumnStub,
    ElTabs: ElTabsStub,
    ElTag: ElTagStub,
    ElTooltip: ElTooltipStub
  }
}
