document.getElementById('btnLogin').addEventListener('click', async ()=>{
  const username=document.getElementById('username').value.trim();
  const password=document.getElementById('password').value.trim();
  const msg = document.getElementById('login-msg');
  if(!username||!password){msg.textContent='请输入用户名和密码';return;}
  try{
    const res = await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password})});
    if(!res.ok){msg.textContent='登录失败';return;}
    const data = await res.json();
  if(!data || !data.token){ msg.textContent='登录失败：未返回令牌'; return; }
  setToken(data.token);
  location.href='/';
  }catch(e){msg.textContent='网络错误';}
});
