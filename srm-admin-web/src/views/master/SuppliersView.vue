<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { masterApi, type MaterialSupplierRef } from '../../api/master'
import DataTableEmpty from '../../components/DataTableEmpty.vue'

const rows = ref<MaterialSupplierRef[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function load() {
  const r = await masterApi.listSuppliersMaterialDerived({
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
      <span class="title">供应商</span>
    </div>
    <p class="hint">
      列表来自物料主数据中的 U9 供应商编码/名称及多供应商表；新建订单、请购等下拉选项与此一致（后端会按编码同步 supplier 主档并授权全部采购组织）。
    </p>
    <el-table :data="rows" stripe>
      <template #empty>
        <DataTableEmpty />
      </template>
      <el-table-column prop="u9SupplierCode" label="U9 供应商编码" width="160" />
      <el-table-column prop="u9SupplierName" label="U9 供应商名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="refCount" label="引用计数" width="100" />
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
