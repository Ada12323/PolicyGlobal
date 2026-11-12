async function loadStats(){
  // 调色板：清新柔和
  const palette = ['#8ecae6','#219ebc','#90be6d','#ffd166','#ef476f','#bdb2ff','#a8dadc','#ffc6ff','#cce3de','#ffd6a5'];
  const getColor = (i)=> palette[i % palette.length];

  const wcEl = document.getElementById('wc');
  const barEl = document.getElementById('barChart');
  const pieEl = document.getElementById('pieChart');
  const wc = echarts.init(wcEl);
  const bar = echarts.init(barEl);
  const pie = echarts.init(pieEl);

  // 动态生成自适应椭圆遮罩（基于 Canvas），在容器尺寸变化时重建
  function makeEllipseMaskDynamic(){
    const W = wcEl.clientWidth || 1000;
    const H = wcEl.clientHeight || 800;
    const scale = window.devicePixelRatio || 1;
    const c = document.createElement('canvas');
    c.width = Math.floor(W*scale);
    c.height = Math.floor(H*scale);
    c.style.width = W + 'px';
    c.style.height = H + 'px';
    const ctx = c.getContext('2d');
    // 透明背景 + 实心黑色椭圆：非透明区域（黑色椭圆）为可填充区域
    ctx.clearRect(0,0,c.width,c.height);
    const padX = Math.floor(6*scale);
    const padY = Math.floor(6*scale);
    const rx = (c.width/1.2 - padX);
    const ry = (c.height/1.2 - padY);
    ctx.beginPath();
    ctx.ellipse(c.width/1.2, c.height/1.2, rx, ry, 0, 0, Math.PI*2);
    ctx.fillStyle = '#000';
    ctx.fill();
    const img = new Image();
    img.src = c.toDataURL('image/png');
    return img;
  }
  let maskImage = makeEllipseMaskDynamic();

  // 自适应：窗口变化时重建 mask 并应用配置
  window.addEventListener('resize', ()=>{
    maskImage = makeEllipseMaskDynamic();
    wc.resize();
    applyOption();
    bar.resize();
    pie.resize();
  });

  // 1) 词云数据
  const words = await apiGet('/wordcloud/top?limit=120');
  const wcData = (words||[]).slice(0, 120).map((d,i)=>({
    name: d.word,
    value: d.count,
    textStyle: { color: getColor(i) }
  }));

  // 应用词云配置
  function applyOption(){
    const set = () => wc.setOption({
      backgroundColor: 'rgba(255,255,255,0)',
      tooltip: { show: true },
      series: [{
        type: 'wordCloud',
        shape: 'circle',
        maskImage: maskImage || undefined,
        gridSize: 9,
        sizeRange: [20, 64],
        rotationRange: [0, 0],
        drawOutOfBound: false,
        textStyle: {
          fontFamily: 'Inter, system-ui, sans-serif',
          emphasis: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.18)' }
        },
        data: wcData
      }]
    });
    if(maskImage && !maskImage.complete){
      maskImage.onload = () => set();
    } else {
      set();
    }
  }
  applyOption();

  // 2) 词频 Top15 柱状图（横向）- 按频次降序，从上到下
  const top15 = ([...(words||[])].sort((a,b)=> (b.count||0) - (a.count||0))).slice(0,15);
  bar.setOption({
    grid: { left: 80, right: 20, top: 20, bottom: 20 },
    xAxis: { type: 'value', axisLine:{show:false}, splitLine:{ lineStyle:{ color:'rgba(148,163,184,0.25)'} } },
    yAxis: { type: 'category', data: top15.map(d=>d.word), axisTick:{show:false}, axisLine:{show:false} },
    tooltip: { trigger: 'axis' },
    color: ['#8ecae6'],
    series: [{
      type: 'bar',
      data: top15.map(d=>d.count),
      barWidth: 16,
      itemStyle: {
        borderRadius: [6,6,6,6],
        color: new echarts.graphic.LinearGradient(0,0,1,0,[
          {offset:0,color:'#8ecae6'},{offset:1,color:'#219ebc'}
        ])
      }
    }]
  });

  // 3) 机构分布饼图 Top10 + 其他
  const orgStats = await apiGet('/policy/org/stats');
  const sorted = (orgStats||[]).filter(s=>s.orgName).sort((a,b)=>b.count-a.count);
  const top10 = sorted.slice(0,10);
  const others = sorted.slice(10);
  const sumOthers = others.reduce((acc,s)=>acc+(s.count||0),0);
  const pieData = top10.map((s,i)=>({ name: s.orgName, value: s.count, itemStyle:{ color:getColor(i) } }));
  if(sumOthers>0) pieData.push({ name:'其他', value: sumOthers, itemStyle:{ color: '#cce3de' }});
  pie.setOption({
    tooltip: { trigger: 'item' },
    legend: { type: 'scroll', bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['38%','70%'],
      center: ['50%','45%'],
      avoidLabelOverlap: true,
      label: { formatter: '{b}: {d}%' },
      labelLine: { length: 12, length2: 10 },
      data: pieData
    }]
  });
}

// 由 echarts-loader 在依赖加载成功后调用
window.initStats = function(){
  loadStats();
};
