<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { masterApi, type MaterialWarehouseRef } from '../../api/master'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const rows = ref<MaterialWarehouseRef[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function load() {
  const r = await masterApi.listWarehouses({
    page: currentPage.value - 1,
    size: pageSize.value,
  })
  rows.value = r.data.content
  total.value = r.data.totalElements
}

function onPageChange() {
  load()
}

function onSizeChange() {
  currentPage.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <span class="title">仓库</span>
    </div>
    <p class="hint">
      数据来自数据库
      <code>warehouse</code>
      表（四厂仓从 U9/帆软同步到物料后写入）。仓库名称在同步时已按编码请求帆软 cangku.cpt 解析并落库；列表中的「物料条数」为当前引用该仓编码的物料数量。
    </p>
    <el-table :data="rows" stripe>
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="procurementOrg" label="采购组织" width="108" />
      <el-table-column prop="warehouseCode" label="仓库编码" width="140" show-overflow-tooltip />
      <el-table-column prop="warehouseName" label="仓库名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="materialCount" label="物料条数" width="100" />
    </el-table>
    <div class="pager-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0 0 16px;
  line-height: 1.5;
}
.pager-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
