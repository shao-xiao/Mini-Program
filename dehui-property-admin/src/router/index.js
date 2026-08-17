import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import { createAuthGuard } from './authGuard.js'

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
    children: [
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/DashboardView.vue'),
        meta: { title: '首页驾驶舱' }
      },
      {
        path: '/403',
        name: 'Forbidden',
        component: () => import('../views/error/ForbiddenView.vue'),
        meta: { title: '无权限访问', publicAuth: true }
      },

      {
        path: '/assets/spaces',
        name: 'AssetSpaces',
        component: () => import('../views/assets/AssetSpacesView.vue'),
        meta: { title: '楼宇空间管理' }
      },
      {
        path: '/assets/equipment',
        name: 'AssetEquipment',
        component: () => import('../views/building/EquipmentList.vue'),
        meta: { title: '设备台账' }
      },
      {
        path: '/assets/energy',
        name: 'AssetEnergy',
        component: () => import('../views/assets/AssetEnergyView.vue'),
        meta: { title: '能耗管理' }
      },
      {
        path: '/assets/parking',
        name: 'AssetParking',
        component: () => import('../views/assets/AssetParkingView.vue'),
        meta: { title: '停车位管理' }
      },
      {
        path: '/assets/meetings',
        name: 'AssetMeetings',
        component: () => import('../views/assets/AssetMeetingsView.vue'),
        meta: { title: '会议管理' }
      },
      {
        path: '/assets',
        redirect: '/assets/spaces'
      },
      {
        path: '/buildings',
        name: 'BuildingList',
        redirect: to => ({ path: '/assets/spaces', query: to.query })
      },
      {
        path: '/floors',
        name: 'FloorList',
        redirect: to => ({ path: '/assets/spaces', query: to.query })
      },
      {
        path: '/rooms',
        name: 'RoomList',
        redirect: to => ({ path: '/assets/spaces', query: to.query })
      },
      {
        path: '/equipment',
        name: 'EquipmentList',
        redirect: to => ({ path: '/assets/equipment', query: to.query })
      },

      {
        path: '/tenant/workspace',
        name: 'TenantWorkspace',
        component: () => import('../views/tenant/TenantWorkspaceView.vue'),
        meta: { title: '租户档案' }
      },
      {
        path: '/tenants',
        name: 'TenantList',
        redirect: to => ({ path: '/tenant/workspace', query: to.query })
      },
      {
        path: '/leases',
        name: 'LeaseList',
        redirect: to => ({ path: '/tenant/workspace', query: { ...to.query, panel: 'occupancy' } })
      },
      {
        path: '/fee-rules',
        name: 'FeeRuleList',
        redirect: to => ({ path: '/bills', query: to.query })
      },
      {
        path: '/billing-plans',
        name: 'BillingPlanList',
        redirect: to => ({ path: '/bills', query: to.query })
      },
      {
        path: '/investment/manage',
        name: 'InvestmentManage',
        component: () => import('../views/investment/InvestmentManageView.vue'),
        meta: { title: '招商管理' }
      },
      {
        path: '/investment/leads',
        name: 'InvestmentLeadList',
        component: () => import('../views/investment/InvestmentLeadList.vue'),
        meta: { title: '招商线索' }
      },
      {
        path: '/investment/contents',
        name: 'InvestmentContentList',
        redirect: to => ({ path: '/investment/manage', query: { ...to.query, tab: 'content' } })
      },
      {
        path: '/investment/listings',
        name: 'InvestmentRoomListingList',
        redirect: to => ({ path: '/investment/manage', query: { ...to.query, tab: 'listings' } })
      },
      {
        path: '/investment/announcements',
        name: 'InvestmentAnnouncementList',
        component: () => import('../views/operation/AnnouncementList.vue'),
        meta: { title: '公告管理' }
      },
      {
        path: '/contracts',
        name: 'ContractList',
        redirect: to => ({ path: '/tenant/workspace', query: { ...to.query, panel: 'contracts' } })
      },
      {
        path: '/bills',
        name: 'BillList',
        component: () => import('../views/tenant/BillList.vue'),
        meta: { title: '账单管理' }
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
        redirect: to => ({ path: '/assets/parking', query: { ...to.query, tab: 'spaces' } })
      },
      {
        path: '/parking/bills',
        name: 'ParkingBillList',
        redirect: to => ({ path: '/bills', query: { ...to.query, billType: 'PARKING' } })
      },

      {
        path: '/meetings/rooms',
        name: 'MeetingRoomList',
        redirect: to => ({ path: '/assets/meetings', query: { ...to.query, tab: 'rooms' } })
      },
      {
        path: '/meetings/bookings',
        name: 'MeetingBookingList',
        redirect: to => ({ path: '/assets/meetings', query: { ...to.query, tab: 'bookings' } })
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
        redirect: to => ({ path: '/investment/announcements', query: { ...to.query } })
      },

      {
        path: '/energy/meters',
        name: 'EnergyMeterList',
        redirect: to => ({ path: '/assets/energy', query: { ...to.query, tab: 'meters' } })
      },
      {
        path: '/energy/records',
        name: 'EnergyRecordList',
        redirect: to => ({ path: '/assets/energy', query: { ...to.query, tab: 'records' } })
      },
      {
        path: '/energy/stats',
        name: 'EnergyStats',
        redirect: to => ({ path: '/assets/energy', query: { ...to.query, tab: 'statistics' } })
      },
      {
        path: '/energy/statistics',
        redirect: to => ({ path: '/assets/energy', query: { ...to.query, tab: 'statistics' } })
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
        path: '/system/mobile-users',
        name: 'MobileUserList',
        component: () => import('../views/system/MobileUserList.vue'),
        meta: { title: '小程序用户' }
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

router.beforeEach(createAuthGuard())

export default router
