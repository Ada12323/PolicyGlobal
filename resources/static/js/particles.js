/* Minimal particle network background - lightweight and dependency-free */
(function(){
  const FPS_CAP = 60;
  const FRAME_MS = 1000/FPS_CAP;
  const MOBILE_MAX = 60; // mobile particle count
  const DESKTOP_MAX = 100; // desktop particle count
  const MAX_V = 0.5; // max velocity per axis
  const LINK_DIST = 140; // px
  const LINK_DIST2 = LINK_DIST*LINK_DIST;
  const DOT_SIZE = 2.0;
  const LINE_BASE_ALPHA = 0.25;
  const COLOR = { r: 148, g: 163, b: 184 }; // #94a3b8 slate-300

  function init(canvas){
    const ctx = canvas.getContext('2d');
    let W = canvas.width = window.innerWidth;
    let H = canvas.height = window.innerHeight;
    const isMobile = Math.min(W,H) < 680;
    const COUNT = isMobile ? MOBILE_MAX : DESKTOP_MAX;

    const ps = new Array(COUNT).fill(0).map(()=>({
      x: Math.random()*W,
      y: Math.random()*H,
      vx: (Math.random()*2-1)*MAX_V,
      vy: (Math.random()*2-1)*MAX_V
    }));

    let last = performance.now();
    function step(now){
      const dt = now - last; if (dt < FRAME_MS) { requestAnimationFrame(step); return; }
      last = now;
      // update
      for (let p of ps){
        p.x += p.vx; p.y += p.vy;
        if (p.x < 0 || p.x > W) p.vx *= -1;
        if (p.y < 0 || p.y > H) p.vy *= -1;
        if (p.x < 0) p.x = 0; else if (p.x > W) p.x = W;
        if (p.y < 0) p.y = 0; else if (p.y > H) p.y = H;
      }
      // draw
      ctx.clearRect(0,0,W,H);
      // points
      ctx.fillStyle = `rgba(${COLOR.r},${COLOR.g},${COLOR.b},0.8)`;
      for (let p of ps){
        ctx.beginPath();
        ctx.arc(p.x, p.y, DOT_SIZE, 0, Math.PI*2);
        ctx.fill();
      }
      // lines
      for (let i=0;i<COUNT;i++){
        const a = ps[i];
        for (let j=i+1;j<COUNT;j++){
          const b = ps[j];
          const dx = a.x-b.x, dy = a.y-b.y;
          const d2 = dx*dx + dy*dy;
          if (d2 < LINK_DIST2){
            const alpha = LINE_BASE_ALPHA * (1 - d2/LINK_DIST2);
            ctx.strokeStyle = `rgba(${COLOR.r},${COLOR.g},${COLOR.b},${alpha.toFixed(3)})`;
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(a.x, a.y);
            ctx.lineTo(b.x, b.y);
            ctx.stroke();
          }
        }
      }
      requestAnimationFrame(step);
    }

    function onResize(){
      W = canvas.width = window.innerWidth;
      H = canvas.height = window.innerHeight;
    }
    window.addEventListener('resize', onResize);
    requestAnimationFrame(step);

    return {
      destroy(){ window.removeEventListener('resize', onResize); }
    };
  }

  // expose init to global for ease of use
  window.ParticlesBG = { init };
})();
