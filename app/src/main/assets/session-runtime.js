(() => {
  if (!window.GSBSession) return;

  let latest = { state: 'IDLE', gameId: '' };

  function stateLabel(state) {
    switch (state) {
      case 'PREPARING': return 'PREPARING';
      case 'LAUNCHING': return 'STARTING';
      case 'RUNNING': return 'CURRENT SESSION // RUNNING';
      case 'SUSPENDED': return 'CURRENT SESSION // SUSPENDED';
      case 'ENDED': return 'SESSION ENDED';
      default: return '';
    }
  }

  function apply(state) {
    latest = state || latest;
    const gameId = String(latest.gameId || '');
    const sessionState = String(latest.state || 'IDLE');
    document.documentElement.dataset.gsbSessionState = sessionState;
    document.documentElement.dataset.gsbSessionGame = gameId;

    for (const card of document.querySelectorAll('#carousel .gameCard')) {
      const pkg = card.dataset.packageName || '';
      const top = card.querySelector('.cardTop');
      const button = card.querySelector('.playBtn');
      const isCurrent = !!gameId && pkg === gameId;
      card.dataset.session = isCurrent ? sessionState : '';
      if (!isCurrent) continue;

      const label = stateLabel(sessionState);
      if (top && label) top.textContent = label;
      if (button) {
        if (sessionState === 'SUSPENDED') button.textContent = '▶ 返回游戏';
        else if (sessionState === 'RUNNING') button.textContent = '▶ 返回游戏';
        else if (sessionState === 'LAUNCHING' || sessionState === 'PREPARING') button.textContent = '启动中…';
      }
    }

    for (const item of document.querySelectorAll('.homeScene .activity')) {
      const text = item.textContent || '';
      if (!gameId || !text.includes(gameId)) continue;
      const strong = item.querySelector('strong');
      if (strong) {
        strong.textContent = sessionState === 'RUNNING' ? 'RUNNING'
          : sessionState === 'SUSPENDED' ? 'RESUME'
          : sessionState === 'LAUNCHING' ? 'STARTING'
          : '启动';
      }
    }
  }

  function read() {
    try {
      const raw = GSBSession.state();
      if (raw) apply(JSON.parse(raw));
    } catch (_) {}
  }

  window.onGSBSessionState = apply;
  read();

  // game-library.js may finish rendering after this script. Re-apply a few times without owning
  // the source of truth; native GameSession remains authoritative.
  let attempts = 0;
  const timer = setInterval(() => {
    attempts++;
    apply(latest);
    if (attempts >= 12) clearInterval(timer);
  }, 350);
})();
