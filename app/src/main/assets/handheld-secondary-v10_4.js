(() => {
  const root = document.documentElement;
  const stage = document.querySelector('.stage');
  const BACK_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15.5 5.5 9 12l6.5 6.5"/></svg>';
  let pickerListObserver = null;

  function handheld() {
    return root.dataset.gsbTheme === 'handheld';
  }

  function buzz(cue = 'tick') {
    try { if (window.GSBNative && GSBNative.haptic) GSBNative.haptic(cue); } catch (_) {}
  }

  function activeScene() {
    return document.querySelector('.scene.active');
  }

  function showHome() {
    const homeTab = document.querySelector('.tab[data-scene="home"]');
    if (homeTab) homeTab.click();
    else {
      try { if (typeof window.showScene === 'function') window.showScene('home'); } catch (_) {}
    }
    buzz('tick');
  }

  function ensureSceneBackButtons() {
    document.querySelectorAll('.scene:not(.homeScene)').forEach(scene => {
      if (scene.querySelector(':scope > .hhSceneBack')) return;
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'hhSceneBack';
      button.dataset.noSwipe = '1';
      button.setAttribute('aria-label', '返回主页');
      button.innerHTML = `${BACK_ICON}<span>返回</span>`;
      button.addEventListener('click', e => {
        e.preventDefault();
        e.stopPropagation();
        showHome();
      });
      scene.appendChild(button);
    });
  }

  function rows() {
    return [...document.querySelectorAll('#gsbGamePickerList .gsbGamePick')];
  }

  function normalizePickerRows() {
    rows().forEach(row => {
      const img = row.querySelector('img');
      if (!img) {
        const empty = [...row.children].find(el =>
          el.tagName === 'SPAN' && !el.textContent.trim() && /width\s*:\s*30px/.test(el.getAttribute('style') || '')
        );
        if (empty) empty.style.display = 'none';
        row.classList.add('hhNoIcon');
      } else {
        row.classList.remove('hhNoIcon');
      }
    });
  }

  function applyPickerFilter() {
    const input = document.getElementById('hhPickerSearch');
    const query = (input?.value || '').trim().toLocaleLowerCase();
    const all = rows();
    let visible = 0;
    all.forEach(row => {
      const match = !query || row.textContent.toLocaleLowerCase().includes(query);
      row.hidden = !match;
      if (match) visible++;
    });
    const count = document.getElementById('hhPickerCount');
    if (count) count.textContent = `${visible} / ${all.length}`;
    const empty = document.getElementById('hhPickerEmpty');
    if (empty) empty.hidden = visible !== 0;
  }

  function ensurePickerTools(picker) {
    const panel = picker.querySelector('.gsbGamePickerPanel');
    const head = picker.querySelector('.gsbGamePickerHead');
    const list = picker.querySelector('#gsbGamePickerList');
    if (!panel || !head || !list) return;

    const close = picker.querySelector('#gsbGamePickerClose');
    if (close) {
      close.textContent = '← 返回';
      close.setAttribute('aria-label', '关闭应用选择器');
    }

    if (!picker.querySelector('.hhPickerTools')) {
      const tools = document.createElement('div');
      tools.className = 'hhPickerTools';
      tools.innerHTML = '<input id="hhPickerSearch" class="hhPickerSearch" type="search" autocomplete="off" spellcheck="false" placeholder="搜索应用名称或包名"><span id="hhPickerCount" class="hhPickerCount">0 / 0</span>';
      head.insertAdjacentElement('afterend', tools);
      const input = tools.querySelector('#hhPickerSearch');
      input?.addEventListener('input', applyPickerFilter);
    }

    if (!picker.querySelector('#hhPickerEmpty')) {
      const empty = document.createElement('div');
      empty.id = 'hhPickerEmpty';
      empty.className = 'hhPickerEmpty';
      empty.hidden = true;
      empty.textContent = '没有匹配的应用';
      list.appendChild(empty);
    }

    if (pickerListObserver) pickerListObserver.disconnect();
    pickerListObserver = new MutationObserver(() => {
      normalizePickerRows();
      const empty = picker.querySelector('#hhPickerEmpty');
      if (empty && empty.parentElement !== list) list.appendChild(empty);
      applyPickerFilter();
    });
    pickerListObserver.observe(list, { childList:true });

    normalizePickerRows();
    applyPickerFilter();
  }

  function enhancePicker() {
    const picker = document.getElementById('gsbGamePicker');
    if (!picker) return;
    ensurePickerTools(picker);
  }

  function pickerOpen() {
    return document.getElementById('gsbGamePicker')?.classList.contains('open');
  }

  function closePicker() {
    const picker = document.getElementById('gsbGamePicker');
    if (!picker) return false;
    picker.classList.remove('open');
    buzz('tick');
    return true;
  }

  ensureSceneBackButtons();
  enhancePicker();

  const bodyObserver = new MutationObserver(() => {
    ensureSceneBackButtons();
    enhancePicker();
  });
  bodyObserver.observe(document.body, { childList:true, subtree:true });

  if (stage) {
    new MutationObserver(ensureSceneBackButtons).observe(stage, {
      subtree:true,
      attributes:true,
      attributeFilter:['class']
    });
  }

  window.addEventListener('keydown', e => {
    if (!handheld()) return;
    const key = e.key.toLowerCase();
    if (e.key !== 'Escape' && key !== 'b') return;

    if (pickerOpen()) {
      closePicker();
      e.preventDefault();
      e.stopImmediatePropagation();
      return;
    }

    const scene = activeScene();
    if (scene && !scene.classList.contains('homeScene')) {
      showHome();
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  }, true);

  document.addEventListener('gsb-theme-change', () => {
    ensureSceneBackButtons();
    enhancePicker();
  });
})();
