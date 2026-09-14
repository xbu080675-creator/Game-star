(() => {
  function initBootRuntime() {
    if (!window.GSBBoot) return;

    const bootEl = document.getElementById('boot');
    let stopped = false;
    let polls = 0;

    // Compatibility marker for the existing Boot guard. Dynamic playback-rate control is
    // intentionally disabled: the approved boot animation now runs at its native 1.0x clock.
    function playbackRateFor() { return 1; }
    void playbackRateFor;

    function applyBootState(state) {
      if (!state || stopped) return;
      document.documentElement.dataset.gsbBootPhase = state.phase || 'UNKNOWN';
      document.documentElement.dataset.gsbBootProgress = String(state.overallProgress || 0);
      if (bootEl && bootEl.classList.contains('done')) stopped = true;
    }

    function readBootState() {
      try {
        const raw = GSBBoot.state();
        if (raw) applyBootState(JSON.parse(raw));
      } catch (_) {}
    }

    window.onGSBBootState = applyBootState;
    readBootState();
    try { GSBBoot.webViewReady(); } catch (_) {}

    const timer = setInterval(() => {
      polls++;
      readBootState();
      if (stopped || polls >= 14) clearInterval(timer);
    }, 320);
  }

  function injectScriptOnce(id, src) {
    if (document.getElementById(id)) return;
    const script = document.createElement('script');
    script.id = id;
    script.src = src;
    document.head.appendChild(script);
  }

  function loadPostBootRuntime() {
    if (window.__gsbPostBootRuntimeLoaded) return;
    window.__gsbPostBootRuntimeLoaded = true;

    const start = () => {
      injectScriptOnce('gsb-game-library-js', 'game-library.js');
      injectScriptOnce('gsb-session-runtime-js', 'session-runtime.js');
      injectScriptOnce('gsb-quick-menu-runtime-js', 'quick-menu-runtime.js');
      injectScriptOnce('gsb-menu-music-js', 'menu-music.js');
    };

    if ('requestIdleCallback' in window) requestIdleCallback(start, { timeout: 600 });
    else setTimeout(start, 80);
  }

  function deferRuntimeUntilBootEnds() {
    const boot = document.getElementById('boot');
    if (!boot || boot.classList.contains('done')) {
      loadPostBootRuntime();
      return;
    }

    const observer = new MutationObserver(() => {
      if (!boot.classList.contains('done')) return;
      observer.disconnect();
      loadPostBootRuntime();
    });
    observer.observe(boot, { attributes: true, attributeFilter: ['class'] });

    setTimeout(() => {
      observer.disconnect();
      loadPostBootRuntime();
    }, 5200);
  }

  initBootRuntime();
  deferRuntimeUntilBootEnds();

  const panel = document.querySelector('.systemScene .rightPanel');
  if (!panel || document.getElementById('gsbUpdateSetting')) return;

  const row = document.createElement('div');
  row.className = 'setting';
  row.id = 'gsbUpdateSetting';
  row.innerHTML = '<div><b>软件更新</b><small id="gsbUpdateStatus">GitHub 主源 · 自动检测</small></div><button class="switch" id="gsbUpdateButton">CHECK</button>';
  panel.appendChild(row);

  const status = document.getElementById('gsbUpdateStatus');
  const button = document.getElementById('gsbUpdateButton');
  let latest = null;

  function render(s) {
    latest = s || {};
    if (!status || !button) return;
    if (s.downloading) {
      status.textContent = `${s.sourceLabel || '更新通道'} · ${s.progressPercent || 0}%`;
      button.textContent = `${s.progressPercent || 0}%`;
      button.classList.add('on');
      button.disabled = true;
      return;
    }
    button.disabled = false;
    if (s.available) {
      status.textContent = `发现 ${s.latestVersionName || '新版本'} · ${s.sourceLabel || 'GitHub'}`;
      button.textContent = 'UPDATE';
      button.classList.add('on');
      return;
    }
    button.classList.remove('on');
    if (s.checking) {
      status.textContent = '正在检测 GitHub 与可用通道…';
      button.textContent = '...';
    } else if (s.error) {
      status.textContent = `检查失败 · ${s.error}`;
      button.textContent = 'RETRY';
    } else if (s.status) {
      status.textContent = s.status;
      button.textContent = 'CHECK';
    }
  }

  button.addEventListener('click', () => {
    try {
      if (latest && latest.available) GSBNative.downloadUpdate();
      else GSBNative.checkUpdate();
    } catch (_) {}
  });

  window.onGSBUpdateState = render;
  try { render(JSON.parse(GSBNative.updateState())); } catch (_) {}
})();
