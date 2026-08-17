<template>
  <div class="list-summary-grid">
    <div
      v-for="item in cards"
      :key="item.label"
      class="list-summary-card"
      :class="getTone(item)"
    >
      <div class="summary-label">{{ item.label }}</div>
      <div class="summary-value">{{ item.value }}</div>
      <div class="summary-footer">
        <span v-if="item.trend" class="summary-trend" :class="trendClass(item.trend)">
          <el-icon>
            <Top v-if="item.trend.direction === 'up'" />
            <Bottom v-else-if="item.trend.direction === 'down'" />
            <Minus v-else />
          </el-icon>
          {{ item.trend.value }}
        </span>
        <span class="summary-desc">{{ item.desc }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Bottom, Minus, Top } from '@element-plus/icons-vue'

defineProps({
  cards: {
    type: Array,
    default: () => []
  }
})

function getTone(item) {
  return item?.tone || item?.className || 'neutral'
}

function trendClass(trend) {
  if (trend?.favorable === true) return 'favorable'
  if (trend?.favorable === false) return 'unfavorable'
  return 'neutral'
}
</script>

<style scoped>
.list-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(172px, 1fr));
  gap: 12px;
  margin: 0 0 16px;
}

.list-summary-card {
  --summary-accent: #94a3b8;
  --summary-value: var(--dhic-text-primary);
  --summary-bg: var(--dhic-card-bg);
  position: relative;
  min-height: 92px;
  padding: 14px 14px 13px 17px;
  overflow: hidden;
  border: 1px solid var(--dhic-border-color);
  border-radius: var(--dhic-radius-large);
  background: var(--summary-bg);
  box-shadow: var(--dhic-shadow-card);
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.list-summary-card::before {
  position: absolute;
  top: 12px;
  bottom: 12px;
  left: 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--summary-accent);
  content: '';
}

.list-summary-card:hover {
  border-color: #d3d8df;
  box-shadow: 0 3px 10px rgba(32, 33, 36, 0.06);
}

.list-summary-card.neutral {
  --summary-accent: #8a96a8;
  --summary-bg: #f8fafc;
}

.list-summary-card.primary {
  --summary-accent: var(--dhic-brand-red);
  --summary-value: var(--dhic-brand-red);
  --summary-bg: #fffafa;
}

.list-summary-card.danger {
  --summary-accent: #d93026;
  --summary-value: #b42318;
  --summary-bg: #fff6f6;
}

.list-summary-card.success {
  --summary-accent: #18a058;
  --summary-value: #137333;
  --summary-bg: #f4fbf7;
}

.list-summary-card.warning {
  --summary-accent: #d97706;
  --summary-value: #9a4d00;
  --summary-bg: #fff9ed;
}

.list-summary-card.money {
  --summary-accent: #64748b;
  --summary-value: #334155;
  --summary-bg: #fafbfc;
}

.summary-label,
.summary-desc {
  overflow: hidden;
  color: #7b8494;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-label {
  font-weight: 500;
}

.summary-value {
  margin: 7px 0 4px;
  color: var(--summary-value);
  font-size: 25px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
  line-height: 1.15;
  word-break: break-word;
}

.summary-desc {
  color: #98a2b3;
}

.summary-footer {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.summary-trend {
  display: inline-flex;
  align-items: center;
  flex: none;
  gap: 2px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.summary-trend.favorable {
  color: #15803d;
}

.summary-trend.unfavorable {
  color: #c2413a;
}

.summary-trend.neutral {
  color: #7b8494;
}

@media (max-width: 640px) {
  .list-summary-grid {
    grid-template-columns: 1fr;
  }

  .summary-value {
    font-size: 22px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .list-summary-card {
    transition: none;
  }

}
</style>
