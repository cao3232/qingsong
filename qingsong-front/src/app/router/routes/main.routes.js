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
    // 会话分离路由：roleId/chatId 均可选，三个形态（/chat、/chat/:roleId、/chat/:roleId/:chatId）
    // 共用同一 route name（AIChat），避免 AppShell 用 name 做组件 key 时 URL 变化触发 AIChatPage 重挂载全量刷新
    path: '/chat/:roleId?/:chatId?',
    name: 'AIChat',
    component: () => import('../../../modules/chat/index.js').then(module => module.AIChatPage)
  },
  {
    // 聊天消息收藏页：路径不能放 /chat/* 下（会被 /chat/:roleId?/:chatId? 动态段吞掉）
    path: '/chat-favorites',
    name: 'ChatFavorites',
    component: () => import('../../../modules/chat/index.js').then(module => module.ChatFavoritesPage)
  },
  {
    path: '/pdf-reader',
    name: 'PDFReader',
    meta: { failOpen: true },
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
    meta: { failOpen: true },
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
      },
      {
        path: 'dict',
        name: 'ConfigDict',
        component: () => import('../../../modules/tools/pages/DictManagePage.vue')
      }
    ]
  }
]
