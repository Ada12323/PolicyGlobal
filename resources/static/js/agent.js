const btnCrawl = document.getElementById('btnCrawl');
const chatArea = document.getElementById('chatArea');
const orgSelect = document.getElementById('orgSelect');
const orgSelectHint = document.getElementById('orgSelectHint');
const orgSelectAllBtn = document.getElementById('orgSelectAll');
const orgSelectClearBtn = document.getElementById('orgSelectClear');
const orgToggleBtn = document.getElementById('orgToggleBtn');
const orgSelectWrapper = document.getElementById('orgSelectWrapper');
const orgAddNameInput = document.getElementById('orgAddName');
const orgAddUrlInput = document.getElementById('orgAddUrl');
const orgAddBtn = document.getElementById('orgAddBtn');
const orgAddMsg = document.getElementById('orgAddMsg');
const selectedOrgIds = new Set();
let orgOptions = [];
let orgCollapsed = true;
let lastCreatedOrgId = null;
const ORG_PREVIEW_LIMIT = 8;

// 弹窗管理功能
const modalManager = {
  countdownTimer: null,
  countdownSeconds: 5,

  // 显示弹窗
  show: function(title, message, options = {}) {
    const modal = document.getElementById('customModal');
    const modalTitle = document.getElementById('modalTitle');
    const modalMessage = document.getElementById('modalMessage');
    const confirmBtn = document.getElementById('confirmBtn');
    const cancelBtn = document.getElementById('cancelBtn');
    const closeBtn = document.querySelector('.close-btn');

    // 清除之前的倒计时
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }

    // 设置弹窗内容
    modalTitle.textContent = title;

    // 如果有倒计时选项，设置倒计时
    if (options.countdown && options.redirectUrl) {
      this.countdownSeconds = options.countdownSeconds || 5;
      modalMessage.innerHTML = `${message}<br><br><strong style="color: #5b7cff;">将在 <span id="countdown">${this.countdownSeconds}</span> 秒后自动跳转...</strong>`;

      // 启动倒计时
      this.startCountdown(options.redirectUrl);
    } else {
      modalMessage.textContent = message;
    }

    // 配置按钮
    confirmBtn.textContent = options.confirmText || '确定';

    // 设置确认按钮点击事件
    if (options.redirectUrl) {
      confirmBtn.onclick = () => {
        this.hide();
        window.location.href = options.redirectUrl;
      };
    } else {
      confirmBtn.onclick = options.onConfirm || (() => this.hide());
    }

    // 显示/隐藏取消按钮
    if (options.showCancel) {
      cancelBtn.style.display = 'block';
      cancelBtn.textContent = options.cancelText || '取消';
      cancelBtn.onclick = options.onCancel || (() => this.hide());
    } else {
      cancelBtn.style.display = 'none';
    }

    // 显示弹窗
    modal.style.display = 'flex';
    document.body.style.overflow = 'hidden'; // 防止背景滚动

    // 关闭按钮事件
    closeBtn.onclick = () => {
      this.hide();
      if (options.onCancel) options.onCancel();
    };

    // 点击弹窗外部关闭
    modal.onclick = (event) => {
      if (event.target === modal && options.closeOnBackdrop !== false) {
        this.hide();
        if (options.onCancel) options.onCancel();
      }
    };

    // ESC键关闭
    if (options.closeOnEsc !== false) {
      this.escapeHandler = (event) => {
        if (event.key === 'Escape') {
          this.hide();
          if (options.onCancel) options.onCancel();
        }
      };
      document.addEventListener('keydown', this.escapeHandler);
    }
  },

  // 启动倒计时
  startCountdown: function(redirectUrl) {
    const countdownElement = document.getElementById('countdown');

    this.countdownTimer = setInterval(() => {
      this.countdownSeconds--;

      if (countdownElement) {
        countdownElement.textContent = this.countdownSeconds;
      }

      if (this.countdownSeconds <= 0) {
        this.hide();
        window.location.href = redirectUrl;
      }
    }, 1000);
  },

  // 隐藏弹窗
  hide: function() {
    const modal = document.getElementById('customModal');
    modal.style.display = 'none';
    document.body.style.overflow = ''; // 恢复滚动

    // 清除倒计时
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }

    // 移除ESC事件监听
    if (this.escapeHandler) {
      document.removeEventListener('keydown', this.escapeHandler);
      this.escapeHandler = null;
    }
  },

  // 成功弹窗（带倒计时自动跳转）
  success: function(message, redirectUrl = '/allpolicy', countdownSeconds = 5) {
    this.show('操作成功', message, {
      countdown: true,
      countdownSeconds: countdownSeconds,
      redirectUrl: redirectUrl,
      confirmText: '立即跳转'
    });
  },

  // 错误弹窗
  error: function(message) {
    this.show('操作失败', message, {
      confirmText: '重试'
    });
  },

  // 确认弹窗
  confirm: function(message, onConfirm, onCancel) {
    this.show('确认操作', message, {
      showCancel: true,
      onConfirm: () => {
        this.hide();
        if (onConfirm) onConfirm();
      },
      onCancel: () => {
        this.hide();
        if (onCancel) onCancel();
      }
    });
  }
};

// 弹窗调用函数 - 可以在任何地方使用
function showSuccessModal(message, redirectUrl = '/allpolicy', countdownSeconds = 5) {
  modalManager.success(message, redirectUrl, countdownSeconds);
}

function showErrorModal(message) {
  modalManager.error(message);
}

function showConfirmModal(message, onConfirm, onCancel) {
  modalManager.confirm(message, onConfirm, onCancel);
}

// API 工具函数
function authHeaders() {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token');
  return token ? { 'Authorization': `Bearer ${token}` } : {};
}

async function apiGet(url) {
  const response = await fetch(`${API_BASE}${url}`, {
    headers: { ...authHeaders() },
    credentials: 'include'
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
  return response.json();
}

async function apiPost(url, data) {
  const response = await fetch(`${API_BASE}${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders()
    },
    credentials: 'include',
    body: JSON.stringify(data)
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
  return response.json();
}

// 显示消息气泡
function showBubble(text, type) {
  const div = document.createElement('div');
  div.className = 'bubble' + (type === 'llm' ? ' bubble-llm' : type === 'error' ? ' bubble-thinking' : '');
  if (type === 'llm' && window.marked) {
    div.innerHTML = '<span class="icon">🤖</span> <span class="llm-content">' + marked.parse(text) + '</span>';
    if (window.hljs) div.querySelectorAll('pre code').forEach(block => hljs.highlightElement(block));
  } else {
    const prefix = type === 'llm' ? '<span class="icon">🤖</span> ' : type === 'error' ? '<span class="icon">⚠️</span> ' : '';
    div.innerHTML = prefix + `<span>${(text || '').replace(/\n/g, '<br>')}</span>`;
  }
  chatArea.appendChild(div);
}

// 更新机构选择提示
function updateOrgHint() {
  if (orgSelectHint) {
    orgSelectHint.style.display = selectedOrgIds.size === 0 ? 'block' : 'none';
  }
}

// 渲染机构选项
function renderOrgOptions() {
  if (!orgSelect) return;
  orgSelect.innerHTML = '';
  if (!Array.isArray(orgOptions) || orgOptions.length === 0) {
    const span = document.createElement('span');
    span.className = 'small muted';
    span.textContent = '暂无机构可选';
    orgSelect.appendChild(span);
    updateOrgHint();
    updateOrgToggle();
    return;
  }
  const visibleList = orgCollapsed ? orgOptions.slice(0, ORG_PREVIEW_LIMIT) : orgOptions;
  visibleList.forEach(org => {
    if (!org || org.id == null) return;
    const label = document.createElement('label');
    label.className = 'org-option';
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.value = String(org.id);
    checkbox.checked = selectedOrgIds.has(String(org.id));
    checkbox.addEventListener('change', () => {
      if (checkbox.checked) {
        selectedOrgIds.add(String(org.id));
      } else {
        selectedOrgIds.delete(String(org.id));
      }
      updateOrgHint();
    });
    const span = document.createElement('span');
    span.textContent = org.orgName || `机构#${org.id}`;
    label.appendChild(checkbox);
    label.appendChild(span);
    if (org.sampleUrl) {
      const link = document.createElement('a');
      link.href = org.sampleUrl;
      link.textContent = '示例';
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.className = 'small muted';
      label.appendChild(link);
    }
    orgSelect.appendChild(label);
  });
  if (orgCollapsed && orgOptions.length > visibleList.length) {
    const more = document.createElement('span');
    more.className = 'small muted';
    more.style.marginTop = '6px';
    more.textContent = `已隐藏 ${orgOptions.length - visibleList.length} 个机构，点击"展开更多"查看全部。`;
    orgSelect.appendChild(more);
  }
  updateOrgHint();
  updateOrgToggle();
}

// 加载机构选项
async function loadOrgOptions() {
  if (!orgSelect) return;
  try {
    const previousSelection = new Set(selectedOrgIds);
    const data = await apiGet('/agent/orgs');
    orgOptions = Array.isArray(data) ? data : [];
    const hadSelection = previousSelection.size > 0;
    selectedOrgIds.clear();
    orgOptions.forEach(org => {
      if (!org || org.id == null) return;
      const id = String(org.id);
      if (!hadSelection || previousSelection.has(id) || (lastCreatedOrgId && id === lastCreatedOrgId)) {
        selectedOrgIds.add(id);
      }
    });
    lastCreatedOrgId = null;
    renderOrgOptions();
  } catch (e) {
    orgSelect.innerHTML = `<span class="small muted">加载机构失败：${e.message || e}</span>`;
    updateOrgToggle();
  }
}

// 更新机构折叠状态
function updateOrgToggle() {
  if (!orgToggleBtn || !orgSelectWrapper) return;
  const needsCollapse = Array.isArray(orgOptions) && orgOptions.length > ORG_PREVIEW_LIMIT;
  if (!needsCollapse) {
    orgCollapsed = false;
    orgSelectWrapper.setAttribute('data-collapsed', 'false');
    orgToggleBtn.style.display = 'none';
    return;
  }
  orgToggleBtn.style.display = '';
  orgSelectWrapper.setAttribute('data-collapsed', orgCollapsed ? 'true' : 'false');
  orgToggleBtn.textContent = orgCollapsed ? '展开更多' : '收起';
}

// 显示机构添加消息
function showOrgAddMessage(text, type = 'info') {
  if (!orgAddMsg) return;
  orgAddMsg.style.display = 'block';
  orgAddMsg.textContent = text;
  if (type === 'error') {
    orgAddMsg.classList.remove('text-success');
    orgAddMsg.style.color = '#b91c1c';
  } else if (type === 'success') {
    orgAddMsg.classList.add('text-success');
    orgAddMsg.style.color = '';
  } else {
    orgAddMsg.classList.remove('text-success');
    orgAddMsg.style.color = '';
  }
}

// 初始化机构选择按钮事件
if (orgSelectAllBtn) {
  orgSelectAllBtn.addEventListener('click', () => {
    orgOptions.forEach(org => {
      if (org && org.id != null) {
        selectedOrgIds.add(String(org.id));
      }
    });
    renderOrgOptions();
  });
}

if (orgSelectClearBtn) {
  orgSelectClearBtn.addEventListener('click', () => {
    selectedOrgIds.clear();
    renderOrgOptions();
  });
}

if (orgToggleBtn) {
  orgToggleBtn.addEventListener('click', () => {
    orgCollapsed = !orgCollapsed;
    renderOrgOptions();
  });
}

// 添加新机构
if (orgAddBtn) {
  orgAddBtn.addEventListener('click', async () => {
    if (!orgAddNameInput) return;
    const name = (orgAddNameInput.value || '').trim();
    const url = (orgAddUrlInput?.value || '').trim();
    if (!name) {
      showOrgAddMessage('机构名称不能为空。', 'error');
      return;
    }
    orgAddBtn.disabled = true;
    showOrgAddMessage('正在提交...', 'info');
    try {
      const payload = { orgName: name, sampleUrl: url || null };
      const created = await apiPost('/agent/orgs', payload);
      lastCreatedOrgId = created && created.id != null ? String(created.id) : null;
      showOrgAddMessage('新增机构成功，已自动更新列表。', 'success');
      orgAddNameInput.value = '';
      if (orgAddUrlInput) orgAddUrlInput.value = '';
      await loadOrgOptions();
    } catch (e) {
      showOrgAddMessage(`新增失败：${e?.message || e}`, 'error');
    } finally {
      orgAddBtn.disabled = false;
    }
  });
}

// 加载机构选项
loadOrgOptions();

// 主爬取按钮点击事件
btnCrawl.addEventListener('click', async () => {
  const start = document.getElementById('aStart').value;
  const end = document.getElementById('aEnd').value;
  const limit = document.getElementById('aLimit').value;
  const orgIds = Array.from(selectedOrgIds);

  if (!start || !end) {
    showBubble('请先选择开始与结束日期', 'error');
    return;
  }
  if (selectedOrgIds.size === 0) {
    updateOrgHint();
    showBubble('请至少勾选一个机构后再试。', 'error');
    return;
  }

  btnCrawl.disabled = true;
  chatArea.innerHTML = '';

  const thinkingBubble = document.createElement('div');
  thinkingBubble.className = 'bubble bubble-thinking';
  thinkingBubble.innerHTML = `<span class="icon">🧠</span> <span>智能体正在深度思考并准备入库</span> <span class="thinking-dots"><span></span><span></span><span></span></span>`;
  chatArea.appendChild(thinkingBubble);

  let llmBubble = null;
  let rawContent = '';
  let eventSource = null;

  // 构建流式调用参数
  const streamParams = new URLSearchParams({
    startDate: start,
    endDate: end,
    limit: String(limit),
    maxTokens: String(160000)
  });
  orgIds.forEach(id => streamParams.append('orgIds', id));

  try {
    // 使用新的流式入库接口
    eventSource = new EventSource(`${API_BASE}/agent/probe/stream-ingest?${streamParams.toString()}`);

    eventSource.onmessage = function (event) {
      if (event.data.startsWith('[THINKING]')) {
        thinkingBubble.innerHTML = `<span class="icon">🧠</span> <span>${event.data.replace('[THINKING]', '').trim()}</span>`;
        return;
      }

      if (!llmBubble) {
        llmBubble = document.createElement('div');
        llmBubble.className = 'bubble bubble-llm';
        llmBubble.innerHTML = `<span class="icon">🤖</span> <span class="llm-content"></span>`;
        chatArea.appendChild(llmBubble);
      }

      const chunk = event.data ? event.data.replace(/\\n/g, '\n') : '';
      rawContent += chunk;

      const renderTarget = llmBubble.querySelector('.llm-content');
      if (window.marked) {
        renderTarget.innerHTML = marked.parse(rawContent);
      } else {
        renderTarget.textContent = rawContent;
      }
      if (window.hljs) {
        llmBubble.querySelectorAll('pre code').forEach(block => hljs.highlightElement(block));
      }
    };

    // 监听入库结果事件
    eventSource.addEventListener('ingest-result', function (event) {
      if (eventSource) eventSource.close();

      const result = JSON.parse(event.data);
      thinkingBubble.innerHTML = `<span class="icon">✅</span> <span>数据入库完成</span>`;

      // 显示入库结果
      showBubble('入库处理结果：\n' + JSON.stringify(result, null, 2), 'llm');

      // 显示成功弹窗
      const successMessage = result.inserted > 0
          ? `智能体检索已完成！成功入库 ${result.inserted} 条数据。`
          : '智能体检索已完成，但没有新数据入库。';

      showSuccessModal(successMessage, '/allpolicy', 5);
      btnCrawl.disabled = false;
    });

    // 监听入库错误事件
    eventSource.addEventListener('ingest-error', function (event) {
      if (eventSource) eventSource.close();

      thinkingBubble.innerHTML = `<span class="icon">❌</span> <span>入库失败</span>`;
      showBubble('入库失败：' + event.data, 'error');
      btnCrawl.disabled = false;
    });

    // 处理流式错误
    eventSource.onerror = function () {
      if (eventSource) eventSource.close();
      btnCrawl.disabled = false;
      showBubble('流式输出中断，请稍后重试。', 'error');
    };

    // 处理连接打开
    eventSource.onopen = function () {
      thinkingBubble.innerHTML = `<span class="icon">🧠</span> <span>LLM已开始流式输出...</span>`;
    };

  } catch (e) {
    btnCrawl.disabled = false;
    showBubble('请求失败：' + (e?.message || e), 'error');
  }
});

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
  console.log('智能体检索页面已加载');
});