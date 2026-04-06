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
import PurchaseAnalyticsReportView from '../views/purchase/PurchaseAnalyticsReportView.vue'
import PrListView from '../views/pr/PrListView.vue'
import PrCreateView from '../views/pr/PrCreateView.vue'
import PrDetailView from '../views/pr/PrDetailView.vue'
import ApprovalRulesView from '../views/approval/ApprovalRulesView.vue'
import ApprovalListView from '../views/approval/ApprovalListView.vue'
import PerfEvalListView from '../views/perf/PerfEvalListView.vue'
import PerfEvalCreateView from '../views/perf/PerfEvalCreateView.vue'
import PerfEvalDetailView from '../views/perf/PerfEvalDetailView.vue'
import InvoiceListView from '../views/invoice/InvoiceListView.vue'
import InvoiceDetailView from '../views/invoice/InvoiceDetailView.vue'
import ReconListView from '../views/invoice/ReconListView.vue'
import UsersView from '../views/system/UsersView.vue'
import AuditLogView from '../views/system/AuditLogView.vue'
import NotificationsView from '../views/collab/NotificationsView.vue'
import QualityCoordinationView from '../views/collab/QualityCoordinationView.vue'
import RfqListView from '../views/sourcing/RfqListView.vue'
import RfqCreateView from '../views/sourcing/RfqCreateView.vue'
import RfqDetailView from '../views/sourcing/RfqDetailView.vue'
import ContractListView from '../views/sourcing/ContractListView.vue'
import ContractCreateView from '../views/sourcing/ContractCreateView.vue'
import ContractDetailView from '../views/sourcing/ContractDetailView.vue'

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
        { path: 'pr', name: 'pr-list', component: PrListView, meta: { title: '请购单' } },
        { path: 'pr/new', name: 'pr-new', component: PrCreateView, meta: { title: '新建请购' } },
        { path: 'pr/:id', name: 'pr-detail', component: PrDetailView, meta: { title: '请购详情' } },
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
        {
          path: 'purchase/reports/analytics',
          name: 'report-analytics',
          component: PurchaseAnalyticsReportView,
          meta: { title: '采购分析报表' },
        },
        { path: 'approval/rules', name: 'approval-rules', component: ApprovalRulesView, meta: { title: '审批规则' } },
        { path: 'approval/list', name: 'approval-list', component: ApprovalListView, meta: { title: '审批工作台' } },
        { path: 'perf/evaluations', name: 'perf-list', component: PerfEvalListView, meta: { title: '绩效考核' } },
        { path: 'perf/evaluations/new', name: 'perf-new', component: PerfEvalCreateView, meta: { title: '新建考核' } },
        { path: 'perf/evaluations/:id', name: 'perf-detail', component: PerfEvalDetailView, meta: { title: '考核详情' } },
        { path: 'invoice', name: 'invoice-list', component: InvoiceListView, meta: { title: '发票管理' } },
        { path: 'invoice/:id', name: 'invoice-detail', component: InvoiceDetailView, meta: { title: '发票详情' } },
        { path: 'reconciliation', name: 'recon-list', component: ReconListView, meta: { title: '对账管理' } },
        { path: 'notifications', name: 'notifications', component: NotificationsView, meta: { title: '消息中心' } },
        { path: 'quality', name: 'quality', component: QualityCoordinationView, meta: { title: '质量协同' } },
        { path: 'sourcing/rfq', name: 'rfq-list', component: RfqListView, meta: { title: '询价单' } },
        { path: 'sourcing/rfq/new', name: 'rfq-new', component: RfqCreateView, meta: { title: '新建询价' } },
        { path: 'sourcing/rfq/:id', name: 'rfq-detail', component: RfqDetailView, meta: { title: '询价详情' } },
        { path: 'sourcing/contracts', name: 'contract-list', component: ContractListView, meta: { title: '合同台账' } },
        { path: 'sourcing/contracts/new', name: 'contract-new', component: ContractCreateView, meta: { title: '新建合同' } },
        { path: 'sourcing/contracts/:id', name: 'contract-detail', component: ContractDetailView, meta: { title: '合同详情' } },
        { path: 'system/users', name: 'users', component: UsersView, meta: { title: '用户管理' } },
        { path: 'system/audit-log', name: 'audit-log', component: AuditLogView, meta: { title: '审计日志' } },
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
