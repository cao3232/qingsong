/**
 * Modern Theme - Default sleek glassmorphism style
 * 现代风格 - 默认毛玻璃效果
 */
export default {
  id: 'modern',
  name: '现代风格',
  description: '默认的现代化毛玻璃设计',

  /* ===== AIChatPage.vue ===== */
  aiChatPage: `
.chat-page-container {
  background: linear-gradient(135deg, #f0f4ff 0%, #e8f0fe 50%, #fdf8f6 100%);
}
.chat-layout {
  border-radius: 20px;
  box-shadow: 0 25px 60px rgba(15, 23, 42, 0.12), 0 0 0 1px rgba(255, 255, 255, 0.5);
  overflow: hidden;
  backdrop-filter: blur(24px);
  background: rgba(255, 255, 255, 0.65);
  font-family: system-ui, -apple-system, sans-serif;
}
`,

  /* ===== ConversationSidebar.vue ===== */
  conversationSidebar: `
.chat-sidebar {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, rgba(255,255,255,0.96), rgba(248,250,252,0.92));
  border-right: 1px solid rgba(226,232,240,0.88);
  border-radius: 16px 0 0 16px;
  overflow: hidden;
  flex-shrink: 0;
}
.chat-sidebar .section-header {
  padding: 10px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: transparent;
  border-bottom: 1px solid rgba(226,232,240,0.7);
}
.chat-sidebar .section-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}
.chat-sidebar .refresh-btn,
.chat-sidebar .new-chat-btn,
.chat-sidebar .toggle-delete-btn {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  border: none;
  background: rgba(255,255,255,0.9);
  color: #475569;
  cursor: pointer;
  transition: all 0.18s ease;
}
.chat-sidebar .refresh-btn:hover,
.chat-sidebar .new-chat-btn:hover,
.chat-sidebar .toggle-delete-btn:hover {
  background: rgba(59,130,246,0.08);
  color: #2563eb;
  transform: translateY(-1px);
}
.chat-sidebar .chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}
.chat-sidebar .chat-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.18s ease;
  border: 1px solid transparent;
  background: rgba(255,255,255,0.55);
}
.chat-sidebar .chat-item:hover {
  background: rgba(255,255,255,0.85);
  border-color: rgba(59,130,246,0.2);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(15,23,42,0.06);
}
.chat-sidebar .chat-item.active {
  background: linear-gradient(135deg, rgba(59,130,246,0.1), rgba(99,102,241,0.08));
  border-color: rgba(59,130,246,0.28);
  box-shadow: 0 2px 8px rgba(59,130,246,0.1);
}
.chat-sidebar .feature-card {
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(226,232,240,0.88);
  background: linear-gradient(180deg, rgba(255,255,255,0.98), rgba(248,250,252,0.96));
  box-shadow: 0 2px 8px rgba(15,23,42,0.03);
  transition: all 0.18s ease;
}
.chat-sidebar .feature-card:hover {
  border-color: rgba(59,130,246,0.2);
  box-shadow: 0 4px 14px rgba(15,23,42,0.05);
}
.chat-sidebar .feature-card.active {
  border-color: rgba(59,130,246,0.35);
  background: linear-gradient(135deg, rgba(239,246,255,0.98), rgba(248,250,252,0.95));
}
`,

  /* ===== RoleSidebar.vue ===== */
  roleSidebar: `
.role-drawer { --rail: 60px; --panel: 248px; position: relative; width: 100%; height: 100%; pointer-events: none; }
.rail, .panel { pointer-events: auto; }
.role-drawer.expanded .rail { border-color: rgba(37,99,235,.24); box-shadow: 0 16px 32px rgba(15,23,42,.1); }
.rail {
  width: var(--rail); height: 100%;
  display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 12px 8px;
  border: 1px solid rgba(226,232,240,.88); border-radius: 20px;
  background: linear-gradient(180deg, rgba(255,255,255,.96), rgba(248,250,252,.92));
  backdrop-filter: blur(14px); box-shadow: 0 10px 22px rgba(15,23,42,.05);
  transition: border-color .2s ease, box-shadow .2s ease, background .2s ease;
}
.rail.clickable:hover { border-color: rgba(37,99,235,.22); background: linear-gradient(180deg, rgba(255,255,255,.98), rgba(241,245,249,.94)); }
.trigger, .filter-btn {
  display: inline-flex; align-items: center; justify-content: center;
  border: 1px solid rgba(226,232,240,.88); background: rgba(255,255,255,.94);
  color: #475569; cursor: pointer; transition: .18s ease; border-radius: 12px;
}
.trigger { width: 36px; height: 36px; }
.trigger:hover, .filter-btn:hover:not(:disabled) { color: #2563eb; border-color: rgba(37,99,235,.24); transform: translateY(-1px); }
.preview-avatar {
  width: 36px; height: 36px; border-radius: 12px; position: relative; border: none; cursor: pointer;
  box-shadow: 0 6px 12px rgba(15,23,42,.1), inset 0 1px 1px rgba(255,255,255,.24);
  transition: opacity .18s ease, filter .18s ease, box-shadow .18s ease;
}
.panel {
  position: absolute; top: 0; left: 8px;
  width: calc(var(--panel) + var(--rail)); height: 100%;
  padding-left: calc(var(--rail) - 6px);
  opacity: 0; transform: translateX(-8px) scale(.985); pointer-events: none;
  transition: opacity .2s ease, transform .2s ease;
}
.surface {
  height: 100%; display: flex; flex-direction: column; overflow: hidden;
  border: 1px solid rgba(226,232,240,.92); border-radius: 24px;
  background: linear-gradient(180deg, rgba(255,255,255,.98), rgba(248,250,252,.95));
  backdrop-filter: blur(16px); box-shadow: 0 14px 30px rgba(15,23,42,.08);
}
.header {
  display: flex; align-items: center; gap: 8px; padding: 14px 16px;
  border-bottom: 1px solid rgba(226,232,240,.92);
}
.title h3 { margin: 0; font-size: 14px; font-weight: 700; color: #0f172a; }
.title p { margin: 3px 0 0; font-size: 11px; color: #94a3b8; }
.filter-btn { width: 34px; height: 34px; border-radius: 12px; }
.filter-btn.active { color: #2563eb; border-color: rgba(37,99,235,.28); background: rgba(37,99,235,.12); }
.search-wrap { padding: 12px 16px; border-bottom: 1px solid rgba(226,232,240,.92); }
.search-box {
  display: flex; align-items: center; gap: 8px; padding: 9px 10px;
  border: 1px solid rgba(203,213,225,.94); border-radius: 14px;
  background: rgba(248,250,252,.9); overflow: hidden;
}
.search-box:focus-within { border-color: rgba(37,99,235,.28); box-shadow: 0 0 0 4px rgba(37,99,235,.08); background: rgba(255,255,255,.98); }
.list { flex: 1; min-height: 0; overflow-y: auto; padding: 14px 16px 16px; }
.section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-size: 12px; font-weight: 700; color: #475569; }
.grid { display: flex; flex-direction: column; gap: 10px; }
.card {
  display: flex; align-items: center; gap: 10px; padding: 10px 11px;
  border: 1px solid rgba(226,232,240,.94); border-radius: 16px;
  background: linear-gradient(180deg, rgba(255,255,255,.98), rgba(248,250,252,.96));
  box-shadow: 0 6px 14px rgba(15,23,42,.045); cursor: pointer;
  transition: transform .16s ease, border-color .16s ease, box-shadow .16s ease;
}
.card:hover { transform: translateY(-1px); border-color: rgba(37,99,235,.18); box-shadow: 0 10px 18px rgba(15,23,42,.06); }
.card.selected { border-color: rgba(37,99,235,.26); background: linear-gradient(180deg, rgba(239,246,255,.98), rgba(248,250,252,.95)); }
.avatar { width: 38px; height: 38px; flex-shrink: 0; border-radius: 14px; box-shadow: 0 6px 14px rgba(15,23,42,.1), inset 0 1px 1px rgba(255,255,255,.24); }
.avatar-text { font-size: 14px; font-weight: 700; text-shadow: 0 1px 2px rgba(0,0,0,.2); }
.name { font-size: 13px; font-weight: 700; color: #0f172a; }
.badge { min-width: 28px; height: 18px; padding: 0 6px; background: rgba(37,99,235,.1); color: #2563eb; border-radius: 999px; font-size: 10px; font-weight: 700; }
.desc { margin: 4px 0 0; font-size: 11px; line-height: 1.4; color: #475569; opacity: 0.88; }
.favorite-btn { width: 30px; height: 30px; border-radius: 10px; border: none; background: transparent; color: #cbd5e1; cursor: pointer; }
.favorite-btn:hover { background: rgba(245,158,11,.1); color: #d97706; }
.favorite-btn.on { color: #f59e0b; }
.drag-handle { width: 30px; height: 30px; border-radius: 10px; background: rgba(241,245,249,.94); color: #94a3b8; cursor: grab; }
.empty { padding: 24px 16px; border: 1px dashed rgba(203,213,225,.94); border-radius: 16px; background: rgba(248,250,252,.8); text-align: center; font-size: 14px; font-weight: 700; color: #0f172a; }
`,

  /* ===== ChatWorkspaceHeader.vue ===== */
  chatWorkspaceHeader: `
.chat-header {
  padding: 14px 20px;
  border-bottom: 1px solid rgba(226,232,240,0.78);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  min-height: 52px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
}
.role-badge {
  display: flex; align-items: center; gap: 8px; padding: 6px 14px;
  background: linear-gradient(135deg, rgba(255,255,255,0.95), rgba(248,250,252,0.9));
  border: 1px solid rgba(226,232,240,0.78); border-radius: 12px;
  cursor: pointer; transition: all 0.18s ease;
  box-shadow: 0 2px 8px rgba(15,23,42,0.04);
}
.role-badge:hover { border-color: rgba(59,130,246,0.3); box-shadow: 0 4px 14px rgba(15,23,42,0.06); }
.role-name { font-size: 13px; font-weight: 600; color: #0f172a; letter-spacing: 0.2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.model-select {
  height: 34px; padding: 0 32px 0 12px;
  border: 1px solid rgba(203,213,225,0.82); border-radius: 10px;
  background: rgba(255,255,255,0.92); color: #334155; font-size: 13px;
  cursor: pointer; transition: all 0.18s ease;
  appearance: none; background-image: url("data:image/svg+xml,...");
}
.model-select:focus { outline: none; border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59,130,246,0.12); }
.action-button {
  background: rgba(255,255,255,0.86); border: 1px solid rgba(226,232,240,0.82);
  width: 36px; height: 36px; border-radius: 10px; display: flex; align-items: center; justify-content: center;
  cursor: pointer; color: #475569; transition: all 0.18s ease;
}
.action-button:hover { background: rgba(59,130,246,0.07); color: #2563eb; border-color: rgba(59,130,246,0.22); transform: translateY(-1px); }
.action-button.active { background: rgba(59,130,246,0.1); color: #2563eb; border-color: rgba(59,130,246,0.3); }
.message-count { font-size: 12px; color: #94a3b8; white-space: nowrap; font-weight: 500; }
`,

  /* ===== ChatWorkspace.vue ===== */
  chatWorkspace: `
.chat-workspace {
  flex: 1; display: flex; flex-direction: column;
  min-width: 0; background: transparent; overflow: hidden; border-radius: 0 16px 16px 0;
}
.messages-area {
  flex: 1; overflow-y: auto; padding: 20px 24px;
  scroll-behavior: smooth; -webkit-overflow-scrolling: touch;
  background: linear-gradient(180deg, rgba(248,250,252,0.45) 0%, rgba(253,252,245,0.35) 100%);
}
.scroll-to-bottom-btn {
  position: absolute; bottom: 20px; right: 24px;
  width: 40px; height: 40px; border-radius: 50%;
  background: rgba(255,255,255,0.92); border: 1px solid rgba(226,232,240,0.82);
  box-shadow: 0 4px 16px rgba(15,23,42,0.1); cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: #475569; transition: all 0.2s ease;
}
.scroll-to-bottom-btn:hover { background: #ffffff; color: #2563eb; box-shadow: 0 6px 22px rgba(15,23,42,0.15); transform: translateY(-2px); }
`,

  /* ===== ChatComposer.vue ===== */
  chatComposer: `
.composer-wrapper {
  padding: 16px 20px 12px; border-top: 1px solid rgba(226,232,240,0.68);
  background: linear-gradient(180deg, rgba(255,255,255,0.78), rgba(248,250,252,0.62));
  backdrop-filter: blur(16px); flex-shrink: 0;
}
.input-container {
  border: 1px solid rgba(203,213,225,0.82); border-radius: 16px;
  background: rgba(255,255,255,0.92); overflow: hidden;
  transition: all 0.2s ease; box-shadow: 0 2px 8px rgba(15,23,42,0.03);
}
.input-container:focus-within {
  border-color: #93c5fd; box-shadow: 0 0 0 4px rgba(59,130,246,0.08), 0 4px 16px rgba(15,23,42,0.04);
}
.message-input { padding: 12px 16px; font-size: 14px; border: none; background: transparent; resize: none; outline: none; }
.action-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px 10px; border-top: 1px solid rgba(226,232,240,0.5);
}
.send-button {
  padding: 7px 18px; border-radius: 10px; border: none;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: #fff; font-size: 13px; font-weight: 600; cursor: pointer;
  transition: all 0.18s ease; box-shadow: 0 2px 8px rgba(37,99,235,0.25);
}
.send-button:hover { transform: translateY(-1px); box-shadow: 0 4px 14px rgba(37,99,235,0.35); }
.icon-button {
  width: 32px; height: 32px; border-radius: 8px; border: none;
  background: transparent; color: #64748b; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.16s ease;
}
.icon-button:hover { background: rgba(148,163,184,0.1); color: #475569; }
`
}
