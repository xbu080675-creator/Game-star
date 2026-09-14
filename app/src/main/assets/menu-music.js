(() => {
  if (window.GSBMenuMusic) return;

  const STORAGE_KEY = 'gsb_menu_music_enabled_v1';
  const TARGET_VOLUME = 0.105;
  const audio = new Audio('menu_bgm.ogg');
  audio.loop = true;
  audio.preload = 'auto';
  audio.volume = 0;

  let enabled = localStorage.getItem(STORAGE_KEY) !== '0';
  let fadeToken = 0;
  let retryArmed = false;

  function bootFinished() {
    const boot = document.getElementById('boot');
    return !boot || boot.classList.contains('done');
  }

  function fadeTo(target, duration, done) {
    const token = ++fadeToken;
    const start = audio.volume;
    const started = performance.now();
    const step = now => {
      if (token !== fadeToken) return;
      const p = Math.min(1, Math.max(0, (now - started) / duration));
      const eased = 1 - Math.pow(1 - p, 3);
      audio.volume = start + (target - start) * eased;
      if (p < 1) requestAnimationFrame(step);
      else if (done) done();
    };
    requestAnimationFrame(step);
  }

  function armRetry() {
    if (retryArmed) return;
    retryArmed = true;
    const retry = () => {
      retryArmed = false;
      start();
    };
    document.addEventListener('pointerdown', retry, { once: true, passive: true });
    document.addEventListener('keydown', retry, { once: true });
  }

  function start() {
    if (!enabled || document.hidden || !bootFinished()) return;
    const result = audio.play();
    if (result && typeof result.then === 'function') {
      result.then(() => fadeTo(TARGET_VOLUME, 1100)).catch(armRetry);
    } else {
      fadeTo(TARGET_VOLUME, 1100);
    }
  }

  function pauseSoft() {
    if (audio.paused) return;
    fadeTo(0, 260, () => audio.pause());
  }

  function setEnabled(value) {
    enabled = !!value;
    localStorage.setItem(STORAGE_KEY, enabled ? '1' : '0');
    renderToggle();
    if (enabled) start();
    else pauseSoft();
  }

  function renderToggle() {
    const button = document.getElementById('gsbMenuMusicButton');
    const status = document.getElementById('gsbMenuMusicStatus');
    if (!button || !status) return;
    button.textContent = enabled ? 'ON' : 'OFF';
    button.classList.toggle('on', enabled);
    status.textContent = enabled ? 'Spacelife #14 · CC0 · 10%' : '已关闭';
  }

  function injectSetting() {
    const panel = document.querySelector('.systemScene .rightPanel');
    if (!panel || document.getElementById('gsbMenuMusicSetting')) return;
    const row = document.createElement('div');
    row.className = 'setting';
    row.id = 'gsbMenuMusicSetting';
    row.innerHTML = '<div><b>主界面音乐</b><small id="gsbMenuMusicStatus">Spacelife #14 · CC0</small></div><button class="switch" id="gsbMenuMusicButton">ON</button>';
    panel.appendChild(row);
    document.getElementById('gsbMenuMusicButton')?.addEventListener('click', () => setEnabled(!enabled));
    renderToggle();
  }

  function waitForBoot() {
    injectSetting();
    if (bootFinished()) {
      start();
      return;
    }
    setTimeout(waitForBoot, 80);
  }

  document.addEventListener('visibilitychange', () => {
    if (document.hidden) pauseSoft();
    else if (enabled) start();
  });

  window.GSBMenuMusic = {
    setEnabled,
    isEnabled: () => enabled,
    source: 'Spacelife #14 / yd / CC0'
  };

  waitForBoot();
})();
