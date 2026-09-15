(() => {
  const STORAGE_KEY = 'gsb.ui.theme.v10';
  const THEMES = new Set(['console', 'handheld']);
  const root = document.documentElement;
  const home = document.querySelector('.homeScene');
  const stage = document.querySelector('.stage');
  const status = document.querySelector('.status');
  const systemPanel = document.querySelector('.systemScene .rightPanel');
  const brand = document.querySelector('.brand');
  let dockFocus = -1;
  let toastTimer = 0;

  const icons = Object.freeze({
    library:'<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="3.5" width="7" height="7" rx="1.4"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.4"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.4"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.4"/></svg>',
    esports:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 9h8a4 4 0 0 1 3.7 2.5l1.1 2.8a3.2 3.2 0 0 1-5 3.6L14 16.5h-4L8.2 18a3.2 3.2 0 0 1-5-3.6l1.1-2.8A4 4 0 0 1 8 9Z"/><path d="M8 12v3M6.5 13.5h3M16.8 13.1h.1M18.5 14.5h.1"/><path d="M9 9 8 6.5M15 9l1-2.5"/></svg>',
    album:'<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2.2"/><circle cx="8.3" cy="9" r="1.5"/><path d="m5.5 17 4.2-4.2 3 3 2.2-2.2 3.6 3.4"/></svg>',
    controller:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7.3 7.5h9.4A4.3 4.3 0 0 1 21 11.8v3a3 3 0 0 1-5 2.2l-1.8-1.7H9.8L8 17a3 3 0 0 1-5-2.2v-3a4.3 4.3 0 0 1 4.3-4.3Z"/><path d="M7.5 10.4v4M5.5 12.4h4M16.2 11.5h.1M18.2 13.3h.1"/></svg>',
    quick:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16"/><circle cx="8" cy="6" r="1.7"/><circle cx="15.5" cy="12" r="1.7"/><circle cx="10.5" cy="18" r="1.7"/></svg>',
    theme:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3.2a8.8 8.8 0 1 0 0 17.6c1.3 0 2.1-.8 2.1-1.8 0-.5-.2-1-.5-1.4-.4-.5-.1-1.3.6-1.3h1.9A4.7 4.7 0 0 0 20.8 12 8.8 8.8 0 0 0 12 3.2Z"/><circle cx="7.7" cy="10" r=".7"/><circle cx="10" cy="7" r=".7"/><circle cx="14" cy="7.3" r=".7"/><circle cx="16.4" cy="10.3" r=".7"/></svg>',
    settings:'<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3.2"/><path d="M19.1 13.3v-2.6l-2-.7a7.6 7.6 0 0 0-.8-1.8l.9-1.9-1.9-1.9-1.9.9a7.6 7.6 0 0 0-1.8-.8l-.7-2H8.7l-.7 2a7.6 7.6 0 0 0-1.8.8l-1.9-.9-1.9 1.9.9 1.9a7.6 7.6 0 0 0-.8 1.8l-2 .7v2.6l2 .7a7.6 7.6 0 0 0 .8 1.8l-.9 1.9 1.9 1.9 1.9-.9a7.6 7.6 0 0 0 1.8.8l.7 2h2.6l.7-2a7.6 7.6 0 0 0 1.8-.8l1.9.9 1.9-1.9-.9-1.9a7.6 7.6 0 0 0 .8-1.8l2-.7Z" transform="scale(.82) translate(2.6 2.6)"/></svg>',
    sleep:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18.4 15.7A7.8 7.8 0 0 1 8.3 5.6 8.3 8.3 0 1 0 18.4 15.7Z"/></svg>'
  });

  function readTheme() {
    try {
      const current = localStorage.getItem(STORAGE_KEY);
      if (THEMES.has(current)) return current;
    } catch (_) {}
    return 'handheld';
  }

  function buzz(cue = 'tick') {
    try { if (window.GSBNative && GSBNative.haptic) GSBNative.haptic(cue); } catch (_) {}
  }

  function showScene(name) {
    const tab = document.querySelector(`.tab[data-scene="${name}"]`);
    if (tab) { tab.click(); return; }
    try { if (typeof window.showScene === 'function') window.showScene(name); } catch (_) {}
  }

  function quickMenu() {
    try {
      if (typeof window.openQuick === 'function') window.openQuick();
      else document.getElementById('overlay')?.classList.add('open');
    } catch (_) {}
  }

  function toast(message) {
    let el = document.getElementById('hhToast');
    if (!el) {
      el = document.createElement('div');
      el.id = 'hhToast';
      el.className = 'hhToast';
      document.body.appendChild(el);
    }
    el.textContent = message;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), 1500);
  }

  function activeSceneName() { return document.querySelector('.scene.active')?.dataset?.scene || 'home'; }
  function selectedCard() { return document.querySelector('#carousel .gameCard.active'); }
  function selectedLaunchButton() { return selectedCard()?.querySelector('.playBtn') || null; }

  function scrollFocusedCard() {
    if (root.dataset.gsbTheme !== 'handheld' || activeSceneName() !== 'home') return;
    const card = selectedCard();
    if (!card) return;
    try { card.scrollIntoView({ behavior:'smooth', inline:'center', block:'nearest' }); } catch (_) {}
  }

  function ensureSelectedTitle() {
    if (!home || document.getElementById('hhSelectedTitle')) return;
    const el = document.createElement('div');
    el.className = 'hhSelectedTitle';
    el.id = 'hhSelectedTitle';
    home.appendChild(el);
  }

  function syncSelectedTitle() {
    const out = document.getElementById('hhSelectedTitle');
    if (!out) return;
    const card = selectedCard();
    const title = card?.querySelector('.cardInfo b')?.textContent?.trim() || 'Game Star Box';
    const running = card?.dataset?.session === 'RUNNING' || card?.dataset?.session === 'SUSPENDED';
    out.innerHTML = `<small>${running ? '●' : '◆'}</small>${title}`;
    scrollFocusedCard();
  }

  function ensureClock() {
    if (!status || document.getElementById('hhClock')) return;
    const clock = document.createElement('span');
    clock.id = 'hhClock';
    clock.className = 'hhClock';
    status.insertBefore(clock, status.firstChild);
    const tick = () => {
      const d = new Date();
      clock.textContent = `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
    };
    tick();
    setInterval(tick, 15000);
  }

  function ensureThemeToggle() {
    if (!status || document.getElementById('gsbThemeToggle')) return;
    const button = document.createElement('button');
    button.id = 'gsbThemeToggle';
    button.className = 'gsbThemeToggle';
    button.dataset.noSwipe = '1';
    button.onclick = () => toggleTheme();
    status.appendChild(button);
  }

  function ensureSystemSetting() {
    if (!systemPanel || document.getElementById('gsbThemeSetting')) return;
    const row = document.createElement('div');
    row.className = 'setting';
    row.id = 'gsbThemeSetting';
    row.innerHTML = '<div><b>主界面主题</b><small id="gsbThemeSettingLabel">掌机 / 主机布局</small></div><button class="switch" id="gsbThemeSettingButton">HANDHELD</button>';
    systemPanel.appendChild(row);
    row.querySelector('button').onclick = () => toggleTheme();
  }

  const dockItems = [
    ['library','游戏库', () => showScene('library')],
    ['esports','电竞', () => showScene('esports')],
    ['album','截图', () => toast('截图继续使用 Android / REDMAGIC 系统能力')],
    ['controller','控制器', () => showScene('system')],
    ['quick','控制台', () => quickMenu()],
    ['theme','主题', () => toggleTheme(), 'theme'],
    ['settings','系统', () => showScene('system')],
    ['sleep','休眠', () => toast('休眠继续交给 Android / REDMAGIC 系统管理')]
  ];

  function ensureDock() {
    if (!home || document.getElementById('hhDock')) return;
    const dock = document.createElement('div');
    dock.id = 'hhDock';
    dock.className = 'hhDock';
    dock.dataset.noSwipe = '1';
    for (const [icon, label, action, extra] of dockItems) {
      const button = document.createElement('button');
      button.className = 'hhDockBtn' + (extra ? ` ${extra}` : '');
      button.type = 'button';
      button.dataset.label = label;
      button.setAttribute('aria-label', label);
      button.innerHTML = icons[icon] || '';
      button.onclick = () => {
        dockFocus = [...dock.children].indexOf(button);
        action();
        buzz('tick');
        syncDockFocus();
      };
      dock.appendChild(button);
    }
    home.appendChild(dock);
  }

  function syncDockFocus() {
    const buttons = [...document.querySelectorAll('.hhDockBtn')];
    buttons.forEach((b,i) => b.classList.toggle('active', i === dockFocus));
  }

  function syncLabels(theme) {
    const top = document.getElementById('gsbThemeToggle');
    if (top) top.textContent = theme === 'handheld' ? 'HANDHELD' : 'CONSOLE';
    const button = document.getElementById('gsbThemeSettingButton');
    if (button) {
      button.textContent = theme === 'handheld' ? 'HANDHELD' : 'CONSOLE';
      button.classList.toggle('on', theme === 'handheld');
    }
    const label = document.getElementById('gsbThemeSettingLabel');
    if (label) label.textContent = theme === 'handheld'
      ? '应用图标直出 · 系统功能带 · 掌机优先'
      : '大幅卡片 · 多层信息 · 大屏优先';
  }

  function syncHomeChrome() {
    const isHandheldHome = root.dataset.gsbTheme === 'handheld' && activeSceneName() === 'home';
    const title = document.getElementById('hhSelectedTitle');
    const dock = document.getElementById('hhDock');
    if (title) title.hidden = !isHandheldHome;
    if (dock) dock.hidden = !isHandheldHome;
    if (!isHandheldHome) { dockFocus = -1; syncDockFocus(); }
  }

  function applyTheme(theme, persist = true) {
    if (!THEMES.has(theme)) theme = 'console';
    root.dataset.gsbTheme = theme;
    if (persist) {
      try { localStorage.setItem(STORAGE_KEY, theme); } catch (_) {}
    }
    dockFocus = -1;
    syncDockFocus();
    syncLabels(theme);
    syncSelectedTitle();
    syncHomeChrome();
    try { document.dispatchEvent(new CustomEvent('gsb-theme-change', { detail:{ theme } })); } catch (_) {}
    buzz('tick');
  }

  function toggleTheme() { applyTheme(root.dataset.gsbTheme === 'handheld' ? 'console' : 'handheld'); }
  function launchSelected() { selectedLaunchButton()?.click(); }

  ensureSelectedTitle();
  ensureClock();
  ensureThemeToggle();
  ensureSystemSetting();
  ensureDock();
  applyTheme(readTheme(), false);

  const carousel = document.getElementById('carousel');
  if (carousel && window.MutationObserver) {
    new MutationObserver(() => { syncSelectedTitle(); syncHomeChrome(); }).observe(carousel, {
      subtree:true, childList:true, characterData:true, attributes:true,
      attributeFilter:['class','data-session','data-package-name']
    });
  }

  if (stage && window.MutationObserver) {
    new MutationObserver(() => { syncHomeChrome(); syncSelectedTitle(); }).observe(stage, {
      subtree:true, attributes:true, attributeFilter:['class']
    });
  }

  document.addEventListener('gsb-session-state', syncSelectedTitle);

  window.addEventListener('keydown', e => {
    if (root.dataset.gsbTheme !== 'handheld') return;
    const key = e.key.toLowerCase();
    const scene = activeSceneName();
    if ((e.key === 'Escape' || key === 'b') && scene !== 'home') {
      showScene('home'); dockFocus = -1; syncDockFocus(); buzz('tick');
      e.preventDefault(); e.stopImmediatePropagation(); return;
    }
    if (scene !== 'home') return;
    const buttons = [...document.querySelectorAll('.hhDockBtn')];
    if (e.key === 'ArrowDown') {
      dockFocus = dockFocus < 0 ? 0 : dockFocus; syncDockFocus(); buttons[dockFocus]?.focus();
      e.preventDefault(); e.stopImmediatePropagation(); return;
    }
    if (e.key === 'ArrowUp') {
      if (dockFocus >= 0) { dockFocus = -1; syncDockFocus(); selectedCard()?.focus?.(); }
      e.preventDefault(); e.stopImmediatePropagation(); return;
    }
    if (dockFocus >= 0 && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
      dockFocus = (dockFocus + (e.key === 'ArrowRight' ? 1 : -1) + buttons.length) % buttons.length;
      syncDockFocus(); buttons[dockFocus]?.focus(); buzz('tick');
      e.preventDefault(); e.stopImmediatePropagation(); return;
    }
    if ((e.key === 'Enter' || key === 'a') && dockFocus >= 0) {
      buttons[dockFocus]?.click(); e.preventDefault(); e.stopImmediatePropagation(); return;
    }
    if ((e.key === 'Enter' || key === 'a') && dockFocus < 0) {
      launchSelected(); e.preventDefault(); e.stopImmediatePropagation(); return;
    }
    if (key === 't') { toggleTheme(); e.preventDefault(); e.stopImmediatePropagation(); }
  }, true);

  document.addEventListener('click', e => {
    if (root.dataset.gsbTheme !== 'handheld' || activeSceneName() !== 'home') return;
    const card = e.target?.closest?.('#carousel .gameCard');
    if (!card || e.target?.closest?.('.playBtn')) return;
    const wasActive = card.classList.contains('active');
    if (wasActive) setTimeout(() => card.querySelector('.playBtn')?.click(), 0);
    else setTimeout(syncSelectedTitle, 0);
  }, true);

  if (brand) {
    brand.style.cursor = 'pointer';
    brand.addEventListener('click', () => { if (root.dataset.gsbTheme === 'handheld') showScene('home'); });
  }

  window.GSBTheme = Object.freeze({
    current: () => root.dataset.gsbTheme || 'console',
    set: theme => applyTheme(theme),
    toggle: toggleTheme
  });
})();
