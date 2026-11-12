// 前端兜底排序：保证“热门”与“最新”视觉上区分明显
function sortByClicksDescThenDateThenId(a,b){
  const cb = (b.clicks ?? 0), ca = (a.clicks ?? 0);
  if(cb !== ca) return cb - ca; // 点击数降序
  const pb = b.publishDate || '', pa = a.publishDate || '';
  if(pb || pa){
    if(!pa) return 1; if(!pb) return -1;
    const d = pb.localeCompare(pa); // 日期降序（新在前）
    if(d !== 0) return d;
  }
  const ib = b.id ?? Number.MAX_SAFE_INTEGER;
  const ia = a.id ?? Number.MAX_SAFE_INTEGER;
  return ib - ia; // id 降序（新在前）
}

function sortByDateDescThenId(a,b){
  const pb = b.publishDate || '', pa = a.publishDate || '';
  if(pb || pa){
    if(!pa) return 1; if(!pb) return -1;
    const d = pb.localeCompare(pa); // 日期降序
    if(d !== 0) return d;
  }
  const ib = b.id ?? Number.MAX_SAFE_INTEGER;
  const ia = a.id ?? Number.MAX_SAFE_INTEGER;
  return ib - ia; // id 降序
}

const pageState = { type: 'latest', page: 0, size: 10, total: 0 };

async function loadHome(){
  try{
    bindTabsPagination();
    await refreshPage();
    loadOrgStats();
  }catch(e){console.error(e);}
}

function updatePager(){
  const pageInfo = document.getElementById('pageInfo');
  const totalPages = Math.max(1, Math.ceil((pageState.total||0)/pageState.size));
  pageInfo.textContent = `${pageState.page+1} / ${totalPages}`;
  document.getElementById('prevPage').disabled = pageState.page <= 0;
  document.getElementById('nextPage').disabled = pageState.page >= totalPages-1;
}

async function refreshPage(){
  const res = await apiGet(`/policy/page/${pageState.type}?page=${pageState.page}&size=${pageState.size}`);
  const arr = Array.isArray(res.items)? res.items : [];
  pageState.total = Number(res.total||0);
  if (pageState.type==='latest') renderList('latestList', arr);
  if (pageState.type==='hot') renderList('hotList', arr);
  if (pageState.type==='recommend') {
    const tabRec = document.getElementById('tabRecommend');
    if (tabRec) tabRec.disabled = false; // 启用推荐按钮
    if (arr.length===0){
      // 兜底：推荐为空时，用热门分页结果
      const backup = await apiGet(`/policy/page/hot?page=${pageState.page}&size=${pageState.size}`);
      const bakArr = Array.isArray(backup.items)? backup.items : [];
      renderList('recommendList', bakArr);
      if (tabRec) tabRec.title = '登录后为你提供更精准推荐（当前显示热门兜底）';
    } else {
      renderList('recommendList', arr);
      if (tabRec) tabRec.removeAttribute('title');
    }
  }
  updatePager();
}

function renderList(id, arr){
  const ul=document.getElementById(id);ul.innerHTML='';
  arr.forEach(p=>{
    const li=document.createElement('li');
    const title = p.zhTitle || p.originalTitle || '(无标题)';
    const summary = p.summary || '';
    const clicksInfo = (id==='hotList' && p.clicks!=null) ? ` <span class="small muted">点击：${p.clicks}</span>` : '';
    li.innerHTML = `
      <div class="card">
        <div style="font-weight:800; font-size:18px; margin-bottom:8px; animation:fadeIn .4s ease both">${escapeHtml(title)}</div>
        <div class="small muted" style="margin-bottom:8px; animation:fadeIn .5s ease both">${p.publishDate?('发布日期：'+p.publishDate):''}${p.orgName? ' · 机构：'+escapeHtml(p.orgName):''}${clicksInfo}</div>
        <div class="small line-2" style="animation:fadeIn .6s ease both">${escapeHtml(summary)}</div>
      </div>`;
    if(p.id) li.onclick=()=>goPolicy(p.id);
    ul.appendChild(li);
  });
}

const btnSearch = document.getElementById('btnSearch');
btnSearch?.addEventListener('click', async ()=>{
  try{
    btnSearch.disabled = true;
    const kw=document.getElementById('kw').value.trim();
    // 过滤条件位于 tabs 右侧
    const start=document.getElementById('startDate')?.value; 
    const end=document.getElementById('endDate')?.value; 
    const limit=document.getElementById('limit')?.value || 20;
    const qs= new URLSearchParams();
    if(kw) qs.append('keyword', kw);
    if(start) qs.append('startDate', start);
    if(end) qs.append('endDate', end);
    if(limit) qs.append('limit', limit);
    const data = await apiGet('/policy/search?'+qs.toString());
    document.getElementById('panel-search').style.display = 'block';
    renderList('searchList', data);
    // 滚动到搜索结果
    document.getElementById('panel-search').scrollIntoView({behavior:'smooth'});
  } finally {
    btnSearch.disabled = false;
  }
});

// 确认按钮：应用日期与数量筛选，也复用搜索逻辑
const btnApply = document.getElementById('btnApplyFilter');
if (btnApply) {
  btnApply.addEventListener('click', ()=> btnSearch?.click());
}

// 当日期或数量改变时可以轻提示按钮可用（不强制自动触发）
['startDate','endDate','limit'].forEach(id=>{
  const el = document.getElementById(id);
  if(!el) return;
  el.addEventListener('change', ()=>{
    btnApply?.classList.add('btn-primary');
    setTimeout(()=>btnApply?.classList.remove('btn-primary'), 600);
  });
});

// 按回车快速搜索
document.getElementById('kw')?.addEventListener('keydown', (e)=>{
  if(e.key === 'Enter'){
    e.preventDefault();
    btnSearch.click();
  }
});

async function loadOrgStats(){
  try{
  const stats = await apiGet('/policy/org/stats');
    const box = document.getElementById('orgStats');
    box.innerHTML='';
    stats.forEach(s=>{
      const d=document.createElement('div');
      d.className='chip';
      d.textContent = `${s.orgName||'未知机构'} · ${s.count}`;
      box.appendChild(d);
    });
  }catch(e){
    console.warn('org stats load fail', e);
    document.getElementById('orgStatsSec').style.display='none';
  }
}

function bindTabsPagination(){
  const buttons = document.querySelectorAll('.tab-btn');
  const panels = document.querySelectorAll('.tab-panel');
  buttons.forEach(btn=>{
    btn.addEventListener('click', async ()=>{
      if(btn.disabled) return;
      buttons.forEach(b=>b.classList.remove('active'));
      btn.classList.add('active');
      const target = btn.getAttribute('data-target');
      panels.forEach(p=>p.classList.remove('active'));
      document.getElementById('panel-'+target).classList.add('active');
      pageState.type = target;
      pageState.page = 0; // 切换标签重置到第一页
      await refreshPage();
    });
  });
  // 分页按钮
  document.getElementById('prevPage')?.addEventListener('click', async ()=>{
    if (pageState.page>0){ pageState.page--; await refreshPage(); }
  });
  document.getElementById('nextPage')?.addEventListener('click', async ()=>{
    const totalPages = Math.max(1, Math.ceil((pageState.total||0)/pageState.size));
    if (pageState.page < totalPages-1){ pageState.page++; await refreshPage(); }
  });
  // 每页数量
  const sel = document.getElementById('pageSizeSel');
  if (sel){
    sel.value = String(pageState.size);
    sel.addEventListener('change', async ()=>{
      const val = Number(sel.value||10);
      pageState.size = (val===15||val===20)? val : 10;
      pageState.page = 0;
      await refreshPage();
    });
  }
}

function escapeHtml(s){
  return String(s).replace(/[&<>"]/g, (c)=>({
    '&':'&amp;',
    '<':'&lt;',
    '>':'&gt;',
    '"':'&quot;'
  }[c]));
}

loadHome();
