import { config } from '@vue/test-utils'

class TestResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

globalThis.ResizeObserver ||= TestResizeObserver
globalThis.matchMedia ||= () => ({
  matches: false,
  addEventListener() {},
  removeEventListener() {},
  addListener() {},
  removeListener() {}
})

config.global.directives = {
  loading: {
    beforeMount(element, binding) {
      element.dataset.loading = String(Boolean(binding.value))
    },
    updated(element, binding) {
      element.dataset.loading = String(Boolean(binding.value))
    }
  }
}
