(() => {
  const capabilityApi = window.GSBCapabilities;
  const sessionApi = window.GSBSession;
  if (!capabilityApi && !sessionApi) return;

  const CAP = {
    performance: 'game.performance.boost',
    refresh: 'display.refresh_rate',
    touch: 'input.touch_policy',
    fan: 'device.fan_control',
    fps: 'overlay.fps_monitor'
  };

  let capabilities = {};
  let session = { state: 'IDLE', gameId: '' };

  function availabilityText(entry) {
    const state = entry && entry.availability;
    switch (state) {
      case 'AVAILABLE': return 'READY';
      case 'DENIED': return 'DENIED';
      case 'DEAD': return 'OFFLINE';
      case 'UNSUPPORTED': return 'N/A';
      case 'UNAVAILABLE': return 'N/A';
      case 'UNKNOWN': return '待验证';
      default: return '待接入';
    }
  }

  function isAvailable(entry) {
    return !!entry && entry.availability === 'AVAILABLE';
  }

  function findRow(label) {
    for (const row of document.querySelectorAll('.quickRow')) {
      const title = row.querySelector('b');
      if (title && title.textContent.trim() === label) return row;
    }
    return null;
  }

  function setValue(label, text, enabled) {
    const row = findRow(label);
    if (!row) return;
    let value = row.querySelector('.qValue');
    let button = row.querySelector('.qBtn');
    if (value) value.textContent = text;
    if (button) {
      button.textContent = text;
      button.disabled = !enabled;
      button.classList.toggle('on', !!enabled);
    }
    row.dataset.available = enabled ? '1' : '0';
  }

  function renderCapabilities() {
    setValue('性能模式', availabilityText(capabilities[CAP.performance]), isAvailable(capabilities[CAP.performance]));
    setValue('刷新率', availabilityText(capabilities[CAP.refresh]), isAvailable(capabilities[CAP.refresh]));
    setValue('触控采样', availabilityText(capabilities[CAP.touch]), isAvailable(capabilities[CAP.touch]));
    setValue('风扇', availabilityText(capabilities[CAP.fan]), isAvailable(capabilities[CAP.fan]));

    const hudRow = findRow('赛事 HUD');
    if (hudRow) {
      const button = hudRow.querySelector('.qBtn');
      const current = session && session.gameId ? session.gameId : '';
      const state = session && session.state ? session.state : 'IDLE';
      const enabled = state === 'RUNNING' || state === 'SUSPENDED';
      if (button) {
        button.textContent = enabled ? state : '等待游戏';
        button.disabled = !enabled;
        button.classList.toggle('on', enabled);
      }
      const small = hudRow.querySelector('small');
      if (small) small.textContent = current || '等待真实 Game Session';
    }

    const head = document.querySelector('.quickHead small');
    if (head) {
      head.textContent = session && session.gameId
        ? `QUICK ACCESS // ${session.gameId}`
        : 'QUICK ACCESS // NO ACTIVE GAME';
    }
  }

  function applyCapabilities(next) {
    capabilities = next || {};
    renderCapabilities();
  }

  function applySession(next) {
    session = next || session;
    renderCapabilities();
  }

  window.onGSBCapabilityState = applyCapabilities;
  document.addEventListener('gsb-session-state', event => applySession(event.detail || {}));

  try {
    if (capabilityApi) applyCapabilities(JSON.parse(capabilityApi.state() || '{}'));
  } catch (_) {}
  try {
    if (sessionApi) applySession(JSON.parse(sessionApi.state() || '{}'));
  } catch (_) {}

  // Static prototype values such as PRO / 144 Hz / 720 Hz must never survive as if they were
  // device evidence. The runtime render above replaces them with actual capability availability.
  setTimeout(renderCapabilities, 250);
  setTimeout(renderCapabilities, 900);
})();
