function log(msg){console.log('[admin]',msg);}

// 全局状态变量
let allOrgs = [];

// 加载机构列表
async function loadOrgs(){
  try {
    const data = await apiGet('/api/admin/orgs');
    allOrgs = data;
    const ul = document.getElementById('orgList');
    ul.innerHTML = '';

    data.forEach(o => {
      const li = document.createElement('li');
      li.className = 'org-item';
      li.innerHTML = `
        <div class="org-info">
          <div class="org-name">${o.orgName}</div>
          <div class="org-id">ID: ${o.id} ${o.sampleUrl ? '| 样本URL: ' + o.sampleUrl : ''}</div>
        </div>
        <div class="item-actions">
          <button class="btn btn-danger btn-small delete-org" data-id="${o.id}">删除</button>
        </div>
      `;
      ul.appendChild(li);
    });

    // 更新政策表单中的机构下拉列表
    updateOrgDropdown();
  } catch (error) {
    console.error('加载机构失败:', error);
    alert('加载机构失败: ' + error.message);
  }
}
async function setUserRole() {
  try {
    const userId = document.getElementById('setRoleUserId').value.trim();
    const roleType = document.getElementById('setRoleType').value;

    if (!userId) {
      alert('请输入用户ID');
      return;
    }

    const roleData = {
      roleType: parseInt(roleType)
    };

    await apiPut(`/api/admin/users/${userId}/set-role`, roleData);

    // 清空表单并提示成功
    document.getElementById('setRoleUserId').value = '';
    alert('角色设置成功！');

    // 刷新用户列表以显示更新后的角色
    loadUsers();
  } catch (error) {
    console.error('设置用户角色失败:', error);
    alert('设置用户角色失败: ' + error.message);
  }
}

// 添加PUT请求辅助函数
async function apiPut(url, data) {
  const response = await fetch(url, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders()
    },
    body: JSON.stringify(data)
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`HTTP ${response.status}: ${errorText || response.statusText}`);
  }

  return response;
}
// 更新政策表单中的机构下拉列表
function updateOrgDropdown() {
  const orgSelect = document.getElementById('policyOrg');
  const currentValue = orgSelect.value;

  // 清空选项（保留第一个空选项）
  orgSelect.innerHTML = '<option value="">-- 选择机构 --</option>';

  // 添加机构选项
  allOrgs.forEach(org => {
    const option = document.createElement('option');
    option.value = org.id;
    option.textContent = `${org.orgName} (ID: ${org.id})`;
    orgSelect.appendChild(option);
  });

  // 恢复之前选择的值
  if (currentValue) {
    orgSelect.value = currentValue;
  }
}

// 添加机构
async function addOrg(){
  try {
    const name = document.getElementById('newOrgName').value.trim();
    const sampleUrl = document.getElementById('newOrgSampleUrl').value.trim();

    if(!name) {
      alert('请输入机构名称');
      return;
    }

    const orgData = { orgName: name };
    if (sampleUrl) {
      orgData.sampleUrl = sampleUrl;
    }

    await apiPost('/api/admin/orgs', orgData);
    document.getElementById('newOrgName').value = '';
    document.getElementById('newOrgSampleUrl').value = '';
    loadOrgs();
  } catch (error) {
    console.error('添加机构失败:', error);
    alert('添加机构失败: ' + error.message);
  }
}

// 删除机构
async function deleteOrg(orgId) {
  if (!confirm('确定要删除这个机构吗？')) {
    return;
  }

  try {
    await apiDelete(`/api/admin/orgs/${orgId}`);
    loadOrgs();
  } catch (error) {
    console.error('删除机构失败:', error);
    alert('删除机构失败: ' + error.message);
  }
}

// 加载用户列表
async function loadUsers(){
  try {
    const data = await apiGet('/api/admin/users');
    const ul = document.getElementById('userList');
    ul.innerHTML = '';

    data.forEach(u => {
      const li = document.createElement('li');
      li.className = 'user-item';
      li.innerHTML = `
        <div class="user-info">
          <div class="user-name">${u.username} ${u.nickname ? '(' + u.nickname + ')' : ''}</div>
          <div class="user-roles">ID: ${u.id} | 状态: ${u.status === 1 ? '正常' : '禁用'} | 角色: ${u.roles.join(', ')}</div>
        </div>
        <div class="item-actions">
          <button class="btn btn-danger btn-small delete-user" data-id="${u.id}">删除</button>
        </div>
      `;
      ul.appendChild(li);
    });
  } catch (error) {
    console.error('加载用户失败:', error);
    alert('加载用户失败: ' + error.message);
  }
}

// 添加用户 - 修正：只传递username、password、nickname
async function addUser() {
  try {
    const username = document.getElementById('newUsername').value.trim();
    const password = document.getElementById('newPassword').value.trim();
    const nickname = document.getElementById('newNickname').value.trim();

    if (!username || !password) {
      alert('请输入用户名和密码');
      return;
    }

    // 根据Controller，只传递这三个字段
    const userData = {
      username: username,
      password: password,
      nickname: nickname || username
    };

    await apiPost('/api/admin/users', userData);

    // 清空表单
    document.getElementById('newUsername').value = '';
    document.getElementById('newPassword').value = '';
    document.getElementById('newNickname').value = '';

    loadUsers();
  } catch (error) {
    console.error('添加用户失败:', error);
    alert('添加用户失败: ' + error.message);
  }
}

// 删除用户
async function deleteUser(userId) {
  if (!confirm('确定要删除这个用户吗？')) {
    return;
  }

  try {
    await apiDelete(`/api/admin/users/${userId}`);
    loadUsers();
  } catch (error) {
    console.error('删除用户失败:', error);
    alert('删除用户失败: ' + error.message);
  }
}

// 加载政策列表
async function loadPolicies() {
  try {

    const data = await apiGet('/api/policy/search');
    const ul = document.getElementById('policyList');
    ul.innerHTML = '';

    if (data.length === 0) {
      const li = document.createElement('li');
      li.textContent = '暂无政策数据';
      li.style.color = '#aaa';
      li.style.textAlign = 'center';
      li.style.padding = '20px';
      ul.appendChild(li);
      return;
    }

    data.forEach(p => {
      const li = document.createElement('li');
      li.className = 'policy-item';

      const orgName = p.org ? p.org.orgName : '未分配机构';
      const publishDate = p.publishDate ? new Date(p.publishDate).toLocaleDateString() : '未设置';
      const summary = p.summary ? (p.summary.length > 100 ? p.summary.substring(0, 100) + '...' : p.summary) : '无摘要';

      li.innerHTML = `
        <div class="policy-info">
          <div class="policy-title">${p.zhTitle || p.originalTitle || '未命名政策'}</div>
          <div class="policy-meta">
            <span>ID: ${p.id}</span>
            <span>机构: ${orgName}</span>
            <span>发布日期: ${publishDate}</span>
            <span>原文标题: ${p.originalTitle || '无'}</span>
          </div>
          <div class="policy-summary">${summary}</div>
          ${p.sourceUrl ? `<div class="policy-meta"><a href="${p.sourceUrl}" target="_blank">来源链接</a></div>` : ''}
          ${p.pdfUrl ? `<div class="policy-meta"><a href="${p.pdfUrl}" target="_blank">PDF链接</a></div>` : ''}
        </div>
        <div class="policy-actions-small">
          <button class="btn btn-danger btn-small delete-policy" data-id="${p.id}">删除</button>
        </div>
      `;
      ul.appendChild(li);
    });
  } catch (error) {
    console.error('加载政策失败:', error);
    // 如果API端点不存在，显示模拟数据或错误信息
    const ul = document.getElementById('policyList');
    ul.innerHTML = '<li style="color: #ff6b6b; padding: 20px; text-align: center;">加载政策失败: ' + error.message + '</li>';
  }
}

// 添加政策
async function addPolicy() {
  try {
    const zhTitle = document.getElementById('policyZhTitle').value.trim();
    const originalTitle = document.getElementById('policyOriginalTitle').value.trim();
    const summary = document.getElementById('policySummary').value.trim();
    const publishDate = document.getElementById('policyPublishDate').value;
    const sourceUrl = document.getElementById('policySourceUrl').value.trim();
    const pdfUrl = document.getElementById('policyPdfUrl').value.trim();
    const orgId = document.getElementById('policyOrg').value;

    if (!zhTitle) {
      alert('请输入政策中文标题');
      return;
    }

    const policyData = {
      zhTitle: zhTitle,
      originalTitle: originalTitle || null,
      summary: summary || null,
      publishDate: publishDate || null,
      sourceUrl: sourceUrl || null,
      pdfUrl: pdfUrl || null,
      orgId: orgId || null
    };

    await apiPost('/api/admin/policies', policyData);
    clearPolicyForm();
    loadPolicies();
  } catch (error) {
    console.error('添加政策失败:', error);
    alert('添加政策失败: ' + error.message);
  }
}

// 清空政策表单
function clearPolicyForm() {
  document.getElementById('policyZhTitle').value = '';
  document.getElementById('policyOriginalTitle').value = '';
  document.getElementById('policySummary').value = '';
  document.getElementById('policyPublishDate').value = '';
  document.getElementById('policySourceUrl').value = '';
  document.getElementById('policyPdfUrl').value = '';
  document.getElementById('policyOrg').value = '';
}

// 删除政策
async function deletePolicy(policyId) {
  if (!confirm('确定要删除这个政策吗？')) {
    return;
  }

  try {
    await apiDelete(`/api/admin/policies/${policyId}`);
    loadPolicies();
  } catch (error) {
    console.error('删除政策失败:', error);
    alert('删除政策失败: ' + error.message);
  }
}

// API辅助函数
async function apiGet(url) {
  const response = await fetch(url, {
    headers: authHeaders()
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }
  return await response.json();
}

async function apiPost(url, data) {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders()
    },
    body: JSON.stringify(data)
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`HTTP ${response.status}: ${errorText || response.statusText}`);
  }

  return await response.json();
}

async function apiDelete(url) {
  const response = await fetch(url, {
    method: 'DELETE',
    headers: authHeaders()
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`HTTP ${response.status}: ${errorText || response.statusText}`);
  }

  return response;
}

function authHeaders() {
  // 这里应该添加认证头，例如Bearer token
  // 根据实际认证系统调整
  const token = localStorage.getItem('auth_token');
  return token ? { 'Authorization': `Bearer ${token}` } : {};
}

// 初始化页面
function initPage() {
  // 绑定按钮事件
  document.getElementById('btnLoadOrgs').onclick = loadOrgs;
  document.getElementById('btnAddOrg').onclick = addOrg;
  document.getElementById('btnLoadUsers').onclick = loadUsers;
  document.getElementById('btnLoadPolicies').onclick = loadPolicies;
  document.getElementById('btnAddPolicy').onclick = addPolicy;
  document.getElementById('btnClearPolicyForm').onclick = clearPolicyForm;
  document.getElementById('btnAddUser').onclick = addUser;
  document.getElementById('btnSetRole').onclick = setUserRole;

  // 使用事件委托处理动态元素的点击事件
  document.addEventListener('click', function(event) {
    // 处理删除机构
    if (event.target.classList.contains('delete-org')) {
      const orgId = event.target.getAttribute('data-id');
      deleteOrg(orgId);
    }

    // 处理删除用户
    if (event.target.classList.contains('delete-user')) {
      const userId = event.target.getAttribute('data-id');
      deleteUser(userId);
    }

    // 处理删除政策
    if (event.target.classList.contains('delete-policy')) {
      const policyId = event.target.getAttribute('data-id');
      deletePolicy(policyId);
    }
  });

  // 设置发布日期为今天
  const today = new Date().toISOString().split('T')[0];
  document.getElementById('policyPublishDate').value = today;

  // 初始加载数据
  loadOrgs();
  loadUsers();

  // 延迟加载政策，确保机构先加载完成
  setTimeout(loadPolicies, 500);
}

// 页面加载完成后初始化
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initPage);
} else {
  initPage();
}