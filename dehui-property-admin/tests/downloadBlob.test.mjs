import test from 'node:test'
import assert from 'node:assert/strict'
import { downloadBlob } from '../src/utils/downloadBlob.js'

test('downloadBlob triggers download and points preview window to blob url', () => {
  const clicks = []
  const appended = []
  const removed = []
  const previewWindow = { location: { href: '' } }

  global.window = {
    URL: {
      createObjectURL: () => 'blob:invoice-pdf',
      revokeObjectURL: () => {}
    },
    setTimeout: (fn) => {
      fn()
      return 1
    }
  }
  global.document = {
    createElement: (tag) => ({
      tagName: tag,
      style: {},
      click: () => clicks.push(tag)
    }),
    body: {
      appendChild: element => appended.push(element),
      removeChild: element => removed.push(element)
    }
  }

  const result = downloadBlob(new Blob(['pdf']), 'invoice.pdf', { previewWindow })

  assert.equal(clicks.length, 1)
  assert.equal(appended[0].download, 'invoice.pdf')
  assert.equal(appended[0].href, 'blob:invoice-pdf')
  assert.equal(previewWindow.location.href, 'blob:invoice-pdf')
  assert.equal(removed.length, 1)
  assert.equal(result.previewOpened, true)
})
