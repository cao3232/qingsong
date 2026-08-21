export const mainRoutes = [
  {
    path: '/',
    name: 'Home',
    meta: { public: true },
    component: () => import('../../../app/pages/index.js').then(module => module.HomePage)
  },
  {
    path: '/login',
    name: 'Login',
    meta: { public: true, guestOnly: true },
    component: () => import('../../../app/pages/index.js').then(module => module.BiophilicAuthPage)
  },
  {
    path: '/register',
    name: 'Register',
    meta: { public: true, guestOnly: true },
    component: () => import('../../../app/pages/index.js').then(module => module.BiophilicAuthPage)
  },
  {
    path: '/chat',
    name: 'AIChat',
    component: () => import('../../../modules/chat/index.js').then(module => module.AIChatPage)
  },
  {
    path: '/pdf-reader',
    name: 'PDFReader',
    component: () => import('../../../modules/chat/index.js').then(module => module.PDFReaderPage)
  },
  {
    path: '/knowledge-base',
    name: 'KnowledgeBase',
    component: () => import('../../../modules/knowledge-base/index.js').then(module => module.KnowledgeBasePage)
  },
  {
    path: '/knowledge-base/:id',
    name: 'KnowledgeBaseDetail',
    component: () => import('../../../modules/knowledge-base/index.js').then(module => module.KnowledgeBaseDetailPage)
  },
  {
    path: '/config',
    name: 'Config',
    redirect: '/config/system',
    component: () => import('../../../modules/tools/pages/ConfigView.vue'),
    children: [
      {
        path: 'system',
        name: 'ConfigSystem',
        component: () => import('../../../modules/tools/pages/SystemSettingPage.vue')
      },
      {
        path: 'source',
        name: 'ConfigSource',
        component: () => import('../../../modules/tools/pages/ModelSourcePage.vue')
      },
      {
        path: 'model',
        name: 'ConfigModel',
        component: () => import('../../../modules/tools/pages/ModelManagePage.vue')
      },
      {
        path: 'role',
        name: 'ConfigRole',
        component: () => import('../../../modules/tools/pages/RoleManagePage.vue')
      },
      {
        path: 'user',
        name: 'ConfigUser',
        component: () => import('../../../modules/tools/pages/UserConfigPage.vue')
      }
    ]
  }
]
