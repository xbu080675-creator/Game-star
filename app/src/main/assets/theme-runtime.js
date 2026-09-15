(() => {
  const STORAGE_KEY = 'gsb.ui.theme.v10';
  const THEMES = new Set(['console', 'handheld']);
  const root = document.documentElement;
  const shell = document.querySelector('.shell');
  const home = document.querySelector('.homeScene');
  const stage = document.querySelector('.stage');
  const status = document.querySelector('.status');
  const systemPanel = document.querySelector('.systemScene .rightPanel');
  const brand = document.querySelector('.brand');
  let dockFocus = -1;
  let toastTimer = 0;

  function readTheme() {
    try {
      const value = localStorage.getItem(STORAGE_KEY);
      if (THEMES.has(value)) return value;
    } catch (_) {}
    // v10 is specifically a handheld-direction prototype, so install-over testing sees it first.
    return 'handheld';
  }

  function buzz(cue = 'tick') {
    try { if (window.GSBNative && GSBNative.haptic) GSBNative.haptic(cue); } catch (_) {}
  }

  function showScene(name) {
    const tab = document.querySelector(`.tab[data-scene="${name}"]`);
    if (tab) {
      tab.click();
      return;
    }
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

  function activeSceneName() {
    return document.querySelector('.scene.active')?.dataset?.scene || 'home';
  }

  function selectedCard() {
    return document.querySelector('#carousel .gameCard.active');
  }

  function selectedLaunchButton() {
    return selectedCard()?.querySelector('.playBtn') || null;
  }

  function scrollFocusedCard() {
    if (root.dataset.gsbTheme !== 'handheld' || activeSceneName() !== 'home') return;
    const card = selectedCard();
    if (!card) return;
    try { card.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' }); } catch (_) {}
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
    out.innerHTML = `<small>${running ? '●' : '■'}</small>${title}`;
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
    ['▦', '游戏库', () => showScene('library')],
    ['◇', '电竞', () => showScene('esports')],
    ['▣', '截图', () => toast('截图继续使用 Android / REDMAGIC 系统能力')],
    ['⌁', '控制器', () => showScene('system')],
    ['◎', '控制台', () => quickMenu()],
    ['◐', '主题', () => toggleTheme(), 'theme'],
    ['⚙', '系统', () => showScene('system')],
    ['◌', '休眠', () => toast('休眠继续交给 Android / REDMAGIC 系统管理')]
  ];

  function ensureDock() {
    if (!home || document.getElementById('hhDock')) return;
    const dock = document.createElement('div');
    dock.id = 'hhDock';
    dock.className = 'hhDock';
    dock.dataset.noSwipe = '1';
    for (const [glyph, label, action, extra] of dockItems) {
      const button = document.createElement('button');
      button.className = 'hhDockBtn' + (extra ? ` ${extra}` : '');
      button.type = 'button';
      button.dataset.label = label;
      button.setAttribute('aria-label', label);
      button.textContent = glyph;
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
    buttons.forEach((b, i) => b.classList.toggle('active', i === dockFocus));
  }

  function syncLabels(theme) {
    const top = document.getElementById('gsbThemeToggle');
    if (top) top.textContent = theme === 'handheld' ? '▦ HANDHELD' : '▰ CONSOLE';
    const button = document.getElementById('gsbThemeSettingButton');
    if (button) {
      button.textContent = theme === 'handheld' ? 'HANDHELD' : 'CONSOLE';
      button.classList.toggle('on', theme === 'handheld');
    }
    const label = document.getElementById('gsbThemeSettingLabel');
    if (label) label.textContent = theme === 'handheld'
      ? '方形软件列 · 系统功能带 · 掌机优先'
      : '大幅卡片 · 多层信息 · 大屏优先';
  }

  function syncHomeChrome() {
    const isHandheldHome = root.dataset.gsbTheme === 'handheld' && activeSceneName() === 'home';
    const title = document.getElementById('hhSelectedTitle');
    const dock = document.getElementById('hhDock');
    if (title) title.hidden = !isHandheldHome;
    if (dock) dock.hidden = !isHandheldHome;
    if (!isHandheldHome) {
      dockFocus = -1;
      syncDockFocus();
    }
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
    try { document.dispatchEvent(new CustomEvent('gsb-theme-change', { detail: { theme } })); } catch (_) {}
    buzz('tick');
  }

  function toggleTheme() {
    applyTheme(root.dataset.gsbTheme === 'handheld' ? 'console' : 'handheld');
  }

  function launchSelected() {
    selectedLaunchButton()?.click();
  }

  ensureSelectedTitle();
  ensureClock();
  ensureThemeToggle();
  ensureSystemSetting();
  ensureDock();
  applyTheme(readTheme(), false);

  const carousel = document.getElementById('carousel');
  if (carousel && window.MutationObserver) {
    new MutationObserver(() => {
      syncSelectedTitle();
      syncHomeChrome();
    }).observe(carousel, {
      subtree:true,
      childList:true,
      characterData:true,
      attributes:true,
      attributeFilter:['class','data-session','data-package-name']
    });
  }

  // Scenes are switched by the existing console runtime. Observe their active classes so handheld
  // chrome cannot leak over Library / Esports / System.
  if (stage && window.MutationObserver) {
    new MutationObserver(() => {
      syncHomeChrome();
      syncSelectedTitle();
    }).observe(stage, { subtree:true, attributes:true, attributeFilter:['class'] });
  }

  document.addEventListener('gsb-session-state', syncSelectedTitle);

  window.addEventListener('keydown', e => {
    if (root.dataset.gsbTheme !== 'handheld') return;
    const key = e.key.toLowerCase();
    const scene = activeSceneName();

    if ((e.key === 'Escape' || key === 'b') && scene !== 'home') {
      showScene('home');
      dockFocus = -1;
      syncDockFocus();
      buzz('tick');
      e.preventDefault();
      e.stopImmediatePropagation();
      return;
    }
    if (scene !== 'home') return;

    const buttons = [...document.querySelectorAll('.hhDockBtn')];
    if (e.key === 'ArrowDown') {
      dockFocus = dockFocus < 0 ? 0 : dockFocus;
      syncDockFocus();
      buttons[dockFocus]?.focus();
      e.preventDefault();
      e.stopImmediatePropagation();
      return;
    }
    if (e.key === 'ArrowUp') {
      if (dockFocus >= 0) {
        dockFocus = -1;
        syncDockFocus();
        selectedCard()?.focus?.();
      }
      // HOME's software rail has nothing above it; prevent legacy vertical tab cycling.
      e.preventDefault();
      e.stopImmediatePropagation();
      return;
    }
    if (dockFocus >= 0 && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
      dockFocus = (dockFocus + (e.key === 'ArrowRight' ? 1 : -1) + buttons.length) % buttons.length;
      syncDockFocus();
      buttons[dockFocus]?.focus();
      buzz('tick');
      e.preventDefault();
      e.stopImmediatePropagation();
      return;
    }
    if ((e.key === 'Enter' || key === 'a') && dockFocus >= 0) {
      buttons[dockFocus]?.click();
      e.preventDefault();
      e.stopImmediatePropagation();
      return;
    }
    if ((e.key === 'Enter' || key === 'a') && dockFocus < 0) {
      launchSelected();
      e.preventDefault();
      e.stopImmediatePropagation();
      return;
    }
    if (key === 't') {
      toggleTheme();
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  }, true);

  // Touch: first tap moves focus; a second tap on the focused square launches through the existing
  // typed GSBGames launch button. We do not duplicate package/session logic here.
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
    brand.addEventListener('click', () => {
      if (root.dataset.gsbTheme === 'handheld') showScene('home');
    });
  }

  window.GSBTheme = Object.freeze({
    current: () => root.dataset.gsbTheme || 'console',
    set: theme => applyTheme(theme),
    toggle: toggleTheme
  });
})();
