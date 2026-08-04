(function () {
  'use strict';

  const state = {
    token: localStorage.getItem('token') || null,
    username: localStorage.getItem('username') || null,
    displayName: localStorage.getItem('displayName') || null,
    avatarUrl: localStorage.getItem('avatarUrl') || null,
    status: localStorage.getItem('status') || null,
    stompClient: null,
    contacts: [],
    myGroups: [],
    newsPosts: [],
    activeChat: null,       // username of open 1-on-1 conversation
    activeGroup: null,      // group object of open group/channel conversation
    groupSubscription: null,
    listTab: 'direct',
    typingTimeout: null,
    typingResetTimeout: null,
    pendingCode: null,      // { content, ext, lang } detected from paste, awaiting send
    mediaRecorder: null,
    recordedChunks: [],
    recordingTimer: null,
    recordingStart: null,
    cropper: null,
    boardPosts: [],
    boardType: 'SCHEDULE',
    boardMode: 'feed',
    replyTarget: null,          // { clientId, sender, snippet } while composing a reply
    recentMessages: {},         // clientId -> { sender, snippet }, for building reply previews
    messageReactions: {},       // clientId -> { emoji: Set(username) }
    pollVotes: {},              // clientId -> { optionIndex: Set(username) }
    linkPreviewCache: {},       // url -> Promise<dto|{available:false}>
    mentionStart: -1,
    selfDestructSeconds: 0,     // 0 = off; otherwise attached as expiresInSeconds to outgoing messages
    call: null,                 // active/ringing WebRTC call session, or null
    e2eKeyPair: null,           // this browser's ECDH CryptoKeyPair, loaded/generated on startup
    e2eSharedKeys: {},          // peer username -> derived AES-GCM CryptoKey (cached per session)
    currentPanel: 'empty',      // 'empty' | 'chat' | 'board' | 'news' — which main panel is visible
    isAdmin: false,             // global site-admin flag, loaded from /api/users/me
    role: 'USER',               // USER | MODERATOR | ADMIN | SUPER_ADMIN, loaded from /api/users/me
    adminUsersPage: 0,          // current page in the admin "Пользователи" table (server-side paged)
    adminUsersSearch: '',       // current search term applied to the admin users list
    adminAuditPage: 0,          // current page in the admin "Журнал" audit-log table (server-side paged)
    boardFeedFilter: 'ALL',     // 'ALL' | 'SCHEDULE' | 'ANNOUNCEMENT' — sub-tab filter for the board feed
    boardTables: [],            // editable tables being built in the board-modal (SCHEDULE only)
    activeGroupRole: 'MEMBER',  // this user's role in the currently-open group
    activePin: null,            // { clientId, sender, snippet } currently pinned in the open chat
    forwardClientId: null,      // clientId of the message about to be forwarded
    unreadCounts: {},           // sidebar key -> count of messages received while that chat wasn't open (this session only)
    lastMessagePreview: {},     // sidebar key -> { text, at, mine } for the sidebar preview line (this session only)
    groupTypers: {},            // username -> displayName, everyone currently typing in the open group chat
    selectMode: false,          // multi-select mode active in the open chat
    selectedClientIds: new Set(),
    mutedChats: JSON.parse(localStorage.getItem('mutedChats') || '[]'),   // array of sidebar keys
    draftTexts: JSON.parse(localStorage.getItem('draftTexts') || '{}'),   // sidebar key -> unsent text
    notificationSound: localStorage.getItem('notificationSound') || 'classic', // 'classic' | 'soft' | 'none'
    blockedUsernames: [],       // usernames this account has blocked, loaded from /api/users/me/blocked
    mySticker: [],
    groupCall: null,            // active group call session: { callId, groupId, video, peers: Map<username, {pc, stream}> }
    groupMemberNames: {},       // username -> displayName for members of the currently-open group (mention rendering only)
    adminChart: null,           // Chart.js instance for the admin analytics trend chart
  };

  function contactKey(username) { return 'u:' + username; }
  function groupChatKey(id) { return 'g:' + id; }
  function activeChatKey() {
    if (state.activeGroup) return groupChatKey(state.activeGroup.id);
    if (state.activeChat) return contactKey(state.activeChat);
    return null;
  }

  function saveDraft() {
    const key = activeChatKey();
    if (!key) return;
    const text = el('message-input').value;
    if (text) state.draftTexts[key] = text;
    else delete state.draftTexts[key];
    localStorage.setItem('draftTexts', JSON.stringify(state.draftTexts));
    if (state.listTab === 'direct') renderContacts();
    else if (state.listTab === 'groups') renderGroups();
  }

  function clearDraft() {
    const key = activeChatKey();
    if (!key) return;
    delete state.draftTexts[key];
    localStorage.setItem('draftTexts', JSON.stringify(state.draftTexts));
    if (state.listTab === 'direct') renderContacts();
    else if (state.listTab === 'groups') renderGroups();
  }

  function loadDraftIntoComposer() {
    const key = activeChatKey();
    el('message-input').value = (key && state.draftTexts[key]) || '';
  }

  function isChatMuted(key) { return state.mutedChats.includes(key); }

  function toggleMuteChat(key) {
    const idx = state.mutedChats.indexOf(key);
    if (idx >= 0) state.mutedChats.splice(idx, 1);
    else state.mutedChats.push(key);
    localStorage.setItem('mutedChats', JSON.stringify(state.mutedChats));
    if (state.listTab === 'direct') renderContacts();
    else if (state.listTab === 'groups') renderGroups();
  }

  function buildSidebarPreview(m) {
    const byType = {
      IMAGE: '📷 Фото',
      FILE: '📎 ' + (m.mediaName || 'Файл'),
      CODE: '💻 ' + (m.mediaName || 'Код'),
      VOICE: '🎤 Голосовое',
      POLL: '📊 ' + (m.pollQuestion || 'Опрос'),
      LOCATION: '📍 Геопозиция',
    };
    const text = byType[m.type] || (m.content || '').trim();
    return text.length > 42 ? text.slice(0, 42) + '…' : text;
  }

  const ICE_SERVERS = [{ urls: 'stun:stun.l.google.com:19302' }];
  const SELF_DESTRUCT_OPTIONS = [0, 5, 10, 30, 60];
  const TASK_STATUSES = ['TODO', 'IN_PROGRESS', 'DONE'];
  const TASK_STATUS_LABEL = { TODO: 'Нужно сделать', IN_PROGRESS: 'В процессе', DONE: 'Готово' };
  const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🔥'];
  const PRESENCE_LABELS = {
    ON_CALL: 'На созвоне', BUSY: 'Занят(а)', LUNCH: 'Обед', DND: 'Не беспокоить', VACATION: 'В отпуске',
  };
  const SLASH_COMMANDS = [
    { cmd: '/poll', template: '/poll Вопрос? | Вариант 1 | Вариант 2', desc: 'Создать опрос' },
    { cmd: '/me', template: '/me делает что-то', desc: 'Сообщение-действие' },
    { cmd: '/shrug', template: '/shrug', desc: 'Добавить ¯\\_(ツ)_/¯' },
  ];
  const CHAT_THEMES = {
    default: { label: 'Prism', mine: 'linear-gradient(135deg, #00b8cc, #7c3aed)', primary: '#00e5ff' },
    sunset: { label: 'Закат', mine: 'linear-gradient(135deg, #ff9966, #ff5e62)', primary: '#ff8a5c' },
    forest: { label: 'Лес', mine: 'linear-gradient(135deg, #2f9e44, #a8e063)', primary: '#6fcf47' },
    grape: { label: 'Виноград', mine: 'linear-gradient(135deg, #7f00ff, #e100ff)', primary: '#b347ff' },
    rose: { label: 'Роза', mine: 'linear-gradient(135deg, #f857a6, #ff5858)', primary: '#ff6b9d' },
    mono: { label: 'Моно', mine: '#33333d', primary: '#c9c9d6' },
  };
  // "Вариант D" — самые свежие эмодзи (Unicode 2022–2024) плюс актуальные тренды 2024–2025,
  // выбрано и утверждено пользователем вместо прежнего набора.
  const STICKER_EMOJIS = [
    '🎀','🧿','🪩','🫨','🫠','🫥','🫡','🩷','🩵','🩶',
    '🫧','🍒','🐐','🦦','🧋','🩹','🪭','🍄','🐦‍🔥','🍋‍🟩',
    '🛜','🐊','🔥','💀','🫶','🤌','🍿','🚩',
  ];

  function uid() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'id-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
  }

  // Extensions treated as source code -> rendered as a highlighted code message instead of a plain file.
  const CODE_EXTENSIONS = {
    java: 'Java', kt: 'Kotlin', js: 'JavaScript', ts: 'TypeScript', py: 'Python',
    c: 'C', cpp: 'C++', cs: 'C#', go: 'Go', rb: 'Ruby', php: 'PHP',
    html: 'HTML', css: 'CSS', json: 'JSON', xml: 'XML', sql: 'SQL', sh: 'Shell', yml: 'YAML', yaml: 'YAML', txt: 'Текст'
  };
  const MAX_INLINE_CODE_CHARS = 3500;

  const EMOJI_CATEGORIES = [
    { icon: '🙂', name: 'Смайлы', emojis: ['😀','😁','😂','🤣','😃','😄','😅','😆','😉','😊','😋','😎','😍','🥰','😘','😗','😙','😚','🙂','🤗','🤩','🤔','🤨','😐','😑','😶','🙄','😏','😣','😥','😮','🤐','😯','😪','😫','🥱','😴','😌','😛','😜','😝','🤤','😒','😓','😔','😕','🙃','🤑','😲','☹️','🙁','😖','😞','😟','😤','😢','😭','😦','😧','😨','😩','🤯','😬','😰','😱','🥵','🥶','😳','🤪','😵','🥴','😡','😠','🤬','😷','🤒','🤕','🤢','🤮','🥳','🥺','🤫','🤭','🧐','🤓'] },
    { icon: '👍', name: 'Жесты', emojis: ['👍','👎','👏','🙌','🙏','💪','✌️','🤞','🤟','🤘','👌','🤙','👈','👉','👆','👇','☝️','✋','🤚','🖐️','🖖','👋','🤝','👊','✊','🤛','🤜','🖕','✍️','💅','🤳'] },
    { icon: '❤️', name: 'Сердца', emojis: ['❤️','🧡','💛','💚','💙','💜','🖤','🤍','🤎','💔','❣️','💕','💞','💓','💗','💖','💘','💝','💟','✨','⭐','🌟','💫','💥'] },
    { icon: '🐾', name: 'Животные', emojis: ['🐶','🐱','🐭','🐹','🐰','🦊','🐻','🐼','🐨','🐯','🦁','🐮','🐷','🐸','🐵','🙈','🙉','🙊','🐔','🐧','🐦','🐤','🦄','🐴','🐝','🐢','🐍','🦋','🐌','🐙','🦀','🐬','🐳'] },
    { icon: '🍕', name: 'Еда', emojis: ['🍏','🍎','🍊','🍋','🍌','🍉','🍇','🍓','🍒','🍑','🥝','🍍','🥑','🍆','🥔','🥕','🌽','🍕','🍔','🍟','🌭','🥪','🌮','🌯','🍿','🧀','🥐','🍞','🥞','🧇','🍳','🍗','🍖','🍤','🍣','🍩','🍪','🎂','🍰','🍫','🍬','🍭','🍺','🍷','☕','🍵','🥤'] },
    { icon: '⚽', name: 'Активности', emojis: ['⚽','🏀','🏈','⚾','🎾','🏐','🏉','🎱','🏓','🏸','🥊','🎯','🎮','🎲','🎉','🎊','🎁','🏆','🥇','🎨','🎸','🎹','🎧','📷','🚗','✈️','🚀','⛵','🚲','🏝️'] },
  ];

  const el = (id) => document.getElementById(id);

  // ---------- Auth screen ----------

  function showTab(tab) {
    document.querySelector('#auth-screen .tabs').classList.remove('hidden');
    document.querySelectorAll('#auth-screen .tab-btn').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
    el('login-form').classList.toggle('hidden', tab !== 'login');
    el('register-form').classList.toggle('hidden', tab !== 'register');
    el('forgot-password-form').classList.add('hidden');
    el('reset-password-form').classList.add('hidden');
  }
  document.querySelectorAll('#auth-screen .tab-btn').forEach(b => b.addEventListener('click', () => showTab(b.dataset.tab)));

  // Свободные от вкладок Вход/Регистрация экраны: запрос ссылки и установка нового пароля.
  function showAuthForm(name) {
    document.querySelector('#auth-screen .tabs').classList.add('hidden');
    ['login-form', 'register-form', 'forgot-password-form', 'reset-password-form'].forEach(id => {
      el(id).classList.toggle('hidden', id !== name);
    });
  }

  el('forgot-password-link').addEventListener('click', () => {
    el('forgot-password-error').textContent = '';
    el('forgot-password-success').textContent = '';
    el('forgot-username').value = el('login-username').value.trim();
    showAuthForm('forgot-password-form');
  });
  el('back-to-login-link').addEventListener('click', () => showTab('login'));
  el('reset-back-to-login-link').addEventListener('click', () => showTab('login'));

  el('forgot-password-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    el('forgot-password-error').textContent = '';
    el('forgot-password-success').textContent = '';
    const usernameOrEmail = el('forgot-username').value.trim();
    try {
      const res = await fetch('/api/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ usernameOrEmail })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Не удалось отправить ссылку');
      el('forgot-password-form').querySelector('button[type="submit"]').disabled = true;
      el('forgot-password-success').textContent = data.message || 'Если такой аккаунт существует, на почту отправлена ссылка';
    } catch (err) {
      el('forgot-password-error').textContent = err.message;
    }
  });

  let resetPasswordToken = null;

  el('reset-password-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    el('reset-password-error').textContent = '';
    const newPassword = el('reset-new-password').value;
    const confirmPassword = el('reset-confirm-password').value;
    if (newPassword !== confirmPassword) {
      el('reset-password-error').textContent = 'Пароли не совпадают';
      return;
    }
    try {
      const res = await fetch('/api/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: resetPasswordToken, newPassword })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Не удалось сохранить пароль');
      history.replaceState(null, '', location.pathname);
      showTab('login');
      el('login-error').textContent = '';
      showToast('Пароль сохранён — теперь можно войти');
    } catch (err) {
      el('reset-password-error').textContent = err.message;
    }
  });

  // Если по ссылке из письма пришли с ?resetToken=..., сразу открываем форму нового пароля.
  (function checkResetTokenInUrl() {
    const params = new URLSearchParams(location.search);
    const token = params.get('resetToken');
    if (token) {
      resetPasswordToken = token;
      showAuthForm('reset-password-form');
    }
  })();

  el('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    el('login-error').textContent = '';
    const username = el('login-username').value.trim();
    const password = el('login-password').value;
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Ошибка входа');
      onAuthSuccess(data);
    } catch (err) {
      el('login-error').textContent = err.message;
    }
  });

  el('register-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    el('register-error').textContent = '';
    const username = el('reg-username').value.trim();
    const displayName = el('reg-displayname').value.trim();
    const email = el('reg-email').value.trim();
    const password = el('reg-password').value;
    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, displayName, email, password })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Ошибка регистрации');
      onAuthSuccess(data);
    } catch (err) {
      el('register-error').textContent = err.message;
    }
  });

  function onAuthSuccess(data) {
    state.token = data.token;
    state.username = data.username;
    state.displayName = data.displayName;
    state.avatarUrl = data.avatarUrl || null;
    state.status = data.status || null;
    localStorage.setItem('token', data.token);
    localStorage.setItem('username', data.username);
    localStorage.setItem('displayName', data.displayName);
    if (state.avatarUrl) localStorage.setItem('avatarUrl', state.avatarUrl); else localStorage.removeItem('avatarUrl');
    if (state.status) localStorage.setItem('status', state.status); else localStorage.removeItem('status');
    startApp();
  }

  el('logout-btn').addEventListener('click', () => {
    if (state.stompClient) state.stompClient.deactivate();
    localStorage.clear();
    location.reload();
  });

  el('back-btn').addEventListener('click', () => {
    el('app-screen').classList.remove('chat-open');
  });

  // ---------- Helpers ----------

  function authHeaders() {
    return { 'Authorization': 'Bearer ' + state.token };
  }

  function initials(name) {
    return (name || '?').trim().charAt(0).toUpperCase();
  }

  const AVATAR_GRADIENTS = [
    'linear-gradient(135deg,#00e5ff,#a855f7)',
    'linear-gradient(135deg,#ff2ee6,#a855f7)',
    'linear-gradient(135deg,#39ff9d,#00b8cc)',
    'linear-gradient(135deg,#00e5ff,#39ff9d)',
    'linear-gradient(135deg,#a855f7,#ff2ee6)',
    'linear-gradient(135deg,#ffb86c,#ff2ee6)',
    'linear-gradient(135deg,#00b8cc,#a855f7)',
  ];

  function avatarStyle(name) {
    const str = name || '?';
    let hash = 0;
    for (let i = 0; i < str.length; i++) hash = str.charCodeAt(i) + ((hash << 5) - hash);
    const idx = Math.abs(hash) % AVATAR_GRADIENTS.length;
    return AVATAR_GRADIENTS[idx];
  }

  /** Applies either an uploaded photo or a generated initials avatar to a DOM element. */
  function setAvatar(elNode, name, avatarUrl) {
    if (avatarUrl) {
      elNode.textContent = '';
      elNode.style.backgroundImage = `url('${avatarUrl}')`;
      elNode.style.backgroundColor = 'transparent';
    } else {
      elNode.style.backgroundImage = 'none';
      elNode.textContent = initials(name);
      elNode.style.background = avatarStyle(name);
    }
  }

  function avatarInlineStyle(avatarUrl, name) {
    return avatarUrl
      ? `background-image:url('${avatarUrl}');background-size:cover;background-position:center;`
      : `background:${avatarStyle(name)}`;
  }

  function formatTime(iso) {
    try {
      const d = new Date(iso);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (e) { return ''; }
  }

  function escapeHtml(str) {
    const d = document.createElement('div');
    d.textContent = str == null ? '' : str;
    return d.innerHTML;
  }

  function openModal(id) { el(id).classList.remove('hidden'); }
  function closeModal(id) { el(id).classList.add('hidden'); }
  function wireOverlayClose(modalId, closeBtnId, onClose) {
    el(closeBtnId).addEventListener('click', () => { closeModal(modalId); if (onClose) onClose(); });
    el(modalId).addEventListener('click', (e) => { if (e.target === el(modalId)) { closeModal(modalId); if (onClose) onClose(); } });
  }

  // ---------- App screen ----------

  async function startApp() {
    el('auth-screen').classList.add('hidden');
    el('app-screen').classList.remove('hidden');
    el('me-name').textContent = state.displayName;
    setAvatar(el('me-avatar'), state.displayName, state.avatarUrl);

    await loadMe();
    await loadContacts();
    await loadBlockedUsers();
    await loadMyStickers();
    initE2E();
    connectWebSocket();
    initEmojiPicker();
    initStickerPicker();
    initChatThemes();
    initProfileModal();
    initListTabs();
    initGroupModal();
    initGroupInfo();
    initVoiceRecorder();
    initCodePasteDetection();
    initAvatarCropper();
    initBoard();
    initNews();
    initNotifications();
    initCalls();
    initChatExtras();
    initSelectMode();
    initChatSearch();
    initContactProfile();
    initForward();
    initAdminPanel();
    initReportModal();
    initEmailReminder();
    if (state.role !== 'USER') refreshAdminReportsBadge();
  }

  /** Loads the current user's own profile, mainly to know whether they're the site admin. */
  async function loadMe() {
    try {
      const res = await fetch('/api/users/me', { headers: authHeaders() });
      if (res.status === 401) return logoutForced();
      const me = await res.json();
      state.isAdmin = !!me.isAdmin;
      state.role = me.role || 'USER';
      el('admin-panel-btn').classList.toggle('hidden', state.role === 'USER');
      el('board-add-btn').classList.toggle('hidden', !state.isAdmin);
      checkEmailReminder(me.email);
    } catch (e) { /* ignore — UI just stays in the non-admin state */ }
  }

  // ---------- Напоминание привязать email (для аккаунтов, заведённых до этой фичи) ----------

  const EMAIL_REMINDER_SNOOZE_KEY = 'emailReminderSnoozedUntil';
  const EMAIL_REMINDER_SNOOZE_DAYS = 7;

  function checkEmailReminder(email) {
    const banner = el('email-reminder-banner');
    if (email) { banner.classList.add('hidden'); return; }
    const snoozedUntil = parseInt(localStorage.getItem(EMAIL_REMINDER_SNOOZE_KEY) || '0', 10);
    if (Date.now() < snoozedUntil) { banner.classList.add('hidden'); return; }
    banner.classList.remove('hidden');
  }

  function snoozeEmailReminder() {
    const until = Date.now() + EMAIL_REMINDER_SNOOZE_DAYS * 24 * 60 * 60 * 1000;
    localStorage.setItem(EMAIL_REMINDER_SNOOZE_KEY, String(until));
    el('email-reminder-banner').classList.add('hidden');
    el('email-reminder-form').classList.add('hidden');
  }

  function initEmailReminder() {
    el('email-reminder-attach-btn').addEventListener('click', () => {
      el('email-reminder-form').classList.toggle('hidden');
      el('email-reminder-input').focus();
    });
    el('email-reminder-dismiss-btn').addEventListener('click', snoozeEmailReminder);
    el('email-reminder-later-btn').addEventListener('click', snoozeEmailReminder);
    el('email-reminder-save-btn').addEventListener('click', async () => {
      el('email-reminder-error').textContent = '';
      const email = el('email-reminder-input').value.trim();
      if (!email) { el('email-reminder-error').textContent = 'Введите email'; return; }
      try {
        const res = await fetch('/api/users/me', {
          method: 'PUT',
          headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
          body: JSON.stringify({ email }),
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Не удалось сохранить email');
        el('email-reminder-banner').classList.add('hidden');
        showToast('Email привязан — теперь можно восстановить пароль');
      } catch (err) {
        el('email-reminder-error').textContent = err.message;
      }
    });
  }

  async function loadContacts() {
    const res = await fetch('/api/users/contacts', { headers: authHeaders() });
    if (res.status === 401) return logoutForced();
    state.contacts = await res.json();
    renderContacts();
  }

  function renderContacts() {
    const container = el('contacts');
    const emptyBanner = el('contacts-empty');
    container.innerHTML = '';

    if (state.contacts.length === 0) {
      emptyBanner.classList.remove('hidden');
      emptyBanner.textContent = 'Пока нет других пользователей — попросите кого-нибудь зарегистрироваться';
      return;
    }

    const term = (el('contact-search').value || '').trim().toLowerCase();
    const filtered = term
      ? state.contacts.filter(c => c.displayName.toLowerCase().includes(term) || c.username.toLowerCase().includes(term))
      : state.contacts;

    if (filtered.length === 0) {
      emptyBanner.classList.remove('hidden');
      emptyBanner.textContent = 'Ничего не найдено';
      return;
    }
    emptyBanner.classList.add('hidden');

    filtered.forEach(c => {
      const div = document.createElement('div');
      div.className = 'contact' + (state.activeChat === c.username ? ' active' : '');
      // data-атрибут для локаторов в автотестах — намеренно хранит displayName, а не
      // username, чтобы логин не светился в DOM даже как техническая метка.
      div.dataset.displayName = c.displayName;
      const statusLine = c.online ? 'в сети' : 'не в сети';
      const bio = c.status ? ' · ' + escapeHtml(c.status) : '';
      const preview = state.lastMessagePreview[contactKey(c.username)];
      const unread = state.unreadCounts[contactKey(c.username)] || 0;
      const draft = state.draftTexts[contactKey(c.username)];
      const muted = state.mutedChats.includes(contactKey(c.username));
      const secondLine = draft
        ? `<span class="draft-tag">Черновик:</span> ${escapeHtml(draft)}`
        : preview
          ? (preview.mine ? 'Вы: ' : '') + escapeHtml(preview.text)
          : statusLine + bio;
      const timeBadge = preview ? `<span class="contact-time">${formatTime(preview.at)}</span>` : '';
      const muteIcon = muted ? '<i class="mute-icon" title="Уведомления выключены">🔕</i>' : '';
      div.innerHTML = `
        <div class="avatar" style="${avatarInlineStyle(c.avatarUrl, c.displayName)}">${c.avatarUrl ? '' : initials(c.displayName)}</div>
        <div class="contact-info">
          <div class="contact-name-row">
            <div class="contact-name">${escapeHtml(c.displayName)}${muteIcon}</div>
            ${timeBadge}
          </div>
          <div class="contact-status">${secondLine}</div>
        </div>
        ${unread > 0 && !muted ? `<div class="contact-badge">${unread > 99 ? '99+' : unread}</div>` : `<div class="dot ${c.online ? 'online' : ''}"></div>`}
      `;
      div.addEventListener('click', () => openChat(c.username));
      container.appendChild(div);
    });
  }

  el('contact-search').addEventListener('input', () => {
    if (state.listTab === 'direct') renderContacts();
    else if (state.listTab === 'groups') renderGroups();
  });

  function showMainPanel(panel) {
    state.currentPanel = panel;
    el('chat-empty').classList.toggle('hidden', panel !== 'empty');
    el('chat-active').classList.toggle('hidden', panel !== 'chat');
    el('board-view').classList.toggle('hidden', panel !== 'board');
    el('news-view').classList.toggle('hidden', panel !== 'news');
    if (panel === 'board') setIconBadge('board-badge', false);
    if (panel === 'news') setIconBadge('news-badge', false);
  }

  async function openChat(username) {
    leaveGroupSubscription();
    state.activeGroup = null;
    state.activeChat = username;
    state.unreadCounts[contactKey(username)] = 0;
    renderContacts();
    showMainPanel('chat');
    el('typing-indicator').classList.add('hidden');
    el('app-screen').classList.add('chat-open');
    closeModal('emoji-modal');
    closeChatSearch();
    if (state.selectMode) exitSelectMode();
    state.groupTypers = {};

    const contact = state.contacts.find(c => c.username === username);
    const displayName = contact ? contact.displayName : username;
    el('chat-with-name').textContent = displayName;
    const onlineText = contact && contact.online ? 'в сети' : 'не в сети';
    const presenceLabel = contact && contact.presenceStatus && PRESENCE_LABELS[contact.presenceStatus];
    let statusText = contact && contact.status ? onlineText + ' · ' + contact.status : onlineText;
    if (presenceLabel) statusText = presenceLabel + ' · ' + statusText;
    el('chat-with-status').textContent = statusText;
    setAvatar(el('chat-with-avatar'), displayName, contact ? contact.avatarUrl : null);
    el('call-audio-btn').classList.remove('hidden');
    el('call-video-btn').classList.remove('hidden');
    el('call-group-video-btn').classList.add('hidden');
    el('group-info-btn').classList.add('hidden');
    state.activePin = null;
    renderPinnedBanner();

    // Messages aren't stored anywhere, so there's no history to load — the pane
    // starts empty and fills in with whatever arrives live from here on.
    el('messages').innerHTML = '';
    state.recentMessages = {};
    state.messageReactions = {};
    state.pollVotes = {};
    state.groupMemberNames = {};
    clearReplyTarget();
    applyChatTheme();
    loadDraftIntoComposer();
    // Refresh contacts in the background so a peer's E2E public key (published after we last
    // loaded the list) is picked up before the next message is sent.
    loadContacts();
  }

  // ---------- Voice message waveform player ----------

  function buildVoiceBarsHtml(n) {
    let html = '';
    for (let i = 0; i < n; i++) {
      const h = 5 + Math.round(Math.abs(Math.sin(i * 1.7)) * 15);
      html += `<span class="voice-bar" style="height:${h}px"></span>`;
    }
    return html;
  }

  function formatDuration(sec) {
    sec = Math.max(0, Math.round(sec || 0));
    return Math.floor(sec / 60) + ':' + String(sec % 60).padStart(2, '0');
  }

  function wireVoicePlayer(container, src) {
    if (!container) return;
    const btn = container.querySelector('.voice-play-btn');
    const iconPlay = container.querySelector('.icon-play');
    const iconPause = container.querySelector('.icon-pause');
    const fg = container.querySelector('.voice-wave-fg');
    const timeEl = container.querySelector('.voice-time');
    const audio = new Audio(src);
    let playing = false;

    audio.addEventListener('loadedmetadata', () => {
      if (isFinite(audio.duration)) timeEl.textContent = formatDuration(audio.duration);
    });
    audio.addEventListener('timeupdate', () => {
      const pct = audio.duration ? (audio.currentTime / audio.duration) * 100 : 0;
      fg.style.clipPath = `inset(0 ${100 - pct}% 0 0)`;
      timeEl.textContent = formatDuration((audio.duration || 0) - audio.currentTime);
    });
    audio.addEventListener('ended', () => {
      playing = false;
      iconPlay.classList.remove('hidden'); iconPause.classList.add('hidden');
      fg.style.clipPath = 'inset(0 100% 0 0)';
      if (isFinite(audio.duration)) timeEl.textContent = formatDuration(audio.duration);
    });
    btn.addEventListener('click', () => {
      // Pause every other voice message before playing this one.
      document.querySelectorAll('audio').forEach(a => { if (a !== audio) a.pause(); });
      if (playing) {
        audio.pause();
        playing = false;
        iconPlay.classList.remove('hidden'); iconPause.classList.add('hidden');
      } else {
        audio.play().catch(() => {});
        playing = true;
        iconPlay.classList.add('hidden'); iconPause.classList.remove('hidden');
      }
    });
  }

  function buildSnippet(m) {
    const byType = {
      IMAGE: '📷 Фото',
      FILE: '📎 ' + (m.mediaName || 'Файл'),
      CODE: '💻 ' + (m.mediaName || 'Код'),
      VOICE: '🎤 Голосовое сообщение',
      POLL: '📊 ' + (m.pollQuestion || 'Опрос'),
      LOCATION: '📍 Геопозиция',
    };
    if (byType[m.type]) return byType[m.type];
    const text = (m.content || '').trim();
    return text.length > 80 ? text.slice(0, 80) + '…' : text;
  }

  let contactsRefreshInFlight = false;

  // Mentions are stored/matched by username (a stable id), but usernames are login
  // credentials and must never be shown on screen — only the display name is rendered.
  // If the local contacts cache doesn't have this person yet (e.g. they just registered),
  // this must NOT fall back to the raw username — that would leak a login credential onto
  // screen. Instead show a neutral placeholder and opportunistically refresh the contacts
  // cache once, so the next render (new message, reopening the chat) resolves it properly.
  function displayNameForUsername(username) {
    if (username === state.username) return state.displayName || 'Вы';
    const contact = (state.contacts || []).find(c => c.username === username);
    if (contact) return contact.displayName;
    if (state.groupMemberNames && state.groupMemberNames[username]) return state.groupMemberNames[username];
    if (!contactsRefreshInFlight) {
      contactsRefreshInFlight = true;
      loadContacts().finally(() => { contactsRefreshInFlight = false; });
    }
    return 'пользователь';
  }

  function linkifyMentions(text) {
    const escaped = escapeHtml(text);
    return escaped.replace(/(^|[^\w@])@([A-Za-z0-9_]{2,32})/g, (match, pre, name) =>
      `${pre}<span class="mention">@${escapeHtml(displayNameForUsername(name))}</span>`);
  }

  function findMsgWrap(clientId) {
    return Array.from(el('messages').querySelectorAll('.msg-wrap')).find(w => w.dataset.clientId === clientId) || null;
  }

  function renderReactionsFor(clientId) {
    const wrap = findMsgWrap(clientId);
    if (!wrap) return;
    const container = wrap.querySelector('.msg-reactions');
    container.innerHTML = '';
    const data = state.messageReactions[clientId];
    if (!data) return;
    Object.keys(data).forEach(emoji => {
      const users = data[emoji];
      if (!users || users.size === 0) return;
      const pill = document.createElement('button');
      pill.type = 'button';
      pill.className = 'reaction-pill' + (users.has(state.username) ? ' mine-reaction' : '');
      pill.innerHTML = `<span>${emoji}</span><span>${users.size}</span>`;
      pill.addEventListener('click', () => toggleReaction(clientId, emoji));
      container.appendChild(pill);
    });
  }

  function toggleReaction(clientId, emoji) {
    if (!state.stompClient || !state.stompClient.connected) return;
    if (!state.activeChat && !state.activeGroup) return;
    const data = state.messageReactions[clientId] || (state.messageReactions[clientId] = {});
    const users = data[emoji] || (data[emoji] = new Set());
    const action = users.has(state.username) ? 'remove' : 'add';

    const payload = { targetClientId: clientId, emoji, action };
    if (state.activeGroup) payload.groupId = state.activeGroup.id;
    else payload.recipientUsername = state.activeChat;

    // Wait for the server echo (sent back to the sender too) to actually mutate state,
    // so there's a single source of truth and no risk of double-toggling.
    state.stompClient.publish({ destination: '/app/chat.react', body: JSON.stringify(payload) });
  }

  function handleReactionEvent(payload) {
    if (payload.groupId) {
      if (!(state.activeGroup && String(state.activeGroup.id) === String(payload.groupId))) return;
    } else {
      const other = payload.senderUsername === state.username ? payload.recipientUsername : payload.senderUsername;
      if (state.activeGroup || other !== state.activeChat) return;
    }
    const data = state.messageReactions[payload.targetClientId] || (state.messageReactions[payload.targetClientId] = {});
    const users = data[payload.emoji] || (data[payload.emoji] = new Set());
    if (payload.action === 'remove') users.delete(payload.senderUsername);
    else users.add(payload.senderUsername);
    renderReactionsFor(payload.targetClientId);
  }

  function handlePinEvent(payload) {
    if (payload.groupId) {
      if (!(state.activeGroup && String(state.activeGroup.id) === String(payload.groupId))) return;
    } else {
      const other = payload.senderUsername === state.username ? payload.recipientUsername : payload.senderUsername;
      if (state.activeGroup || other !== state.activeChat) return;
    }
    if (payload.action === 'unpin') {
      state.activePin = null;
    } else {
      state.activePin = { clientId: payload.targetClientId, sender: payload.senderName, snippet: payload.snippet };
    }
    renderPinnedBanner();
  }

  function renderPinnedBanner() {
    const banner = el('pinned-banner');
    if (!state.activePin) { banner.classList.add('hidden'); return; }
    el('pinned-banner-sender').textContent = state.activePin.sender + ':';
    el('pinned-banner-snippet').textContent = state.activePin.snippet;
    banner.classList.remove('hidden');
  }

  function sendPin(targetClientId, senderName, snippet) {
    const base = state.activeGroup ? { groupId: state.activeGroup.id } : { recipientUsername: state.activeChat };
    state.stompClient.publish({
      destination: '/app/chat.pin',
      body: JSON.stringify(Object.assign({}, base, { targetClientId, senderName, snippet, action: 'pin' }))
    });
  }

  function sendUnpin() {
    const base = state.activeGroup ? { groupId: state.activeGroup.id } : { recipientUsername: state.activeChat };
    state.stompClient.publish({
      destination: '/app/chat.pin',
      body: JSON.stringify(Object.assign({}, base, { action: 'unpin' }))
    });
  }

  function closeReactionPicker() {
    const existing = document.querySelector('.reaction-picker');
    if (existing) existing.remove();
  }

  function openReactionPicker(wrap, clientId) {
    closeReactionPicker();
    const picker = document.createElement('div');
    picker.className = 'reaction-picker';
    QUICK_REACTIONS.forEach(emoji => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.textContent = emoji;
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleReaction(clientId, emoji);
        closeReactionPicker();
      });
      picker.appendChild(btn);
    });
    wrap.appendChild(picker);
    setTimeout(() => document.addEventListener('click', closeReactionPicker, { once: true }), 0);
  }

  // ---------- Message "..." menu (reactions + reply/forward/copy/edit/delete) ----------

  function closeMessageMenu() {
    const existing = document.querySelector('.msg-menu');
    if (existing) existing.remove();
  }

  function openMessageMenu(wrap, clientId) {
    closeMessageMenu();
    closeReactionPicker();
    const mine = wrap.classList.contains('mine-wrap');
    const info = state.recentMessages[clientId];
    const raw = info && info.raw;
    const canDelete = mine && raw && !raw.action;
    const canEdit = mine && raw && !raw.action && ['TEXT', 'IMAGE', 'FILE'].includes(raw.type || 'TEXT');

    const menu = document.createElement('div');
    menu.className = 'msg-menu';

    const reactRow = document.createElement('div');
    reactRow.className = 'msg-menu-reactions';
    QUICK_REACTIONS.forEach(emoji => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.textContent = emoji;
      btn.addEventListener('click', (e) => { e.stopPropagation(); toggleReaction(clientId, emoji); closeMessageMenu(); });
      reactRow.appendChild(btn);
    });
    menu.appendChild(reactRow);

    function addItem(label, iconSvg, onClick, danger) {
      const item = document.createElement('button');
      item.type = 'button';
      item.className = 'msg-menu-item' + (danger ? ' danger' : '');
      item.innerHTML = `${iconSvg}<span>${label}</span>`;
      item.addEventListener('click', (e) => { e.stopPropagation(); onClick(); closeMessageMenu(); });
      menu.appendChild(item);
    }

    addItem('Ответить', '<svg viewBox="0 0 24 24" fill="none"><path d="M9 10 4 15l5 5M4 15h10a6 6 0 0 0 6-6V7" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>',
      () => setReplyTarget(clientId));
    addItem('Переслать', '<svg viewBox="0 0 24 24" fill="none"><path d="M14 10 20 15l-6 5M20 15H8a5 5 0 0 1-5-5V7" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>',
      () => openForwardModal(clientId));
    if (raw && (raw.type === 'TEXT' || !raw.type) && raw.content) {
      addItem('Копировать', '<svg viewBox="0 0 24 24" fill="none"><rect x="8" y="8" width="12" height="12" rx="2" stroke="currentColor" stroke-width="1.6"/><path d="M4 16V5a1 1 0 0 1 1-1h11" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>',
        () => copyMessageText(raw.content));
    }
    addItem('Выбрать', '<svg viewBox="0 0 24 24" fill="none"><path d="M20 6 9 17l-5-5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
      () => enterSelectMode(clientId));
    if (canEdit) {
      addItem('Изменить', '<svg viewBox="0 0 24 24" fill="none"><path d="M4 8.5A1.5 1.5 0 0 1 5.5 7M17 4l3 3-11 11-4 1 1-4Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>',
        () => startEditMessage(wrap, clientId));
    }
    if (canDelete) {
      addItem('Удалить', '<svg viewBox="0 0 24 24" fill="none"><path d="M5 7h14M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2m-8 0 1 12a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1l1-12" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
        () => sendDelete(clientId), true);
    }

    wrap.appendChild(menu);
    setTimeout(() => document.addEventListener('click', closeMessageMenu, { once: true }), 0);
  }

  function copyMessageText(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).catch(() => {});
    }
  }

  function startEditMessage(wrap, clientId) {
    const bubble = wrap.querySelector('.msg');
    const textDiv = bubble && bubble.querySelector(':scope > div:not(.meta):not(.msg-reply-quote)');
    const info = state.recentMessages[clientId];
    const raw = info && info.raw;
    if (!bubble || !textDiv || !raw) return;

    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'msg-edit-input';
    input.value = raw.content || '';
    textDiv.replaceWith(input);
    input.focus();
    input.setSelectionRange(input.value.length, input.value.length);

    function restore(text) {
      const div = document.createElement('div');
      div.innerHTML = linkifyMentions(text);
      input.replaceWith(div);
    }

    function commit() {
      const val = input.value.trim();
      input.removeEventListener('blur', commit);
      if (val && val !== raw.content) sendEdit(clientId, val);
      else restore(raw.content || '');
    }
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); commit(); }
      if (e.key === 'Escape') { input.removeEventListener('blur', commit); restore(raw.content || ''); }
    });
    input.addEventListener('blur', commit);
  }

  function sendEdit(clientId, newContent) {
    if (!state.stompClient || !state.stompClient.connected) return;
    const base = state.activeGroup ? { groupId: state.activeGroup.id } : { recipientUsername: state.activeChat };
    state.stompClient.publish({
      destination: '/app/chat.edit',
      body: JSON.stringify(Object.assign({}, base, { targetClientId: clientId, newContent, action: 'edit' }))
    });
  }

  function sendDelete(clientId) {
    if (!state.stompClient || !state.stompClient.connected) return;
    const base = state.activeGroup ? { groupId: state.activeGroup.id } : { recipientUsername: state.activeChat };
    state.stompClient.publish({
      destination: '/app/chat.edit',
      body: JSON.stringify(Object.assign({}, base, { targetClientId: clientId, action: 'delete' }))
    });
  }

  function handleEditEvent(payload) {
    if (payload.groupId) {
      if (!(state.activeGroup && String(state.activeGroup.id) === String(payload.groupId))) return;
    } else {
      const other = payload.senderUsername === state.username ? payload.recipientUsername : payload.senderUsername;
      if (state.activeGroup || other !== state.activeChat) return;
    }
    const wrap = findMsgWrap(payload.targetClientId);
    const info = state.recentMessages[payload.targetClientId];
    if (payload.action === 'delete') {
      if (wrap) wrap.remove();
      delete state.recentMessages[payload.targetClientId];
      delete state.messageReactions[payload.targetClientId];
      return;
    }
    if (info && info.raw) {
      info.raw.content = payload.newContent;
      info.raw.edited = true;
      info.snippet = buildSnippet(info.raw);
      if (wrap) {
        const bubble = wrap.querySelector('.msg');
        const textDiv = bubble && bubble.querySelector(':scope > div:not(.meta):not(.msg-reply-quote)');
        if (textDiv) textDiv.innerHTML = linkifyMentions(payload.newContent);
        const meta = bubble && bubble.querySelector('.meta');
        if (meta && !meta.querySelector('.edited-tag')) {
          meta.insertAdjacentHTML('afterbegin', '<span class="edited-tag">изменено · </span>');
        }
      }
    }
  }

  // ---------- Множественный выбор сообщений ----------

  function enterSelectMode(initialClientId) {
    state.selectMode = true;
    state.selectedClientIds = new Set(initialClientId ? [initialClientId] : []);
    el('messages').classList.add('select-mode');
    document.querySelectorAll('.msg-select-btn').forEach(b => b.classList.remove('hidden'));
    renderSelectToolbar();
    if (initialClientId) {
      const wrap = findMsgWrap(initialClientId);
      if (wrap) wrap.classList.add('msg-selected');
    }
  }

  function exitSelectMode() {
    state.selectMode = false;
    state.selectedClientIds = new Set();
    el('messages').classList.remove('select-mode');
    document.querySelectorAll('.msg-select-btn').forEach(b => b.classList.add('hidden'));
    document.querySelectorAll('.msg-wrap.msg-selected').forEach(w => w.classList.remove('msg-selected'));
    const toolbar = el('select-toolbar');
    if (toolbar) toolbar.classList.add('hidden');
  }

  function toggleMessageSelected(clientId) {
    if (!state.selectedClientIds) state.selectedClientIds = new Set();
    const wrap = findMsgWrap(clientId);
    if (state.selectedClientIds.has(clientId)) {
      state.selectedClientIds.delete(clientId);
      if (wrap) wrap.classList.remove('msg-selected');
    } else {
      state.selectedClientIds.add(clientId);
      if (wrap) wrap.classList.add('msg-selected');
    }
    renderSelectToolbar();
  }

  function renderSelectToolbar() {
    let toolbar = el('select-toolbar');
    toolbar.classList.remove('hidden');
    const count = state.selectedClientIds ? state.selectedClientIds.size : 0;
    el('select-toolbar-count').textContent = count + ' выбрано';
  }

  function initSelectMode() {
    const toolbar = document.createElement('div');
    toolbar.id = 'select-toolbar';
    toolbar.className = 'select-toolbar hidden';
    toolbar.innerHTML = `
      <button type="button" id="select-toolbar-cancel" class="voice-btn" aria-label="Отменить">✕</button>
      <span id="select-toolbar-count">0 выбрано</span>
      <button type="button" id="select-toolbar-forward" class="msg-action-btn" title="Переслать выбранные" aria-label="Переслать выбранные">
        <svg viewBox="0 0 24 24" fill="none"><path d="M14 10 20 15l-6 5M20 15H8a5 5 0 0 1-5-5V7" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </button>
      <button type="button" id="select-toolbar-delete" class="msg-action-btn danger" title="Удалить выбранные" aria-label="Удалить выбранные">
        <svg viewBox="0 0 24 24" fill="none"><path d="M5 7h14M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2m-8 0 1 12a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1l1-12" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </button>
    `;
    el('chat-active').appendChild(toolbar);

    el('messages').addEventListener('click', (e) => {
      const selectBtn = e.target.closest('.msg-select-btn');
      if (selectBtn && state.selectMode) {
        e.stopPropagation();
        const wrap = selectBtn.closest('.msg-wrap');
        if (wrap && wrap.dataset.clientId) toggleMessageSelected(wrap.dataset.clientId);
      }
    });

    el('select-toolbar-cancel').addEventListener('click', exitSelectMode);
    el('select-toolbar-forward').addEventListener('click', () => {
      const ids = Array.from(state.selectedClientIds || []);
      if (!ids.length) return;
      openForwardModal(ids); // openForwardModal accepts a single id or an array
      exitSelectMode();
    });
    el('select-toolbar-delete').addEventListener('click', () => {
      const ids = Array.from(state.selectedClientIds || []);
      ids.forEach(id => {
        const info = state.recentMessages[id];
        // Only the sender can broadcast a real delete; for others' messages this just hides
        // it locally, same spirit as "delete for me" since nothing is persisted anyway.
        if (info && info.raw && info.raw.senderUsername === state.username) {
          sendDelete(id);
        } else {
          const wrap = findMsgWrap(id);
          if (wrap) wrap.remove();
        }
      });
      exitSelectMode();
    });
  }

  // ---------- Polls ----------

  function pluralVotes(n) {
    const mod10 = n % 10, mod100 = n % 100;
    if (mod10 === 1 && mod100 !== 11) return 'голос';
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return 'голоса';
    return 'голосов';
  }

  function renderPollMarkup(m) {
    const options = m.pollOptions || [];
    let optsHtml = '';
    options.forEach((opt, idx) => {
      optsHtml += `<button type="button" class="poll-option" data-option-index="${idx}">
        <span class="poll-option-fill"></span>
        <span class="poll-option-label">${escapeHtml(opt)}</span>
        <span class="poll-option-count">0</span>
      </button>`;
    });
    return `
      <div class="poll-question">📊 ${escapeHtml(m.pollQuestion || '')}</div>
      <div class="msg-poll-options">${optsHtml}</div>
      <div class="poll-footer">0 голосов</div>
    `;
  }

  function renderPollFor(clientId) {
    const wrap = findMsgWrap(clientId);
    if (!wrap) return;
    const votes = state.pollVotes[clientId] || {};
    const total = Object.keys(votes).reduce((sum, k) => sum + (votes[k] ? votes[k].size : 0), 0);
    wrap.querySelectorAll('.poll-option').forEach(btn => {
      const idx = btn.dataset.optionIndex;
      const users = votes[idx];
      const count = users ? users.size : 0;
      const pct = total > 0 ? Math.round((count / total) * 100) : 0;
      btn.querySelector('.poll-option-fill').style.width = pct + '%';
      btn.querySelector('.poll-option-count').textContent = String(count);
      btn.classList.toggle('mine-vote', !!(users && users.has(state.username)));
    });
    const footer = wrap.querySelector('.poll-footer');
    if (footer) footer.textContent = total + ' ' + pluralVotes(total);
  }

  function voteOption(clientId, optionIndex) {
    if (!state.stompClient || !state.stompClient.connected) return;
    if (!state.activeChat && !state.activeGroup) return;
    const votes = state.pollVotes[clientId] || (state.pollVotes[clientId] = {});
    const key = String(optionIndex);
    const alreadyVotedHere = votes[key] && votes[key].has(state.username);

    const base = state.activeGroup ? { groupId: state.activeGroup.id } : { recipientUsername: state.activeChat };
    const publish = (idx, action) => state.stompClient.publish({
      destination: '/app/chat.vote',
      body: JSON.stringify(Object.assign({ targetClientId: clientId, optionIndex: idx, action }, base))
    });

    if (alreadyVotedHere) {
      publish(optionIndex, 'remove');
      return;
    }
    // Single-choice poll: clear any existing vote by this user on another option first.
    Object.keys(votes).forEach(k => {
      if (k !== key && votes[k] && votes[k].has(state.username)) publish(parseInt(k, 10), 'remove');
    });
    publish(optionIndex, 'add');
  }

  function handleVoteEvent(payload) {
    if (payload.groupId) {
      if (!(state.activeGroup && String(state.activeGroup.id) === String(payload.groupId))) return;
    } else {
      const other = payload.senderUsername === state.username ? payload.recipientUsername : payload.senderUsername;
      if (state.activeGroup || other !== state.activeChat) return;
    }
    const votes = state.pollVotes[payload.targetClientId] || (state.pollVotes[payload.targetClientId] = {});
    const key = String(payload.optionIndex);
    const users = votes[key] || (votes[key] = new Set());
    if (payload.action === 'remove') users.delete(payload.senderUsername);
    else users.add(payload.senderUsername);
    renderPollFor(payload.targetClientId);
  }

  // ---------- Link previews ----------

  function extractFirstUrl(text) {
    const m = (text || '').match(/https?:\/\/[^\s]+/i);
    return m ? m[0].replace(/[)\]}.,!?]+$/, '') : null;
  }

  function getLinkPreview(url) {
    if (!state.linkPreviewCache[url]) {
      state.linkPreviewCache[url] = fetch('/api/link-preview?url=' + encodeURIComponent(url), { headers: authHeaders() })
        .then(r => r.json())
        .catch(() => ({ available: false }));
    }
    return state.linkPreviewCache[url];
  }

  async function attachLinkPreview(wrap, url) {
    const data = await getLinkPreview(url);
    if (!data || data.available === false || !wrap.isConnected) return;
    let host = '';
    try { host = new URL(url).hostname; } catch (e) { /* noop */ }

    const card = document.createElement('a');
    card.className = 'link-preview-card';
    card.href = url;
    card.target = '_blank';
    card.rel = 'noopener noreferrer';

    if (data.image) {
      const img = document.createElement('img');
      img.className = 'link-preview-img';
      img.src = data.image;
      img.alt = '';
      card.appendChild(img);
    }
    const body = document.createElement('div');
    body.className = 'link-preview-body';
    body.innerHTML = `
      <div class="link-preview-title">${escapeHtml(data.title || host)}</div>
      ${data.description ? `<div class="link-preview-desc">${escapeHtml(data.description)}</div>` : ''}
      <div class="link-preview-host">${escapeHtml(data.siteName || host)}</div>
    `;
    card.appendChild(body);

    const msgDiv = wrap.querySelector('.msg');
    if (msgDiv) msgDiv.appendChild(card);
  }

  // ---------- Slash commands ----------

  function handleSlashCommand(raw) {
    if (!raw.startsWith('/')) return false;

    if (raw === '/shrug' || raw.startsWith('/shrug ')) {
      const rest = raw.slice(6).trim();
      sendChat({ content: (rest ? rest + ' ' : '') + '¯\\_(ツ)_/¯', type: 'TEXT' });
      return true;
    }

    if (raw.startsWith('/me ')) {
      const text = raw.slice(4).trim();
      if (!text) return false;
      sendChat({ content: text, type: 'TEXT', action: true });
      return true;
    }

    if (raw.startsWith('/poll ')) {
      const parts = raw.slice(6).split('|').map(s => s.trim()).filter(Boolean);
      if (parts.length < 3) {
        showToast('Формат: /poll Вопрос? | Вариант 1 | Вариант 2');
        return true;
      }
      const [question, ...options] = parts;
      sendChat({ type: 'POLL', pollQuestion: question, pollOptions: options.slice(0, 8) });
      return true;
    }

    return false;
  }

  function closeMentionSuggest() {
    const box = el('mention-suggest');
    box.classList.add('hidden');
    box.innerHTML = '';
    state.mentionStart = -1;
  }

  function closeSlashSuggest() {
    const box = el('slash-suggest');
    box.classList.add('hidden');
    box.innerHTML = '';
  }

  function updateMentionSuggest() {
    const input = el('message-input');
    const value = input.value;
    const caret = input.selectionStart;
    const upToCaret = value.slice(0, caret);
    const match = upToCaret.match(/(^|\s)@([A-Za-z0-9_]*)$/);
    if (!match || (!state.activeChat && !state.activeGroup)) { closeMentionSuggest(); return; }
    const query = match[2].toLowerCase();
    state.mentionStart = caret - query.length - 1;
    // In a group, suggest every member (not just this user's own direct contacts) so
    // @-mentioning someone you haven't messaged 1:1 still works.
    const pool = (state.contacts || []).slice();
    if (state.activeGroup && state.groupMemberNames) {
      Object.keys(state.groupMemberNames).forEach(username => {
        if (username === state.username) return;
        if (!pool.some(c => c.username === username)) {
          pool.push({ username, displayName: state.groupMemberNames[username] });
        }
      });
    }
    const candidates = pool
      .filter(c => c.username.toLowerCase().includes(query) || c.displayName.toLowerCase().includes(query))
      .slice(0, 6);
    const box = el('mention-suggest');
    if (candidates.length === 0) { closeMentionSuggest(); return; }
    box.innerHTML = '';
    candidates.forEach(c => {
      const item = document.createElement('div');
      item.className = 'mention-suggest-item';
      item.innerHTML = `<span class="avatar" style="${avatarInlineStyle(c.avatarUrl, c.displayName)}">${c.avatarUrl ? '' : initials(c.displayName)}</span><span>${escapeHtml(c.displayName)}</span>`;
      item.addEventListener('mousedown', (e) => {
        e.preventDefault();
        const before = value.slice(0, state.mentionStart);
        const after = value.slice(caret);
        const insertion = '@' + c.username + ' ';
        input.value = before + insertion + after;
        const newCaret = (before + insertion).length;
        input.setSelectionRange(newCaret, newCaret);
        closeMentionSuggest();
        input.focus();
      });
      box.appendChild(item);
    });
    box.classList.remove('hidden');
  }

  function updateSlashSuggest() {
    const input = el('message-input');
    const box = el('slash-suggest');
    const value = input.value;
    if (!(state.activeChat || state.activeGroup)) { closeSlashSuggest(); return; }
    const matches = SLASH_COMMANDS.filter(c => c.cmd.startsWith(value.toLowerCase()));
    if (matches.length === 0) { closeSlashSuggest(); return; }
    box.innerHTML = '';
    matches.forEach(c => {
      const item = document.createElement('div');
      item.className = 'mention-suggest-item';
      item.innerHTML = `<strong>${escapeHtml(c.cmd)}</strong>&nbsp;<span style="color:var(--text-faint)">${escapeHtml(c.desc)}</span>`;
      item.addEventListener('mousedown', (e) => {
        e.preventDefault();
        input.value = c.template;
        closeSlashSuggest();
        input.focus();
        input.setSelectionRange(input.value.length, input.value.length);
      });
      box.appendChild(item);
    });
    box.classList.remove('hidden');
  }

  function updateComposerSuggestions() {
    const value = el('message-input').value;
    if (/^\/[a-zA-Zа-яА-Я]*$/.test(value)) {
      closeMentionSuggest();
      updateSlashSuggest();
    } else {
      closeSlashSuggest();
      updateMentionSuggest();
    }
  }

  function setReplyTarget(clientId) {
    const info = state.recentMessages[clientId];
    if (!info) return;
    state.replyTarget = Object.assign({ clientId }, info);
    el('reply-preview-sender').textContent = info.sender;
    el('reply-preview-snippet').textContent = info.snippet;
    el('reply-preview').classList.remove('hidden');
    el('message-input').focus();
  }

  function clearReplyTarget() {
    state.replyTarget = null;
    el('reply-preview').classList.add('hidden');
  }

  /** True if the message text is nothing but a handful of emoji (no words) — those render bigger,
   *  same convention as WhatsApp/Telegram, instead of the normal message text size. */
  function isEmojiOnlyContent(text) {
    if (!text) return false;
    const stripped = text.trim().replace(/\s+/g, '');
    if (!stripped || !/^[\p{Extended_Pictographic}‍️]+$/u.test(stripped)) return false;
    const graphemes = window.Intl && Intl.Segmenter
      ? [...new Intl.Segmenter('en', { granularity: 'grapheme' }).segment(stripped)]
      : [...stripped];
    return graphemes.length > 0 && graphemes.length <= 6;
  }

  // Consecutive messages from the same sender within this window are visually grouped:
  // no repeated avatar/name, tighter spacing, softened "joining" corner on the bubble.
  const MESSAGE_GROUP_WINDOW_MS = 5 * 60 * 1000;

  function renderMessage(m, showSender) {
    const box = el('messages');
    const mine = m.senderUsername === state.username;

    const prevWraps = box.querySelectorAll('.msg-wrap');
    const prevWrap = prevWraps.length ? prevWraps[prevWraps.length - 1] : null;
    const prevAtMs = prevWrap ? Number(prevWrap.dataset.createdAtMs || 0) : 0;
    const thisAtMs = m.createdAt ? new Date(m.createdAt).getTime() : Date.now();
    const grouped = !!prevWrap
      && prevWrap.dataset.senderUsername === m.senderUsername
      && (thisAtMs - prevAtMs) >= 0
      && (thisAtMs - prevAtMs) < MESSAGE_GROUP_WINDOW_MS;

    if (showSender && !mine && !grouped) {
      const label = document.createElement('div');
      label.className = 'msg-sender-label';
      label.innerHTML = `<span class="avatar avatar-xs msg-sender-avatar" style="${avatarInlineStyle(m.senderAvatarUrl, m.senderDisplayName || m.senderUsername)}">${m.senderAvatarUrl ? '' : initials(m.senderDisplayName || m.senderUsername)}</span><span>${escapeHtml(m.senderDisplayName || m.senderUsername)}</span>`;
      box.appendChild(label);
    }

    const wrap = document.createElement('div');
    wrap.className = 'msg-wrap ' + (mine ? 'mine-wrap' : 'theirs-wrap') + (grouped ? ' grouped' : '');
    if (m.clientId) wrap.dataset.clientId = m.clientId;
    wrap.dataset.senderName = m.senderDisplayName || m.senderUsername || '';
    wrap.dataset.senderUsername = m.senderUsername || '';
    wrap.dataset.createdAtMs = String(thisAtMs);

    const div = document.createElement('div');
    div.className = 'msg ' + (mine ? 'mine' : 'theirs');

    let inner = '';
    if (m.replyToSnippet) {
      inner += `<div class="msg-reply-quote">
        <div class="msg-reply-quote-sender">${escapeHtml(m.replyToSenderName || '')}</div>
        <span class="msg-reply-quote-snippet">${escapeHtml(m.replyToSnippet)}</span>
      </div>`;
    }

    if (m.type === 'IMAGE') {
      inner += `<img src="${m.mediaUrl}" alt="${escapeHtml(m.mediaName)}">`;
      if (m.content) inner += `<div>${linkifyMentions(m.content)}</div>`;
    } else if (m.type === 'VOICE') {
      const bars = buildVoiceBarsHtml(24);
      inner += `<div class="voice-player">
        <button type="button" class="voice-play-btn" aria-label="Воспроизвести">
          <svg class="icon-play" viewBox="0 0 24 24" fill="none"><path d="M8 5v14l11-7Z" fill="currentColor"/></svg>
          <svg class="icon-pause hidden" viewBox="0 0 24 24" fill="none"><rect x="6" y="5" width="4" height="14" fill="currentColor"/><rect x="14" y="5" width="4" height="14" fill="currentColor"/></svg>
        </button>
        <div class="voice-wave"><div class="voice-wave-bg">${bars}</div><div class="voice-wave-fg" style="clip-path: inset(0 100% 0 0)">${bars}</div></div>
        <span class="voice-time">0:00</span>
      </div>`;
    } else if (m.type === 'LOCATION') {
      const mapsUrl = 'https://www.openstreetmap.org/?mlat=' + m.lat + '&mlon=' + m.lng + '#map=15/' + m.lat + '/' + m.lng;
      inner += `<a class="location-card" href="${mapsUrl}" target="_blank" rel="noopener">
        <div class="location-card-map"><i class="location-pin">📍</i></div>
        <div class="location-card-label">Геопозиция<span>Открыть на карте</span></div>
      </a>`;
    } else if (m.type === 'CODE') {
      div.className += ' code-msg';
      const ext = (m.mediaName || '').includes('.') ? m.mediaName.split('.').pop().toLowerCase() : '';
      const lang = CODE_EXTENSIONS[ext] || 'Код';
      const lines = (m.content || '').split('\n').length;
      inner += `<div class="code-file-header"><span>📄 ${escapeHtml(m.mediaName || 'snippet.txt')}</span><span class="code-file-meta">${lang} · ${lines} стр.</span></div>`;
      inner += `<code class="code-block">${highlightCode(m.content || '')}</code>`;
      if (m.mediaUrl) inner += `<a class="code-download" href="${m.mediaUrl}" target="_blank" download>⬇ скачать файл</a>`;
    } else if (m.type === 'FILE') {
      inner += `<a class="file-link" href="${m.mediaUrl}" target="_blank" download>📎 ${escapeHtml(m.mediaName)}</a>`;
      if (m.content) inner += `<div>${linkifyMentions(m.content)}</div>`;
    } else if (m.type === 'POLL') {
      div.className += ' poll-msg';
      inner += renderPollMarkup(m);
    } else if (m.type === 'STICKER') {
      div.className += ' sticker-msg';
      inner += `<div>${escapeHtml(m.content)}</div>`;
    } else if (m.action) {
      div.className += ' action-msg';
      inner += `<div class="action-text">${escapeHtml(m.senderDisplayName || m.senderUsername)} ${linkifyMentions(m.content)}</div>`;
    } else {
      if (isEmojiOnlyContent(m.content)) div.className += ' emoji-only';
      inner += `<div>${linkifyMentions(m.content)}</div>`;
    }
    const selfDestructTag = m.expiresInSeconds ? ` · <span class="self-destruct-tag">🔥${m.expiresInSeconds}с</span>` : '';
    const e2eTag = m.encrypted && !m.decryptFailed ? ` <span class="e2e-tag" title="Сквозное шифрование">🔒</span>` : '';
    inner += `<div class="meta">${formatTime(m.createdAt)}${selfDestructTag}${e2eTag}</div>`;
    div.innerHTML = inner;

    if (m.type === 'POLL') {
      div.querySelectorAll('.poll-option').forEach(btn => {
        btn.addEventListener('click', () => voteOption(m.clientId, parseInt(btn.dataset.optionIndex, 10)));
      });
    }
    if (m.type === 'VOICE' && m.mediaUrl) {
      wireVoicePlayer(div.querySelector('.voice-player'), m.mediaUrl);
    }

    const actions = document.createElement('div');
    actions.className = 'msg-actions';
    actions.innerHTML = `
      <button type="button" class="msg-action-btn msg-select-btn hidden" title="Выбрать" aria-label="Выбрать">
        <span class="msg-select-check"></span>
      </button>
      <button type="button" class="msg-action-btn msg-pin-btn" title="Закрепить" aria-label="Закрепить">
        <svg viewBox="0 0 24 24" fill="none"><path d="M12 2v6l3 3v2H9v-2l3-3V2Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M12 13v9" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
      </button>
      <button type="button" class="msg-action-btn msg-menu-btn" title="Ещё" aria-label="Ещё">
        <svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="5" r="1.8" fill="currentColor"/><circle cx="12" cy="12" r="1.8" fill="currentColor"/><circle cx="12" cy="19" r="1.8" fill="currentColor"/></svg>
      </button>
    `;

    const reactions = document.createElement('div');
    reactions.className = 'msg-reactions';

    wrap.appendChild(div);
    wrap.appendChild(actions);
    wrap.appendChild(reactions);
    box.appendChild(wrap);

    if (m.clientId) {
      state.recentMessages[m.clientId] = { sender: m.senderDisplayName || m.senderUsername, snippet: buildSnippet(m), raw: m };
      renderReactionsFor(m.clientId);
      if (m.type === 'POLL') renderPollFor(m.clientId);
    }

    if ((m.type === 'TEXT' || m.type === 'FILE' || m.type === 'IMAGE') && !m.action && m.content) {
      const url = extractFirstUrl(m.content);
      if (url) attachLinkPreview(wrap, url);
    }

    if (m.expiresInSeconds) {
      const cid = m.clientId;
      setTimeout(() => {
        wrap.remove();
        if (cid) {
          delete state.recentMessages[cid];
          delete state.messageReactions[cid];
          delete state.pollVotes[cid];
        }
      }, m.expiresInSeconds * 1000);
    }
  }

  // ---------- Code syntax highlighting (lightweight, Java-aware) ----------

  const CODE_KEYWORDS = new Set([
    'public','private','protected','static','final','void','class','interface','extends','implements',
    'new','return','if','else','for','while','do','switch','case','break','continue','try','catch','finally',
    'throw','throws','import','package','this','super','null','true','false','enum','abstract','synchronized',
    'const','let','var','function','def','from','as','elif','print','int','long','double','float','boolean',
    'char','byte','short','String'
  ]);

  function highlightCode(rawCode) {
    const escaped = escapeHtml(rawCode);
    const tokenRegex = /(\/\/.*$)|("(?:[^"\\]|\\.)*")|('(?:[^'\\]|\\.)*')|(#.*$)|(\b\d+(?:\.\d+)?\b)|(\b[A-Za-z_][A-Za-z0-9_]*\b)/gm;
    return escaped.replace(tokenRegex, (match, comment, dqStr, sqStr, hashComment, num, word) => {
      if (comment || hashComment) return `<span class="tok-cmt">${match}</span>`;
      if (dqStr || sqStr) return `<span class="tok-str">${match}</span>`;
      if (num) return `<span class="tok-num">${match}</span>`;
      if (word) {
        if (CODE_KEYWORDS.has(word)) return `<span class="tok-kw">${match}</span>`;
        if (/^[A-Z]/.test(word)) return `<span class="tok-type">${match}</span>`;
      }
      return match;
    });
  }

  function detectCodeLanguage(text) {
    if (/public\s+(static\s+)?(class|void)|System\.out\.println/.test(text)) return { ext: 'java', lang: 'Java' };
    if (/^\s*(def |import \w+$|print\()/m.test(text) && !/[{};]/.test(text)) return { ext: 'py', lang: 'Python' };
    if (/function\s*\(|=>|const\s|let\s|console\.log/.test(text)) return { ext: 'js', lang: 'JavaScript' };
    if (/<\/?[a-z][\s\S]*>/i.test(text)) return { ext: 'html', lang: 'HTML' };
    if (/SELECT\s|INSERT\s+INTO|CREATE\s+TABLE/i.test(text)) return { ext: 'sql', lang: 'SQL' };
    return { ext: 'txt', lang: 'Текст' };
  }

  async function appendIncoming(m) {
    if (m.kind === 'reaction') { handleReactionEvent(m); return; }
    if (m.kind === 'vote') { handleVoteEvent(m); return; }
    if (m.kind === 'call') { handleCallSignal(m); return; }
    if (m.kind === 'pin') { handlePinEvent(m); return; }
    if (m.kind === 'edit') { handleEditEvent(m); return; }
    if (m.kind === 'typing') { handleGroupTypingEvent(m); return; }

    // E2E: content/iv still hold ciphertext at this point — decrypt in place before anything
    // below reads m.content (rendering, notifications, snippets for replies). ECDH is
    // symmetric, so this also transparently decrypts our own echoed-back messages.
    if (m.encrypted && !m.groupId) {
      const peer = m.senderUsername === state.username ? m.recipientUsername : m.senderUsername;
      const plaintext = await decryptFromPeer(peer, m.content, m.iv);
      m.content = plaintext !== null ? plaintext : '🔒 Не удалось расшифровать сообщение';
      m.decryptFailed = plaintext === null;
    }

    // Nothing is persisted server-side, so a message only ever appears in the DOM if the
    // relevant conversation is open in the UI at the moment it arrives. A browser notification
    // is the only way the user finds out about anything else (or about a message that arrived
    // while the tab was in the background).
    if (m.groupId) {
      const isOpen = state.activeGroup && String(state.activeGroup.id) === String(m.groupId);
      const gKey = groupChatKey(m.groupId);
      const mine = m.senderUsername === state.username;
      if (!m.action) {
        state.lastMessagePreview[gKey] = { text: buildSidebarPreview(m), at: m.createdAt, mine, senderName: mine ? null : (m.senderDisplayName || m.senderUsername) };
        if (!isOpen && !mine) state.unreadCounts[gKey] = (state.unreadCounts[gKey] || 0) + 1;
        if (state.listTab === 'groups') renderGroups();
      }
      if (isOpen) {
        renderMessage(m, true);
        const box = el('messages');
        box.scrollTop = box.scrollHeight;
      }
      if (!isOpen || document.hidden) {
        const group = state.myGroups.find(g => String(g.id) === String(m.groupId));
        notifyIncoming(m, group ? group.name : null, gKey);
      }
      return;
    }
    const other = m.senderUsername === state.username ? m.recipientUsername : m.senderUsername;
    const isOpen = !state.activeGroup && other === state.activeChat;
    const cKey = contactKey(other);
    const mine = m.senderUsername === state.username;
    if (!m.action) {
      state.lastMessagePreview[cKey] = { text: buildSidebarPreview(m), at: m.createdAt, mine };
      if (!isOpen && !mine) state.unreadCounts[cKey] = (state.unreadCounts[cKey] || 0) + 1;
      if (state.listTab === 'direct') renderContacts();
    }
    if (isOpen) {
      renderMessage(m, false);
      const box = el('messages');
      box.scrollTop = box.scrollHeight;
    }
    if (!isOpen || document.hidden) {
      notifyIncoming(m, null, cKey);
    }
  }

  // ---------- Браузерные уведомления ----------

  function initNotifications() {
    if (!('Notification' in window)) return;
    if (Notification.permission === 'default') {
      Notification.requestPermission().catch(() => {});
    }
  }

  function notifyIncoming(m, contextLabel, chatKey) {
    if (!m.senderUsername || m.senderUsername === state.username) return; // our own echoed message
    const muted = chatKey && isChatMuted(chatKey);
    if (!muted) playNotificationSound();
    if (!('Notification' in window) || Notification.permission !== 'granted' || muted) return;
    const senderName = m.senderDisplayName || m.senderUsername;
    const title = contextLabel ? senderName + ' · ' + contextLabel : senderName;
    const body = m.action ? senderName + ' ' + buildSnippet(m) : (buildSnippet(m) || 'Новое сообщение');
    try {
      const n = new Notification(title, {
        body,
        icon: 'icon-192.png',
        tag: m.groupId ? 'group-' + m.groupId : 'dm-' + m.senderUsername,
      });
      n.onclick = () => { window.focus(); n.close(); };
    } catch (e) {
      // Some browsers throw if called outside a user-gesture context in edge cases — safe to ignore.
    }
  }

  /** Lightweight non-blocking toast, used instead of alert() so errors never freeze the UI thread. */
  function showToast(text) {
    let host = document.getElementById('toast-host');
    if (!host) {
      host = document.createElement('div');
      host.id = 'toast-host';
      document.body.appendChild(host);
    }
    const el = document.createElement('div');
    el.className = 'toast-msg';
    el.textContent = text;
    host.appendChild(el);
    setTimeout(() => el.classList.add('show'), 10);
    const duration = Math.min(7000, Math.max(3500, text.length * 60));
    setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 300); }, duration);
  }

  /**
   * DOM-based replacement for window.confirm() — used everywhere a destructive action
   * needs a yes/no confirmation, so nothing depends on the browser's native blocking dialog.
   * Returns a Promise<boolean> resolving true if the user confirmed.
   */
  function showConfirm(message) {
    return new Promise((resolve) => {
      const modal = el('confirm-modal');
      el('confirm-message').textContent = message;
      modal.classList.remove('hidden');

      const okBtn = el('confirm-ok-btn');
      const cancelBtn = el('confirm-cancel-btn');

      function cleanup(result) {
        modal.classList.add('hidden');
        okBtn.removeEventListener('click', onOk);
        cancelBtn.removeEventListener('click', onCancel);
        modal.removeEventListener('click', onBackdrop);
        resolve(result);
      }
      function onOk() { cleanup(true); }
      function onCancel() { cleanup(false); }
      function onBackdrop(e) { if (e.target === modal) cleanup(false); }

      okBtn.addEventListener('click', onOk);
      cancelBtn.addEventListener('click', onCancel);
      modal.addEventListener('click', onBackdrop);
    });
  }

  /**
   * DOM-based replacement for window.prompt() — used for short free-text input tied to a
   * confirmation (e.g. an optional ban reason). Returns a Promise<string|null>: null if
   * cancelled, otherwise the trimmed input value (may be an empty string if left blank).
   */
  function showPrompt(message, placeholder) {
    return new Promise((resolve) => {
      const modal = el('prompt-modal');
      const input = el('prompt-input');
      el('prompt-message').textContent = message;
      input.value = '';
      input.placeholder = placeholder || '';
      modal.classList.remove('hidden');
      setTimeout(() => input.focus(), 0);

      const okBtn = el('prompt-ok-btn');
      const cancelBtn = el('prompt-cancel-btn');

      function cleanup(result) {
        modal.classList.add('hidden');
        okBtn.removeEventListener('click', onOk);
        cancelBtn.removeEventListener('click', onCancel);
        modal.removeEventListener('click', onBackdrop);
        input.removeEventListener('keydown', onKeydown);
        resolve(result);
      }
      function onOk() { cleanup(input.value.trim()); }
      function onCancel() { cleanup(null); }
      function onBackdrop(e) { if (e.target === modal) cleanup(null); }
      function onKeydown(e) { if (e.key === 'Enter') onOk(); else if (e.key === 'Escape') onCancel(); }

      okBtn.addEventListener('click', onOk);
      cancelBtn.addEventListener('click', onCancel);
      modal.addEventListener('click', onBackdrop);
      input.addEventListener('keydown', onKeydown);
    });
  }

  /** Plays a short in-app chime via WebAudio (no external sound files needed). */
  function playNotificationSound() {
    if (state.notificationSound === 'none') return;
    try {
      const Ctx = window.AudioContext || window.webkitAudioContext;
      if (!Ctx) return;
      const ctx = new Ctx();
      const notes = state.notificationSound === 'soft' ? [660, 880] : [880, 1320];
      let t = ctx.currentTime;
      notes.forEach((freq, i) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = state.notificationSound === 'soft' ? 'sine' : 'triangle';
        osc.frequency.value = freq;
        gain.gain.setValueAtTime(0, t);
        gain.gain.linearRampToValueAtTime(0.12, t + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.001, t + 0.16);
        osc.connect(gain).connect(ctx.destination);
        osc.start(t);
        osc.stop(t + 0.18);
        t += 0.1;
      });
      setTimeout(() => ctx.close().catch(() => {}), 500);
    } catch (e) { /* ignore */ }
  }

  function registerServiceWorker() {
    if (!('serviceWorker' in navigator)) return;
    // If an older service worker (from before it stopped caching app.js/style.css) is still
    // controlling this tab, the very first load after it updates can still be served by the
    // outgoing worker. Reload once automatically when control actually switches over, so a
    // returning user never has to figure out a manual hard-refresh to pick up a fix.
    let reloadedForNewWorker = false;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (reloadedForNewWorker) return;
      reloadedForNewWorker = true;
      window.location.reload();
    });
    navigator.serviceWorker.register('/sw.js').catch(() => {});
  }

  // ---------- End-to-end шифрование (ECDH P-256 + AES-GCM) ----------
  //
  // Each browser generates its own ECDH key pair on first use and stores the private key in
  // IndexedDB (never leaves the device). The public key is published through the server so
  // contacts can fetch it, but the server only ever relays it — the shared AES key is derived
  // independently by both ends and the server never sees plaintext or the derived key. Only
  // direct (1:1) text/action messages are encrypted; groups and non-text message types
  // (polls, files, stickers, code) are left as plain relayed content, same as before.

  function bytesToBase64(bytes) {
    let binary = '';
    const chunk = 0x8000;
    for (let i = 0; i < bytes.length; i += chunk) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
    }
    return btoa(binary);
  }

  function base64ToBytes(b64) {
    const binary = atob(b64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
  }

  function e2eSupported() {
    return !!(window.crypto && window.crypto.subtle && window.indexedDB);
  }

  function openE2eDb() {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open('messenger-e2e', 1);
      req.onupgradeneeded = () => { req.result.createObjectStore('keys'); };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  function idbGet(db, store, key) {
    return new Promise((resolve, reject) => {
      const req = db.transaction(store, 'readonly').objectStore(store).get(key);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  function idbPut(db, store, key, value) {
    return new Promise((resolve, reject) => {
      const tx = db.transaction(store, 'readwrite');
      tx.objectStore(store).put(value, key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  async function initE2E() {
    if (!e2eSupported()) return;
    try {
      const db = await openE2eDb();
      let record = await idbGet(db, 'keys', 'ecdh');
      if (!record || !record.privateKey) {
        const pair = await crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveKey']);
        const rawPub = await crypto.subtle.exportKey('raw', pair.publicKey);
        record = { privateKey: pair.privateKey, publicKeyB64: bytesToBase64(new Uint8Array(rawPub)) };
        await idbPut(db, 'keys', 'ecdh', record);
      }
      state.e2eKeyPair = record;
      // Idempotent: keeps the server copy in sync even if it was wiped or this is a new device.
      fetch('/api/users/me/public-key', {
        method: 'PUT',
        headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
        body: JSON.stringify({ publicKey: record.publicKeyB64 }),
      }).catch(() => {});
    } catch (e) {
      state.e2eKeyPair = null; // falls back to plaintext everywhere below
    }
  }

  async function deriveSharedKeyFor(peerUsername) {
    if (!state.e2eKeyPair) return null;
    if (state.e2eSharedKeys[peerUsername]) return state.e2eSharedKeys[peerUsername];
    const contact = state.contacts.find(c => c.username === peerUsername);
    if (!contact || !contact.publicKey) return null;
    try {
      const peerKey = await crypto.subtle.importKey(
        'raw', base64ToBytes(contact.publicKey), { name: 'ECDH', namedCurve: 'P-256' }, false, []
      );
      const sharedKey = await crypto.subtle.deriveKey(
        { name: 'ECDH', public: peerKey }, state.e2eKeyPair.privateKey,
        { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']
      );
      state.e2eSharedKeys[peerUsername] = sharedKey;
      return sharedKey;
    } catch (e) {
      return null;
    }
  }

  /** Returns { content, iv } (both base64) on success, or null if E2E isn't available for this peer. */
  async function encryptForPeer(peerUsername, plaintext) {
    const key = await deriveSharedKeyFor(peerUsername);
    if (!key) return null;
    const iv = crypto.getRandomValues(new Uint8Array(12));
    try {
      const ctBuf = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(plaintext));
      return { content: bytesToBase64(new Uint8Array(ctBuf)), iv: bytesToBase64(iv) };
    } catch (e) {
      return null;
    }
  }

  /** Returns the decrypted string, or null if decryption isn't possible/fails (shown as a lock-with-warning). */
  async function decryptFromPeer(peerUsername, contentB64, ivB64) {
    const key = await deriveSharedKeyFor(peerUsername);
    if (!key) return null;
    try {
      const ptBuf = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: base64ToBytes(ivB64) }, key, base64ToBytes(contentB64)
      );
      return new TextDecoder().decode(ptBuf);
    } catch (e) {
      return null;
    }
  }

  // ---------- WebSocket / STOMP ----------

  function connectWebSocket() {
    state.stompClient = new StompJs.Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: { Authorization: 'Bearer ' + state.token },
      reconnectDelay: 3000,
      // data-ws-connected на body — маячок для автотестов: реальный статус STOMP-соединения,
      // чтобы тест мог дождаться его перед отправкой сообщения, а не гадать с фиксированной
      // паузой. На обычных пользователей никак не влияет.
      onConnect: () => {
        document.body.dataset.wsConnected = 'true';
        state.stompClient.subscribe('/user/queue/messages', (frame) => {
          appendIncoming(JSON.parse(frame.body));
        });
        state.stompClient.subscribe('/user/queue/typing', (frame) => {
          const payload = JSON.parse(frame.body);
          if (payload.senderUsername === state.activeChat) {
            showTyping(payload.typing);
          }
        });
        state.stompClient.subscribe('/topic/presence', (frame) => {
          const payload = JSON.parse(frame.body);
          const c = state.contacts.find(x => x.username === payload.username);
          if (c) {
            c.online = payload.online;
            renderContacts();
            if (state.activeChat === payload.username) {
              const onlineText = payload.online ? 'в сети' : 'не в сети';
              el('chat-with-status').textContent = c.status ? onlineText + ' · ' + c.status : onlineText;
            }
          }
        });
        state.stompClient.subscribe('/topic/board', (frame) => {
          const payload = JSON.parse(frame.body);
          const boardOpen = state.currentPanel === 'board';
          if (boardOpen) {
            loadBoard();
          } else if (payload.authorUsername !== state.username) {
            setIconBadge('board-badge', true);
          }
        });
        state.stompClient.subscribe('/topic/news', (frame) => {
          const payload = JSON.parse(frame.body);
          const newsOpen = state.currentPanel === 'news';
          if (newsOpen) {
            loadNews();
          } else if (payload.authorUsername !== state.username) {
            setIconBadge('news-badge', true);
          }
        });
        state.stompClient.subscribe('/user/queue/admin', (frame) => {
          const payload = JSON.parse(frame.body);
          if (payload.action === 'FORCE_LOGOUT') {
            showToast('Администратор завершил вашу сессию.');
            logoutForced();
          }
        });
        if (state.activeGroup) subscribeToGroup(state.activeGroup.id);
      },
      onDisconnect: () => { document.body.dataset.wsConnected = 'false'; },
      onWebSocketClose: () => { document.body.dataset.wsConnected = 'false'; },
    });
    state.stompClient.activate();
  }

  function setIconBadge(elementId, show) {
    el(elementId).classList.toggle('hidden', !show);
  }

  function subscribeToGroup(groupId) {
    if (!state.stompClient || !state.stompClient.connected) return;
    state.groupSubscription = state.stompClient.subscribe('/topic/group.' + groupId, (frame) => {
      appendIncoming(JSON.parse(frame.body));
    });
  }

  function leaveGroupSubscription() {
    if (state.groupSubscription) {
      try { state.groupSubscription.unsubscribe(); } catch (e) {}
      state.groupSubscription = null;
    }
  }

  function showTyping(isTyping, label) {
    const indicator = el('typing-indicator');
    indicator.classList.toggle('hidden', !isTyping);
    const textEl = el('typing-indicator-text');
    if (textEl) textEl.textContent = label || 'печатает';
    clearTimeout(state.typingResetTimeout);
    if (isTyping) {
      state.typingResetTimeout = setTimeout(() => indicator.classList.add('hidden'), 4000);
    }
  }

  // groupTypers: username -> displayName of everyone currently typing in the open group chat.
  function handleGroupTypingEvent(payload) {
    if (payload.senderUsername === state.username) return;
    if (!(state.activeGroup && String(state.activeGroup.id) === String(payload.groupId))) return;
    if (!state.groupTypers) state.groupTypers = {};
    if (payload.typing) {
      state.groupTypers[payload.senderUsername] = payload.senderDisplayName || payload.senderUsername;
    } else {
      delete state.groupTypers[payload.senderUsername];
    }
    const names = Object.values(state.groupTypers);
    if (names.length === 0) { showTyping(false); return; }
    const label = names.length === 1 ? names[0] + ' печатает' : names.slice(0, 2).join(', ') + ' печатают';
    showTyping(true, label);
  }

  // ---------- Sending messages ----------

  el('message-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const input = el('message-input');

    if (state.pendingCode) {
      const pc = state.pendingCode;
      sendChat({ content: pc.content, type: 'CODE', mediaName: 'snippet.' + pc.ext });
      clearPendingCode();
      return;
    }

    const content = input.value.trim();
    if (!content || (!state.activeChat && !state.activeGroup)) return;

    if (!state.stompClient || !state.stompClient.connected) {
      showToast('Нет соединения с сервером — сообщение сохранено как черновик, оно отправится, когда связь восстановится.');
      saveDraft();
      return;
    }

    if (handleSlashCommand(content)) {
      input.value = '';
      clearDraft();
      closeSlashSuggest();
      closeMentionSuggest();
      if (state.activeChat || state.activeGroup) sendTyping(false);
      return;
    }

    sendChat({ content, type: 'TEXT' });
    input.value = '';
    clearDraft();
    closeSlashSuggest();
    closeMentionSuggest();
    if (state.activeChat || state.activeGroup) sendTyping(false);
  });

  el('message-input').addEventListener('input', () => {
    updateComposerSuggestions();
    saveDraft();
    if (!state.activeChat && !state.activeGroup) return;
    sendTyping(true);
    clearTimeout(state.typingTimeout);
    state.typingTimeout = setTimeout(() => sendTyping(false), 2000);
  });

  function sendTyping(isTyping) {
    if (!state.stompClient || !state.stompClient.connected) return;
    if (!state.activeChat && !state.activeGroup) return;
    const base = state.activeGroup ? { groupId: state.activeGroup.id } : { recipientUsername: state.activeChat };
    state.stompClient.publish({
      destination: '/app/chat.typing',
      body: JSON.stringify(Object.assign({}, base, { typing: isTyping }))
    });
  }

  /** payload needs: content, type, and optionally mediaUrl/mediaName. Recipient/group is filled in automatically. */
  async function sendChat(payload) {
    if (!state.stompClient || !state.stompClient.connected) return;
    if (state.activeGroup) {
      payload.groupId = state.activeGroup.id;
    } else if (state.activeChat) {
      payload.recipientUsername = state.activeChat;
    } else {
      return;
    }
    payload.clientId = uid();
    if (state.replyTarget) {
      payload.replyToClientId = state.replyTarget.clientId;
      payload.replyToSenderName = state.replyTarget.sender;
      payload.replyToSnippet = state.replyTarget.snippet;
      clearReplyTarget();
    }
    if (state.selfDestructSeconds) {
      payload.expiresInSeconds = state.selfDestructSeconds;
    }

    // End-to-end encrypt plain text (and /me action) messages in direct chats, if we've been
    // able to derive a shared key with this peer. Everything else (polls, files, stickers,
    // code, groups) is relayed as-is, same as before. ECDH is symmetric, so when the server
    // echoes this same message back to us as the sender, we can decrypt it the same way the
    // recipient does — no separate local-echo path needed.
    if (!payload.groupId && payload.type === 'TEXT' && payload.content) {
      const enc = await encryptForPeer(payload.recipientUsername, payload.content);
      if (enc) {
        payload.content = enc.content;
        payload.iv = enc.iv;
        payload.encrypted = true;
      }
    }

    state.stompClient.publish({
      destination: '/app/chat.send',
      body: JSON.stringify(payload)
    });
  }

  function readAsText(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = reject;
      reader.readAsText(file);
    });
  }

  el('file-input').addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file || (!state.activeChat && !state.activeGroup)) return;
    const progress = el('upload-progress');
    progress.classList.remove('hidden');
    progress.textContent = 'Загрузка ' + file.name + '...';

    const ext = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : '';
    const isCodeFile = Object.prototype.hasOwnProperty.call(CODE_EXTENSIONS, ext) && file.size < 512 * 1024;

    try {
      let codePreview = null;
      if (isCodeFile) {
        const text = await readAsText(file);
        codePreview = text.length > MAX_INLINE_CODE_CHARS
          ? text.slice(0, MAX_INLINE_CODE_CHARS) + '\n... (файл обрезан, полный код доступен по ссылке)'
          : text;
      }

      const formData = new FormData();
      formData.append('file', file);
      const res = await fetch('/api/media/upload', {
        method: 'POST',
        headers: authHeaders(),
        body: formData
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Ошибка загрузки');

      sendChat({
        content: isCodeFile ? codePreview : '',
        type: isCodeFile ? 'CODE' : data.type,
        mediaUrl: data.url,
        mediaName: data.name
      });
    } catch (err) {
      showToast(err.message);
    } finally {
      progress.classList.add('hidden');
      e.target.value = '';
    }
  });

  function logoutForced() {
    localStorage.clear();
    location.reload();
  }

  // ---------- Paste-to-code detection ----------

  function initCodePasteDetection() {
    el('message-input').addEventListener('paste', (e) => {
      const text = (e.clipboardData || window.clipboardData).getData('text');
      if (!text) return;
      const lineCount = text.split('\n').length;
      if (lineCount < 4 && text.length < 200) return; // short paste, treat as normal text

      e.preventDefault();
      const detected = detectCodeLanguage(text);
      state.pendingCode = { content: text, ext: detected.ext, lang: detected.lang };
      el('code-paste-label').textContent = `Обнаружен код (${detected.lang}, ${lineCount} строк) — отправится как файл с подсветкой`;
      el('code-paste-chip').classList.remove('hidden');
      el('message-input').value = '';
      el('message-input').placeholder = 'Нажмите "Отправить", чтобы отправить код...';
    });

    el('code-paste-cancel').addEventListener('click', clearPendingCode);
  }

  function clearPendingCode() {
    state.pendingCode = null;
    el('code-paste-chip').classList.add('hidden');
    el('message-input').placeholder = 'Написать сообщение...';
  }

  // ---------- Voice messages ----------

  function initVoiceRecorder() {
    el('mic-btn').addEventListener('click', startRecording);
    el('voice-cancel-btn').addEventListener('click', () => stopRecording(false));
    el('voice-stop-btn').addEventListener('click', () => stopRecording(true));
  }

  async function startRecording() {
    if (!state.activeChat && !state.activeGroup) return;
    if (!navigator.mediaDevices || !window.MediaRecorder) {
      showToast('Запись голоса не поддерживается этим браузером');
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      state.recordedChunks = [];
      state.mediaRecorder = new MediaRecorder(stream);
      state.mediaRecorder.ondataavailable = (e) => { if (e.data.size > 0) state.recordedChunks.push(e.data); };
      state.mediaRecorder.onstop = () => stream.getTracks().forEach(t => t.stop());
      state.mediaRecorder.start();

      state.recordingStart = Date.now();
      el('voice-timer').textContent = '0:00';
      state.recordingTimer = setInterval(() => {
        const secs = Math.floor((Date.now() - state.recordingStart) / 1000);
        el('voice-timer').textContent = Math.floor(secs / 60) + ':' + String(secs % 60).padStart(2, '0');
      }, 500);

      document.querySelector('.message-form').classList.add('hidden');
      el('voice-recorder').classList.remove('hidden');
    } catch (err) {
      showToast('Нет доступа к микрофону: ' + err.message);
    }
  }

  function stopRecording(send) {
    clearInterval(state.recordingTimer);
    document.querySelector('.message-form').classList.remove('hidden');
    el('voice-recorder').classList.add('hidden');

    if (!state.mediaRecorder) return;
    const recorder = state.mediaRecorder;
    state.mediaRecorder = null;

    if (!send) { recorder.stop(); return; }

    recorder.onstop = async () => {
      recorder.stream.getTracks().forEach(t => t.stop());
      const blob = new Blob(state.recordedChunks, { type: 'audio/webm' });
      if (blob.size === 0) return;
      const progress = el('upload-progress');
      progress.classList.remove('hidden');
      progress.textContent = 'Отправка голосового...';
      try {
        const formData = new FormData();
        formData.append('file', blob, 'voice-message.webm');
        const res = await fetch('/api/media/upload', { method: 'POST', headers: authHeaders(), body: formData });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Ошибка загрузки');
        sendChat({ content: '', type: 'VOICE', mediaUrl: data.url, mediaName: data.name });
      } catch (err) {
        showToast(err.message);
      } finally {
        progress.classList.add('hidden');
      }
    };
    recorder.stop();
  }

  // ---------- Emoji modal ----------

  // Эмодзи и стикеры теперь живут в одном окне (#emoji-modal) с переключателем вкладок,
  // а не в двух разных модалках — так быстрее переключаться, не закрывая/открывая заново.
  function switchEmojiStickerTab(tab) {
    document.querySelectorAll('.emoji-sticker-tab').forEach(btn => btn.classList.toggle('active', btn.dataset.tab === tab));
    el('emoji-picker').classList.toggle('hidden', tab !== 'emoji');
    el('sticker-picker').classList.toggle('hidden', tab !== 'sticker');
  }

  function initEmojiPicker() {
    const picker = el('emoji-picker');
    const categoriesBar = document.createElement('div');
    categoriesBar.className = 'emoji-picker-categories';
    const grid = document.createElement('div');
    grid.className = 'emoji-picker-grid';

    function renderCategory(idx) {
      Array.from(categoriesBar.children).forEach((c, i) => c.classList.toggle('active', i === idx));
      grid.innerHTML = '';
      EMOJI_CATEGORIES[idx].emojis.forEach(emo => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.textContent = emo;
        btn.addEventListener('click', () => insertEmoji(emo));
        grid.appendChild(btn);
      });
    }

    EMOJI_CATEGORIES.forEach((cat, idx) => {
      const span = document.createElement('span');
      span.textContent = cat.icon;
      span.title = cat.name;
      span.addEventListener('click', () => renderCategory(idx));
      categoriesBar.appendChild(span);
    });

    picker.innerHTML = '';
    picker.appendChild(categoriesBar);
    picker.appendChild(grid);
    renderCategory(0);

    document.querySelectorAll('.emoji-sticker-tab').forEach(btn => {
      btn.addEventListener('click', () => switchEmojiStickerTab(btn.dataset.tab));
    });

    el('emoji-btn').addEventListener('click', () => { switchEmojiStickerTab('emoji'); openModal('emoji-modal'); });
    wireOverlayClose('emoji-modal', 'emoji-close-btn');
  }

  function initStickerPicker() {
    const picker = el('sticker-picker');
    picker.innerHTML = '';
    STICKER_EMOJIS.forEach(emo => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.textContent = emo;
      btn.addEventListener('click', () => {
        closeModal('emoji-modal');
        sendChat({ content: emo, type: 'STICKER' });
      });
      picker.appendChild(btn);
    });
    if (state.mySticker && state.mySticker.length) {
      const label = document.createElement('div');
      label.className = 'sticker-picker-mine-label';
      label.textContent = 'Мои';
      picker.appendChild(label);
      state.mySticker.forEach(url => {
        const img = document.createElement('img');
        img.src = url;
        img.className = 'sticker-img-btn';
        img.alt = 'sticker';
        img.addEventListener('click', () => {
          closeModal('emoji-modal');
          sendChat({ type: 'IMAGE', mediaUrl: url, mediaName: 'sticker.png', content: '' });
        });
        picker.appendChild(img);
      });
    }
  }

  // ---------- Кастомные темы чата (per-conversation, хранится в localStorage) ----------

  function currentThemeStorageKey() {
    if (state.activeGroup) return 'chatTheme:group:' + state.activeGroup.id;
    if (state.activeChat) return 'chatTheme:direct:' + state.activeChat;
    return null;
  }

  function applyChatTheme() {
    const container = el('chat-active');
    const key = currentThemeStorageKey();
    const themeName = (key && localStorage.getItem(key)) || 'default';
    const theme = CHAT_THEMES[themeName] || CHAT_THEMES.default;
    container.style.setProperty('--chat-bubble-mine', theme.mine);
    container.style.setProperty('--primary', theme.primary);
    document.querySelectorAll('.theme-swatch').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.theme === themeName);
    });
  }

  function initChatThemes() {
    const picker = el('theme-picker');
    picker.innerHTML = '';
    Object.keys(CHAT_THEMES).forEach(key => {
      const theme = CHAT_THEMES[key];
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'theme-swatch';
      btn.dataset.theme = key;
      btn.innerHTML = `<span class="theme-swatch-dot" style="background:${theme.mine}"></span><span class="theme-swatch-label">${theme.label}</span>`;
      btn.addEventListener('click', () => {
        const storageKey = currentThemeStorageKey();
        if (!storageKey) return;
        localStorage.setItem(storageKey, key);
        applyChatTheme();
        closeModal('theme-modal');
      });
      picker.appendChild(btn);
    });

    el('theme-btn').addEventListener('click', () => { applyChatTheme(); openModal('theme-modal'); });
    wireOverlayClose('theme-modal', 'theme-close-btn');
  }

  function insertEmoji(emoji) {
    const input = el('message-input');
    const start = input.selectionStart || input.value.length;
    const end = input.selectionEnd || input.value.length;
    input.value = input.value.slice(0, start) + emoji + input.value.slice(end);
    input.focus();
    input.selectionStart = input.selectionEnd = start + emoji.length;
  }

  // ---------- Groups ----------

  function initListTabs() {
    document.querySelectorAll('.list-tab-btn').forEach(btn => {
      btn.addEventListener('click', () => switchListTab(btn.dataset.list));
    });
    el('create-group-btn').addEventListener('click', () => openGroupModal());
  }

  function switchListTab(tab) {
    state.listTab = tab;
    document.querySelectorAll('.list-tab-btn').forEach(b => b.classList.toggle('active', b.dataset.list === tab));
    el('contacts').classList.toggle('hidden', tab !== 'direct');
    el('contacts-empty').classList.add('hidden');
    el('groups').classList.toggle('hidden', tab !== 'groups');
    el('groups-empty').classList.add('hidden');
    el('create-group-btn').classList.toggle('hidden', tab !== 'groups');
    el('contact-search').value = '';

    if (tab === 'direct') renderContacts();
    else if (tab === 'groups') loadGroups();
    else if (tab === 'news') showNewsView();
  }

  async function loadGroups() {
    const res = await fetch('/api/groups/mine', { headers: authHeaders() });
    if (res.status === 401) return logoutForced();
    const all = await res.json();
    state.myGroups = all.filter(g => g.type === 'GROUP');
    renderGroups();
  }

  function renderGroups() {
    const container = el('groups');
    const emptyBanner = el('groups-empty');
    container.innerHTML = '';
    const term = (el('contact-search').value || '').trim().toLowerCase();
    const list = term ? state.myGroups.filter(g => g.name.toLowerCase().includes(term)) : state.myGroups;

    if (list.length === 0) {
      emptyBanner.classList.remove('hidden');
      emptyBanner.textContent = state.myGroups.length === 0 ? 'У тебя пока нет групп — нажми "+", чтобы создать' : 'Ничего не найдено';
      return;
    }
    emptyBanner.classList.add('hidden');

    list.forEach(g => {
      const div = document.createElement('div');
      div.className = 'contact' + (state.activeGroup && state.activeGroup.id === g.id ? ' active' : '');
      const preview = state.lastMessagePreview[groupChatKey(g.id)];
      const unread = state.unreadCounts[groupChatKey(g.id)] || 0;
      const draft = state.draftTexts[groupChatKey(g.id)];
      const muted = state.mutedChats.includes(groupChatKey(g.id));
      const secondLine = draft
        ? `<span class="draft-tag">Черновик:</span> ${escapeHtml(draft)}`
        : preview
          ? (preview.mine ? 'Вы: ' : preview.senderName ? preview.senderName + ': ' : '') + escapeHtml(preview.text)
          : g.memberCount + ' участников';
      const timeBadge = preview ? `<span class="contact-time">${formatTime(preview.at)}</span>` : '';
      const muteIcon = muted ? '<i class="mute-icon" title="Уведомления выключены">🔕</i>' : '';
      div.innerHTML = `
        <div class="avatar" style="${avatarInlineStyle(g.avatarUrl, g.name)}">${g.avatarUrl ? '' : initials(g.name)}</div>
        <div class="contact-info">
          <div class="contact-name-row">
            <div class="contact-name">${escapeHtml(g.name)}${muteIcon}</div>
            ${timeBadge}
          </div>
          <div class="contact-status">${secondLine}</div>
        </div>
        ${unread > 0 && !muted ? `<div class="contact-badge">${unread > 99 ? '99+' : unread}</div>` : ''}
      `;
      div.addEventListener('click', () => openGroup(g));
      container.appendChild(div);
    });
  }

  async function openGroup(group) {
    leaveGroupSubscription();
    state.activeChat = null;
    state.activeGroup = group;
    state.unreadCounts[groupChatKey(group.id)] = 0;
    renderGroups();

    showMainPanel('chat');
    el('typing-indicator').classList.add('hidden');
    el('app-screen').classList.add('chat-open');
    closeModal('emoji-modal');
    closeChatSearch();
    if (state.selectMode) exitSelectMode();
    state.groupTypers = {};

    el('chat-with-name').textContent = group.name;
    el('chat-with-status').textContent = group.memberCount + ' участников';
    setAvatar(el('chat-with-avatar'), group.name, group.avatarUrl);
    // 1:1 calls only — hide the call buttons for group chats, show the group-call button instead.
    el('call-audio-btn').classList.add('hidden');
    el('call-video-btn').classList.add('hidden');
    el('call-group-video-btn').classList.remove('hidden');
    el('group-info-btn').classList.remove('hidden');
    state.activePin = null;
    renderPinnedBanner();

    subscribeToGroup(group.id);

    // Messages aren't stored anywhere, so there's no history to load — the pane
    // starts empty and fills in with whatever arrives live from here on.
    el('messages').innerHTML = '';
    state.recentMessages = {};
    state.messageReactions = {};
    state.pollVotes = {};
    clearReplyTarget();
    applyChatTheme();
    loadDraftIntoComposer();
    loadGroupMemberNames(group.id);
  }

  // Caches username -> displayName for this group's members so @mentions can be rendered
  // by display name instead of the raw login (usernames are never shown on screen).
  async function loadGroupMemberNames(groupId) {
    state.groupMemberNames = {};
    try {
      const res = await fetch('/api/groups/' + groupId + '/members', { headers: authHeaders() });
      if (!res.ok) return;
      const members = await res.json();
      if (!state.activeGroup || String(state.activeGroup.id) !== String(groupId)) return; // switched away meanwhile
      members.forEach(m => { state.groupMemberNames[m.username] = m.displayName; });
    } catch (e) { /* mention rendering just falls back to raw username */ }
  }

  function initGroupModal() {
    const selectedMembers = new Set();

    function renderMembersPicker() {
      const list = el('group-members-list');
      list.innerHTML = '';
      state.contacts.forEach(c => {
        const row = document.createElement('label');
        row.className = 'group-member-row';
        row.innerHTML = `<input type="checkbox" value="${escapeHtml(c.username)}"> <span>${escapeHtml(c.displayName)}</span>`;
        const checkbox = row.querySelector('input');
        checkbox.addEventListener('change', () => {
          row.classList.toggle('checked', checkbox.checked);
          if (checkbox.checked) selectedMembers.add(c.username); else selectedMembers.delete(c.username);
        });
        list.appendChild(row);
      });
    }

    window.openGroupModal = function () {
      selectedMembers.clear();
      el('group-name-input').value = '';
      el('group-error').textContent = '';
      renderMembersPicker();
      openModal('group-modal');
    };

    wireOverlayClose('group-modal', 'group-close-btn');

    el('group-create-btn').addEventListener('click', async () => {
      const name = el('group-name-input').value.trim();
      el('group-error').textContent = '';
      if (!name) { el('group-error').textContent = 'Введите название'; return; }
      try {
        const res = await fetch('/api/groups', {
          method: 'POST',
          headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
          body: JSON.stringify({ name, type: 'GROUP', members: Array.from(selectedMembers) })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Не удалось создать');
        closeModal('group-modal');
        switchListTab('groups');
        openGroup(data);
      } catch (err) {
        el('group-error').textContent = err.message;
      }
    });
  }

  function openGroupModal() {
    if (window.openGroupModal) window.openGroupModal();
  }

  // ---------- Personal cabinet (профиль: имя, статус, фото) ----------

  function initProfileModal() {
    const openBtns = [el('settings-btn'), el('open-profile-btn')];
    const modal = el('profile-modal');

    async function open() {
      el('profile-error').textContent = '';
      el('profile-success').classList.add('hidden');
      try {
        const res = await fetch('/api/users/me', { headers: authHeaders() });
        if (res.status === 401) return logoutForced();
        const me = await res.json();
        el('profile-displayname').value = me.displayName || '';
        el('profile-status').value = me.status || '';
        el('profile-email').value = me.email || '';
        el('profile-show-online').checked = me.showOnlineStatus !== false;
        el('profile-presence-input').value = me.presenceStatus || '';
        el('profile-jobtitle').value = me.jobTitle || '';
        el('profile-sound-input').value = state.notificationSound;
        setAvatar(el('profile-avatar'), me.displayName, me.avatarUrl);
        state.avatarUrl = me.avatarUrl || null;
        if (state.avatarUrl) localStorage.setItem('avatarUrl', state.avatarUrl); else localStorage.removeItem('avatarUrl');
        loadMyStickers();
      } catch (err) {
        el('profile-error').textContent = 'Не удалось загрузить профиль';
      }
      openModal('profile-modal');
    }

    openBtns.forEach(btn => btn && btn.addEventListener('click', open));
    wireOverlayClose('profile-modal', 'profile-close-btn');

    el('profile-save-btn').addEventListener('click', async () => {
      el('profile-error').textContent = '';
      el('profile-success').classList.add('hidden');
      const displayName = el('profile-displayname').value.trim();
      const status = el('profile-status').value.trim();
      const email = el('profile-email').value.trim();
      const showOnlineStatus = el('profile-show-online').checked;
      const presenceStatus = el('profile-presence-input').value;
      const jobTitle = el('profile-jobtitle').value.trim();
      const sound = el('profile-sound-input').value;
      try {
        const res = await fetch('/api/users/me', {
          method: 'PUT',
          headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
          body: JSON.stringify({ displayName, status, email, showOnlineStatus, presenceStatus, jobTitle })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Не удалось сохранить профиль');

        state.displayName = data.displayName;
        state.status = data.status;
        state.notificationSound = sound;
        localStorage.setItem('notificationSound', sound);
        localStorage.setItem('displayName', data.displayName);
        if (data.status) localStorage.setItem('status', data.status); else localStorage.removeItem('status');

        el('me-name').textContent = state.displayName;
        setAvatar(el('me-avatar'), state.displayName, state.avatarUrl);
        el('profile-success').classList.remove('hidden');
        await loadContacts();
      } catch (err) {
        el('profile-error').textContent = err.message;
      }
    });

    el('sticker-upload-input').addEventListener('change', async (e) => {
      const file = e.target.files[0];
      if (!file) return;
      const formData = new FormData();
      formData.append('file', file);
      try {
        const res = await fetch('/api/users/me/stickers', { method: 'POST', headers: authHeaders(), body: formData });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Не удалось загрузить стикер');
        state.mySticker = data;
        renderMyStickersList();
        initStickerPicker();
      } catch (err) {
        el('profile-error').textContent = err.message;
      }
      e.target.value = '';
    });
  }

  async function loadMyStickers() {
    try {
      const res = await fetch('/api/users/me/stickers', { headers: authHeaders() });
      if (!res.ok) return;
      state.mySticker = await res.json();
      renderMyStickersList();
    } catch (e) { /* ignore */ }
  }

  function renderMyStickersList() {
    const list = el('my-stickers-list');
    if (!list) return;
    list.innerHTML = '';
    (state.mySticker || []).forEach(url => {
      const img = document.createElement('img');
      img.src = url;
      img.alt = 'sticker';
      list.appendChild(img);
    });
  }

  // ---------- Avatar photo editor (crop/resize before upload) ----------

  function initAvatarCropper() {
    el('avatar-input').addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        el('crop-image').src = reader.result;
        openModal('crop-modal');
        if (state.cropper) state.cropper.destroy();
        state.cropper = new Cropper(el('crop-image'), {
          aspectRatio: 1,
          viewMode: 1,
          background: false,
          zoomOnWheel: true,
          ready: function () { el('crop-zoom').value = 0; },
        });
      };
      reader.readAsDataURL(file);
      e.target.value = '';
    });

    el('crop-zoom').addEventListener('input', (e) => {
      if (!state.cropper) return;
      const val = parseFloat(e.target.value);
      state.cropper.zoomTo(1 + val);
    });

    function closeCropModal() {
      closeModal('crop-modal');
      if (state.cropper) { state.cropper.destroy(); state.cropper = null; }
    }
    wireOverlayClose('crop-modal', 'crop-close-btn', closeCropModal);

    el('crop-save-btn').addEventListener('click', () => {
      if (!state.cropper) return;
      el('crop-error').textContent = '';
      const canvas = state.cropper.getCroppedCanvas({
        width: 512,
        height: 512,
        imageSmoothingEnabled: true,
        imageSmoothingQuality: 'high',
      });
      canvas.toBlob(async (blob) => {
        try {
          const formData = new FormData();
          formData.append('file', blob, 'avatar.jpg');
          const res = await fetch('/api/users/me/avatar', { method: 'POST', headers: authHeaders(), body: formData });
          const data = await res.json();
          if (!res.ok) throw new Error(data.error || 'Не удалось загрузить фото');

          state.avatarUrl = data.avatarUrl;
          localStorage.setItem('avatarUrl', data.avatarUrl);
          setAvatar(el('profile-avatar'), state.displayName, state.avatarUrl);
          setAvatar(el('me-avatar'), state.displayName, state.avatarUrl);
          closeCropModal();
          el('profile-success').classList.remove('hidden');
        } catch (err) {
          el('crop-error').textContent = err.message;
        }
      }, 'image/jpeg', 0.92);
    });
  }

  // ---------- Доска (общая доска расписаний и объявлений) ----------

  function showBoardView() {
    leaveGroupSubscription();
    state.activeChat = null;
    state.activeGroup = null;
    renderContacts();
    if (state.listTab === 'groups') renderGroups();
    showMainPanel('board');
    el('app-screen').classList.add('chat-open');
    closeModal('emoji-modal');
    loadBoard();
  }

  async function loadBoard() {
    const res = await fetch('/api/board', { headers: authHeaders() });
    if (res.status === 401) return logoutForced();
    state.boardPosts = await res.json();
    renderBoard();
    renderTaskBoard();
    setBoardMode(state.boardMode);
  }

  function formatDateTime(iso) {
    try {
      return new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
    } catch (e) { return ''; }
  }

  // Editable tables (SCHEDULE posts only) are appended to the description text behind a marker
  // the user never sees — the modal shows a real editable grid, never raw markup.
  const TABLE_MARKER = '\n<<<TABLES>>>';

  function serializeDescriptionWithTables(text, tables) {
    const base = (text || '').trim();
    if (!tables || tables.length === 0) return base;
    return base + TABLE_MARKER + JSON.stringify(tables);
  }

  function parseDescriptionWithTables(desc) {
    const idx = (desc || '').indexOf(TABLE_MARKER);
    if (idx === -1) return { text: desc || '', tables: [] };
    const text = desc.slice(0, idx);
    let tables = [];
    try { tables = JSON.parse(desc.slice(idx + TABLE_MARKER.length)); } catch (e) { tables = []; }
    return { text, tables };
  }

  function renderEmbeddedTablesHtml(tables) {
    return tables.map(t => `<table class="board-embedded-table">${
      t.map(row => `<tr>${row.map(c => `<td>${escapeHtml(c)}</td>`).join('')}</tr>`).join('')
    }</table>`).join('');
  }

  function renderBoard() {
    const container = el('board-list');
    const emptyBanner = el('board-empty');
    container.innerHTML = '';

    document.querySelectorAll('#board-feed-subtabs .board-subtab-btn').forEach(b => {
      b.classList.toggle('active', b.dataset.feedFilter === state.boardFeedFilter);
    });

    let feedPosts = state.boardPosts.filter(p => p.type !== 'TASK');
    if (state.boardFeedFilter !== 'ALL') {
      feedPosts = feedPosts.filter(p => p.type === state.boardFeedFilter);
    }

    if (feedPosts.length === 0) {
      emptyBanner.classList.remove('hidden');
      return;
    }
    emptyBanner.classList.add('hidden');

    feedPosts.forEach(p => {
      const div = document.createElement('div');
      div.className = 'board-card' + (p.type === 'ANNOUNCEMENT' ? ' announcement' : '');
      const badge = p.type === 'ANNOUNCEMENT' ? 'Объявление' : 'Расписание';
      const canManage = state.isAdmin;
      const dateLine = p.eventAt ? `<div class="board-card-date">🗓 ${formatDateTime(p.eventAt)}</div>` : '';
      const { text, tables } = parseDescriptionWithTables(p.description);
      div.innerHTML = `
        <div class="board-card-top">
          <span class="board-type-badge">${badge}</span>
          <span class="board-card-author">${escapeHtml(p.authorDisplayName)} · ${formatDateTime(p.createdAt)}</span>
          ${canManage ? '<button type="button" class="board-card-del" aria-label="Удалить">✕</button>' : ''}
        </div>
        <div class="board-card-title">${escapeHtml(p.title)}</div>
        ${text ? `<div class="board-card-desc">${escapeHtml(text)}</div>` : ''}
        ${tables.length ? renderEmbeddedTablesHtml(tables) : ''}
        ${dateLine}
      `;
      if (canManage) {
        div.querySelector('.board-card-del').addEventListener('click', async () => {
          if (!(await showConfirm('Удалить эту запись с доски?'))) return;
          const res = await fetch('/api/board/' + p.id, { method: 'DELETE', headers: authHeaders() });
          if (res.ok) loadBoard();
        });
      }
      container.appendChild(div);
    });
  }

  function renderTaskBoard() {
    const tasks = state.boardPosts.filter(p => p.type === 'TASK');
    const emptyBanner = el('task-board-empty');
    const board = el('task-board');

    TASK_STATUSES.forEach(status => {
      const col = el('task-col-' + status);
      col.innerHTML = '';
    });

    if (tasks.length === 0) {
      emptyBanner.classList.remove('hidden');
      board.classList.add('hidden');
      return;
    }
    emptyBanner.classList.add('hidden');
    board.classList.remove('hidden');

    const grouped = { TODO: [], IN_PROGRESS: [], DONE: [] };
    tasks.forEach(t => grouped[t.status || 'TODO'].push(t));

    const STATUS_ICON = { TODO: '📋', IN_PROGRESS: '⏳', DONE: '✅' };
    // Admin can only reset a task back to TODO; everyone else only toggles IN_PROGRESS/DONE.
    const adminPills = ['TODO'];
    const userPills = ['IN_PROGRESS', 'DONE'];
    const PRIORITY_LABEL = { LOW: 'Низкий', MEDIUM: 'Средний', HIGH: 'Высокий' };

    TASK_STATUSES.forEach(status => {
      el('task-count-' + status).textContent = grouped[status].length;
      const col = el('task-col-' + status);
      grouped[status].forEach(t => {
        const priority = t.priority || 'MEDIUM';
        const card = document.createElement('div');
        card.className = 'task-card priority-' + priority.toLowerCase() + (status === 'DONE' ? ' done' : '');
        const canDelete = state.isAdmin;
        const dueChip = t.eventAt ? `<span class="task-due-chip">🗓 ${formatDateTime(t.eventAt)}</span>` : '';
        const startChip = t.startAt ? `<span class="task-start-chip">▶ ${formatDateTime(t.startAt)}</span>` : '';

        const priorityBlock = state.isAdmin
          ? `<select class="task-priority-select priority-${priority.toLowerCase()}">
               ${Object.keys(PRIORITY_LABEL).map(p => `<option value="${p}" ${p === priority ? 'selected' : ''}>${PRIORITY_LABEL[p]}</option>`).join('')}
             </select>`
          : `<span class="task-priority-tag priority-${priority.toLowerCase()}">${PRIORITY_LABEL[priority]}</span>`;

        const pillStatuses = state.isAdmin ? adminPills : userPills;
        const pillsHtml = pillStatuses.map(s => `
          <div class="task-status-pill${s === status ? ' checked' : ''}" data-status="${s}">
            <span class="task-status-check">${s === status ? '✓' : ''}</span>
            <span class="task-status-emoji">${STATUS_ICON[s]}</span>
            <span class="task-status-text">${TASK_STATUS_LABEL[s]}</span>
          </div>
        `).join('');

        const assigneeBlock = state.isAdmin
          ? `<div class="task-assignee-row">
               <span class="task-assignee-label">Кому:</span>
               <select class="task-assignee-select">
                 <option value="">Не назначено</option>
                 ${state.contacts.map(c => `<option value="${escapeHtml(c.username)}" ${c.username === t.assigneeUsername ? 'selected' : ''}>${escapeHtml(c.displayName)}</option>`).join('')}
               </select>
             </div>`
          : (t.assigneeDisplayName
              ? `<div class="task-assignee-row"><span class="task-assignee-label">Кому:</span> <span class="task-assignee-readonly">${escapeHtml(t.assigneeDisplayName)}</span></div>`
              : '');

        const seenByHtml = (state.isAdmin && t.seenBy && t.seenBy.length)
          ? `<div class="task-seenby-row"><span class="task-seenby-label">Просмотрели:</span><div class="task-seenby-avatars">${
              t.seenBy.filter(u => u.username !== state.username).map(u =>
                `<span class="avatar-xs" style="${avatarInlineStyle(u.avatarUrl, u.displayName)}" title="${escapeHtml(u.displayName)}">${u.avatarUrl ? '' : initials(u.displayName)}</span>`
              ).join('')
            }</div></div>`
          : '';

        card.innerHTML = `
          <div class="task-card-top">
            <span class="task-avatar-dot" style="${avatarInlineStyle(t.authorAvatarUrl, t.authorDisplayName)}"></span>
            <span class="task-card-author">${escapeHtml(t.authorDisplayName)}</span>
            ${priorityBlock}
            ${canDelete ? '<button type="button" class="board-card-del task-card-del" aria-label="Удалить">✕</button>' : ''}
          </div>
          <div class="task-card-title">${status === 'DONE' ? '✓ ' : ''}${escapeHtml(t.title)}</div>
          ${t.description ? `<div class="board-card-desc">${escapeHtml(t.description)}</div>` : ''}
          <div class="task-card-bottom">
            <div class="task-dates">${startChip}${dueChip}</div>
          </div>
          <div class="task-status-pills">${pillsHtml}</div>
          ${assigneeBlock}
          ${seenByHtml}
        `;
        card.querySelectorAll('.task-status-pill').forEach(pill => {
          pill.addEventListener('click', async () => {
            const res = await fetch('/api/board/' + t.id + '/status', {
              method: 'PATCH',
              headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
              body: JSON.stringify({ status: pill.dataset.status })
            });
            if (res.ok) loadBoard();
          });
        });
        if (state.isAdmin) {
          card.querySelector('.task-assignee-select').addEventListener('change', async (e) => {
            const res = await fetch('/api/board/' + t.id + '/assignee', {
              method: 'PATCH',
              headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
              body: JSON.stringify({ assigneeUsername: e.target.value })
            });
            if (res.ok) loadBoard();
          });
          card.querySelector('.task-priority-select').addEventListener('change', async (e) => {
            const res = await fetch('/api/board/' + t.id + '/priority', {
              method: 'PATCH',
              headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
              body: JSON.stringify({ priority: e.target.value })
            });
            if (res.ok) loadBoard();
          });
        }
        if (canDelete) {
          card.querySelector('.task-card-del').addEventListener('click', async () => {
            if (!(await showConfirm('Удалить эту задачу?'))) return;
            const res = await fetch('/api/board/' + t.id, { method: 'DELETE', headers: authHeaders() });
            if (res.ok) loadBoard();
          });
        }
        col.appendChild(card);
        // Fire-and-forget: lets an admin see who's opened this task ("seen by").
        fetch('/api/board/' + t.id + '/view', { method: 'POST', headers: authHeaders() }).catch(() => {});
      });
    });
  }

  function setBoardMode(mode) {
    state.boardMode = mode;
    el('board-mode-feed').classList.toggle('active', mode === 'feed');
    el('board-mode-tasks').classList.toggle('active', mode === 'tasks');
    el('board-list').classList.toggle('hidden', mode !== 'feed');
    el('board-empty').classList.toggle('hidden', mode !== 'feed' || state.boardPosts.filter(p => p.type !== 'TASK').length > 0);
    const hasTasks = state.boardPosts.filter(p => p.type === 'TASK').length > 0;
    el('task-board').classList.toggle('hidden', mode !== 'tasks' || !hasTasks);
    el('task-board-empty').classList.toggle('hidden', mode !== 'tasks' || hasTasks);
  }

  function setBoardType(type) {
    state.boardType = type;
    document.querySelectorAll('#board-modal .tab-btn').forEach(b => b.classList.toggle('active', b.dataset.boardType === type));
    el('board-date-field').classList.toggle('hidden', type === 'ANNOUNCEMENT');
    el('board-date-label').textContent = type === 'TASK' ? 'Срок (необязательно)' : 'Дата и время';
    el('board-start-field').classList.toggle('hidden', type !== 'TASK');
    el('board-assignee-field').classList.toggle('hidden', type !== 'TASK');
    el('board-priority-field').classList.toggle('hidden', type !== 'TASK');
    el('board-table-field').classList.toggle('hidden', type !== 'SCHEDULE');
    if (type === 'TASK') populateAssigneeSelect();
  }

  function populateAssigneeSelect() {
    const select = el('board-assignee-input');
    select.innerHTML = '<option value="">Не назначено</option>' +
      state.contacts.map(c => `<option value="${escapeHtml(c.username)}">${escapeHtml(c.displayName)}</option>`).join('');
  }

  // ---------- Редактируемые таблицы в записи "Расписание" ----------

  function renderBoardTableBuilder() {
    const list = el('board-table-list');
    list.innerHTML = '';
    state.boardTables.forEach((table, ti) => {
      const wrap = document.createElement('div');
      wrap.className = 'board-table-wrap';

      const toolbar = document.createElement('div');
      toolbar.className = 'board-table-toolbar';
      toolbar.innerHTML = `
        <span class="board-table-label">Таблица</span>
        <button type="button" class="board-table-mini-btn" data-act="row">+ строка</button>
        <button type="button" class="board-table-mini-btn" data-act="col">+ столбец</button>
        <button type="button" class="board-table-mini-btn board-table-remove" data-act="remove" aria-label="Удалить таблицу">✕</button>
      `;

      const tableEl = document.createElement('table');
      tableEl.className = 'board-edit-table';
      table.forEach((row, ri) => {
        const tr = document.createElement('tr');
        row.forEach((cell, ci) => {
          const td = document.createElement('td');
          const input = document.createElement('input');
          input.type = 'text';
          input.value = cell;
          input.placeholder = ri === 0 ? 'Заголовок' : '';
          input.addEventListener('input', () => { state.boardTables[ti][ri][ci] = input.value; });
          td.appendChild(input);
          tr.appendChild(td);
        });
        tableEl.appendChild(tr);
      });

      toolbar.querySelector('[data-act="row"]').addEventListener('click', () => {
        const cols = table[0] ? table[0].length : 2;
        table.push(new Array(cols).fill(''));
        renderBoardTableBuilder();
      });
      toolbar.querySelector('[data-act="col"]').addEventListener('click', () => {
        table.forEach(row => row.push(''));
        renderBoardTableBuilder();
      });
      toolbar.querySelector('[data-act="remove"]').addEventListener('click', () => {
        state.boardTables.splice(ti, 1);
        renderBoardTableBuilder();
      });

      wrap.appendChild(toolbar);
      wrap.appendChild(tableEl);
      list.appendChild(wrap);
    });
  }

  function initBoard() {
    el('board-btn').addEventListener('click', showBoardView);

    el('board-mode-feed').addEventListener('click', () => setBoardMode('feed'));
    el('board-mode-tasks').addEventListener('click', () => setBoardMode('tasks'));

    document.querySelectorAll('#board-feed-subtabs .board-subtab-btn').forEach(b => {
      b.addEventListener('click', () => {
        state.boardFeedFilter = b.dataset.feedFilter;
        renderBoard();
      });
    });

    document.querySelectorAll('#board-modal .tab-btn').forEach(b => {
      b.addEventListener('click', () => setBoardType(b.dataset.boardType));
    });

    el('board-table-add-btn').addEventListener('click', () => {
      state.boardTables.push([['', ''], ['', '']]);
      renderBoardTableBuilder();
    });

    el('board-add-btn').addEventListener('click', () => {
      el('board-title-input').value = '';
      el('board-desc-input').value = '';
      el('board-date-input').value = '';
      el('board-start-input').value = '';
      el('board-error').textContent = '';
      state.boardTables = [];
      renderBoardTableBuilder();
      setBoardType(state.boardMode === 'tasks' ? 'TASK' : 'SCHEDULE');
      el('board-assignee-input').value = '';
      el('board-priority-input').value = 'MEDIUM';
      openModal('board-modal');
    });

    wireOverlayClose('board-modal', 'board-close-btn');

    el('board-create-btn').addEventListener('click', async () => {
      const title = el('board-title-input').value.trim();
      el('board-error').textContent = '';
      if (!title) { el('board-error').textContent = 'Введите заголовок'; return; }
      const rawDesc = el('board-desc-input').value.trim();
      const description = state.boardType === 'SCHEDULE'
        ? serializeDescriptionWithTables(rawDesc, state.boardTables)
        : rawDesc;
      const body = { type: state.boardType, title, description };
      if ((state.boardType === 'SCHEDULE' || state.boardType === 'TASK') && el('board-date-input').value) {
        body.eventAt = el('board-date-input').value;
      }
      if (state.boardType === 'TASK') {
        if (el('board-start-input').value) body.startAt = el('board-start-input').value;
        if (el('board-assignee-input').value) body.assigneeUsername = el('board-assignee-input').value;
        body.priority = el('board-priority-input').value;
      }
      try {
        const res = await fetch('/api/board', {
          method: 'POST',
          headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
          body: JSON.stringify(body)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Не удалось опубликовать');
        closeModal('board-modal');
        await loadBoard();
      } catch (err) {
        el('board-error').textContent = err.message;
      }
    });
  }

  // ---------- Пересылка сообщений ----------

  function openForwardModal(clientId) {
    state.forwardClientId = clientId;
    const list = el('forward-target-list');
    list.innerHTML = '';
    el('forward-error').textContent = '';

    state.contacts.forEach(c => {
      const row = document.createElement('div');
      row.className = 'forward-target-row';
      row.innerHTML = `
        <span class="avatar avatar-sm" style="${avatarInlineStyle(c.avatarUrl, c.displayName)}">${c.avatarUrl ? '' : initials(c.displayName)}</span>
        <span>${escapeHtml(c.displayName)}</span>
      `;
      row.addEventListener('click', () => forwardTo({ recipientUsername: c.username }));
      list.appendChild(row);
    });
    state.myGroups.forEach(g => {
      const row = document.createElement('div');
      row.className = 'forward-target-row';
      row.innerHTML = `
        <span class="avatar avatar-sm" style="${avatarInlineStyle(g.avatarUrl, g.name)}">${g.avatarUrl ? '' : initials(g.name)}</span>
        <span>${escapeHtml(g.name)}</span>
      `;
      row.addEventListener('click', () => forwardTo({ groupId: g.id }));
      list.appendChild(row);
    });

    openModal('forward-modal');
  }

  async function forwardTo(target) {
    const ids = Array.isArray(state.forwardClientId) ? state.forwardClientId : [state.forwardClientId];
    let sentAny = false;
    ids.forEach(id => {
      const info = state.recentMessages[id];
      if (!info || !info.raw) return;
      const src = info.raw;
      const payload = Object.assign({}, target, {
        clientId: uid(),
        type: src.type || 'TEXT',
        content: src.content,
        mediaUrl: src.mediaUrl,
        mediaName: src.mediaName,
        forwardedFrom: src.senderDisplayName || src.senderUsername
      });
      state.stompClient.publish({ destination: '/app/chat.send', body: JSON.stringify(payload) });
      sentAny = true;
    });
    if (!sentAny) { el('forward-error').textContent = 'Не удалось переслать это сообщение'; return; }
    closeModal('forward-modal');
    // Nothing is persisted server-side — a forwarded message only ever renders in a chat
    // that's open at the moment it arrives, and the forward target is usually a different
    // chat than the one it was forwarded from. Switch to it so the result is actually visible.
    if (target.recipientUsername) {
      if (state.activeChat !== target.recipientUsername) openChat(target.recipientUsername);
    } else if (target.groupId != null) {
      if (!state.activeGroup || String(state.activeGroup.id) !== String(target.groupId)) {
        const group = state.myGroups.find(g => String(g.id) === String(target.groupId));
        if (group) openGroup(group);
      }
    }
    showToast('Переслано');
  }

  function initForward() {
    wireOverlayClose('forward-modal', 'forward-close-btn');
  }

  // ---------- Роли и управление группой ----------

  async function loadGroupInfo() {
    if (!state.activeGroup) return;
    const res = await fetch('/api/groups/' + state.activeGroup.id + '/members', { headers: authHeaders() });
    if (res.status === 401) return logoutForced();
    const members = await res.json();
    const me = members.find(m => m.username === state.username);
    state.activeGroupRole = me ? me.role : 'MEMBER';
    renderGroupInfoMembers(members);
    el('group-delete-btn').classList.toggle('hidden', state.activeGroupRole !== 'ADMIN');
  }

  function renderGroupInfoMembers(members) {
    const list = el('group-info-members');
    list.innerHTML = '';
    const iAmAdmin = state.activeGroupRole === 'ADMIN';
    members.forEach(m => {
      const row = document.createElement('div');
      row.className = 'group-info-member-row';
      const badge = m.role === 'ADMIN' ? '<span class="role-badge">Админ</span>' : '';
      const canManage = iAmAdmin && m.username !== state.username;
      row.innerHTML = `
        <span class="avatar avatar-sm" style="${avatarInlineStyle(m.avatarUrl, m.displayName)}">${m.avatarUrl ? '' : initials(m.displayName)}</span>
        <span class="group-info-member-name">${escapeHtml(m.displayName)}</span>
        ${badge}
        ${canManage ? `<button type="button" class="group-member-kebab" aria-label="Действия">⋮</button>` : ''}
      `;
      if (canManage) {
        const kebab = row.querySelector('.group-member-kebab');
        kebab.addEventListener('click', (e) => {
          e.stopPropagation();
          document.querySelectorAll('.group-member-menu').forEach(n => n.remove());
          const menu = document.createElement('div');
          menu.className = 'group-member-menu';
          menu.innerHTML = `
            <button type="button" data-act="role">${m.role === 'ADMIN' ? 'Убрать из админов' : 'Сделать админом'}</button>
            <button type="button" data-act="kick" class="danger-item">Удалить из группы</button>
          `;
          menu.querySelector('[data-act="role"]').addEventListener('click', async () => {
            menu.remove();
            const newRole = m.role === 'ADMIN' ? 'MEMBER' : 'ADMIN';
            await fetch(`/api/groups/${state.activeGroup.id}/members/${encodeURIComponent(m.username)}/role`, {
              method: 'PATCH',
              headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
              body: JSON.stringify({ role: newRole })
            });
            loadGroupInfo();
          });
          menu.querySelector('[data-act="kick"]').addEventListener('click', async () => {
            menu.remove();
            if (!(await showConfirm('Удалить ' + m.displayName + ' из группы?'))) return;
            await fetch(`/api/groups/${state.activeGroup.id}/members/${encodeURIComponent(m.username)}`, {
              method: 'DELETE', headers: authHeaders()
            });
            loadGroupInfo();
          });
          row.appendChild(menu);
          const closeOnce = () => { menu.remove(); document.removeEventListener('click', closeOnce); };
          setTimeout(() => document.addEventListener('click', closeOnce), 0);
        });
      }
      list.appendChild(row);
    });
  }

  function initGroupInfo() {
    el('group-info-btn').addEventListener('click', () => { loadGroupInfo(); renderGroupMuteButton(); openModal('group-info-modal'); });
    wireOverlayClose('group-info-modal', 'group-info-close-btn');

    el('group-mute-btn').addEventListener('click', () => {
      if (!state.activeGroup) return;
      toggleMuteChat(groupChatKey(state.activeGroup.id));
      renderGroupMuteButton();
    });

    el('group-leave-btn').addEventListener('click', async () => {
      if (!state.activeGroup) return;
      if (!(await showConfirm('Покинуть группу «' + state.activeGroup.name + '»?'))) return;
      const res = await fetch('/api/groups/' + state.activeGroup.id + '/leave', { method: 'POST', headers: authHeaders() });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) { el('group-info-error').textContent = data.error || 'Не удалось выйти из группы'; return; }
      closeModal('group-info-modal');
      state.activeGroup = null;
      showMainPanel('empty');
      el('app-screen').classList.remove('chat-open');
      loadGroups();
    });

    el('group-delete-btn').addEventListener('click', async () => {
      if (!state.activeGroup) return;
      if (!(await showConfirm('Удалить группу «' + state.activeGroup.name + '» для всех участников?'))) return;
      const res = await fetch('/api/groups/' + state.activeGroup.id, { method: 'DELETE', headers: authHeaders() });
      if (!res.ok) { const data = await res.json().catch(() => ({})); el('group-info-error').textContent = data.error || 'Не удалось удалить группу'; return; }
      closeModal('group-info-modal');
      state.activeGroup = null;
      showMainPanel('empty');
      el('app-screen').classList.remove('chat-open');
      loadGroups();
    });
  }

  function renderGroupMuteButton() {
    if (!state.activeGroup) return;
    const muted = isChatMuted(groupChatKey(state.activeGroup.id));
    el('group-mute-label').textContent = muted ? 'Включить уведомления' : 'Отключить уведомления';
    el('group-mute-btn').classList.toggle('active', muted);
  }

  // ---------- Профиль контакта (клик по шапке 1:1 чата) ----------

  function renderContactProfileMute() {
    if (!state.activeChat) return;
    const muted = isChatMuted(contactKey(state.activeChat));
    el('contact-profile-mute-label').textContent = muted ? 'Включить уведомления' : 'Отключить уведомления';
    el('contact-profile-mute-btn').classList.toggle('active', muted);
  }

  function renderContactProfileBlock() {
    if (!state.activeChat) return;
    const blocked = state.blockedUsernames.includes(state.activeChat);
    el('contact-profile-block-label').textContent = blocked ? 'Разблокировать' : 'Заблокировать';
    el('contact-profile-block-btn').classList.toggle('active', blocked);
  }

  function openContactProfile() {
    if (!state.activeChat) return;
    const contact = state.contacts.find(c => c.username === state.activeChat);
    const displayName = contact ? contact.displayName : state.activeChat;
    setAvatar(el('contact-profile-avatar'), displayName, contact ? contact.avatarUrl : null);
    el('contact-profile-name').textContent = displayName;
    el('contact-profile-username').textContent = '@' + state.activeChat;
    const jobTitleEl = el('contact-profile-jobtitle');
    if (contact && contact.jobTitle) {
      jobTitleEl.textContent = contact.jobTitle;
      jobTitleEl.classList.remove('hidden');
    } else {
      jobTitleEl.classList.add('hidden');
    }
    const presenceEl = el('contact-profile-presence');
    if (contact && contact.presenceStatus && PRESENCE_LABELS[contact.presenceStatus]) {
      presenceEl.textContent = PRESENCE_LABELS[contact.presenceStatus];
      presenceEl.classList.remove('hidden');
    } else {
      presenceEl.classList.add('hidden');
    }
    el('contact-profile-error').textContent = '';
    renderContactProfileMute();
    renderContactProfileBlock();
    openModal('contact-profile-modal');
  }

  function initContactProfile() {
    el('chat-header-identity').addEventListener('click', () => {
      if (state.activeGroup) { loadGroupInfo(); renderGroupMuteButton(); openModal('group-info-modal'); return; }
      if (state.activeChat) openContactProfile();
    });
    wireOverlayClose('contact-profile-modal', 'contact-profile-close-btn');

    el('contact-profile-mute-btn').addEventListener('click', () => {
      if (!state.activeChat) return;
      toggleMuteChat(contactKey(state.activeChat));
      renderContactProfileMute();
    });

    el('contact-profile-block-btn').addEventListener('click', async () => {
      if (!state.activeChat) return;
      const blocked = state.blockedUsernames.includes(state.activeChat);
      try {
        const res = await fetch('/api/users/' + state.activeChat + '/block', {
          method: blocked ? 'DELETE' : 'PUT', headers: authHeaders()
        });
        if (!res.ok) throw new Error('Не удалось изменить блокировку');
        if (blocked) {
          state.blockedUsernames = state.blockedUsernames.filter(u => u !== state.activeChat);
        } else {
          state.blockedUsernames.push(state.activeChat);
        }
        renderContactProfileBlock();
      } catch (err) {
        el('contact-profile-error').textContent = err.message;
      }
    });
  }

  async function loadBlockedUsers() {
    try {
      const res = await fetch('/api/users/me/blocked', { headers: authHeaders() });
      if (!res.ok) return;
      state.blockedUsernames = await res.json();
    } catch (e) { /* ignore */ }
  }

  // ---------- Жалоба на пользователя ----------

  function initReportModal() {
    el('contact-profile-report-btn').addEventListener('click', () => {
      if (!state.activeChat) return;
      state.reportTarget = state.activeChat;
      el('report-reason').value = '';
      el('report-error').textContent = '';
      closeModal('contact-profile-modal');
      openModal('report-modal');
    });
    wireOverlayClose('report-modal', 'report-close-btn');

    el('report-submit-btn').addEventListener('click', async () => {
      const reason = el('report-reason').value.trim();
      el('report-error').textContent = '';
      if (!reason) { el('report-error').textContent = 'Опишите причину жалобы'; return; }
      if (!state.reportTarget) return;
      const res = await fetch('/api/reports', {
        method: 'POST',
        headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
        body: JSON.stringify({ targetUsername: state.reportTarget, reason }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) { el('report-error').textContent = data.error || 'Не удалось отправить жалобу'; return; }
      closeModal('report-modal');
      showToast('Жалоба отправлена');
    });
  }

  // ---------- Панель администратора (единая, с навигацией по разделам) ----------

  const ADMIN_SECTION_LOADERS = {
    dashboard: () => loadAdminDashboard(),
    users: () => loadAdminUsers(el('admin-users-search').value.trim(), 0),
    reports: () => loadAdminReports(),
    broadcast: () => {},
    audit: () => loadAdminAudit(0),
    settings: () => loadAdminSettings(),
  };

  /** Renders Prev/Next + "Страница X из Y" controls into `containerId`, calling `onPage(pageIndex)` on click. */
  function renderPager(containerId, pageData, onPage) {
    const host = el(containerId);
    if (!pageData || pageData.totalPages <= 1) { host.classList.add('hidden'); host.innerHTML = ''; return; }
    host.classList.remove('hidden');
    host.innerHTML = `
      <button type="button" class="admin-pager-prev" ${pageData.page <= 0 ? 'disabled' : ''}>← Назад</button>
      <span class="admin-pager-info">Страница ${pageData.page + 1} из ${pageData.totalPages} · всего ${pageData.totalElements}</span>
      <button type="button" class="admin-pager-next" ${pageData.page >= pageData.totalPages - 1 ? 'disabled' : ''}>Вперёд →</button>
    `;
    host.querySelector('.admin-pager-prev').addEventListener('click', () => onPage(pageData.page - 1));
    host.querySelector('.admin-pager-next').addEventListener('click', () => onPage(pageData.page + 1));
  }

  // Разделы, недоступные роли MODERATOR — модератор занимается только пользователями и жалобами.
  const MODERATOR_HIDDEN_SECTIONS = ['dashboard', 'broadcast', 'audit', 'settings'];

  function applyAdminNavVisibility() {
    const isModeratorOnly = state.role === 'MODERATOR';
    document.querySelectorAll('.admin-nav-item').forEach(btn => {
      const hide = isModeratorOnly && MODERATOR_HIDDEN_SECTIONS.includes(btn.dataset.section);
      btn.classList.toggle('hidden', hide);
    });
  }

  function switchAdminSection(section) {
    document.querySelectorAll('.admin-nav-item').forEach(btn => btn.classList.toggle('active', btn.dataset.section === section));
    document.querySelectorAll('.admin-section').forEach(sec => sec.classList.toggle('hidden', sec.id !== 'admin-section-' + section));
    const loader = ADMIN_SECTION_LOADERS[section];
    if (loader) loader();
  }

  function initAdminPanel() {
    el('admin-panel-btn').addEventListener('click', () => {
      openModal('admin-panel-modal');
      applyAdminNavVisibility();
      switchAdminSection(state.role === 'MODERATOR' ? 'users' : 'dashboard');
      refreshAdminReportsBadge();
    });
    wireOverlayClose('admin-panel-modal', 'admin-panel-close-btn');
    el('admin-nav').addEventListener('click', (e) => {
      const btn = e.target.closest('.admin-nav-item');
      if (btn) switchAdminSection(btn.dataset.section);
    });

    let adminUsersSearchDebounce = null;
    el('admin-users-search').addEventListener('input', (e) => {
      clearTimeout(adminUsersSearchDebounce);
      const term = e.target.value.trim();
      adminUsersSearchDebounce = setTimeout(() => loadAdminUsers(term, 0), 300);
    });
    el('admin-users-bulk-ban-btn').addEventListener('click', async () => {
      const checked = Array.from(document.querySelectorAll('.admin-user-select:checked')).map(cb => cb.dataset.username);
      if (!checked.length) return;
      if (!(await showConfirm('Забанить выбранных пользователей (' + checked.length + ')?'))) return;
      await fetch('/api/users/admin/ban-bulk', {
        method: 'PUT',
        headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
        body: JSON.stringify(checked),
      });
      loadAdminUsers(el('admin-users-search').value.trim(), state.adminUsersPage || 0);
    });

    el('admin-broadcast-send-btn').addEventListener('click', async () => {
      const message = el('admin-broadcast-text').value.trim();
      el('admin-broadcast-error').textContent = '';
      if (!message) { el('admin-broadcast-error').textContent = 'Введите текст объявления'; return; }
      if (!(await showConfirm('Отправить это объявление всем пользователям?\n\n«' + message + '»'))) return;
      const res = await fetch('/api/admin/broadcast', {
        method: 'POST',
        headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
        body: JSON.stringify({ message }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) { el('admin-broadcast-error').textContent = data.error || 'Не удалось отправить'; return; }
      el('admin-broadcast-text').value = '';
      showToast('Объявление отправлено всем пользователям');
    });

    ['admin-setting-registration', 'admin-setting-group-creation'].forEach(id => {
      el(id).addEventListener('change', saveAdminSettings);
    });
    el('admin-setting-maintenance').addEventListener('change', async (e) => {
      if (e.target.checked && !(await showConfirm('Включить режим техобслуживания? Вход в приложение сразу закроется для всех, кроме администраторов.'))) {
        e.target.checked = false;
        return;
      }
      saveAdminSettings();
    });
  }

  // ---- Пользователи ----

  const ROLE_LABELS = { USER: 'Пользователь', MODERATOR: 'Модератор', ADMIN: 'Админ', SUPER_ADMIN: 'Супер-админ' };

  async function loadAdminUsers(term, page) {
    state.adminUsersPage = page || 0;
    state.adminUsersSearch = term || '';
    const params = new URLSearchParams({ page: String(state.adminUsersPage), size: '20' });
    if (term) params.set('search', term);
    const res = await fetch('/api/users/admin/all?' + params.toString(), { headers: authHeaders() });
    const data = await res.json();
    if (!res.ok) { el('admin-users-error').textContent = data.error || 'Ошибка'; return; }
    el('admin-users-error').textContent = '';
    const list = el('admin-users-list');
    list.innerHTML = '';
    let anySelectable = false;
    data.content.forEach(u => {
      const row = document.createElement('div');
      row.className = 'admin-user-row';
      let roleBadgeClass = 'role-badge';
      if (u.role === 'MODERATOR') roleBadgeClass += ' role-badge-moderator';
      if (u.role === 'SUPER_ADMIN') roleBadgeClass += ' role-badge-superadmin';
      const roleBadge = u.role && u.role !== 'USER' ? `<span class="${roleBadgeClass}">${ROLE_LABELS[u.role]}</span>` : '';
      const verifiedBadge = u.verified ? '<span class="role-badge verified-badge" title="Подтверждён">✓</span>' : '';
      const bannedBadge = u.banned ? '<span class="role-badge banned-badge">Забанен</span>' : '';
      const selectable = !u.isAdmin;
      if (selectable) anySelectable = true;
      const canManageRole = state.role === 'SUPER_ADMIN' && u.username !== state.username;
      const roleSelectHtml = canManageRole
        ? `<select class="admin-role-select" title="Роль">
            ${['USER', 'MODERATOR', 'ADMIN', 'SUPER_ADMIN'].map(r => `<option value="${r}" ${r === u.role ? 'selected' : ''}>${ROLE_LABELS[r]}</option>`).join('')}
          </select>`
        : '';
      row.innerHTML = `
        ${selectable ? `<input type="checkbox" class="admin-user-select" data-username="${u.username}">` : '<span style="width:16px;display:inline-block;"></span>'}
        <span class="avatar avatar-sm" style="${avatarInlineStyle(u.avatarUrl, u.displayName)}">${u.avatarUrl ? '' : initials(u.displayName)}</span>
        <div class="admin-user-info">
          <div>${escapeHtml(u.displayName)} ${verifiedBadge}</div>
          <div class="admin-user-sub">${u.online ? 'в сети' : 'не в сети'}</div>
        </div>
        ${roleBadge}${bannedBadge}
        <div class="admin-user-actions">
          ${!u.isAdmin ? `<button type="button" class="admin-user-action-btn admin-user-ban" title="${u.banned ? 'Разбанить' : 'Забанить'}">${u.banned ? 'Разбанить' : 'Забанить'}</button>` : ''}
          ${!u.isAdmin ? `<button type="button" class="admin-user-action-btn admin-user-verify" title="Верификация">${u.verified ? 'Снять галочку' : 'Верифицировать'}</button>` : ''}
          ${roleSelectHtml}
          ${(state.isAdmin && u.online) ? '<button type="button" class="admin-user-action-btn admin-user-kick" title="Отключить сессию">Отключить</button>' : ''}
          ${(state.isAdmin && !u.isAdmin) ? '<button type="button" class="admin-user-del" aria-label="Удалить">✕</button>' : ''}
        </div>
      `;
      if (!u.isAdmin) {
        row.querySelector('.admin-user-ban').addEventListener('click', async () => {
          let url = '/api/users/admin/' + encodeURIComponent(u.username) + '/ban';
          const method = u.banned ? 'DELETE' : 'PUT';
          if (!u.banned) {
            const reason = await showPrompt('Причина блокировки пользователя ' + u.displayName + ' (необязательно):', 'Причина (необязательно)');
            if (reason === null) return; // отменено
            if (reason) url += '?reason=' + encodeURIComponent(reason);
          }
          await fetch(url, { method, headers: authHeaders() });
          loadAdminUsers(state.adminUsersSearch, state.adminUsersPage);
        });
        row.querySelector('.admin-user-verify').addEventListener('click', async () => {
          await fetch('/api/users/admin/' + encodeURIComponent(u.username) + '/verified?value=' + !u.verified, { method: 'PUT', headers: authHeaders() });
          loadAdminUsers(state.adminUsersSearch, state.adminUsersPage);
        });
      }
      const roleSelect = row.querySelector('.admin-role-select');
      if (roleSelect) {
        roleSelect.addEventListener('change', async (e) => {
          const newRole = e.target.value;
          if (!(await showConfirm('Изменить роль пользователя ' + u.displayName + ' на «' + ROLE_LABELS[newRole] + '»?'))) {
            e.target.value = u.role;
            return;
          }
          const res2 = await fetch('/api/users/admin/' + encodeURIComponent(u.username) + '/role?value=' + newRole, { method: 'PUT', headers: authHeaders() });
          const data2 = await res2.json().catch(() => ({}));
          if (!res2.ok) { showToast(data2.error || 'Ошибка'); e.target.value = u.role; return; }
          loadAdminUsers(state.adminUsersSearch, state.adminUsersPage);
        });
      }
      const delBtn = row.querySelector('.admin-user-del');
      if (delBtn) {
        delBtn.addEventListener('click', async () => {
          if (!(await showConfirm('Удалить пользователя ' + u.displayName + '? Это необратимо.'))) return;
          const delRes = await fetch('/api/users/admin/' + encodeURIComponent(u.username), { method: 'DELETE', headers: authHeaders() });
          if (delRes.ok) loadAdminUsers(state.adminUsersSearch, state.adminUsersPage);
        });
      }
      const kickBtn = row.querySelector('.admin-user-kick');
      if (kickBtn) {
        kickBtn.addEventListener('click', async () => {
          await fetch('/api/admin/users/' + encodeURIComponent(u.username) + '/force-logout', { method: 'PUT', headers: authHeaders() });
          showToast('Запрос на выход отправлен');
        });
      }
      list.appendChild(row);
    });
    el('admin-users-bulk-ban-btn').classList.toggle('hidden', !anySelectable);
    renderPager('admin-users-pager', data, (p) => loadAdminUsers(state.adminUsersSearch, p));
  }

  // ---- Жалобы ----

  async function refreshAdminReportsBadge() {
    if (state.role === 'USER') return;
    try {
      const res = await fetch('/api/admin/reports', { headers: authHeaders() });
      if (!res.ok) return;
      const data = await res.json();
      el('admin-reports-badge').classList.toggle('hidden', data.length === 0);
    } catch (e) { /* ignore */ }
  }

  async function loadAdminReports() {
    const res = await fetch('/api/admin/reports', { headers: authHeaders() });
    const data = await res.json();
    if (!res.ok) { el('admin-reports-error').textContent = data.error || 'Ошибка'; return; }
    const host = el('admin-reports-list');
    if (!data.length) { host.innerHTML = '<div class="admin-top-groups-empty">Открытых жалоб нет.</div>'; return; }
    host.innerHTML = '';
    data.forEach(r => {
      const row = document.createElement('div');
      row.className = 'admin-report-row';
      row.innerHTML = `
        <div>
          <p class="admin-report-text">Жалоба на <b>${escapeHtml(r.targetUsername)}</b> — ${escapeHtml(r.reason)}</p>
          <p class="admin-report-sub">от ${escapeHtml(r.reporterUsername)} · ${formatDateTime(r.createdAt)}</p>
        </div>
        <div class="admin-report-actions">
          <button type="button" class="admin-report-dismiss">Отклонить</button>
          <button type="button" class="admin-report-ban">Забанить</button>
        </div>
      `;
      row.querySelector('.admin-report-dismiss').addEventListener('click', async () => {
        await fetch('/api/admin/reports/' + r.id + '/dismiss', { method: 'PUT', headers: authHeaders() });
        loadAdminReports();
        refreshAdminReportsBadge();
      });
      row.querySelector('.admin-report-ban').addEventListener('click', async () => {
        await fetch('/api/admin/reports/' + r.id + '/ban', { method: 'PUT', headers: authHeaders() });
        loadAdminReports();
        refreshAdminReportsBadge();
      });
      host.appendChild(row);
    });
  }

  // ---- Журнал действий ----

  const AUDIT_ACTION_LABELS = {
    BAN_USER: 'забанил(а)', UNBAN_USER: 'разбанил(а)', BAN_USER_BULK: 'забанил(а) (массово)',
    DELETE_USER: 'удалил(а)', VERIFY_USER: 'верифицировал(а)', UNVERIFY_USER: 'снял(а) верификацию',
    DISMISS_REPORT: 'отклонил(а) жалобу на', ACTION_REPORT_BAN: 'забанил(а) по жалобе',
    BROADCAST: 'отправил(а) рассылку', UPDATE_SETTINGS: 'изменил(а) настройки', FORCE_LOGOUT: 'отключил(а) сессию',
    CHANGE_ROLE: 'изменил(а) роль пользователя',
  };

  async function loadAdminAudit(page) {
    state.adminAuditPage = page || 0;
    const res = await fetch('/api/admin/audit-log?page=' + state.adminAuditPage + '&size=50', { headers: authHeaders() });
    const data = await res.json();
    if (!res.ok) { el('admin-audit-error').textContent = data.error || 'Ошибка'; return; }
    el('admin-audit-error').textContent = '';
    const host = el('admin-audit-list');
    if (!data.content.length) {
      host.innerHTML = '<div class="admin-top-groups-empty">Пока нет записей.</div>';
      el('admin-audit-pager').classList.add('hidden');
      return;
    }
    host.innerHTML = data.content.map(a => `
      <div class="admin-audit-row">
        <span><b>${escapeHtml(a.actorUsername)}</b> ${AUDIT_ACTION_LABELS[a.action] || a.action.toLowerCase()} ${a.target ? escapeHtml(a.target) : ''}${a.details ? ' — ' + escapeHtml(a.details) : ''}</span>
        <span class="admin-audit-time">${formatDateTime(a.createdAt)}</span>
      </div>
    `).join('');
    renderPager('admin-audit-pager', data, (p) => loadAdminAudit(p));
  }

  // ---- Настройки платформы ----

  async function loadAdminSettings() {
    const res = await fetch('/api/admin/settings', { headers: authHeaders() });
    const data = await res.json();
    if (!res.ok) { el('admin-settings-error').textContent = data.error || 'Ошибка'; return; }
    el('admin-setting-registration').checked = data.registrationEnabled;
    el('admin-setting-group-creation').checked = data.groupCreationEnabled;
    el('admin-setting-maintenance').checked = data.maintenanceMode;
  }

  async function saveAdminSettings() {
    el('admin-settings-error').textContent = '';
    const res = await fetch('/api/admin/settings', {
      method: 'PUT',
      headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
      body: JSON.stringify({
        registrationEnabled: el('admin-setting-registration').checked,
        groupCreationEnabled: el('admin-setting-group-creation').checked,
        maintenanceMode: el('admin-setting-maintenance').checked,
      }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) { el('admin-settings-error').textContent = data.error || 'Не удалось сохранить'; return; }
    showToast('Настройки сохранены');
  }

  // ---------- Дашборд и аналитика (админ) ----------

  function formatBytes(bytes) {
    if (!bytes) return '0 Б';
    const units = ['Б', 'КБ', 'МБ', 'ГБ'];
    let val = bytes, i = 0;
    while (val >= 1024 && i < units.length - 1) { val /= 1024; i++; }
    return (i === 0 ? val : val.toFixed(1)) + ' ' + units[i];
  }

  function renderAdminMetrics(d) {
    const cards = [
      { label: 'Всего пользователей', value: d.totalUsers },
      { label: 'Онлайн сейчас', value: d.onlineNow },
      { label: 'Групп', value: d.totalGroups },
      { label: 'Новых сегодня', value: d.newUsersToday },
      { label: 'Медиа на диске', value: formatBytes(d.storageBytes) },
    ];
    el('admin-dashboard-metrics').innerHTML = cards.map(c => `
      <div class="admin-metric-card">
        <div class="admin-metric-label">${c.label}</div>
        <div class="admin-metric-value">${c.value}</div>
      </div>
    `).join('');
  }

  function renderAdminChart(trend) {
    const canvas = el('admin-analytics-chart');
    if (state.adminChart) { state.adminChart.destroy(); state.adminChart = null; }
    if (typeof Chart === 'undefined') return;
    const labels = trend.map(p => p.date.slice(5).split('-').reverse().join('.'));
    state.adminChart = new Chart(canvas.getContext('2d'), {
      type: 'line',
      data: {
        labels,
        datasets: [
          { label: 'Сообщения', data: trend.map(p => p.messageCount), borderColor: '#378ADD', backgroundColor: 'rgba(55,138,221,0.08)', fill: true, tension: 0.3, yAxisID: 'y', pointRadius: 0 },
          { label: 'Новые пользователи', data: trend.map(p => p.newUserCount), borderColor: '#D85A30', backgroundColor: 'rgba(216,90,48,0.08)', fill: true, tension: 0.3, yAxisID: 'y1', pointRadius: 0 },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: true, labels: { boxWidth: 10, font: { size: 11 } } } },
        scales: {
          x: { grid: { display: false }, ticks: { font: { size: 10 }, maxTicksLimit: 7 } },
          y: { position: 'left', beginAtZero: true, ticks: { font: { size: 10 } } },
          y1: { position: 'right', beginAtZero: true, grid: { display: false }, ticks: { font: { size: 10 } } },
        },
      },
    });
  }

  function renderTopGroups(groups) {
    const host = el('admin-top-groups');
    if (!groups.length) { host.innerHTML = '<div class="admin-top-groups-empty">Пока нет активности в группах за этот период.</div>'; return; }
    host.innerHTML = groups.map(g => `
      <div class="admin-top-group-row">
        <div class="admin-top-group-name">${escapeHtml(g.groupName)}</div>
        <div class="admin-top-group-stats">${g.messageCount} сообщ. · ${g.memberCount} участ.</div>
      </div>
    `).join('');
  }

  async function loadAdminDashboard() {
    el('admin-dashboard-error').textContent = '';
    try {
      const [dashRes, trendRes, groupsRes] = await Promise.all([
        fetch('/api/admin/dashboard', { headers: authHeaders() }),
        fetch('/api/admin/analytics/trend?days=14', { headers: authHeaders() }),
        fetch('/api/admin/analytics/top-groups?days=7&limit=5', { headers: authHeaders() }),
      ]);
      const [dash, trend, groups] = await Promise.all([dashRes.json(), trendRes.json(), groupsRes.json()]);
      if (!dashRes.ok || !trendRes.ok || !groupsRes.ok) {
        el('admin-dashboard-error').textContent = (dash && dash.error) || 'Не удалось загрузить аналитику';
        return;
      }
      renderAdminMetrics(dash);
      renderAdminChart(trend);
      renderTopGroups(groups);
    } catch (e) {
      el('admin-dashboard-error').textContent = 'Не удалось загрузить аналитику';
    }
  }


  // ---------- Новости (лента постов вместо каналов) ----------

  function showNewsView() {
    leaveGroupSubscription();
    state.activeChat = null;
    state.activeGroup = null;
    showMainPanel('news');
    el('app-screen').classList.add('chat-open');
    closeModal('emoji-modal');
    loadNews();
  }

  async function loadNews() {
    const res = await fetch('/api/news', { headers: authHeaders() });
    if (res.status === 401) return logoutForced();
    state.newsPosts = await res.json();
    renderNews();
  }

  function renderNews() {
    const container = el('news-feed');
    const emptyBanner = el('news-empty');
    container.innerHTML = '';

    if (state.newsPosts.length === 0) {
      emptyBanner.classList.remove('hidden');
      return;
    }
    emptyBanner.classList.add('hidden');

    state.newsPosts.forEach(p => {
      const div = document.createElement('div');
      const isAnnouncement = p.title === 'Объявление';
      div.className = isAnnouncement ? 'news-card news-card-announcement' : 'news-card';
      const canDelete = p.authorUsername === state.username;
      const photo = p.imageUrl ? `<img class="news-card-photo" src="${escapeHtml(p.imageUrl)}" alt="">` : '';
      const titleHtml = isAnnouncement
        ? `<span class="news-announcement-badge">Объявление</span>`
        : escapeHtml(p.title);
      div.innerHTML = `
        <div class="news-card-header">
          <span class="avatar avatar-sm" style="${avatarInlineStyle(p.authorAvatarUrl, p.authorDisplayName)}">${p.authorAvatarUrl ? '' : initials(p.authorDisplayName)}</span>
          <div class="news-card-author">
            <div class="news-card-author-name">${escapeHtml(p.authorDisplayName)}</div>
            <div class="news-card-date">${formatDateTime(p.createdAt)}</div>
          </div>
          ${canDelete ? '<button type="button" class="news-card-delete" aria-label="Удалить">✕</button>' : ''}
        </div>
        <div class="news-card-title">${titleHtml}</div>
        ${photo}
        <div class="news-card-content">${escapeHtml(p.content)}</div>
      `;
      if (canDelete) {
        div.querySelector('.news-card-delete').addEventListener('click', async () => {
          if (!(await showConfirm('Удалить эту запись?'))) return;
          const res = await fetch('/api/news/' + p.id, { method: 'DELETE', headers: authHeaders() });
          if (res.ok) loadNews();
        });
      }
      container.appendChild(div);
    });
  }

  function initNews() {
    el('news-add-btn').addEventListener('click', () => {
      el('news-title-input').value = '';
      el('news-content-input').value = '';
      el('news-photo-input').value = '';
      const preview = el('news-photo-preview');
      preview.classList.add('hidden');
      preview.removeAttribute('src');
      delete preview.dataset.url;
      el('news-error').textContent = '';
      openModal('news-modal');
    });

    wireOverlayClose('news-modal', 'news-close-btn');

    el('news-photo-input').addEventListener('change', async (e) => {
      const file = e.target.files[0];
      if (!file) return;
      el('news-error').textContent = '';
      try {
        const formData = new FormData();
        formData.append('file', file);
        const res = await fetch('/api/media/upload', { method: 'POST', headers: authHeaders(), body: formData });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Не удалось загрузить фото');
        const preview = el('news-photo-preview');
        preview.src = data.url;
        preview.dataset.url = data.url;
        preview.classList.remove('hidden');
      } catch (err) {
        el('news-error').textContent = err.message;
      }
    });

    el('news-create-btn').addEventListener('click', async () => {
      const title = el('news-title-input').value.trim();
      const content = el('news-content-input').value.trim();
      const imageUrl = el('news-photo-preview').dataset.url || null;
      el('news-error').textContent = '';
      if (!title || !content) { el('news-error').textContent = 'Заполните заголовок и текст'; return; }
      try {
        const res = await fetch('/api/news', {
          method: 'POST',
          headers: Object.assign({ 'Content-Type': 'application/json' }, authHeaders()),
          body: JSON.stringify({ title, content, imageUrl })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Не удалось опубликовать');
        closeModal('news-modal');
        await loadNews();
      } catch (err) {
        el('news-error').textContent = err.message;
      }
    });
  }

  // ---------- Звонки (WebRTC 1:1 аудио/видео) ----------

  function showCallOverlay() { el('call-overlay').classList.remove('hidden'); }
  function hideCallOverlay() { el('call-overlay').classList.add('hidden'); }

  function renderCallUI() {
    const c = state.call;
    if (!c) { hideCallOverlay(); return; }
    showCallOverlay();
    setAvatar(el('call-peer-avatar'), c.peerDisplayName || c.peer, c.peerAvatarUrl);
    el('call-peer-name').textContent = c.peerDisplayName || c.peer;

    const statusMap = {
      'outgoing-ringing': 'Вызов...',
      'incoming-ringing': c.video ? 'Входящий видеозвонок' : 'Входящий звонок',
      connecting: 'Соединение...',
      active: c.video ? 'Видеозвонок' : 'Аудиозвонок',
    };
    el('call-status-text').textContent = statusMap[c.status] || '';

    const isIncomingRinging = c.status === 'incoming-ringing';
    el('call-accept-btn').classList.toggle('hidden', !isIncomingRinging);
    el('call-hangup-btn').classList.remove('hidden');
    el('call-mute-btn').classList.toggle('hidden', isIncomingRinging);
    el('call-camera-btn').classList.toggle('hidden', isIncomingRinging || !c.video);
    el('call-screenshare-btn').classList.toggle('hidden', isIncomingRinging || !c.video);
    el('call-screenshare-btn').classList.toggle('active', !!c.isScreenSharing);
    el('call-fullscreen-btn').classList.toggle('hidden', isIncomingRinging);

    el('call-local-video').classList.toggle('hidden', !c.video || !c.localStream);
    const showRemoteVideo = c.video && c.remoteHasVideo;
    el('call-remote-video').classList.toggle('hidden', !showRemoteVideo);
    el('call-avatar-view').classList.toggle('hidden', showRemoteVideo);
  }

  function sendCallSignal(payload) {
    if (!state.stompClient || !state.stompClient.connected) return;
    state.stompClient.publish({ destination: '/app/call.signal', body: JSON.stringify(payload) });
  }

  function createPeerConnection(peer, callId) {
    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    pc.onicecandidate = (e) => {
      if (e.candidate) {
        sendCallSignal({ callId, recipientUsername: peer, signalType: 'ice-candidate', candidate: JSON.stringify(e.candidate) });
      }
    };
    pc.ontrack = (e) => {
      const remoteVideoEl = el('call-remote-video');
      if (remoteVideoEl.srcObject !== e.streams[0]) remoteVideoEl.srcObject = e.streams[0];
      if (state.call && state.call.callId === callId) {
        if (e.track.kind === 'video') state.call.remoteHasVideo = true;
        renderCallUI();
      }
    };
    pc.onconnectionstatechange = () => {
      if (!state.call || state.call.callId !== callId) return;
      if (pc.connectionState === 'connected') {
        clearTimeout(state.call.ringTimeout);
        state.call.status = 'active';
        renderCallUI();
      } else if (pc.connectionState === 'failed' || pc.connectionState === 'disconnected' || pc.connectionState === 'closed') {
        if (state.call && state.call.callId === callId && state.call.status !== 'ending') endCall('connection-lost');
      }
    };
    return pc;
  }

  async function flushPendingCandidates() {
    if (!state.call || !state.call.pc) return;
    const list = state.call.pendingCandidates || [];
    state.call.pendingCandidates = [];
    for (const c of list) {
      try { await state.call.pc.addIceCandidate(c); } catch (e) { /* ignore stale candidates */ }
    }
  }

  async function startCall(video) {
    if (state.call) return; // already in (or ringing for) a call
    if (!state.activeChat || state.activeGroup) return; // 1:1 only
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      showToast('Звонки не поддерживаются в этом браузере');
      return;
    }
    const peer = state.activeChat;
    const contact = state.contacts.find(c => c.username === peer);

    let localStream;
    try {
      localStream = await navigator.mediaDevices.getUserMedia({ audio: true, video });
    } catch (e) {
      showToast('Не удалось получить доступ к микрофону' + (video ? '/камере' : ''));
      return;
    }

    const callId = uid();
    const pc = createPeerConnection(peer, callId);
    localStream.getTracks().forEach(track => pc.addTrack(track, localStream));

    state.call = {
      callId, peer,
      peerDisplayName: contact ? contact.displayName : peer,
      peerAvatarUrl: contact ? contact.avatarUrl : null,
      video, pc, localStream,
      direction: 'outgoing', status: 'outgoing-ringing',
      pendingCandidates: [], remoteHasVideo: false,
    };
    el('call-local-video').srcObject = localStream;
    renderCallUI();

    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    sendCallSignal({ callId, recipientUsername: peer, signalType: 'offer', sdp: offer.sdp, video });

    state.call.ringTimeout = setTimeout(() => {
      if (state.call && state.call.callId === callId && state.call.status === 'outgoing-ringing') {
        endCall('no-answer');
      }
    }, 30000);
  }

  async function acceptCall() {
    if (!state.call || state.call.status !== 'incoming-ringing') return;
    let localStream;
    try {
      localStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: state.call.video });
    } catch (e) {
      declineCall();
      return;
    }
    const pc = createPeerConnection(state.call.peer, state.call.callId);
    localStream.getTracks().forEach(track => pc.addTrack(track, localStream));
    state.call.pc = pc;
    state.call.localStream = localStream;
    state.call.status = 'connecting';
    el('call-local-video').srcObject = localStream;
    renderCallUI();

    await pc.setRemoteDescription({ type: 'offer', sdp: state.call.offerSdp });
    await flushPendingCandidates();
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    sendCallSignal({ callId: state.call.callId, recipientUsername: state.call.peer, signalType: 'answer', sdp: answer.sdp });
  }

  function declineCall() {
    if (!state.call) return;
    sendCallSignal({ callId: state.call.callId, recipientUsername: state.call.peer, signalType: 'reject' });
    cleanupCall();
  }

  function endCall() {
    if (!state.call) return;
    sendCallSignal({ callId: state.call.callId, recipientUsername: state.call.peer, signalType: 'hangup' });
    cleanupCall();
  }

  function cleanupCall() {
    const c = state.call;
    if (!c) return;
    c.status = 'ending';
    clearTimeout(c.ringTimeout);
    if (c.pc) { try { c.pc.close(); } catch (e) { /* already closed */ } }
    if (c.localStream) c.localStream.getTracks().forEach(t => t.stop());
    if (c.screenStream) c.screenStream.getTracks().forEach(t => t.stop());
    el('call-local-video').srcObject = null;
    el('call-remote-video').srcObject = null;
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    state.call = null;
    hideCallOverlay();
  }

  /** Swaps the outgoing video track for a captured screen/tab/window, and back again — the
   *  receiving peer sees this transparently since it's the same video sender, just a new track. */
  async function toggleScreenShare() {
    const c = state.call;
    if (!c || !c.pc || !c.video) return;

    if (c.isScreenSharing) {
      stopScreenShare();
      return;
    }

    if (!navigator.mediaDevices || !navigator.mediaDevices.getDisplayMedia) {
      showToast('Демонстрация экрана не поддерживается в этом браузере');
      return;
    }

    let screenStream;
    try {
      screenStream = await navigator.mediaDevices.getDisplayMedia({ video: true });
    } catch (e) {
      return; // user cancelled the picker
    }

    const screenTrack = screenStream.getVideoTracks()[0];
    const sender = c.pc.getSenders().find(s => s.track && s.track.kind === 'video');
    if (sender) await sender.replaceTrack(screenTrack);

    c.screenStream = screenStream;
    c.isScreenSharing = true;
    el('call-local-video').srcObject = screenStream;

    // The browser's own "Stop sharing" bar can end this track without going through our button.
    screenTrack.onended = () => { if (state.call && state.call.isScreenSharing) stopScreenShare(); };

    renderCallUI();
  }

  async function stopScreenShare() {
    const c = state.call;
    if (!c || !c.isScreenSharing) return;
    if (c.screenStream) c.screenStream.getTracks().forEach(t => t.stop());
    c.screenStream = null;
    c.isScreenSharing = false;

    const cameraTrack = c.localStream && c.localStream.getVideoTracks()[0];
    const videoSender = c.pc && c.pc.getSenders().find(s => s.track && s.track.kind === 'video');
    if (cameraTrack && videoSender) {
      try { await videoSender.replaceTrack(cameraTrack); } catch (e) { /* ignore */ }
    }
    el('call-local-video').srcObject = c.localStream || null;
    renderCallUI();
  }

  function toggleCallFullscreen() {
    const card = el('call-overlay');
    if (!document.fullscreenElement) {
      card.requestFullscreen().catch(() => {});
    } else {
      document.exitFullscreen().catch(() => {});
    }
  }

  document.addEventListener('fullscreenchange', () => {
    el('call-fullscreen-btn').classList.toggle('active', !!document.fullscreenElement);
  });

  async function handleCallSignal(m) {
    if (m.groupId) { handleGroupCallSignal(m); return; }
    if (!m.senderUsername || m.senderUsername === state.username) return; // echo of our own signal

    if (m.signalType === 'offer') {
      if (state.call) {
        sendCallSignal({ callId: m.callId, recipientUsername: m.senderUsername, signalType: 'busy' });
        return;
      }
      const contact = state.contacts.find(c => c.username === m.senderUsername);
      state.call = {
        callId: m.callId, peer: m.senderUsername,
        peerDisplayName: m.senderDisplayName || m.senderUsername,
        peerAvatarUrl: contact ? contact.avatarUrl : null,
        video: !!m.video, pc: null, localStream: null,
        direction: 'incoming', status: 'incoming-ringing',
        pendingCandidates: [], remoteHasVideo: false, offerSdp: m.sdp,
      };
      renderCallUI();
      notifyIncomingCall(state.call);
      return;
    }

    if (!state.call || state.call.callId !== m.callId) return;

    if (m.signalType === 'answer') {
      if (!state.call.pc) return;
      await state.call.pc.setRemoteDescription({ type: 'answer', sdp: m.sdp });
      await flushPendingCandidates();
    } else if (m.signalType === 'ice-candidate') {
      const candidate = JSON.parse(m.candidate);
      if (state.call.pc && state.call.pc.remoteDescription) {
        try { await state.call.pc.addIceCandidate(candidate); } catch (e) { /* ignore */ }
      } else {
        state.call.pendingCandidates.push(candidate);
      }
    } else if (m.signalType === 'hangup' || m.signalType === 'reject' || m.signalType === 'busy') {
      cleanupCall();
    }
  }

  function notifyIncomingCall(call) {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    if (!document.hidden) return;
    try {
      const n = new Notification((call.peerDisplayName || call.peer) + ' звонит', {
        body: call.video ? 'Видеозвонок' : 'Аудиозвонок',
        icon: 'icon-192.png',
        tag: 'call-' + call.callId,
      });
      n.onclick = () => { window.focus(); n.close(); };
    } catch (e) { /* ignore */ }
  }

  function initCalls() {
    el('call-audio-btn').addEventListener('click', () => startCall(false));
    el('call-video-btn').addEventListener('click', () => startCall(true));
    el('call-group-video-btn').addEventListener('click', () => startGroupCall(true));
    el('call-accept-btn').addEventListener('click', acceptCall);
    el('call-hangup-btn').addEventListener('click', () => {
      if (state.groupCall) { endGroupCall(); return; }
      if (state.call && state.call.status === 'incoming-ringing') declineCall();
      else endCall();
    });
    el('call-mute-btn').addEventListener('click', () => {
      const stream = state.groupCall ? state.groupCall.localStream : (state.call && state.call.localStream);
      if (!stream) return;
      const tracks = stream.getAudioTracks();
      const nextEnabled = !(tracks[0] && tracks[0].enabled);
      tracks.forEach(t => { t.enabled = nextEnabled; });
      el('call-mute-btn').classList.toggle('muted', !nextEnabled);
    });
    el('call-camera-btn').addEventListener('click', () => {
      const stream = state.groupCall ? state.groupCall.localStream : (state.call && state.call.localStream);
      if (!stream) return;
      const tracks = stream.getVideoTracks();
      const nextEnabled = !(tracks[0] && tracks[0].enabled);
      tracks.forEach(t => { t.enabled = nextEnabled; });
      el('call-camera-btn').classList.toggle('muted', !nextEnabled);
    });
    el('call-screenshare-btn').addEventListener('click', toggleScreenShare);
    el('call-fullscreen-btn').addEventListener('click', toggleCallFullscreen);
  }

  // ---------- Групповые видеозвонки (WebRTC mesh) ----------
  //
  // Whoever is already in the call answers a "join" broadcast with a direct offer to the
  // newcomer, so a full mesh forms without a central SFU. Signaling reuses /app/call.signal;
  // messages with groupId set and no recipientUsername are broadcast to the whole group topic
  // (join/leave), everything else (offer/answer/ice-candidate) targets one peer's private queue.

  function startGroupCall(video) {
    if (state.groupCall || !state.activeGroup) return;
    if (state.call) return; // don't overlap with a 1:1 call
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      showToast('Звонки не поддерживаются в этом браузере');
      return;
    }
    navigator.mediaDevices.getUserMedia({ audio: true, video }).then(localStream => {
      state.groupCall = { callId: uid(), groupId: state.activeGroup.id, video, localStream, peers: new Map() };
      renderGroupCallUI();
      sendCallSignal({ callId: state.groupCall.callId, groupId: state.groupCall.groupId, signalType: 'join', video });
    }).catch(() => showToast('Не удалось получить доступ к микрофону' + (video ? '/камере' : '')));
  }

  function createGroupPeerConnection(peerUsername) {
    const gc = state.groupCall;
    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    pc.onicecandidate = (e) => {
      if (e.candidate) {
        sendCallSignal({ callId: gc.callId, groupId: gc.groupId, recipientUsername: peerUsername, signalType: 'ice-candidate', candidate: JSON.stringify(e.candidate) });
      }
    };
    pc.ontrack = (e) => {
      const peer = state.groupCall && state.groupCall.peers.get(peerUsername);
      if (peer) { peer.stream = e.streams[0]; renderGroupCallUI(); }
    };
    if (gc.localStream) gc.localStream.getTracks().forEach(t => pc.addTrack(t, gc.localStream));
    return pc;
  }

  async function handleGroupCallSignal(m) {
    if (!m.senderUsername || m.senderUsername === state.username || !m.groupId) return;
    const gc = state.groupCall;

    if (m.signalType === 'join') {
      if (!gc || String(gc.groupId) !== String(m.groupId) || gc.peers.has(m.senderUsername)) return;
      const pc = createGroupPeerConnection(m.senderUsername);
      gc.peers.set(m.senderUsername, { pc, stream: null, displayName: m.senderDisplayName || m.senderUsername });
      renderGroupCallUI();
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      sendCallSignal({ callId: gc.callId, groupId: m.groupId, recipientUsername: m.senderUsername, signalType: 'offer', sdp: offer.sdp, video: gc.video });
      return;
    }

    if (m.signalType === 'leave' || m.signalType === 'hangup') {
      if (!gc) return;
      const peer = gc.peers.get(m.senderUsername);
      if (peer) { try { peer.pc.close(); } catch (e) { /* already closed */ } gc.peers.delete(m.senderUsername); renderGroupCallUI(); }
      return;
    }

    if (!gc) return;

    if (m.signalType === 'offer') {
      let peer = gc.peers.get(m.senderUsername);
      if (!peer) {
        const pc = createGroupPeerConnection(m.senderUsername);
        peer = { pc, stream: null, displayName: m.senderDisplayName || m.senderUsername };
        gc.peers.set(m.senderUsername, peer);
      }
      await peer.pc.setRemoteDescription({ type: 'offer', sdp: m.sdp });
      const answer = await peer.pc.createAnswer();
      await peer.pc.setLocalDescription(answer);
      sendCallSignal({ callId: gc.callId, groupId: m.groupId, recipientUsername: m.senderUsername, signalType: 'answer', sdp: answer.sdp });
      renderGroupCallUI();
    } else if (m.signalType === 'answer') {
      const peer = gc.peers.get(m.senderUsername);
      if (peer) await peer.pc.setRemoteDescription({ type: 'answer', sdp: m.sdp });
    } else if (m.signalType === 'ice-candidate') {
      const peer = gc.peers.get(m.senderUsername);
      if (peer) { try { await peer.pc.addIceCandidate(JSON.parse(m.candidate)); } catch (e) { /* ignore */ } }
    }
  }

  function renderGroupCallUI() {
    const gc = state.groupCall;
    if (!gc) { hideCallOverlay(); return; }
    showCallOverlay();
    el('call-remote-video').classList.add('hidden');
    el('call-local-video').classList.add('hidden');
    el('call-avatar-view').classList.add('hidden');
    const grid = el('call-group-grid');
    grid.classList.remove('hidden');
    grid.innerHTML = '';

    const localTile = document.createElement('div');
    localTile.className = 'call-group-tile';
    const localVideo = document.createElement('video');
    localVideo.autoplay = true; localVideo.playsInline = true; localVideo.muted = true;
    localVideo.srcObject = gc.localStream;
    localTile.appendChild(localVideo);
    localTile.insertAdjacentHTML('beforeend', '<span class="call-group-tile-name">Вы</span>');
    grid.appendChild(localTile);

    gc.peers.forEach((peer) => {
      const tile = document.createElement('div');
      tile.className = 'call-group-tile';
      if (peer.stream) {
        const v = document.createElement('video');
        v.autoplay = true; v.playsInline = true;
        v.srcObject = peer.stream;
        tile.appendChild(v);
      }
      const label = document.createElement('span');
      label.className = 'call-group-tile-name';
      label.textContent = peer.displayName;
      tile.appendChild(label);
      grid.appendChild(tile);
    });

    el('call-peer-name').textContent = state.activeGroup ? state.activeGroup.name : 'Групповой звонок';
    el('call-status-text').textContent = 'Групповой звонок · ' + (gc.peers.size + 1) + ' участник(а)';
    el('call-accept-btn').classList.add('hidden');
    el('call-hangup-btn').classList.remove('hidden');
    el('call-mute-btn').classList.remove('hidden');
    el('call-camera-btn').classList.toggle('hidden', !gc.video);
    el('call-screenshare-btn').classList.add('hidden');
    el('call-fullscreen-btn').classList.remove('hidden');
  }

  function endGroupCall() {
    const gc = state.groupCall;
    if (!gc) return;
    sendCallSignal({ callId: gc.callId, groupId: gc.groupId, signalType: 'leave' });
    gc.peers.forEach(peer => { try { peer.pc.close(); } catch (e) { /* already closed */ } });
    if (gc.localStream) gc.localStream.getTracks().forEach(t => t.stop());
    state.groupCall = null;
    el('call-group-grid').classList.add('hidden');
    hideCallOverlay();
  }

  // ---------- Reply / reactions / mentions wiring ----------

  function initChatExtras() {
    el('reply-preview-cancel').addEventListener('click', clearReplyTarget);

    el('self-destruct-btn').addEventListener('click', () => {
      const idx = SELF_DESTRUCT_OPTIONS.indexOf(state.selfDestructSeconds);
      state.selfDestructSeconds = SELF_DESTRUCT_OPTIONS[(idx + 1) % SELF_DESTRUCT_OPTIONS.length];
      const badge = el('self-destruct-badge');
      const btn = el('self-destruct-btn');
      if (state.selfDestructSeconds) {
        badge.textContent = state.selfDestructSeconds + 'с';
        badge.classList.remove('hidden');
        btn.classList.add('active');
        btn.title = 'Сообщения исчезнут через ' + state.selfDestructSeconds + ' сек.';
      } else {
        badge.classList.add('hidden');
        btn.classList.remove('active');
        btn.title = 'Самоуничтожение сообщений';
      }
    });

    el('messages').addEventListener('click', (e) => {
      const menuBtn = e.target.closest('.msg-menu-btn');
      if (menuBtn) {
        e.stopPropagation();
        const wrap = menuBtn.closest('.msg-wrap');
        if (wrap && wrap.dataset.clientId) openMessageMenu(wrap, wrap.dataset.clientId);
        return;
      }
      const pinBtn = e.target.closest('.msg-pin-btn');
      if (pinBtn) {
        const wrap = pinBtn.closest('.msg-wrap');
        if (wrap && wrap.dataset.clientId) {
          const info = state.recentMessages[wrap.dataset.clientId];
          sendPin(wrap.dataset.clientId, wrap.dataset.senderName || (info && info.sender) || '', (info && info.snippet) || '');
        }
      }
    });

    el('pinned-banner-clear').addEventListener('click', sendUnpin);

    el('location-btn').addEventListener('click', async () => {
      if (!state.activeChat && !state.activeGroup) return;
      if (!navigator.geolocation) { showToast('Геолокация не поддерживается в этом браузере'); return; }
      const btn = el('location-btn');

      // Check the permission state up front where supported, so a previously-denied
      // permission surfaces a clear, actionable message instead of just silently timing out.
      if (navigator.permissions && navigator.permissions.query) {
        try {
          const status = await navigator.permissions.query({ name: 'geolocation' });
          if (status.state === 'denied') {
            showToast('Доступ к геолокации запрещён. Разрешите его в настройках браузера (значок замка рядом с адресом) или в системных настройках.');
            return;
          }
        } catch (e) { /* Permissions API not supported for geolocation in this browser — fall through */ }
      }

      btn.classList.add('active');
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          btn.classList.remove('active');
          sendChat({ type: 'LOCATION', content: '', lat: pos.coords.latitude, lng: pos.coords.longitude });
        },
        (err) => {
          btn.classList.remove('active');
          const messages = {
            1: 'Доступ к геолокации запрещён. Разрешите его в настройках браузера или системы.',
            2: 'Не удалось определить местоположение. Проверьте, что геолокация включена в системе.',
            3: 'Превышено время ожидания геопозиции. Попробуйте ещё раз.',
          };
          showToast(messages[err.code] || 'Не удалось получить геопозицию');
        },
        { timeout: 10000, maximumAge: 0 }
      );
    });

    // ---- @mention / slash-command suggestion boxes ----
    const input = el('message-input');
    input.addEventListener('click', updateComposerSuggestions);
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') { closeMentionSuggest(); closeSlashSuggest(); }
    });
    input.addEventListener('blur', () => setTimeout(() => { closeMentionSuggest(); closeSlashSuggest(); }, 150));
  }

  // ---------- Поиск по открытому чату (только среди уже отрисованных сообщений) ----------

  function closeChatSearch() {
    el('chat-search-bar').classList.add('hidden');
    el('chat-search-input').value = '';
    clearSearchHighlights();
  }

  function clearSearchHighlights() {
    document.querySelectorAll('#messages mark.search-mark').forEach(m => {
      const parent = m.parentNode;
      parent.replaceChild(document.createTextNode(m.textContent), m);
      parent.normalize();
    });
    document.querySelectorAll('#messages .msg-search-hit').forEach(w => w.classList.remove('msg-search-hit', 'msg-search-hit-active'));
    state.searchHits = [];
    state.searchHitIndex = -1;
  }

  function runChatSearch(term) {
    clearSearchHighlights();
    const countEl = el('chat-search-count');
    if (!term) { countEl.textContent = ''; return; }
    const lower = term.toLowerCase();
    const hits = [];
    document.querySelectorAll('#messages .msg-wrap').forEach(wrap => {
      const textDiv = wrap.querySelector('.msg > div:not(.meta):not(.msg-reply-quote)');
      if (!textDiv) return;
      const text = textDiv.textContent || '';
      const idx = text.toLowerCase().indexOf(lower);
      if (idx === -1) return;
      const before = text.slice(0, idx), match = text.slice(idx, idx + term.length), after = text.slice(idx + term.length);
      textDiv.innerHTML = escapeHtml(before) + '<mark class="search-mark">' + escapeHtml(match) + '</mark>' + escapeHtml(after);
      wrap.classList.add('msg-search-hit');
      hits.push(wrap);
    });
    state.searchHits = hits;
    state.searchHitIndex = hits.length ? 0 : -1;
    countEl.textContent = hits.length ? (state.searchHitIndex + 1) + '/' + hits.length : 'нет совпадений';
    focusSearchHit();
  }

  function focusSearchHit() {
    document.querySelectorAll('.msg-search-hit-active').forEach(w => w.classList.remove('msg-search-hit-active'));
    const hits = state.searchHits || [];
    if (state.searchHitIndex < 0 || state.searchHitIndex >= hits.length) return;
    const wrap = hits[state.searchHitIndex];
    wrap.classList.add('msg-search-hit-active');
    wrap.scrollIntoView({ block: 'center', behavior: 'smooth' });
    el('chat-search-count').textContent = (state.searchHitIndex + 1) + '/' + hits.length;
  }

  function initChatSearch() {
    el('chat-search-btn').addEventListener('click', () => {
      const bar = el('chat-search-bar');
      bar.classList.toggle('hidden');
      if (!bar.classList.contains('hidden')) el('chat-search-input').focus();
      else closeChatSearch();
    });
    el('chat-search-close').addEventListener('click', closeChatSearch);
    el('chat-search-input').addEventListener('input', (e) => runChatSearch(e.target.value.trim()));
    el('chat-search-next').addEventListener('click', () => {
      const hits = state.searchHits || [];
      if (!hits.length) return;
      state.searchHitIndex = (state.searchHitIndex + 1) % hits.length;
      focusSearchHit();
    });
    el('chat-search-prev').addEventListener('click', () => {
      const hits = state.searchHits || [];
      if (!hits.length) return;
      state.searchHitIndex = (state.searchHitIndex - 1 + hits.length) % hits.length;
      focusSearchHit();
    });
  }

  // ---------- Boot ----------
  registerServiceWorker();
  // Ссылка сброса пароля всегда приоритетнее автовхода — даже если в этом браузере уже
  // есть валидный токен от прошлой сессии, форму нового пароля нужно показать в первую очередь.
  if (!resetPasswordToken && state.token && state.username) {
    startApp();
  }
})();
