<template>
  <section class="dhic-table-panel">
    <header v-if="title || $slots.actions" class="dhic-table-panel__header">
      <div><h2 v-if="title">{{ title }}</h2><p v-if="description">{{ description }}</p></div>
      <div v-if="$slots.actions" class="dhic-table-panel__actions"><slot name="actions" /></div>
    </header>
    <div v-if="$slots.toolbar" class="dhic-table-panel__toolbar"><slot name="toolbar" /></div>
    <div class="dhic-table-panel__body"><slot /></div>
    <footer v-if="$slots.footer" class="dhic-table-panel__footer"><slot name="footer" /></footer>
  </section>
</template>
<script setup>
defineOptions({ name: 'DhicTablePanel' })
defineProps({ title: { type: String, default: '' }, description: { type: String, default: '' } })
</script>
<style scoped>
.dhic-table-panel { display: flex; min-width: 0; min-height: 0; flex-direction: column; overflow: hidden; border: 1px solid var(--dhic-border-color); border-radius: var(--dhic-radius-large); background: var(--dhic-card-bg); }
.dhic-table-panel__header { display: flex; min-height: 50px; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 14px; border-bottom: 1px solid var(--dhic-border-color-light); }
h2 { margin: 0; font-size: 16px; font-weight: 600; } p { margin: 3px 0 0; color: var(--dhic-text-secondary); font-size: 12px; }
.dhic-table-panel__actions { display: flex; align-items: center; gap: 8px; }
.dhic-table-panel__toolbar { padding: 10px 12px 0; }
.dhic-table-panel__body { min-width: 0; min-height: 0; flex: 1; overflow: auto; }
.dhic-table-panel__footer { display: flex; min-height: 48px; align-items: center; justify-content: flex-end; padding: 8px 12px; border-top: 1px solid var(--dhic-border-color-light); }
</style>