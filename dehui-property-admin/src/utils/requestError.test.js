import test from 'node:test'
import assert from 'node:assert/strict'
import { requestErrorMessage } from './requestError.js'

test('requestErrorMessage prefers the backend business message over the Axios status text', () => {
  const error = {
    message: 'Request failed with status code 400',
    response: {
      data: {
        code: 400,
        message: '该表具 2026-07 账期已存在抄表记录'
      }
    }
  }

  assert.equal(requestErrorMessage(error), '该表具 2026-07 账期已存在抄表记录')
})

test('requestErrorMessage supports rejected application payloads and stable fallbacks', () => {
  assert.equal(
    requestErrorMessage({ data: { message: '保存内容不合法' } }),
    '保存内容不合法'
  )
  assert.equal(requestErrorMessage({}, '保存失败，请重试'), '保存失败，请重试')
})
