(() => {
  const boot = document.getElementById('boot');
  const overlay = document.getElementById('overlay');
  const carousel = document.getElementById('carousel');
  const cards = () => [...document.querySelectorAll('#carousel .gameCard')];
  const scenes = ['home','library','esports','system'];

  let gesture = null;

  function bootDone() {
    return !boot || boot.classList.contains('done');
  }

  function currentScene() {
    const active = document.querySelector('.scene.active');
    return active?.dataset?.scene || 'home';
  }

  function showScene(name) {
    const tab = document.querySelector(`.tab[data-scene="${name}"]`);
    if (tab) {
      tab.click();
      return;
    }
    document.querySelectorAll('.scene').forEach(s => s.classList.toggle('active', s.dataset.scene === name));
    document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t.dataset.scene === name));
  }

  function moveScene(delta) {
    let i = scenes.indexOf(currentScene());
    if (i < 0) i = 0;
    i = (i + delta + scenes.length) % scenes.length;
    showScene(scenes[i]);
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
      target: e.target
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

    if (currentScene() === 'home' && g.target?.closest?.('#carousel,.heroWrap') && ax > ay * 1.15) {
      selectAdjacentCard(dx < 0 ? 1 : -1);
      e.preventDefault();
      return;
    }

    if (ay > ax * 1.15) {
      moveScene(dy < 0 ? 1 : -1);
      e.preventDefault();
    }
  }, { passive: false });

  window.addEventListener('pointercancel', () => {
    gesture = null;
  }, { passive: true });
})();
