import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import MainLayout from '../layouts/MainLayout.vue'
import LoginView from '../views/LoginView.vue'
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
    { path: '/login', name: 'login', component: LoginView, meta: { title: '登录', public: true } },
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', redirect: '/home' },
        { path: 'home', name: 'home', component: HomeView, meta: { title: '工作台' } },
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

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (to.name === 'login' && auth.isLoggedIn) {
      return { path: '/home' }
    }
    return true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 百得胜采购协同` : '百得胜采购协同'
})

export default router
