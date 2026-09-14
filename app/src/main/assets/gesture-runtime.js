(() => {
  const boot = document.getElementById('boot');
  const overlay = document.getElementById('overlay');
  const cards = () => [...document.querySelectorAll('#carousel .gameCard')];

  let gesture = null;

  function bootDone() {
    return !boot || boot.classList.contains('done');
  }

  function selectAdjacentCard(delta) {
    const list = cards();
    if (!list.length) return;
    let i = list.findIndex(card => card.classList.contains('active'));
    if (i < 0) i = 0;
    i = (i + delta + list.length) % list.length;
    list[i].click();
  }

  function isInteractive(target) {
    return !!target?.closest?.('button,input,a,.switch,.qBtn,.playBtn,.tab,[data-no-swipe]');
  }

  function insideScrollableSurface(target) {
    return !!target?.closest?.('.scene,.libraryMain,.sideMenu,.featurePanel,.rightPanel,.quickBody,.gsbGamePickerList');
  }

  window.addEventListener('pointerdown', e => {
    if (!bootDone()) return;
    if (overlay?.classList.contains('open')) return;
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    if (isInteractive(e.target)) return;

    gesture = {
      id: e.pointerId,
      x: e.clientX,
      y: e.clientY,
      time: performance.now(),
      target: e.target,
      scrollable: insideScrollableSurface(e.target)
    };
  }, { passive: true });

  window.addEventListener('pointerup', e => {
    if (!gesture || gesture.id !== e.pointerId) return;
    const g = gesture;
    gesture = null;

    const dx = e.clientX - g.x;
    const dy = e.clientY - g.y;
    const ax = Math.abs(dx);
    const ay = Math.abs(dy);
    const dt = performance.now() - g.time;

    if (dt > 900 || Math.max(ax, ay) < 42) return;

    // Only the horizontal home carousel owns a custom swipe gesture. Vertical gestures are left
    // completely native so WebView can perform smooth inertial scrolling in menus and panels.
    if (g.target?.closest?.('#carousel,.heroWrap') && ax > ay * 1.15) {
      selectAdjacentCard(dx < 0 ? 1 : -1);
      e.preventDefault();
      return;
    }

    // Never translate a vertical finger movement into a scene change. This used to steal every
    // pan-y gesture from library/system/esports/Quick Menu scroll containers.
    if (g.scrollable && ay >= ax) return;
  }, { passive: false });

  window.addEventListener('pointercancel', () => {
    gesture = null;
  }, { passive: true });

  // Side menu entries should at least retain visual focus when tapped; filtering remains a later
  // data-layer feature and is intentionally not faked here.
  document.addEventListener('click', e => {
    const item = e.target?.closest?.('.sideItem');
    if (!item) return;
    const menu = item.closest('.sideMenu');
    if (!menu) return;
    menu.querySelectorAll('.sideItem').forEach(x => x.classList.toggle('active', x === item));
  });
})();
