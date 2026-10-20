<template>
  <section class="dashboard-panel">
    <header class="panel-heading">
      <div class="panel-heading__text">
        <h3>{{ title }}</h3>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <span v-if="meta" class="panel-meta" :class="{ 'panel-meta--alert': alert }">{{ meta }}</span>
    </header>
    <div class="panel-content"><slot /></div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  meta: { type: String, default: '' },
  alert: { type: Boolean, default: false }
})
</script>

<style scoped>
.dashboard-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--dhic-border-color);
  border-radius: var(--dhic-radius-large);
  background: var(--dhic-card-bg);
  box-shadow: 0 1px 3px rgba(32, 33, 36, .035);
}
.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 64px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--dhic-border-color-light);
  box-sizing: border-box;
}
.panel-heading__text { min-width: 0; }
h3 { margin: 0; color: var(--dhic-text-primary); font-size: 14px; font-weight: 600; line-height: 20px; }
p { margin: 3px 0 0; color: var(--dhic-text-secondary); font-size: 12px; line-height: 18px; }
.panel-meta {
  flex-shrink: 0;
  padding: 3px 8px;
  border-radius: 4px;
  background: #f5f6f8;
  color: #667085;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.panel-meta--alert { background: #fff0ee; color: #b42318; }
.panel-content { padding: 14px 18px; }
@media (max-width: 640px) {
  .panel-heading { padding: 12px; }
  .panel-content { padding: 12px; }
}
</style>
