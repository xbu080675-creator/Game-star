(() => {
  if (!window.GSBGames) return;

  const STORAGE_KEY = 'gsb.manualGames.v1';
  let catalog = { autoGames: [], launchables: [] };
  try { catalog = JSON.parse(GSBGames.catalog() || '{}') || catalog; } catch (_) {}

  const auto = Array.isArray(catalog.autoGames) ? catalog.autoGames : [];
  const launchables = Array.isArray(catalog.launchables) ? catalog.launchables : [];
  const launchableByPackage = new Map(launchables.map(x => [x.packageName, x]));

  function readManual() {
    try {
      const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
      return new Set(Array.isArray(raw) ? raw.filter(x => launchableByPackage.has(x)) : []);
    } catch (_) { return new Set(); }
  }

  let manual = readManual();

  function saveManual() {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify([...manual])); } catch (_) {}
  }

  function currentGames() {
    const seen = new Set();
    const out = [];
    for (const game of auto) {
      if (!game || !game.packageName || seen.has(game.packageName)) continue;
      seen.add(game.packageName);
      out.push(game);
    }
    for (const pkg of manual) {
      const game = launchableByPackage.get(pkg);
      if (!game || seen.has(pkg)) continue;
      seen.add(pkg);
      out.push(game);
    }
    return out;
  }

  function esc(v) {
    return String(v || '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }

  function launch(pkg) {
    if (!pkg) return false;
    try { return !!GSBGames.launch(pkg); } catch (_) { return false; }
  }

  function gameArtStyle(game) {
    const icon = game && game.icon ? `url("${game.icon}")` : 'none';
    return `${icon},radial-gradient(circle at 50% 42%,rgba(122,223,255,.24),transparent 24%),linear-gradient(145deg,#223b4e,#101a22)`;
  }

  function bindLaunch(el, pkg) {
    if (!el) return;
    el.onclick = e => {
      if (e) e.stopPropagation();
      if (pkg) launch(pkg);
    };
  }

  function renderHome() {
    const games = currentGames();
    const cards = [...document.querySelectorAll('#carousel .gameCard')];
    cards.forEach((card, i) => {
      const art = card.querySelector('.art');
      const top = card.querySelector('.cardTop');
      const title = card.querySelector('.cardInfo b');
      const sub = card.querySelector('.cardInfo small');
      const button = card.querySelector('.playBtn');
      const game = games[i];
      card.dataset.packageName = game ? game.packageName : '';
      if (game) {
        if (art) {
          art.className = 'art';
          art.style.backgroundImage = gameArtStyle(game);
          art.style.backgroundSize = '34% auto,auto,auto';
          art.style.backgroundPosition = 'center,center,center';
          art.style.backgroundRepeat = 'no-repeat';
        }
        if (top) top.textContent = i === 0 ? 'INSTALLED GAME // READY' : 'INSTALLED GAME';
        if (title) title.textContent = game.label || game.packageName;
        if (sub) sub.textContent = game.packageName;
        if (button) {
          button.textContent = '▶ 启动';
          bindLaunch(button, game.packageName);
        }
      } else {
        if (art) {
          art.className = 'art';
          art.style.backgroundImage = 'radial-gradient(circle at 50% 42%,rgba(122,223,255,.16),transparent 20%),linear-gradient(145deg,#203542,#101820)';
          art.style.backgroundSize = 'auto';
        }
        if (top) top.textContent = 'GAME LIBRARY';
        if (title) title.textContent = '添加游戏';
        if (sub) sub.textContent = '从本机可启动应用中选择';
        if (button) {
          button.textContent = '＋ 添加';
          button.onclick = e => { e.stopPropagation(); openPicker(); };
        }
      }
    });

    try { if (window.updateBackdrop) window.updateBackdrop(0); } catch (_) {}

    const recent = document.querySelector('.homeScene .rowGrid');
    if (recent) {
      recent.innerHTML = '';
      const list = games.slice(0, 4);
      if (!list.length) {
        const empty = document.createElement('div');
        empty.className = 'activity';
        empty.style.gridColumn = '1 / -1';
        empty.innerHTML = '<b>还没有游戏</b><small>进入游戏库，选择“添加游戏”</small><strong>＋</strong>';
        empty.onclick = openPicker;
        recent.appendChild(empty);
      } else {
        list.forEach((game, i) => {
          const item = document.createElement('div');
          item.className = 'activity';
          item.style.setProperty('--accent', ['#6fa9df','#6fd6b6','#d8ba6f','#9a74e7'][i % 4]);
          item.innerHTML = `<b>${esc(game.label)}</b><small>${esc(game.packageName)} · 已安装</small><strong>启动</strong>`;
          item.onclick = () => launch(game.packageName);
          recent.appendChild(item);
        });
      }
    }
  }

  function renderLibrary() {
    const games = currentGames();
    const grid = document.querySelector('.coverGrid');
    if (!grid) return;
    grid.innerHTML = '';

    for (const game of games) {
      const tile = document.createElement('div');
      tile.className = 'coverTile';
      tile.style.cursor = 'pointer';
      if (game.icon) {
        tile.style.backgroundImage = `url("${game.icon}"),linear-gradient(145deg,#263a49,#101b23)`;
        tile.style.backgroundSize = '52% auto,cover';
        tile.style.backgroundPosition = 'center 40%,center';
        tile.style.backgroundRepeat = 'no-repeat';
      }
      tile.innerHTML = `<span>${esc(game.label)}</span>`;
      tile.onclick = () => launch(game.packageName);
      grid.appendChild(tile);
    }

    const add = document.createElement('div');
    add.className = 'coverTile';
    add.style.cursor = 'pointer';
    add.innerHTML = '<span>＋ 添加游戏</span>';
    add.onclick = openPicker;
    grid.appendChild(add);

    const head = document.querySelector('.libraryHead h2');
    if (head) head.textContent = `全部游戏 · ${games.length}`;
    const search = document.querySelector('.libraryHead .search');
    if (search) {
      search.textContent = '＋ 添加游戏';
      search.style.cursor = 'pointer';
      search.onclick = openPicker;
    }
  }

  function ensurePicker() {
    let root = document.getElementById('gsbGamePicker');
    if (root) return root;

    const style = document.createElement('style');
    style.textContent = '.gsbGamePicker{position:fixed;z-index:90;inset:0;background:rgba(2,7,11,.78);display:none;align-items:center;justify-content:center}.gsbGamePicker.open{display:flex}.gsbGamePickerPanel{width:min(760px,78vw);height:min(520px,76vh);background:#101e28;border:1px solid rgba(218,238,251,.2);box-shadow:0 28px 80px rgba(0,0,0,.55);display:flex;flex-direction:column}.gsbGamePickerHead{height:58px;padding:0 16px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid rgba(216,236,249,.1)}.gsbGamePickerHead b{font-size:13px}.gsbGamePickerHead small{display:block;color:#708391;font-size:7px;margin-top:3px}.gsbGamePickerList{flex:1;overflow:auto;padding:8px 12px}.gsbGamePick{height:48px;width:100%;border:0;border-bottom:1px solid rgba(216,236,249,.08);background:transparent;color:#e9f2f7;text-align:left;padding:0 10px;display:flex;align-items:center;gap:10px}.gsbGamePick img{width:30px;height:30px;border-radius:7px}.gsbGamePick span{flex:1}.gsbGamePick small{color:#6f8190}.gsbGamePick.on{background:rgba(98,230,168,.08)}';
    document.head.appendChild(style);

    root = document.createElement('div');
    root.id = 'gsbGamePicker';
    root.className = 'gsbGamePicker';
    root.innerHTML = '<div class="gsbGamePickerPanel"><div class="gsbGamePickerHead"><div><b>添加游戏</b><small>从本机可启动应用中选择 · 自动识别的游戏无需重复添加</small></div><button class="close" id="gsbGamePickerClose">×</button></div><div class="gsbGamePickerList" id="gsbGamePickerList"></div></div>';
    document.body.appendChild(root);
    root.addEventListener('click', e => { if (e.target === root) root.classList.remove('open'); });
    document.getElementById('gsbGamePickerClose').onclick = () => root.classList.remove('open');
    return root;
  }

  function openPicker() {
    const root = ensurePicker();
    const list = root.querySelector('#gsbGamePickerList');
    if (!list) return;
    list.innerHTML = '';
    const autoPkgs = new Set(auto.map(x => x.packageName));
    const candidates = launchables.filter(x => !autoPkgs.has(x.packageName));

    if (!candidates.length) {
      list.innerHTML = '<div style="padding:18px;color:#8193a1;font-size:10px">没有更多可添加的启动项。</div>';
    } else {
      for (const app of candidates) {
        const row = document.createElement('button');
        row.className = 'gsbGamePick' + (manual.has(app.packageName) ? ' on' : '');
        row.innerHTML = `${app.icon ? `<img src="${app.icon}">` : '<span style="width:30px"></span>'}<span><b>${esc(app.label)}</b><small style="display:block">${esc(app.packageName)}</small></span><small>${manual.has(app.packageName) ? '已添加' : '添加'}</small>`;
        row.onclick = () => {
          if (manual.has(app.packageName)) manual.delete(app.packageName); else manual.add(app.packageName);
          saveManual();
          renderAll();
          openPicker();
        };
        list.appendChild(row);
      }
    }
    root.classList.add('open');
  }

  function renderAll() {
    renderHome();
    renderLibrary();
  }

  renderAll();
})();
