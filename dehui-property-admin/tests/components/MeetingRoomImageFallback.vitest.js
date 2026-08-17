import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CustomFieldValueDisplay from '../../src/components/workspace/CustomFieldValueDisplay.vue'
import CustomFieldValueInput from '../../src/components/workspace/CustomFieldValueInput.vue'
import request from '../../src/utils/request'

vi.mock('../../src/utils/request', () => ({
  default: {
    get: vi.fn()
  }
}))

const ElImageStub = defineComponent({
  name: 'ElImage',
  props: {
    src: String,
    alt: String
  },
  emits: ['error'],
  setup(props, { emit }) {
    return () => h('img', {
      src: props.src,
      alt: props.alt,
      onError: () => emit('error')
    })
  }
})

function mountImageDisplay() {
  return mount(CustomFieldValueDisplay, {
    props: {
      field: { type: 'image', label: '会议室图片' },
      value: [{ fileId: 24, name: '内部原图.png', url: '/api/files/24' }],
      imageAlt: '8F会议室图片',
      mode: 'detail'
    },
    global: {
      components: {
        ElImage: ElImageStub
      }
    }
  })
}

function mountImageInput() {
  return mount(CustomFieldValueInput, {
    props: {
      field: { type: 'image', label: '会议室图片', workspaceKey: 'meeting-rooms' },
      modelValue: [{ fileId: 24, name: '内部原图.png', url: '/api/files/24' }]
    },
    global: {
      components: {
        ElImage: ElImageStub
      }
    }
  })
}

describe('meeting room protected image fallback', () => {
  beforeEach(() => {
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:meeting-room'),
      revokeObjectURL: vi.fn()
    })
  })

  it('emits a failed image state when protected loading fails', async () => {
    request.get.mockRejectedValueOnce(new Error('network unavailable'))
    const wrapper = mountImageDisplay()

    await flushPromises()

    expect(wrapper.emitted('image-state').at(-1)[0]).toEqual({
      loading: false,
      hasImage: false
    })
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('renders a protected blob with a room-specific alternative label', async () => {
    request.get.mockResolvedValueOnce(new Blob(['image']))
    const wrapper = mountImageDisplay()

    await flushPromises()

    expect(wrapper.emitted('image-state').at(-1)[0]).toEqual({
      loading: false,
      hasImage: true
    })
    expect(wrapper.get('img').attributes()).toMatchObject({
      src: 'blob:meeting-room',
      alt: '8F会议室图片'
    })
  })

  it('loads the protected image editor preview through the authorized blob request', async () => {
    request.get.mockResolvedValueOnce(new Blob(['image']))
    const wrapper = mountImageInput()

    await flushPromises()

    expect(request.get).toHaveBeenCalledWith('/files/24', {
      responseType: 'blob',
      silent: true
    })
    expect(wrapper.get('img').attributes()).toMatchObject({
      src: 'blob:meeting-room',
      alt: '内部原图.png'
    })
    expect(wrapper.find('.image-error').exists()).toBe(false)
  })

  it('shows a stable editor fallback when the authorized preview request fails', async () => {
    request.get.mockRejectedValueOnce(new Error('network unavailable'))
    const wrapper = mountImageInput()

    await flushPromises()

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('.image-error').text()).toBe('图片不可用')
  })
})
