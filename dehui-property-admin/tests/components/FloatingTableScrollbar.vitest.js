import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, expect, test, vi } from 'vitest'
import FloatingTableScrollbar from '../../src/components/workspace/FloatingTableScrollbar.vue'

let wrapper
const frames = new Map()
let sequence = 0
async function flushFrames() {
  await nextTick()
  await nextTick()
  for (const [id, callback] of [...frames]) { frames.delete(id); callback() }
}
function setup({ bottom = 1400, top = 250, scrollWidth = 1800, width = 800 } = {}) {
  vi.stubGlobal('requestAnimationFrame', callback => { const id = ++sequence; frames.set(id, callback); return id })
  vi.stubGlobal('cancelAnimationFrame', id => frames.delete(id))
  const table = document.createElement('div')
  table.innerHTML = '<div class="el-table__body-wrapper"><div class="el-scrollbar__wrap"><table class="el-table__body"></table></div></div>'
  let rect = { top, bottom, left: 200, right: 200 + width, width }
  table.getBoundingClientRect = () => rect
  const wrap = table.querySelector('.el-scrollbar__wrap')
  Object.defineProperties(wrap, { clientWidth: { configurable:true,value:width }, scrollWidth: { configurable:true,value:scrollWidth } })
  wrapper = mount(FloatingTableScrollbar, { attachTo: document.body, props: { table: { $el: table }, rows: [], columns: [] } })
  return { wrap, setRect: value => { rect = { ...rect, ...value } } }
}
afterEach(() => { wrapper?.unmount(); document.body.innerHTML = ''; frames.clear(); vi.unstubAllGlobals() })

test('floating bar is visible while the native horizontal scrollbar is below the screen', async () => {
  setup()
  await flushFrames()
  expect(wrapper.element.style.display).toBe('flex')
  expect(wrapper.element.style.left).toBe('200px')
})
test('arrow controls, keyboard, thumb and native table scrolling stay synchronized', async () => {
  const { wrap } = setup()
  await flushFrames()
  await wrapper.get('[aria-label="向右滚动表格"]').trigger('click')
  expect(wrap.scrollLeft).toBe(520)
  await flushFrames()
  const track = wrapper.get('.floating-scrollbar-track')
  expect(track.element.scrollLeft).toBe(520)
  track.element.scrollLeft = 210
  await track.trigger('scroll')
  expect(wrap.scrollLeft).toBe(210)
  await track.trigger('keydown', { key: 'End' })
  expect(wrap.scrollLeft).toBe(1000)
  await track.trigger('keydown', { key: 'Home' })
  expect(wrap.scrollLeft).toBe(0)
  wrap.scrollLeft = 430
  document.dispatchEvent(new Event('scroll'))
  await flushFrames()
  expect(track.element.scrollLeft).toBe(430)
})
test('hides when table fits, leaves the viewport, or its native scrollbar is already visible', async () => {
  const { wrap, setRect } = setup()
  await flushFrames()
  for (const rect of [{ bottom: 500 }, { top: 1500, bottom: 2200 }]) {
    setRect(rect); document.dispatchEvent(new Event('scroll')); await flushFrames()
    expect(wrapper.element.style.display).toBe('none')
  }
  setRect({ top:250, bottom:1400 })
  Object.defineProperty(wrap, 'scrollWidth', { value:800 })
  window.dispatchEvent(new Event('resize')); await flushFrames()
  expect(wrapper.element.style.display).toBe('none')
})
test('unmount cancels scheduled frames and detaches scroll listeners', async () => {
  setup(); await flushFrames()
  window.dispatchEvent(new Event('resize'))
  expect(frames.size).toBe(1)
  wrapper.unmount(); wrapper = null
  expect(frames.size).toBe(0)
  document.dispatchEvent(new Event('scroll'))
  expect(frames.size).toBe(0)
})
