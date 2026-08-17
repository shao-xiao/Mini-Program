import { describe, expect, it } from 'vitest'
import {
  normalizeCustomFieldFiles,
  serializeCustomFieldFiles,
  validateCustomFieldValue
} from '../../src/utils/customFieldValues'

describe('custom field file values', () => {
  it('serializes new values as versioned file id references', () => {
    expect(serializeCustomFieldFiles([
      { fileId: 12, name: 'one.png', url: '/files/public/one.png' },
      { id: 18, name: 'two.png', url: '/files/public/two.png' }
    ])).toEqual({
      version: 1,
      files: [
        { fileId: 12, sortOrder: 0 },
        { fileId: 18, sortOrder: 1 }
      ]
    })
  })

  it('reads versioned values and legacy url values', () => {
    expect(normalizeCustomFieldFiles({
      version: 1,
      files: [{ fileId: 3, name: 'photo.webp', url: '/files/public/photo.webp', sortOrder: 0 }]
    })[0]).toMatchObject({ fileId: 3, name: 'photo.webp' })

    expect(normalizeCustomFieldFiles('["/api/files/public/legacy.jpg"]')[0]).toMatchObject({
      url: '/files/public/legacy.jpg',
      legacy: true
    })
  })

  it('enforces the configured limit up to thirty files', () => {
    const files = Array.from({ length: 10 }, (_, index) => ({ fileId: index + 1 }))
    expect(validateCustomFieldValue(
      { fieldType: 'IMAGE', fieldName: '现场照片', config: { maxFiles: 9 } },
      files
    )).toBe('现场照片最多上传9个文件')
  })
})
