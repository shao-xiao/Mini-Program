<template>
  <div class="metric-panel">
    <ListSummaryGrid :cards="cards" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import ListSummaryGrid from './ListSummaryGrid.vue'
import { buildMetricCards } from '../utils/metricPanel'

defineOptions({ name: 'MetricPanel' })

const props = defineProps({
  definitions: { type: Array, default: () => [] },
  data: { type: Object, default: () => ({}) },
  states: { type: Object, default: () => ({}) }
})

const cards = computed(() => buildMetricCards(props.definitions, props.data, props.states))
</script>

<style scoped>
.metric-panel {
  min-width: 0;
}

.metric-panel :deep(.list-summary-grid) {
  margin: 0;
}
</style>
