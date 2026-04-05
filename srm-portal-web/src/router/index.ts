import { createRouter, createWebHistory } from 'vue-router'
import PortalPoListView from '../views/PortalPoListView.vue'
import PortalPoDetailView from '../views/PortalPoDetailView.vue'
import PortalAsnListView from '../views/PortalAsnListView.vue'
import PortalAsnCreateView from '../views/PortalAsnCreateView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/pos' },
    { path: '/pos', name: 'portal-po-list', component: PortalPoListView },
    { path: '/pos/:id', name: 'portal-po-detail', component: PortalPoDetailView },
    { path: '/asn', name: 'portal-asn-list', component: PortalAsnListView },
    { path: '/asn/new', name: 'portal-asn-new', component: PortalAsnCreateView },
  ],
})

router.afterEach(() => {
  document.title = 'SRM 供应商门户'
})

export default router
