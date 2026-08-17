import assert from 'node:assert/strict'
import test from 'node:test'

import { downloadBlob } from '../src/utils/downloadBlob.js'

test('preview falls back to same-tab navigation when popup window is unavailable', () => {
  const previousWindow = globalThis.window
  const previousDocument = globalThis.document

  const timeouts = []
  const fakeWindow = {
    URL: {
      createObjectURL: () => 'blob:test-preview',
      revokeObjectURL: () => {}
    },
    setTimeout: (fn, delay) => {
      timeouts.push(delay)
      return 1
    },
    location: {
      href: 'http://localhost:5173/contracts'
    }
  }

  const fakeDocument = {
    createElement: () => ({
      style: {},
      click() {}
    }),
    body: {
      appendChild() {},
      removeChild() {}
    }
  }

  globalThis.window = fakeWindow
  globalThis.document = fakeDocument

  try {
    const result = downloadBlob(new Blob(['pdf']), 'contract.pdf', { download: false })

    assert.equal(result.previewOpened, false)
    assert.equal(fakeWindow.location.href, 'blob:test-preview')
    assert.deepEqual(timeouts, [60000])
  } finally {
    globalThis.window = previousWindow
    globalThis.document = previousDocument
  }
})
