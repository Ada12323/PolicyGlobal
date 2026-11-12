/* Global pastel particle network background for all pages */
(function(){
  const ID = 'bg-particles-canvas';
  if (document.getElementById(ID)) return;
  const cvs = document.createElement('canvas');
  cvs.id = ID;
  cvs.className = 'particles-canvas';
  cvs.setAttribute('aria-hidden','true');
  // inline styles so it works even if CSS not loaded
  Object.assign(cvs.style, {
    position: 'fixed', left: '0', top: '0', width: '100vw', height: '100vh',
    zIndex: '-1', pointerEvents: 'none', display: 'block'
  });
  document.body.appendChild(cvs);

  const ctx = cvs.getContext('2d');
  let W, H;
  const DPR = Math.min(window.devicePixelRatio || 1, 2);

  // soft pastel palette
  const COLORS = [
    [148, 163, 184], // slate-300
    [125, 211, 252], // sky-300
    [134, 239, 172], // green-300
    [196, 181, 253]  // violet-300
  ];
  const COUNT_BASE = 90;
  let COUNT = COUNT_BASE;
  const MAX_V = 0.35; // gentle speed
  const LINK_DIST = 140;
  const LINK_DIST2 = LINK_DIST*LINK_DIST;

  let ps = [];
  function resize(){
    W = cvs.width  = Math.floor(window.innerWidth * DPR);
    H = cvs.height = Math.floor(window.innerHeight * DPR);
    cvs.style.width = window.innerWidth + 'px';
    cvs.style.height = window.innerHeight + 'px';
    // particle count scales with area, capped for performance
    const area = (window.innerWidth * window.innerHeight) / (1280*720);
    COUNT = Math.max(50, Math.min(140, Math.floor(COUNT_BASE * area)));
    init();
  }

  function init(){
    ps = new Array(COUNT).fill(0).map(()=>({
      x: Math.random()*W,
      y: Math.random()*H,
      vx: (Math.random()*2-1)*MAX_V*DPR,
      vy: (Math.random()*2-1)*MAX_V*DPR,
      c: COLORS[Math.floor(Math.random()*COLORS.length)]
    }));
  }

  let last = performance.now();
  const FRAME = 1000/60;
  function step(now){
    if (now - last < FRAME) { requestAnimationFrame(step); return; }
    last = now;
    // update
    for (const p of ps){
      p.x += p.vx; p.y += p.vy;
      if (p.x < 0 || p.x > W) p.vx *= -1;
      if (p.y < 0 || p.y > H) p.vy *= -1;
    }
    // draw
    ctx.clearRect(0,0,W,H);
    // links
    for (let i=0;i<COUNT;i++){
      const a = ps[i];
      for (let j=i+1;j<COUNT;j++){
        const b = ps[j];
        const dx = a.x-b.x, dy = a.y-b.y;
        const d2 = dx*dx + dy*dy;
        if (d2 < LINK_DIST2*DPR*DPR){
          const t = 1 - (d2 / (LINK_DIST2*DPR*DPR));
          const alpha = 0.15 * t; // very subtle
          ctx.strokeStyle = `rgba(148,163,184,${alpha.toFixed(3)})`;
          ctx.lineWidth = 1 * DPR;
          ctx.beginPath();
          ctx.moveTo(a.x, a.y);
          ctx.lineTo(b.x, b.y);
          ctx.stroke();
        }
      }
    }
    // points
    for (const p of ps){
      const [r,g,b] = p.c; // soft color
      ctx.fillStyle = `rgba(${r},${g},${b},0.7)`;
      ctx.beginPath();
      ctx.arc(p.x, p.y, 2 * DPR, 0, Math.PI*2);
      ctx.fill();
    }
    requestAnimationFrame(step);
  }

  window.addEventListener('resize', resize);
  resize();
  requestAnimationFrame(step);
})();