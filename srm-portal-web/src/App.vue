<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <div class="shell">
      <header class="nav">
        <router-link to="/pos">采购订单</router-link>
        <router-link to="/asn">发货通知 (ASN)</router-link>
      </header>
      <router-view />
    </div>
  </el-config-provider>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}
.nav {
  display: flex;
  gap: 20px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--el-border-color);
  background: var(--el-fill-color-light);
}
.nav a {
  color: var(--el-color-primary);
  text-decoration: none;
  font-weight: 500;
}
.nav a.router-link-active {
  text-decoration: underline;
}
</style>
