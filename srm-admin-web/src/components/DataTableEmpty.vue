<script setup lang="ts">
import { EMPTY_LIST_DESC, EMPTY_LIST_HINT } from '../constants/emptyCopy'
</script>

<template>
  <el-empty :description="EMPTY_LIST_DESC" :image-size="56">
    <template #default>
      <p class="data-table-empty-hint">{{ EMPTY_LIST_HINT }}</p>
    </template>
  </el-empty>
</template>

<style scoped>
.data-table-empty-hint {
  margin: 0 auto;
  max-width: 400px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>
