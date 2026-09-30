import { createRouter, createWebHashHistory } from 'vue-router'
import LayoutContainer from '@/layouts/basic/LayoutContainer.vue'

const routes: Array<any> = [
  {
    path: '/',
    name: 'home',
    redirect: '/dashboard',
    component: LayoutContainer,
    children: [
      {
        path: '/dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardHome.vue')
      }
    ]
  },
  {
    path: '/auth',
    component: LayoutContainer,
    children: [
      {
        name: 'login',
        path: 'login',
        component: () => import('@/views/common/auth/AuthLogin.vue')
      },
      {
        name: 'register',
        path: 'register',
        component: () => import('@/views/common/auth/AuthRegister.vue')
      }
    ]
  },
  {
    path: '/admin',
    component: LayoutContainer,
    children: [
      {
        name: 'users',
        path: 'users',
        component: () => import('@/views/user/UserView.vue')
      },
      {
        name: 'roles',
        path: 'roles',
        component: () => import('@/views/role/RoleView.vue')
      },
      {
        name: 'methods',
        path: 'methods',
        component: () => import('@/views/method/MethodView.vue')
      },
      {
        name: 'menus',
        path: 'menus',
        component: () => import('@/views/menu/MenuView.vue')
      }
    ]
  },
  {
    path: '/json',
    component: LayoutContainer,
    children: [
      {
        name: 'pretty',
        path: 'pretty',
        component: () => import('@/views/json/JsonPrettyView.vue')
      }
    ]
  },
  {
    path: '/common',
    component: LayoutContainer,
    children: [
      {
        name: '403',
        path: '403',
        component: () => import('@/views/common/code/PageForbidden.vue')
      },
      {
        name: '404',
        path: '404',
        component: () => import('@/views/common/code/PageNotFound.vue')
      },
      {
        name: 'network',
        path: 'network',
        component: () => import('@/views/common/code/PageNetwork.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 路由转换前增加特殊编码，404，403 等页面处理
router.beforeEach((to, from, next) => {
  if (to.matched.length === 0) {
    next({ name: '404' })
  }
  else {
    next()
  }
})

export default router
