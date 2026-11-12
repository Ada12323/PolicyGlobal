/* Parallax ambient blobs inspired by modern landing pages (Feishu-like) */
(function(){
  const root = document.querySelector('.ambient');
  if (!root) return;
  const blobs = Array.from(root.querySelectorAll('.blob'));
  let ry = 0, rx = 0;
  function onMove(e){
    const cx = window.innerWidth/2;
    const cy = window.innerHeight/2;
    const x = (e.clientX ?? (e.touches && e.touches[0].clientX) ?? cx) - cx;
    const y = (e.clientY ?? (e.touches && e.touches[0].clientY) ?? cy) - cy;
    rx = x / cx; ry = y / cy;
  }
  function step(){
    // subtle parallax: different depth per blob
    blobs.forEach((b, i)=>{
      const depth = (i+1) * 4; // 4,8,12,16 px max shift
      const tx = (rx * depth).toFixed(2);
      const ty = (ry * depth).toFixed(2);
      b.style.transform = `translate(${tx}px, ${ty}px)`;
    });
    requestAnimationFrame(step);
  }
  window.addEventListener('mousemove', onMove, {passive:true});
  window.addEventListener('touchmove', onMove, {passive:true});
  requestAnimationFrame(step);
})();
