// detail.js - 政策详情页收藏功能
function parseId(){
    const m = location.pathname.match(/\/(\d+)(\/|$)/);
    return m ? m[1] : null;
}

function showMsg(text){
  try{
    let el = document.getElementById('detail-msg');
    if(!el){
      el = document.createElement('div');
      el.id = 'detail-msg';
      el.className = 'msg';
      const card = document.querySelector('.detail-card');
      if(card){ card.insertBefore(el, card.firstChild); }
    }
    el.textContent = text;
  }catch(_){/* ignore */}
}

// 收藏功能全局变量
let currentPolicyId = null;
let isFavorited = false;
let statusCheckInterval = null;

// 初始化收藏功能
function initFavorite() {
  const favoriteBtn = document.getElementById('favoriteBtn');
  if (!favoriteBtn) return;

  currentPolicyId = parseId();
  if (!currentPolicyId) {
    favoriteBtn.disabled = true;
    favoriteBtn.querySelector('.favorite-text').textContent = '无法收藏';
    return;
  }

  // 立即检查收藏状态
  checkFavoriteStatus();

  // 启动实时状态检查（每30秒检查一次）
  startRealTimeStatusCheck();

  // 绑定点击事件
  favoriteBtn.addEventListener('click', toggleFavorite);
}

// 启动实时状态检查
function startRealTimeStatusCheck() {
  // 每30秒检查一次收藏状态
  statusCheckInterval = setInterval(() => {
    checkFavoriteStatus();
  }, 30000);

  // 页面隐藏时暂停检查，显示时恢复检查
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      clearInterval(statusCheckInterval);
    } else {
      startRealTimeStatusCheck();
    }
  });
}

// 检查收藏状态
async function checkFavoriteStatus() {
  if (!currentPolicyId) return;

  try {
    const response = await fetch(`/api/favorite/check?policyDataId=${currentPolicyId}`, {
      method: 'GET',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
      }
    });

    if (response.ok) {
      const data = await response.json();
      if (data.success) {
        const newStatus = data.data.favorited;

        // 只有状态发生变化时才更新UI
        if (newStatus !== isFavorited) {
          isFavorited = newStatus;
          updateFavoriteButton();
          console.log(`收藏状态更新: ${isFavorited ? '已收藏' : '未收藏'}`);
        }
      }
    }
  } catch (error) {
    console.error('检查收藏状态失败:', error);
  }
}

// 切换收藏状态
async function toggleFavorite() {
  if (!currentPolicyId) return;

  const favoriteBtn = document.getElementById('favoriteBtn');
  if (!favoriteBtn || favoriteBtn.classList.contains('loading')) return;

  // 设置加载状态
  favoriteBtn.classList.add('loading');
  favoriteBtn.disabled = true;

  try {
    let url, method, body;

    if (isFavorited) {
      // 取消收藏
      url = `/api/favorite?policyDataId=${currentPolicyId}`;
      method = 'DELETE';
    } else {
      // 添加收藏
      url = '/api/favorite';
      method = 'POST';
      body = JSON.stringify({ policyDataId: currentPolicyId });
    }

    const response = await fetch(url, {
      method: method,
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
      },
      body: body
    });

    if (response.ok) {
      const data = await response.json();
      if (data.success) {
        // 操作成功后立即检查最新状态
        await checkFavoriteStatus();
        showMsg(data.message || (isFavorited ? '取消收藏成功' : '收藏成功'));
      } else {
        throw new Error(data.message || '操作失败');
      }
    } else {
      throw new Error(`HTTP错误: ${response.status}`);
    }
  } catch (error) {
    console.error('收藏操作失败:', error);
    showMsg('操作失败: ' + (error.message || '未知错误'));
  } finally {
    // 取消加载状态
    if (favoriteBtn) {
      favoriteBtn.classList.remove('loading');
      favoriteBtn.disabled = false;
    }
  }
}

// 更新收藏按钮状态
function updateFavoriteButton() {
  const favoriteBtn = document.getElementById('favoriteBtn');
  if (!favoriteBtn) return;

  const favoriteText = favoriteBtn.querySelector('.favorite-text');
  const favoriteIcon = favoriteBtn.querySelector('.favorite-icon');

  if (isFavorited) {
    favoriteBtn.classList.add('favorited');
    favoriteBtn.classList.remove('not-favorited');
    favoriteText.textContent = '已收藏';
    if (favoriteIcon) {
      favoriteIcon.className = 'favorite-icon fas fa-star';
    }
  } else {
    favoriteBtn.classList.remove('favorited');
    favoriteBtn.classList.add('not-favorited');
    favoriteText.textContent = '收藏';
    if (favoriteIcon) {
      favoriteIcon.className = 'favorite-icon fas fa-star';
    }
  }
}

// 清理资源
function cleanupFavorite() {
  if (statusCheckInterval) {
    clearInterval(statusCheckInterval);
    statusCheckInterval = null;
  }
}

// 原有的loadDetail函数（添加收藏功能初始化）
async function loadDetail(){
  const id = parseId();
  if(!id){ showMsg('无法解析政策ID'); return; }

  try{
    const data = await apiGet('/policy/detail/'+id);
    const title = data.zhTitle||data.originalTitle||'(无标题)';
    document.getElementById('title').textContent=title;
    const ot = (data.originalTitle && data.originalTitle!==data.zhTitle) ? data.originalTitle : null;
    const otEl = document.getElementById('originalTitle');
    if(ot){ otEl.textContent = ot; otEl.style.display='block'; } else { otEl.style.display='none'; }
    document.getElementById('publishDate').textContent=(data.publishDate?('发布日期：'+data.publishDate):'');
    document.getElementById('orgName').textContent=(data.orgName?('机构：'+data.orgName):'');
    document.getElementById('summary').textContent=data.summary||'';
    const su=document.getElementById('sourceUrl');
    if(data.sourceUrl){
      su.href=data.sourceUrl;
      su.textContent = '来源链接';
      su.target = '_blank';
      su.rel = 'noopener';
    } else {
      su.href = '#';
      su.onclick = (e)=>{ e.preventDefault(); showMsg('该条目暂无来源链接'); };
      su.classList.add('disabled');
      su.style.opacity = '.6';
      su.style.cursor = 'not-allowed';
    }

    const pdfOpen=document.getElementById('pdfOpen');
    const togglePdf=document.getElementById('togglePdf');
    if(data.pdfUrl){
      const rawUrl = '/api/pdf/raw/'+id;
      const dlUrl  = '/api/pdf/download/'+id;
      pdfOpen.href = dlUrl;
      pdfOpen.textContent = '下载 PDF';
      togglePdf.style.display='inline-block';
      togglePdf.addEventListener('click',()=>{
        const box=document.getElementById('pdfContainer');
        const frame=document.getElementById('pdfFrame');
        if(box.style.display==='none'){
          box.style.display='block';
          frame.src = rawUrl;
          togglePdf.textContent='关闭预览';
        }else{
          box.style.display='none';
          frame.src='about:blank';
          togglePdf.textContent='内嵌预览';
        }
      });
    } else {
      pdfOpen.href = '#';
      pdfOpen.onclick = (e)=>{ e.preventDefault(); showMsg('该条目暂无 PDF 链接'); };
      pdfOpen.classList.add('disabled');
      pdfOpen.style.opacity = '.6';
      pdfOpen.style.cursor = 'not-allowed';
      togglePdf.style.display='none';
    }

    // 初始化收藏功能
    initFavorite();

  }catch(e){
    console.error(e);
    showMsg('加载详情失败：'+ (e && e.message ? e.message : '未知错误'));
    const su=document.getElementById('sourceUrl');
    const pdfOpen=document.getElementById('pdfOpen');
    const togglePdf=document.getElementById('togglePdf');
    [su,pdfOpen,togglePdf].forEach(el=>{
      if(!el) return;
      el.onclick = (ev)=>ev.preventDefault();
      el.style.opacity = '.6';
      el.style.cursor = 'not-allowed';
    });
  }
}

// 页面卸载时清理资源
window.addEventListener('beforeunload', cleanupFavorite);

loadDetail();