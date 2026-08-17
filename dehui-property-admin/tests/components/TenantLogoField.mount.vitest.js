import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TenantLogoField from '../../src/views/tenant/workspace/TenantLogoField.vue'
import { uploadCustomFieldAttachment } from '../../src/api/customFields.js'
import request from '../../src/utils/request.js'
import { elementStubs } from './elementStubs.js'

vi.mock('../../src/api/customFields.js', () => ({
  uploadCustomFieldAttachment: vi.fn()
}))

vi.mock('../../src/utils/request.js', () => ({
  default: {
    get: vi.fn()
  }
}))

const wrappers = []

function mountField(props = {}) {
  const wrapper = mount(TenantLogoField, {
    props: {
      tenantId: 8,
      modelValue: null,
      previewUrl: '',
      tenantName: '测试企业',
      ...props
    },
    global: {
      stubs: elementStubs()
    }
  })
  wrappers.push(wrapper)
  return wrapper
}

describe('tenant logo field', () => {
  beforeEach(() => {
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:tenant-logo'),
      revokeObjectURL: vi.fn()
    })
  })

  afterEach(() => {
    wrappers.splice(0).forEach(wrapper => wrapper.unmount())
    vi.unstubAllGlobals()
  })

  it('keeps an unconfigured tenant empty without rendering a default logo', () => {
    const wrapper = mountField()

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('.logo-placeholder').text()).toBe('未配置')
    expect(wrapper.text()).toContain('上传 Logo')
    expect(request.get).not.toHaveBeenCalled()
  })

  it('does not expose upload actions before a tenant has been saved', () => {
    const wrapper = mountField({ tenantId: null })

    expect(wrapper.text()).toContain('请先保存租户后配置企业 Logo')
    expect(wrapper.text()).not.toContain('上传 Logo')
  })

  it('loads an existing protected logo through the authenticated request wrapper', async () => {
    request.get.mockResolvedValueOnce(new Blob(['logo']))
    const wrapper = mountField({
      modelValue: 19,
      previewUrl: '/api/files/19'
    })

    await flushPromises()

    expect(request.get).toHaveBeenCalledWith('/files/19', {
      responseType: 'blob',
      silent: true
    })
    expect(wrapper.get('img').attributes()).toMatchObject({
      src: 'blob:tenant-logo',
      alt: '测试企业企业 Logo'
    })
  })

  it('uploads an image in the tenants workspace and emits only its file identity', async () => {
    uploadCustomFieldAttachment.mockResolvedValueOnce({
      id: 23,
      url: '/api/files/23',
      name: 'tenant.png'
    })
    const wrapper = mountField()
    const file = new File(['image'], 'tenant.png', { type: 'image/png' })
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })

    await input.trigger('change')
    await flushPromises()

    expect(uploadCustomFieldAttachment).toHaveBeenCalledWith(file, expect.objectContaining({
      fieldType: 'IMAGE',
      workspaceKey: 'tenants'
    }))
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([23])
    expect(wrapper.emitted('update:previewUrl').at(-1)).toEqual(['/api/files/23'])
    expect(wrapper.emitted('update:fileName').at(-1)).toEqual(['tenant.png'])
    expect(wrapper.emitted('uploading-change')).toEqual([[true], [false]])
    expect(wrapper.get('img').attributes('src')).toBe('blob:tenant-logo')
  })

  it('rejects unsupported image formats before calling the upload endpoint', async () => {
    const wrapper = mountField()
    const file = new File(['vector'], 'tenant.svg', { type: 'image/svg+xml' })
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })

    await input.trigger('change')
    await flushPromises()

    expect(uploadCustomFieldAttachment).not.toHaveBeenCalled()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('clears the persisted file reference without adding a replacement image', async () => {
    request.get.mockResolvedValueOnce(new Blob(['logo']))
    const wrapper = mountField({
      modelValue: 31,
      previewUrl: '/api/files/31'
    })
    await flushPromises()

    await wrapper.get('.clear-button').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([null])
    expect(wrapper.emitted('update:previewUrl').at(-1)).toEqual([''])
    expect(wrapper.emitted('update:fileName').at(-1)).toEqual([''])
    expect(wrapper.find('img').exists()).toBe(false)
  })
})
