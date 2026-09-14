(() => {
  function initBootRuntime() {
    if (!window.GSBBoot) {
      setTimeout(initBootRuntime, 40);
      return;
    }

    const bootEl = document.getElementById('boot');
    let stopped = false;

    // Compatibility marker for the existing Boot guard. Dynamic playback-rate control is
    // intentionally disabled: the approved legacy boot animation is suppressed by native boot.
    function playbackRateFor() { return 1; }
    void playbackRateFor;

    function applyBootState(state) {
      if (!state || stopped) return;
      document.documentElement.dataset.gsbBootPhase = state.phase || 'UNKNOWN';
      document.documentElement.dataset.gsbBootProgress = String(state.overallProgress || 0);
      if (bootEl && bootEl.classList.contains('done')) stopped = true;
    }

    window.onGSBBootState = applyBootState;
    try {
      const raw = GSBBoot.state();
      if (raw) applyBootState(JSON.parse(raw));
    } catch (_) {}
    try { GSBBoot.webViewReady(); } catch (_) {}
  }

  function injectScript(id, src) {
    const existing = document.getElementById(id);
    if (existing) return Promise.resolve();
    return new Promise(resolve => {
      const script = document.createElement('script');
      script.id = id;
      script.src = src;
      script.onload = () => resolve();
      script.onerror = () => resolve();
      document.head.appendChild(script);
    });
  }

  function injectStylesheetOnce(id, href) {
    if (document.getElementById(id)) return;
    const link = document.createElement('link');
    link.id = id;
    link.rel = 'stylesheet';
    link.href = href;
    document.head.appendChild(link);
  }

  function externalBootGateOpen() {
    try {
      if (!window.GSBRuntime || !GSBRuntime.bootGateOpen) return false;
      return !!GSBRuntime.bootGateOpen();
    } catch (_) {
      return false;
    }
  }

  function provisioningCatalogReady() {
    try {
      return !!(window.GSBProvisioning
        && GSBProvisioning.catalogReady
        && GSBProvisioning.catalogReady());
    } catch (_) {
      return false;
    }
  }

  function waitForCatalogReady(startedAt) {
    if (provisioningCatalogReady()) return Promise.resolve();
    // The native catalog is prewarmed off the UI thread. Six seconds is only a deadlock guard;
    // normal first-boot interaction is expected to hide this work completely.
    if (performance.now() - startedAt > 6000) return Promise.resolve();
    return new Promise(resolve => setTimeout(resolve, 45))
      .then(() => waitForCatalogReady(startedAt));
  }

  function twoFrames() {
    return new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  }

  async function prewarmHiddenMainSurface() {
    if (window.__gsbHiddenSurfacePrewarmStarted) return;
    window.__gsbHiddenSurfacePrewarmStarted = true;

    await waitForCatalogReady(performance.now());

    // Everything the user should see immediately after ignition is hydrated while the hardware
    // test is still on screen. Menu music remains post-ignition so it cannot fight the boot sound.
    injectStylesheetOnce('gsb-scroll-runtime-css', 'scroll-runtime.css');
    await injectScript('gsb-gesture-runtime-js', 'gesture-runtime.js');
    await injectScript('gsb-game-library-js', 'game-library.js');
    await injectScript('gsb-session-runtime-js', 'session-runtime.js');
    await injectScript('gsb-quick-menu-runtime-js', 'quick-menu-runtime.js');
    await twoFrames();

    try {
      if (window.GSBProvisioning && GSBProvisioning.mainSurfaceReady) {
        GSBProvisioning.mainSurfaceReady();
      }
    } catch (_) {}
  }

  function loadPostIgnitionRuntime() {
    if (window.__gsbPostIgnitionRuntimeLoaded) return;
    if (!externalBootGateOpen()) {
      setTimeout(loadPostIgnitionRuntime, 60);
      return;
    }

    window.__gsbPostIgnitionRuntimeLoaded = true;
    try { if (window.GSBRuntime) GSBRuntime.ready(); } catch (_) {}

    const startAudio = () => injectScript('gsb-menu-music-js', 'menu-music.js');
    if ('requestIdleCallback' in window) requestIdleCallback(startAudio, { timeout: 500 });
    else setTimeout(startAudio, 50);
  }

  initBootRuntime();
  prewarmHiddenMainSurface();
  loadPostIgnitionRuntime();

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
