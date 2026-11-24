<template>
  <svg viewBox="0 0 72 56" width="66" height="52" fill="none" stroke="#52616a"
    stroke-width="1.3" stroke-linejoin="round" stroke-linecap="round" aria-hidden="true" focusable="false">
    <g v-if="kind === 'building'">
      <path fill="#edf1f2" d="m16 13 27 5v32l-27-5z"/>
      <path fill="#c2cfd2" d="m43 18 13-6v32l-13 6z"/>
      <path fill="#e4eaec" d="m16 13 13-6 27 5-13 6z"/>
      <path d="m21 21 16 3m-16 4 16 3m-16 4 16 3m10-15 5-2m-5 9 5-2m-5 9 5-2"/>
      <path fill="#dfa960" d="m26 47 0-7 7 1v7z"/>
    </g>
    <g v-else-if="kind === 'basement'">
      <path fill="#eef1f2" d="m15 23 15-8 26 5-15 9z"/>
      <path fill="#d6dfe1" d="m15 23 26 6v16l-26-6z"/>
      <path fill="#b7c6c9" d="m41 29 15-9v16l-15 9z"/>
      <path fill="#f6cc88" d="m20 40 0-10 14 3v10z"/>
      <path d="m24 33 6 1m-6 2 6 1"/>
      <path stroke-dasharray="3 3" d="M4 22h9m45 0h10"/>
      <path d="m11 45 7-4m-2 8 7-4"/>
    </g>
    <g v-else>
      <path fill="#eef2f3" d="m9 33 18-9 36 7-18 10z"/>
      <path fill="#c4ced1" d="m9 33 36 8v5L9 38z"/>
      <path fill="#aebabe" d="m45 41 18-10v5L45 46z"/>
      <path :fill="kind === 'roof' ? '#e0e6e7' : '#f6cc88'" d="m13 21 30 6v12l-30-6z"/>
      <path :fill="kind === 'roof' ? '#c1ced0' : '#d9aa66'" d="m43 27 16-8v12l-16 8z"/>
      <path d="m19 24 0 7m8-5 0 7m8-5 0 7m14-8 0 6m5-9 0 6"/>
      <path :fill="kind === 'roof' ? '#f6cc88' : '#e4ebec'" d="m9 19 18-9 36 7-18 10z"/>
      <path fill="#ccd6d8" d="m9 19 36 8v3L9 22zm36 8 18-10v3L45 30z"/>
      <g v-if="kind === 'roof'">
        <path fill="#e5ebec" d="m26 14 8-4 11 2-8 4zm0 0 11 2v6l-11-2z"/>
        <path fill="#b7c4c7" d="m37 16 8-4v6l-8 4z"/>
      </g>
    </g>
  </svg>
</template>

<script setup>
defineProps({ kind: { type: String, required: true } })
</script>
