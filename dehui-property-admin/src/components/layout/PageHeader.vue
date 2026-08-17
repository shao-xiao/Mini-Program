<template>
  <header class="dhic-page-header">
    <div class="dhic-page-header__identity">
      <slot name="eyebrow" />
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="dhic-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
defineOptions({ name: 'DhicPageHeader' })
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' }
})
</script>

<style scoped>
.dhic-page-header {
  display: flex;
  min-width: 0;
  min-height: 56px;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 0 4px 16px;
  border-left: 3px solid var(--dhic-brand-red);
}
.dhic-page-header__identity { min-width: 0; }
h1 { margin: 0; color: var(--dhic-text-primary); font-size: 20px; font-weight: 600; line-height: 1.35; }
p { margin: 4px 0 0; color: var(--dhic-text-secondary); font-size: 13px; line-height: 1.5; }
.dhic-page-header__actions { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
@media (max-width: 900px) { .dhic-page-header { flex-wrap: wrap; } .dhic-page-header__actions { width: 100%; justify-content: flex-start; } }
</style>