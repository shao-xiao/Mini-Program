import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import { canAccessPath, getCurrentRoles } from '../config/access'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },

  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/DashboardView.vue'),
        meta: { title: '首页驾驶舱' }
      },

      {
        path: '/buildings',
        name: 'BuildingList',
        component: () => import('../views/building/BuildingList.vue'),
        meta: { title: '楼宇管理' }
      },
      {
        path: '/floors',
        name: 'FloorList',
        component: () => import('../views/building/FloorList.vue'),
        meta: { title: '楼层管理' }
      },
      {
        path: '/rooms',
        name: 'RoomList',
        component: () => import('../views/building/RoomList.vue'),
        meta: { title: '房间管理' }
      },
      {
        path: '/equipment',
        name: 'EquipmentList',
        component: () => import('../views/building/EquipmentList.vue'),
        meta: { title: '设备台账' }
      },

      {
        path: '/tenants',
        name: 'TenantList',
        component: () => import('../views/tenant/TenantList.vue'),
        meta: { title: '租户管理' }
      },
      {
        path: '/leases',
        name: 'LeaseList',
        component: () => import('../views/tenant/LeaseList.vue'),
        meta: { title: '租约管理' }
      },
      {
        path: '/contracts',
        name: 'ContractList',
        component: () => import('../views/tenant/ContractList.vue'),
        meta: { title: '合同管理' }
      },
      {
        path: '/bills',
        name: 'BillList',
        component: () => import('../views/tenant/BillList.vue'),
        meta: { title: '账单管理' }
      },
      {
        path: '/feerules',
        name: 'FeeRuleList',
        component: () => import('../views/tenant/FeeRuleList.vue'),
        meta: { title: '收费规则管理' }
      },
      {
        path: '/finance/dashboard',
        name: 'FinanceDashboard',
        component: () => import('../views/finance/FinanceDashboard.vue'),
        meta: { title: '财务看板' }
      },

      {
        path: '/parking/spaces',
        name: 'ParkingSpaceList',
        component: () => import('../views/parking/ParkingSpaceList.vue'),
        meta: { title: '车位管理' }
      },
      {
        path: '/parking/bills',
        name: 'ParkingBillList',
        component: () => import('../views/parking/ParkingBillList.vue'),
        meta: { title: '停车账单' }
      },

      {
        path: '/workorders',
        name: 'WorkOrderList',
        component: () => import('../views/operation/WorkOrderList.vue'),
        meta: { title: '工单管理' }
      },
      {
        path: '/visitors',
        name: 'VisitorList',
        component: () => import('../views/operation/VisitorList.vue'),
        meta: { title: '访客管理' }
      },
      {
        path: '/inspections',
        name: 'InspectionList',
        component: () => import('../views/operation/InspectionList.vue'),
        meta: { title: '巡检记录' }
      },
      {
        path: '/announcements',
        name: 'AnnouncementList',
        component: () => import('../views/operation/AnnouncementList.vue'),
        meta: { title: '公告管理' }
      },

      {
        path: '/energy/records',
        name: 'EnergyRecordList',
        component: () => import('../views/energy/EnergyRecordList.vue'),
        meta: { title: '能耗抄表' }
      },
      {
        path: '/energy/stats',
        name: 'EnergyStats',
        component: () => import('../views/energy/EnergyStats.vue'),
        meta: { title: '能耗统计' }
      },

      {
        path: '/ai/daily-report',
        name: 'DailyReport',
        component: () => import('../views/ai/DailyReport.vue'),
        meta: { title: 'AI运营日报' }
      },

      {
        path: '/system/users',
        name: 'SystemUserList',
        component: () => import('../views/system/UserList.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: '/system/roles',
        name: 'SystemRoleList',
        component: () => import('../views/system/RoleList.vue'),
        meta: { title: '角色管理' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.path === '/login') {
    next()
    return
  }

  const token = localStorage.getItem('token')
  if (!token) {
    next('/login')
    return
  }

  const roles = getCurrentRoles()

  if (!canAccessPath(roles, to.path)) {
    next('/dashboard')
    return
  }

  next()
})

export default router
