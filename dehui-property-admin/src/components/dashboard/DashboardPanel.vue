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
  box-shadow: var(--dhic-shadow-subtle);
}
.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 50px;
  padding: 9px 14px;
  border-bottom: 1px solid var(--dhic-border-color-light);
  box-sizing: border-box;
}
.panel-heading__text { min-width: 0; }
h3 { margin: 0; color: var(--dhic-text-primary); font-size: 15px; font-weight: 600; line-height: 20px; }
p { margin: 1px 0 0; color: var(--dhic-text-secondary); font-size: 12px; line-height: 18px; }
.panel-meta {
  flex-shrink: 0;
  max-width: 50%;
  text-align: end;
  padding: 3px 8px;
  border-radius: var(--dhic-radius-small);
  background: var(--el-fill-color-light);
  color: var(--dhic-text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.panel-meta--alert { background: var(--dhic-danger-light); color: var(--dhic-danger); }
.panel-content { padding: 12px 14px; }
@media (max-width: 640px) {
  .panel-heading { padding: 12px; }
  .panel-content { padding: 12px; }
}
</style>
