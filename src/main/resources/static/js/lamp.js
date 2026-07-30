(function () {
  'use strict';

  const screen = document.getElementById('auth-screen');
  if (!screen) return;

  const hero = screen.querySelector('.lamp-hero');
  const cordwrap = screen.querySelector('.lamp-cordwrap');
  const cordline = screen.querySelector('.lamp-cordline');
  const bead = screen.querySelector('.lamp-bead');
  const glow1 = screen.querySelector('.lamp-glow-1');
  const glow2 = screen.querySelector('.lamp-glow-2');
  const shade = screen.querySelector('.lamp-shade');
  const logoBox = screen.querySelector('.lamp-logo-box');
  const shadow = screen.querySelector('.lamp-shadow');
  const hint = screen.querySelector('#lamp-hint');

  let on = false;
  let dragging = false;
  let startY = 0;
  const MAX_PULL = 26;

  function setCord(delta) {
    if (!cordline || !bead) return;
    cordline.setAttribute('y2', 14 + delta);
    bead.setAttribute('cy', 17 + delta);
  }

  function applyState(glowOpacity, bg) {
    if (glow1) glow1.style.opacity = glowOpacity * 0.8;
    if (glow2) glow2.style.opacity = glowOpacity * 0.7;
    if (hero) hero.style.backgroundColor = bg;
    const lit = glowOpacity > 0.4;
    if (shade) shade.style.fill = lit ? 'var(--primary)' : 'var(--border-strong)';
    if (logoBox) logoBox.style.backgroundColor = lit ? 'var(--primary)' : 'var(--purple)';
    if (bead) bead.style.fill = lit ? 'var(--pink)' : 'var(--text-muted)';
    if (cordline) cordline.style.stroke = lit ? 'var(--pink)' : 'var(--border-strong)';
  }

  function flicker(turningOn) {
    const steps = turningOn
      ? [[60, .5, '#0a1420'], [40, 0, '#0a0a16'], [70, .7, '#0a1420'], [30, .1, '#0a0a16'], [120, .9, '#0a1622'], [0, 1, '#0a1622']]
      : [[0, .5, '#0a1420'], [180, 0, '#0a0a16']];
    let t = 0;
    steps.forEach((step) => {
      t += step[0];
      setTimeout(() => applyState(step[1], step[2]), t);
    });
  }

  function toggle() {
    on = !on;
    screen.classList.toggle('is-on', on);
    flicker(on);
    if (shadow) shadow.style.opacity = on ? '.4' : '0';
    if (hint) {
      hint.textContent = on ? 'добро пожаловать' : 'нажмите или потяните шнурок';
      hint.style.color = on ? 'var(--text)' : 'var(--text-muted)';
    }
  }

  if (cordwrap) {
    cordwrap.addEventListener('click', toggle);
    cordwrap.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        toggle();
      }
    });
    cordwrap.addEventListener('pointerdown', (e) => {
      dragging = true;
      startY = e.clientY;
      try { cordwrap.setPointerCapture(e.pointerId); } catch (err) { /* noop */ }
    });
    cordwrap.addEventListener('pointermove', (e) => {
      if (!dragging) return;
      const delta = Math.max(0, Math.min(MAX_PULL, e.clientY - startY));
      setCord(delta);
    });
    const resetCord = () => {
      if (!dragging) return;
      dragging = false;
      setCord(0);
    };
    cordwrap.addEventListener('pointerup', resetCord);
    cordwrap.addEventListener('pointercancel', resetCord);
  }

  // Show/hide password toggle, works for both login and register forms.
  document.querySelectorAll('.eye-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const input = document.getElementById(btn.dataset.target);
      if (!input) return;
      const slash = btn.querySelector('.eye-slash');
      const showing = input.type === 'text';
      input.type = showing ? 'password' : 'text';
      if (slash) slash.setAttribute('opacity', showing ? '0' : '1');
      btn.setAttribute('aria-label', showing ? 'Показать пароль' : 'Скрыть пароль');
    });
  });
})();
