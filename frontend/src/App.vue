<template>
  <div class="app-root">
    <AppHeader v-if="$route.meta.requiresAuth" />
    <router-view />
  </div>
</template>

<script setup lang="ts">
import AppHeader from './components/AppHeader.vue'
</script>
