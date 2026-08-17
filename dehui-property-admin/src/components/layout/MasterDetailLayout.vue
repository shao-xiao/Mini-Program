<template>
  <div class="dhic-master-detail" :style="{ '--dhic-master-width': masterWidth }">
    <aside class="dhic-master-detail__master"><slot name="master" /></aside>
    <section class="dhic-master-detail__detail"><slot /></section>
  </div>
</template>
<script setup>
defineOptions({ name: 'DhicMasterDetailLayout' })
defineProps({ masterWidth: { type: String, default: '300px' } })
</script>
<style scoped>
.dhic-master-detail { display: grid; min-width: 0; min-height: 0; grid-template-columns: var(--dhic-master-width) minmax(0, 1fr); overflow: hidden; border: 1px solid var(--dhic-border-color); border-radius: var(--dhic-radius-large); background: var(--dhic-card-bg); }
.dhic-master-detail__master { min-width: 0; overflow: hidden; border-right: 1px solid var(--dhic-border-color); background: #f7f8fa; }
.dhic-master-detail__detail { min-width: 0; overflow: hidden; }
@media (max-width: 900px) { .dhic-master-detail { grid-template-columns: minmax(220px, 34%) minmax(0, 1fr); } }
</style>