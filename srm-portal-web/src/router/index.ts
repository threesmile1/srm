import { createRouter, createWebHistory } from 'vue-router'
import { usePortalAuthStore } from '../stores/portalAuth'
import PortalLayout from '../layouts/PortalLayout.vue'
import LoginView from '../views/LoginView.vue'
import PortalPoListView from '../views/PortalPoListView.vue'
import PortalPoDetailView from '../views/PortalPoDetailView.vue'
import PortalAsnListView from '../views/PortalAsnListView.vue'
import PortalAsnCreateView from '../views/PortalAsnCreateView.vue'
import PortalInvoiceListView from '../views/PortalInvoiceListView.vue'
import PortalInvoiceCreateView from '../views/PortalInvoiceCreateView.vue'
import PortalReconListView from '../views/PortalReconListView.vue'
import PortalRfqListView from '../views/PortalRfqListView.vue'
import PortalRfqDetailView from '../views/PortalRfqDetailView.vue'
import PortalNotificationsView from '../views/PortalNotificationsView.vue'
import PortalPerfListView from '../views/PortalPerfListView.vue'
import PortalPerfDetailView from '../views/PortalPerfDetailView.vue'
import PortalContractListView from '../views/PortalContractListView.vue'
import PortalQualityView from '../views/PortalQualityView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'portal-login', component: LoginView, meta: { public: true } },
    {
      path: '/',
      component: PortalLayout,
      children: [
        { path: '', redirect: '/pos' },
        { path: 'pos', name: 'portal-po-list', component: PortalPoListView },
        { path: 'pos/:id', name: 'portal-po-detail', component: PortalPoDetailView },
        { path: 'asn', name: 'portal-asn-list', component: PortalAsnListView },
        { path: 'asn/new', name: 'portal-asn-new', component: PortalAsnCreateView },
        { path: 'rfq', name: 'portal-rfq-list', component: PortalRfqListView },
        { path: 'rfq/:id', name: 'portal-rfq-detail', component: PortalRfqDetailView },
        { path: 'invoices', name: 'portal-invoice-list', component: PortalInvoiceListView },
        { path: 'invoices/new', name: 'portal-invoice-new', component: PortalInvoiceCreateView },
        { path: 'reconciliation', name: 'portal-recon-list', component: PortalReconListView },
        { path: 'notifications', name: 'portal-notifications', component: PortalNotificationsView },
        { path: 'perf', name: 'portal-perf-list', component: PortalPerfListView },
        { path: 'perf/:id', name: 'portal-perf-detail', component: PortalPerfDetailView },
        { path: 'contracts', name: 'portal-contracts', component: PortalContractListView },
        { path: 'quality', name: 'portal-quality', component: PortalQualityView },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = usePortalAuthStore()
  if (to.meta.public) {
    if (to.name === 'portal-login' && auth.isLoggedIn) {
      return { path: '/pos' }
    }
    return true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  let title = '百得胜采购协同'
  if (to.path.startsWith('/pos/') && to.params.id) title = '订单详情'
  else if (to.path === '/asn/new') title = '新建 ASN'
  else if (to.path === '/pos') title = '采购订单'
  else if (to.path === '/asn') title = '发货通知'
  else if (to.path === '/rfq') title = '询价报价'
  else if (to.path.startsWith('/rfq/')) title = '询价详情'
  else if (to.path === '/notifications') title = '消息中心'
  else if (to.path === '/perf') title = '绩效考核'
  else if (to.path.startsWith('/perf/')) title = '考核详情'
  else if (to.path === '/contracts') title = '我的合同'
  else if (to.path === '/reconciliation') title = '对账'
  else if (to.path === '/quality') title = '质量协同'
  document.title = `${title} · 百得胜`
})

export default router
