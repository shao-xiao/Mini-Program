<template>
  <svg class="equipment-illustration" :data-illustration="kind" viewBox="0 0 96 76" fill="none"
    stroke="#44525a" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round"
    aria-hidden="true" focusable="false">
    <use :href="`${sheet}#${kind}`" />
  </svg>
</template>

<script setup>
import { computed } from 'vue'
import sheet from './equipment-illustrations.svg?no-inline'
import { equipmentIllustrationKind } from './equipmentIllustrations'

const props = defineProps({ name: { type: String, default: '' } })
const kind = computed(() => equipmentIllustrationKind(props.name))
</script>

<style scoped>
.equipment-illustration { display: block; width: 100%; height: 100%; }
</style>
