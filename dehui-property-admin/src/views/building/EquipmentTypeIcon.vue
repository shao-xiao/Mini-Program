<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65"
    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
    <g v-if="kind === 'ELEVATOR'">
      <rect x="4" y="3" width="16" height="18" rx="2" />
      <path d="M7.5 9h9v12h-9zM12 9v12m-4-14.8 1.25-1.15 1.25 1.15m3-1.15 1.25 1.15L16 5.05" />
    </g>
    <g v-else-if="kind === 'HVAC'">
      <circle cx="12" cy="12" r="2" />
      <path v-for="angle in [0, 90, 180, 270]" :key="angle" :transform="`rotate(${angle} 12 12)`"
        d="M10.3 10.4C7.7 7.7 10.1 3.1 13.4 3.1c1.5 0 2.6.6 3.4 1.7-2.8.4-4.2 2.2-4 5.1" />
    </g>
    <g v-else-if="kind === 'FIRE'">
      <path d="M9.1 10.5a3.65 3.65 0 0 1 7.3 0v9.8H9.1zm.1 4.1h7m-3.6-7.8V3.6m-2.7.2 6.6-1m-3.6 3.1h3.4c2.1 0 3.1 1.3 3.1 3.4m-8.9-3.8C6.3 5.3 5 7.6 5 10.3v2.4m-.8.1h1.6" />
    </g>
    <path v-else-if="kind === 'POWER'" d="m13.9 2.5-9 11.3h6.2l-1 7.7 9-12h-6.2z" />
    <g v-else-if="kind === 'WATER'">
      <path d="M12 2.7c-2 3.3-7 8.4-7 12a7 7 0 0 0 14 0c0-3.6-5-8.7-7-12Z" />
      <path d="M8.2 14.8a3.8 3.8 0 0 0 3.8 3.8" />
    </g>
    <g v-else-if="kind === 'SECURITY'">
      <path d="m4 5.2 16.2 3.6-5.5 5.7L2.9 11l1.1-5.8Zm3.1 7.1-.7 3.2 4.9 1.6v3.3m6.1-8.8 2.6.8 1.1-3.1M8.9 20.4h4.6" />
    </g>
    <g v-else>
      <rect x="3.5" y="3.5" width="7" height="7" rx="1.5" />
      <rect x="13.5" y="3.5" width="7" height="7" rx="1.5" />
      <rect x="3.5" y="13.5" width="7" height="7" rx="1.5" />
      <rect x="13.5" y="13.5" width="7" height="7" rx="1.5" />
    </g>
  </svg>
</template>

<script setup>
import { computed } from 'vue'
import { equipmentTypeOptions } from '../../workspaceConfigs/equipment'

const props = defineProps({ folder: { type: Object, required: true } })
const kind = computed(() => equipmentTypeOptions.find(option => (
  option.value === String(props.folder.key).toUpperCase() || option.label === props.folder.label
))?.value)
</script>
