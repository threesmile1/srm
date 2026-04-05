import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import HomeView from '../views/HomeView.vue'
import SuppliersView from '../views/master/SuppliersView.vue'
import MaterialsView from '../views/master/MaterialsView.vue'
import PoListView from '../views/purchase/PoListView.vue'
import PoCreateView from '../views/purchase/PoCreateView.vue'
import PoDetailView from '../views/purchase/PoDetailView.vue'
import GrListView from '../views/purchase/GrListView.vue'
import GrCreateView from '../views/purchase/GrCreateView.vue'
import PurchaseExecutionReportView from '../views/purchase/PurchaseExecutionReportView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', redirect: '/home' },
        { path: 'home', name: 'home', component: HomeView, meta: { title: '首页' } },
        { path: 'master/suppliers', name: 'suppliers', component: SuppliersView, meta: { title: '供应商' } },
        { path: 'master/materials', name: 'materials', component: MaterialsView, meta: { title: '物料' } },
        { path: 'purchase/orders', name: 'po-list', component: PoListView, meta: { title: '采购订单' } },
        { path: 'purchase/orders/new', name: 'po-new', component: PoCreateView, meta: { title: '新建订单' } },
        { path: 'purchase/orders/:id', name: 'po-detail', component: PoDetailView, meta: { title: '订单详情' } },
        { path: 'purchase/receipts', name: 'gr-list', component: GrListView, meta: { title: '收货单' } },
        { path: 'purchase/receipts/new', name: 'gr-new', component: GrCreateView, meta: { title: '新建收货' } },
        {
          path: 'purchase/reports/execution',
          name: 'report-execution',
          component: PurchaseExecutionReportView,
          meta: { title: '采购执行报表' },
        },
      ],
    },
  ],
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · SRM 管理端` : 'SRM 管理端'
})

export default router
