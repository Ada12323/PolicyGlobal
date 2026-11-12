(function(){
  function loadScript(url){
    return new Promise(function(resolve, reject){
      var s = document.createElement('script');
      s.src = url; s.async = true; s.crossOrigin = 'anonymous';
      s.onload = function(){ resolve(url); };
      s.onerror = function(){ reject(new Error('fail: '+url)); };
      document.head.appendChild(s);
    });
  }

  async function trySeq(urls){
    let lastErr;
    for(const u of urls){
      try{ await loadScript(u); return u; }catch(e){ lastErr = e; }
    }
    throw lastErr || new Error('All sources failed');
  }

  function showFail(){
    var ids = ['wc','barChart','pieChart'];
    ids.forEach(function(id){
      var el = document.getElementById(id);
      if(el){
        el.innerHTML = '<div style="padding:16px;color:#0f172a;opacity:.8">无法加载图表依赖（ECharts）。请检查网络或改为本地离线包。<br/>可联系管理员配置本地静态资源。</div>';
      }
    });
  }

  (async function init(){
    try{
      // 依次尝试多个 CDN 加载 echarts
      await trySeq([
        // 优先使用用户提供且已验证可达的固定版本
        'https://unpkg.com/echarts@5.6.0/dist/echarts.min.js',
        // 其余候选源
        'https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js',
        'https://cdnjs.cloudflare.com/ajax/libs/echarts/5.5.1/echarts.min.js',
        'https://unpkg.com/echarts@5/dist/echarts.min.js',
        'https://cdn.staticfile.org/echarts/5.4.3/echarts.min.js'
      ]);
      // 然后加载 wordcloud 扩展
      await trySeq([
        // 优先使用用户提供且已验证可达的固定版本
        'https://unpkg.com/echarts-wordcloud@2.1.0/dist/echarts-wordcloud.min.js',
        // 其余候选源
        'https://cdn.jsdelivr.net/npm/echarts-wordcloud@2/dist/echarts-wordcloud.min.js',
        'https://unpkg.com/echarts-wordcloud@2/dist/echarts-wordcloud.min.js'
      ]);
      if(window.initStats){ window.initStats(); }
    }catch(e){
      console.error('[echarts-loader] all sources failed', e);
      showFail();
    }
  })();
})();
