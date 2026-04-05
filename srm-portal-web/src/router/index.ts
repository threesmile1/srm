import { createRouter, createWebHistory } from 'vue-router'
import { usePortalAuthStore } from '../stores/portalAuth'
import PortalLayout from '../layouts/PortalLayout.vue'
import LoginView from '../views/LoginView.vue'
import PortalPoListView from '../views/PortalPoListView.vue'
import PortalPoDetailView from '../views/PortalPoDetailView.vue'
import PortalAsnListView from '../views/PortalAsnListView.vue'
import PortalAsnCreateView from '../views/PortalAsnCreateView.vue'

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
  document.title = `${title} · 百得胜`
})

export default router
