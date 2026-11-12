// 通用工具与认证处理
const API_BASE = '/api';

function getCookie(name){
  const m = document.cookie.match(new RegExp('(?:^|; )'+name.replace(/([.$?*|{}()\[\]\\\/\+^])/g,'\\$1')+'=([^;]*)'));
  return m? decodeURIComponent(m[1]) : null;
}
function setCookie(name,value,days){
  let exp = '';
  if(days){ const d=new Date(); d.setTime(d.getTime()+days*24*60*60*1000); exp = '; expires='+d.toUTCString(); }
  document.cookie = name+'='+encodeURIComponent(value)+'; path=/; SameSite=Lax'+exp;
}
function delCookie(name){ setCookie(name,'',-1); }

function getToken(){
  return localStorage.getItem('jwt') || sessionStorage.getItem('jwt') || getCookie('jwt');
}
function setToken(t){
  try{ localStorage.setItem('jwt', t); }catch(_){/*ignore*/}
  try{ sessionStorage.setItem('jwt', t); }catch(_){/*ignore*/}
  setCookie('jwt', t, 7);
} 
function clearToken(){
  try{ localStorage.removeItem('jwt'); }catch(_){}
  try{ sessionStorage.removeItem('jwt'); }catch(_){}
  delCookie('jwt');
}

function authHeaders(){
  const t = getToken();
  return t? { 'Authorization': 'Bearer '+t } : {};
}

async function apiGet(path){
  const res = await fetch(API_BASE+path,{
    headers:authHeaders(),
    credentials:'include'
  });
  if(res.status===401){clearToken();location.href='/login';return;}
  if(res.status===403){throw new Error('Forbidden: 权限不足或服务未按最新版本运行');}
  const ct = res.headers.get('content-type')||'';
  if(!res.ok){
    const text = await res.text().catch(()=>'');
    throw new Error(`HTTP ${res.status}: ${text.substring(0,200)}`);
  }
  if(ct.includes('application/json')){
    return res.json();
  } else {
    const text = await res.text();
    try{ return JSON.parse(text); }catch{ throw new Error('非JSON响应: '+text.substring(0,200)); }
  }
}

async function apiPost(path, bodyObj){
  const res = await fetch(API_BASE+path,{
    method:'POST',
    headers:{'Content-Type':'application/json',...authHeaders()},
    credentials:'include',
    body: JSON.stringify(bodyObj||{})
  });
  if(res.status===401){clearToken();location.href='/login';return;}
  if(res.status===403){throw new Error('Forbidden');}
  const ct = res.headers.get('content-type')||'';
  if(!res.ok){
    const text = await res.text().catch(()=>'');
    throw new Error(`HTTP ${res.status}: ${text.substring(0,200)}`);
  }
  if(ct.includes('application/json')){
    return res.json();
  } else {
    const text = await res.text();
    try{ return JSON.parse(text); }catch{ throw new Error('非JSON响应: '+text.substring(0,200)); }
  }
}

function goPolicy(id){location.href='/policy/'+id;}

function initAuthLinks(){
  const t = getToken();
  const loginLink = document.getElementById('loginLink');
  const userMenu = document.getElementById('user-menu');

  if(loginLink && userMenu) {
    if (t) {
      loginLink.style.display = 'none';
      userMenu.style.display = 'block';
      const logoutLink = document.getElementById('logoutLink');
      if (logoutLink) {
        logoutLink.onclick = async e => {
          e.preventDefault();
          try { await apiPost('/auth/logout'); } catch (_) { /* 忽略 */ }
          clearToken();
          location.href = '/login';
        };
      }
    } else {
      loginLink.style.display = 'inline';
      userMenu.style.display = 'none';
    }
  }
}

async function enforceAuth(){
  const path = location.pathname;
  const needAuth = (
    path === '/' ||
    path === '/wordcloud' ||
    path === '/agent' ||
    path === '/admin' ||
    /^\/policy\/.+/.test(path)
  );
  if (!needAuth || path === '/login') return;
  try{
    const res = await fetch('/api/auth/session',{credentials:'include'});
    if(!res.ok) throw new Error('session check failed');
    const data = await res.json();
    if(data && data.authenticated === true){
      // 同步服务端会话到本地可读存储（用于前端展示控件切换）；如果 Cookie 里已有 jwt，此处不覆盖
      if(!getToken() && data.username){ setToken(getCookie('jwt')||''); }
      return;
    }
  }catch(_){/* ignore */}
  location.href='/login';
}

document.addEventListener('DOMContentLoaded', ()=>{
  // 页面跳转由后端控制（未登录访问 HTML 将被 302 到 /login）
  initAuthLinks();
});
