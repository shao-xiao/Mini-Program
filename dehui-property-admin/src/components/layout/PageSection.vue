<template>
  <section class="dhic-page-section" :class="{ 'is-compact': compact }">
    <header v-if="title || $slots.actions" class="dhic-page-section__header">
      <div class="dhic-page-section__identity">
        <h2 v-if="title">{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="dhic-page-section__actions"><slot name="actions" /></div>
    </header>
    <div class="dhic-page-section__body"><slot /></div>
  </section>
</template>
<script setup>
defineOptions({ name: 'DhicPageSection' })
defineProps({ title: { type: String, default: '' }, description: { type: String, default: '' }, compact: Boolean })
</script>
<style scoped>
.dhic-page-section { overflow: hidden; border: 1px solid var(--dhic-border-color); border-radius: var(--dhic-radius-large); background: var(--dhic-card-bg); box-shadow: var(--dhic-shadow-card); }
.dhic-page-section__header { display: flex; min-height: 44px; align-items: center; justify-content: space-between; gap: 12px; padding: 9px 14px; border-bottom: 1px solid var(--dhic-border-color-light); }
.dhic-page-section__identity { min-width: 0; }
h2 { position: relative; margin: 0; padding-left: 10px; font-size: 14px; font-weight: 600; }
h2::before { position: absolute; top: 3px; bottom: 3px; left: 0; width: 3px; border-radius: 2px; background: var(--dhic-brand-red); content: ''; }
p { margin: 3px 0 0; color: var(--dhic-text-secondary); font-size: 12px; }
.dhic-page-section__actions { display: flex; align-items: center; gap: 8px; }
.dhic-page-section__body { padding: 14px; }
.is-compact .dhic-page-section__body { padding: 10px; }
</style>