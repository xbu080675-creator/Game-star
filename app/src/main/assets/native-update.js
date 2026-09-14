(() => {
  function initBootRuntime() {
    if (!window.GSBBoot) return;

    const bootEl = document.getElementById('boot');
    const bootAudio = document.getElementById('bootAudio');
    let stopped = false;
    let polls = 0;

    function playbackRateFor(state) {
      const p = Math.max(0, Math.min(100, Number(state && state.overallProgress) || 0));
      if (state && state.blockingReady) return 1.42;
      if (p >= 88) return 1.24;
      if (p >= 65) return 1.08;
      if (p >= 50) return 0.96;
      if (p >= 30) return 0.84;
      return 0.72;
    }

    function applyBootState(state) {
      if (!state || stopped) return;
      const rate = playbackRateFor(state);
      if (bootAudio) {
        try {
          bootAudio.defaultPlaybackRate = rate;
          bootAudio.playbackRate = rate;
        } catch (_) {}
      }
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
      if (stopped || polls >= 28) clearInterval(timer);
    }, 180);
  }

  initBootRuntime();

  if (!document.getElementById('gsb-game-library-js')) {
    const gameScript = document.createElement('script');
    gameScript.id = 'gsb-game-library-js';
    gameScript.src = 'game-library.js';
    document.head.appendChild(gameScript);
  }

  if (!document.getElementById('gsb-menu-music-js')) {
    const musicScript = document.createElement('script');
    musicScript.id = 'gsb-menu-music-js';
    musicScript.src = 'menu-music.js';
    document.head.appendChild(musicScript);
  }

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
