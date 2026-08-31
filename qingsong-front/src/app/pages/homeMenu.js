/**
 * 首页路由菜单（app-card）单一真源
 *
 * 同时供两处使用：
 * 1. HomePage.vue —— v-for 渲染菜单卡片（图标 style 直接绑定 icon.gradient / icon.color）
 * 2. scripts/contrast-audit.mjs —— 对比度硬门禁（图标颜色 vs 图标渐变 ≥ 3:1，WCAG 非文本）
 *
 * 修改 icon.gradient / icon.color 后必须跑 `npm run test:contrast` 确认通过。
 * icon.svg 为 <svg viewBox="0 0 24 24" fill="none"> 的内部静态可信标记，经 v-html 渲染，
 * 颜色一律走 currentColor（由 icon.color 驱动）。
 */
export const homeMenuItems = [
  {
    key: 'chat',
    title: 'AI 对话',
    description: '与AI助手进行智能对话',
    path: '/chat',
    icon: {
      gradient: 'linear-gradient(135deg, #0f9bff 0%, #44e2c4 100%)',
      color: '#333333',
      svg: `<path
        d="M7.12 18.36L4 20l1.08-3.46A7.77 7.77 0 013.5 12c0-4.14 3.8-7.5 8.5-7.5s8.5 3.36 8.5 7.5-3.8 7.5-8.5 7.5c-1.28 0-2.5-.25-3.62-.7-.42-.16-.88-.14-1.26.06z"
        fill="currentColor" opacity="0.22" />
      <path
        d="M7.12 18.36L4 20l1.08-3.46A7.77 7.77 0 013.5 12c0-4.14 3.8-7.5 8.5-7.5s8.5 3.36 8.5 7.5-3.8 7.5-8.5 7.5c-1.28 0-2.5-.25-3.62-.7-.42-.16-.88-.14-1.26.06z"
        stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M8.75 10.5h6.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
      <path d="M8.75 13.5h4.25" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
      <circle cx="15.85" cy="13.5" r="1.15" fill="currentColor" />`
    }
  },
  {
    key: 'pdf-reader',
    title: 'PDF 阅读',
    description: '阅读 PDF 书籍并使用 TTS 朗读',
    path: '/pdf-reader',
    icon: {
      gradient: 'linear-gradient(135deg, #0f766e 0%, #38b2ac 100%)',
      color: '#ffffff',
      svg: `<path d="M6 3h9l3 3v15H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
      <path d="M14 3v4h4M8 12h8M8 15h6M8 18h4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />`
    }
  },
  {
    key: 'knowledge-base',
    title: '知识库管理',
    description: '创建与维护团队知识库',
    path: '/knowledge-base',
    icon: {
      // 历史遗留：模板曾引用不存在的 .knowledge-base-icon 类导致图标无底色，此处补上并通过审计校验
      gradient: 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)',
      color: '#ffffff',
      svg: `<path d="M4 4h12a4 4 0 0 1 4 4v12H8a4 4 0 0 0-4 4V4z" stroke="currentColor" stroke-width="2"
        stroke-linecap="round" stroke-linejoin="round" />
      <path d="M8 4v16" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M12 8h6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M12 12h6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />`
    }
  },
  {
    key: 'config',
    title: '系统配置',
    description: '管理API密钥和模型设置',
    path: '/config',
    icon: {
      gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
      color: '#333333',
      svg: `<path d="M12 15a3 3 0 100-6 3 3 0 000 6z" stroke="currentColor" stroke-width="2" stroke-linecap="round"
        stroke-linejoin="round" />
      <path
        d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 010 2.83 2 2 0 010 2.83l-.06-.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"
        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />`
    }
  }
]
