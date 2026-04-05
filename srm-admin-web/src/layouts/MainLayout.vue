<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px">
      <div class="brand">SRM 管理端</div>
      <el-menu :key="route.path" :default-active="route.path" router>
        <el-menu-item index="/home">首页</el-menu-item>
        <el-sub-menu index="master">
          <template #title>主数据</template>
          <el-menu-item index="/master/suppliers">供应商</el-menu-item>
          <el-menu-item index="/master/materials">物料</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="po">
          <template #title>采购</template>
          <el-menu-item index="/purchase/orders">订单列表</el-menu-item>
          <el-menu-item index="/purchase/orders/new">新建订单</el-menu-item>
          <el-menu-item index="/purchase/receipts">收货单</el-menu-item>
          <el-menu-item index="/purchase/receipts/new">新建收货</el-menu-item>
          <el-menu-item index="/purchase/reports/execution">执行报表</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}
.brand {
  padding: 16px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color);
}
</style>
