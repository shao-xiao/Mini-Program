const assert = require('node:assert/strict')
const test = require('node:test')

const request = require('../utils/request')
const originalGet = request.get

test.afterEach(() => {
  request.get = originalGet
  delete require.cache[require.resolve('../utils/moduleFieldSchema')]
})

test('module schema options and required rules use backend fieldKey definitions', async () => {
  request.get = async () => ({
    version: 1,
    moduleKey: 'workorders',
    fields: [
      {
        fieldKey: 'title',
        apiField: 'title',
        label: '工单标题',
        required: true,
        readonly: false
      },
      {
        fieldKey: 'priority',
        apiField: 'priority',
        options: [{ code: 'HIGH', label: '较高' }]
      },
      {
        fieldKey: 'images',
        apiField: 'imageFileIds',
        validation: { maxFiles: 6 }
      }
    ]
  })
  const schema = require('../utils/moduleFieldSchema')
  const result = await schema.loadModuleFieldSchema('workorders')

  assert.deepEqual(schema.fieldOptions(result, 'priority'), [{ value: 'HIGH', label: '较高' }])
  assert.deepEqual(schema.fieldValidation(result, 'images'), { maxFiles: 6 })
  assert.equal(schema.requiredError(result, { title: '' }), '请填写工单标题')
  assert.equal(schema.requiredError(result, { title: '空调报修' }), '')
})
