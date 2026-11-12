(function () {
  const MAX_HISTORY = 10;
  let conversation = [];
  let isSending = false;
  let dragState = null;
  let resizeState = null;
  let isPanelOpen = false;
  let suppressFabClick = false;
  let resizeHoverRAF = null;
  const STORAGE_KEY = 'assistant-widget-state-v1';
  const PANEL_MIN_HEIGHT = 320;
  const PANEL_MAX_HEIGHT = 560;
  const PANEL_MIN_WIDTH = 300;
  const PANEL_MAX_WIDTH = 520;

  function loadState() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return {};
      const parsed = JSON.parse(raw);
      return typeof parsed === 'object' && parsed ? parsed : {};
    } catch (err) {
      console.warn('[assistant] 无法读取状态', err);
      return {};
    }
  }

  function persistState(next) {
    try {
      const current = loadState();
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...current, ...next }));
    } catch (err) {
      console.warn('[assistant] 无法保存状态', err);
    }
  }

  function escapeHtml(text) {
    return text
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function renderMarkdown(text) {
    if (!text) {
      return "";
    }
    let html = escapeHtml(text);
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
    html = html.replace(/\n\n+/g, '</p><p>');
    html = html.replace(/\n/g, '<br>');
    return '<p>' + html + '</p>';
  }

  function buildSourcesTable(sources) {
    if (!Array.isArray(sources) || sources.length === 0) {
      return null;
    }
    const columns = Object.keys(sources[0] || {});
    if (columns.length === 0) {
      return null;
    }
    const table = document.createElement('table');
    table.className = 'assistant-sources-table';
    const thead = document.createElement('thead');
    const headerRow = document.createElement('tr');
    columns.forEach(col => {
      const th = document.createElement('th');
      th.textContent = col;
      headerRow.appendChild(th);
    });
    thead.appendChild(headerRow);
    table.appendChild(thead);

    const tbody = document.createElement('tbody');
    sources.forEach(row => {
      const tr = document.createElement('tr');
      columns.forEach(col => {
        const td = document.createElement('td');
        const value = row[col];
        if (typeof value === 'string' && /^https?:\/\//i.test(value)) {
          const a = document.createElement('a');
          a.href = value;
          a.target = '_blank';
          a.rel = 'noopener noreferrer';
          a.textContent = value;
          td.appendChild(a);
        } else {
          td.textContent = value == null ? '' : value;
        }
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    return table;
  }

  function createMessageBubble(role, content) {
    const bubble = document.createElement('div');
    bubble.className = `assistant-bubble assistant-${role}`;
    bubble.innerHTML = renderMarkdown(content);
    return bubble;
  }

  function createLoadingBubble() {
    const bubble = document.createElement('div');
    bubble.className = 'assistant-bubble assistant-assistant assistant-loading';
    bubble.innerHTML = '<div class="assistant-thinking"><span></span><span></span><span></span></div>';
    bubble.dataset.loader = 'true';
    return bubble;
  }
  function renderAssistantContent(target, data) {
    target.innerHTML = renderMarkdown(data.reply || '');
    if (Array.isArray(data.sources) && data.sources.length) {
      const title = document.createElement('div');
      title.className = 'assistant-sources-title';
      title.textContent = '数据来源（最新查询）';
      target.appendChild(title);
      const table = buildSourcesTable(data.sources);
      if (table) {
        const scroll = document.createElement('div');
        scroll.className = 'assistant-sources-scroll';
        scroll.appendChild(table);
        target.appendChild(scroll);
      }
    }
    appendWarningChips(target, data.thoughts);
  }
  
  function createStreamingBubble() {
    const bubble = document.createElement('div');
    bubble.className = 'assistant-bubble assistant-assistant assistant-streaming';
    bubble.innerHTML = '<div class="assistant-stream-text"></div><div class="assistant-stream-cursor"></div>';
    return bubble;
  }
  
  function parseSseChunk(raw) {
    const lines = raw.split(/\r?\n/);
    let event = 'message';
    const dataLines = [];
    lines.forEach(line => {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart());
      }
    });
    return {
      event,
      data: dataLines.join('\n')
    };
  }
  
  async function consumeSse(response, onMessage) {
    const reader = response.body?.getReader?.();
    if (!reader) {
      throw new Error('无法读取流式响应');
    }
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    while (true) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      let boundary;
      while ((boundary = buffer.indexOf('\n\n')) >= 0) {
        const chunk = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const trimmed = chunk.trim();
        if (!trimmed) {
          continue;
        }
        const message = parseSseChunk(trimmed);
        await onMessage(message);
      }
    }
    const tail = buffer.trim();
    if (tail) {
      const message = parseSseChunk(tail);
      await onMessage(message);
    }
  }

  function appendWarningChips(container, thoughts) {
    if (!Array.isArray(thoughts) || thoughts.length === 0) {
      return;
    }
    const warnings = thoughts
      .map(t => (typeof t === 'string' ? t.trim() : ''))
      .filter(t => t && !t.startsWith('[MODEL-RAW]'));
    if (!warnings.length) {
      return;
    }
    const wrap = document.createElement('div');
    wrap.className = 'assistant-warnings';
    warnings.forEach(w => {
      const chip = document.createElement('span');
      chip.className = 'assistant-warning-chip';
      chip.textContent = w.replace(/^\[.+?\]\s*/, '');
      wrap.appendChild(chip);
    });
    container.appendChild(wrap);
  }

  function scrollToBottom(list) {
    requestAnimationFrame(() => {
      list.scrollTop = list.scrollHeight;
    });
  }

  function init() {
    if (typeof window === 'undefined' || !document.body) {
      return;
    }
    if (document.getElementById('assistant-widget-root')) {
      return;
    }
    if (typeof authHeaders !== 'function') {
      console.warn('[assistant] authHeaders 未定义，浮动聊天将不初始化');
      return;
    }

    const root = document.createElement('div');
    root.id = 'assistant-widget-root';
    root.innerHTML = `
      <button class="assistant-fab" id="assistantFab" title="打开政策分析助手" draggable="false">
        <span class="assistant-fab-glass"></span>
        <span class="assistant-fab-icon">🤖</span>
        <span class="assistant-fab-label">
          <span class="assistant-fab-badge">政策助手</span>
          <span class="assistant-fab-hint">按住拖动 · 点击展开</span>
        </span>
      </button>
      <div class="assistant-panel" id="assistantPanel" aria-hidden="true" draggable="false">
        <div class="assistant-header">
          <div class="assistant-title">
            <span class="assistant-title-icon">📊</span>
            <span>政策分析助手</span>
          </div>
          <div class="assistant-header-actions">
            <button class="assistant-header-btn" id="assistantClear" title="清空对话">重置</button>
            <button class="assistant-header-btn" id="assistantClose" title="关闭">✕</button>
          </div>
        </div>
        <div class="assistant-history" id="assistantHistory"></div>
        <form class="assistant-input" id="assistantForm">
          <textarea id="assistantTextarea" rows="2" placeholder="有什么政策要分析？比如：25年9月15日到24日美国政府问责局发布了什么"></textarea>
          <div class="assistant-input-actions">
            <button type="submit" class="assistant-send-btn" id="assistantSend">发送</button>
          </div>
        </form>
        <div class="assistant-footnote">优先实时查询数据库，避免臆测。</div>
        <div class="assistant-resize-handle assistant-resize-handle--bottom" id="assistantResizeHandleBottom" title="拖动调整高度"></div>
        <div class="assistant-resize-handle assistant-resize-handle--right" id="assistantResizeHandleRight" title="拖动调整宽度"></div>
        <div class="assistant-resize-handle assistant-resize-handle--corner" id="assistantResizeHandleCorner" title="拖动调整大小"></div>
      </div>
    `;
    document.body.appendChild(root);

    const panel = root.querySelector('#assistantPanel');
    const fab = root.querySelector('#assistantFab');
    const closeBtn = root.querySelector('#assistantClose');
    const clearBtn = root.querySelector('#assistantClear');
    const form = root.querySelector('#assistantForm');
    const textarea = root.querySelector('#assistantTextarea');
    const history = root.querySelector('#assistantHistory');
    const resizeHandleBottom = root.querySelector('#assistantResizeHandleBottom');
    const resizeHandleRight = root.querySelector('#assistantResizeHandleRight');
    const resizeHandleCorner = root.querySelector('#assistantResizeHandleCorner');
    const savedState = loadState();

    if (savedState.panelHeight) {
      panel.style.height = `${Math.min(Math.max(savedState.panelHeight, PANEL_MIN_HEIGHT), PANEL_MAX_HEIGHT)}px`;
    }
    if (savedState.panelWidth) {
      panel.style.width = `${Math.min(Math.max(savedState.panelWidth, PANEL_MIN_WIDTH), PANEL_MAX_WIDTH)}px`;
    }
    if (savedState.fabPosition) {
      root.style.setProperty('--assistant-root-x', `${savedState.fabPosition.x}px`);
      root.style.setProperty('--assistant-root-y', `${savedState.fabPosition.y}px`);
    }

    function openPanel() {
      panel.classList.add('open');
      panel.setAttribute('aria-hidden', 'false');
      isPanelOpen = true;
      root.classList.add('assistant-open');
      persistState({ panelOpen: true });
      textarea.focus();
    }

    function closePanel() {
      panel.classList.remove('open');
      panel.setAttribute('aria-hidden', 'true');
      isPanelOpen = false;
      root.classList.remove('assistant-open');
      persistState({ panelOpen: false });
    }

    if (savedState.panelOpen) {
      requestAnimationFrame(() => openPanel());
    }

    if (window.innerWidth <= 640) {
      panel.style.width = '';
    }

    fab.addEventListener('click', () => {
      if (dragState || suppressFabClick) {
        suppressFabClick = false;
        return;
      }
      if (panel.classList.contains('open')) {
        closePanel();
      } else {
        openPanel();
      }
    });
    closeBtn.addEventListener('click', closePanel);

    clearBtn.addEventListener('click', () => {
      if (isSending) return;
      conversation = [];
      history.innerHTML = '';
      appendAssistantGreeting(history);
    });

    function appendAssistantGreeting(target) {
      const greeting = createMessageBubble('assistant', '你好，我是政策分析助手。请告诉我想查询的政策范围，我会先构造 SQL 从数据库检索再给你分析。');
      target.appendChild(greeting);
      scrollToBottom(target);
    }

    function appendUserMessage(text) {
      const bubble = createMessageBubble('user', text);
      history.appendChild(bubble);
      scrollToBottom(history);
    }

    function appendAssistantResponse(data) {
      const wrap = document.createElement('div');
      wrap.className = 'assistant-bubble assistant-assistant';
      renderAssistantContent(wrap, data);
      history.appendChild(wrap);
      scrollToBottom(history);
    }

    function appendErrorBubble(message) {
      const bubble = document.createElement('div');
      bubble.className = 'assistant-bubble assistant-error';
      bubble.textContent = message;
      history.appendChild(bubble);
      scrollToBottom(history);
    }

    function trimHistoryForRequest() {
      return conversation.slice(-MAX_HISTORY).map(item => ({ role: item.role, content: item.content }));
    }

    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (isSending) {
        return;
      }
      const text = textarea.value.trim();
      if (!text) {
        return;
      }
      textarea.value = '';
      appendUserMessage(text);
      conversation.push({ role: 'user', content: text });

      const streamingBubble = createStreamingBubble();
      const streamTextEl = streamingBubble.querySelector('.assistant-stream-text');
      history.appendChild(streamingBubble);
      scrollToBottom(history);

      const payload = {
        messages: trimHistoryForRequest(),
        stream: true
      };

      isSending = true;
      let aggregatedText = '';
      let meta = { sources: [], thoughts: [] };
      try {
        const response = await fetch('/api/assistant/chat', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...authHeaders()
          },
          body: JSON.stringify(payload),
          credentials: 'include'
        });
        if (response.status === 401) {
          streamingBubble.remove();
          appendErrorBubble('当前未登录，请先登录后再试。');
          clearToken?.();
          closePanel();
          return;
        }
        if (!response.ok) {
          const textBody = await response.text();
          throw new Error(textBody || `HTTP ${response.status}`);
        }

        await consumeSse(response, async ({ event, data }) => {
          if (event === 'delta') {
            aggregatedText += data;
            streamTextEl.textContent = aggregatedText;
            scrollToBottom(history);
          } else if (event === 'meta') {
            if (data) {
              try {
                const parsed = JSON.parse(data);
                meta = {
                  sources: Array.isArray(parsed.sources) ? parsed.sources : [],
                  thoughts: Array.isArray(parsed.thoughts) ? parsed.thoughts : []
                };
              } catch (err) {
                console.debug('[assistant] meta 解析失败', err);
              }
            }
          } else if (event === 'done') {
            if (data) {
              try {
                const parsed = JSON.parse(data);
                if (parsed.reply && !aggregatedText) {
                  aggregatedText = parsed.reply;
                  streamTextEl.textContent = aggregatedText;
                }
              } catch (err) {
                console.debug('[assistant] done 数据解析失败', err);
              }
            }
          } else if (event === 'error') {
            throw new Error(data || '助手流式输出失败');
          }
        });

        const finalData = {
          reply: aggregatedText,
          sources: meta.sources,
          thoughts: meta.thoughts
        };
        renderAssistantContent(streamingBubble, finalData);
        streamingBubble.classList.remove('assistant-streaming');
        if (finalData.reply) {
          conversation.push({ role: 'assistant', content: finalData.reply });
        }
      } catch (err) {
        console.error('[assistant] 流式请求失败', err);
        streamingBubble.remove();
        appendErrorBubble(err.message || '助手暂时不可用，请稍后再试');
      } finally {
        isSending = false;
      }
    });

    textarea.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        form.requestSubmit();
      }
    });

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && panel.classList.contains('open')) {
        closePanel();
      }
    });

    function startDrag(event) {
      const isTouch = event.type.startsWith('touch');
      const point = isTouch ? event.touches[0] : event;
      dragState = {
        startX: point.clientX,
        startY: point.clientY,
        initialX: parseFloat(getComputedStyle(root).getPropertyValue('--assistant-root-x') || '0'),
        initialY: parseFloat(getComputedStyle(root).getPropertyValue('--assistant-root-y') || '0'),
        moved: false
      };
      suppressFabClick = false;
      root.classList.add('dragging');
      document.addEventListener(isTouch ? 'touchmove' : 'mousemove', onDragMove, { passive: false });
      document.addEventListener(isTouch ? 'touchend' : 'mouseup', stopDrag, { once: true });
    }

    function onDragMove(event) {
      if (!dragState) return;
      const isTouch = event.type.startsWith('touch');
      const point = isTouch ? event.touches[0] : event;
      if (isTouch) {
        event.preventDefault();
      }
      const deltaX = point.clientX - dragState.startX;
      const deltaY = point.clientY - dragState.startY;
      const nextX = dragState.initialX - deltaX;
      const nextY = dragState.initialY - deltaY;
      dragState.moved = true;
      suppressFabClick = true;
      root.style.setProperty('--assistant-root-x', `${Math.max(12, Math.min(window.innerWidth - 80, nextX))}px`);
      root.style.setProperty('--assistant-root-y', `${Math.max(12, Math.min(window.innerHeight - 80, nextY))}px`);
    }

    function stopDrag() {
      if (!dragState) return;
      const x = parseFloat(getComputedStyle(root).getPropertyValue('--assistant-root-x') || '0');
      const y = parseFloat(getComputedStyle(root).getPropertyValue('--assistant-root-y') || '0');
      persistState({ fabPosition: { x, y } });
      dragState = null;
      document.removeEventListener('mousemove', onDragMove);
      document.removeEventListener('touchmove', onDragMove);
      root.classList.remove('dragging');
      if (suppressFabClick) {
        setTimeout(() => {
          suppressFabClick = false;
        }, 120);
      }
    }

    function setResizeModeClasses(mode, action) {
      const method = action === 'add' ? 'add' : 'remove';
      const classes = {
        vertical: ['resizing-vertical'],
        horizontal: ['resizing-horizontal'],
        corner: ['resizing-corner']
      }[mode] || [];
      classes.forEach(cls => {
        root.classList[method](cls);
        panel.classList[method](cls);
      });
      if (action !== 'add') {
        root.classList.remove('resizing', 'resizing-vertical', 'resizing-horizontal', 'resizing-corner');
        panel.classList.remove('resizing', 'resizing-vertical', 'resizing-horizontal', 'resizing-corner');
      }
    }

    function startResize(event, mode = 'vertical') {
      const isTouch = event.type.startsWith('touch');
      const point = isTouch ? event.touches[0] : event;
      resizeState = {
        startY: point.clientY,
        startX: point.clientX,
        startWidth: panel.offsetWidth,
        startHeight: panel.offsetHeight,
        mode
      };
      root.classList.add('resizing');
      panel.classList.add('resizing');
      setResizeModeClasses(mode, 'add');
      clearResizeHover();
      document.addEventListener(isTouch ? 'touchmove' : 'mousemove', onResizeMove, { passive: false });
      document.addEventListener(isTouch ? 'touchend' : 'mouseup', stopResize, { once: true });
    }

    function onResizeMove(event) {
      if (!resizeState) return;
      const isTouch = event.type.startsWith('touch');
      const point = isTouch ? event.touches[0] : event;
      if (isTouch) {
        event.preventDefault();
      }
      const deltaY = resizeState.startY - point.clientY;
      const deltaX = resizeState.startX - point.clientX;
      if (resizeState.mode === 'vertical' || resizeState.mode === 'corner') {
        const nextHeight = Math.min(Math.max(resizeState.startHeight + deltaY, PANEL_MIN_HEIGHT), PANEL_MAX_HEIGHT);
        panel.style.height = `${nextHeight}px`;
        persistState({ panelHeight: nextHeight });
      }
      if (resizeState.mode === 'horizontal' || resizeState.mode === 'corner') {
        const maxWidth = Math.min(PANEL_MAX_WIDTH, Math.max(PANEL_MIN_WIDTH, window.innerWidth - 60));
        const nextWidth = Math.min(Math.max(resizeState.startWidth + deltaX, PANEL_MIN_WIDTH), maxWidth);
        panel.style.width = `${nextWidth}px`;
        persistState({ panelWidth: nextWidth });
      }
    }

    function stopResize() {
      if (!resizeState) return;
      const mode = resizeState.mode;
      resizeState = null;
      document.removeEventListener('mousemove', onResizeMove);
      document.removeEventListener('touchmove', onResizeMove);
      root.classList.remove('resizing');
      panel.classList.remove('resizing');
      setResizeModeClasses(mode, 'remove');
    }

    function bindDragGesture(target) {
      target.addEventListener('mousedown', (event) => {
        event.preventDefault();
        startDrag(event);
      });
      target.addEventListener('touchstart', (event) => {
        startDrag(event);
      }, { passive: true });
    }

    bindDragGesture(fab);
    const headerDragRegion = root.querySelector('.assistant-header');
    bindDragGesture(headerDragRegion);

    const bindResizeHandle = (handle, mode) => {
      if (!handle) return;
      handle.addEventListener('mousedown', (event) => {
        event.preventDefault();
        startResize(event, mode);
      });
      handle.addEventListener('touchstart', (event) => {
        event.preventDefault();
        startResize(event, mode);
      }, { passive: false });
    };

    bindResizeHandle(resizeHandleBottom, 'vertical');
    bindResizeHandle(resizeHandleRight, 'horizontal');
    bindResizeHandle(resizeHandleCorner, 'corner');

    function isInVerticalResizeZone(event, threshold = 18) {
      const rect = panel.getBoundingClientRect();
      const point = event.type.startsWith('touch') ? event.touches[0] : event;
      if (!point) return false;
      return rect.bottom - point.clientY <= threshold;
    }

    function isInHorizontalResizeZone(event, threshold = 14) {
      const rect = panel.getBoundingClientRect();
      const point = event.type.startsWith('touch') ? event.touches[0] : event;
      if (!point) return false;
      return rect.right - point.clientX <= threshold;
    }

    function isInCornerResizeZone(event, threshold = 24) {
      return isInVerticalResizeZone(event, threshold) && isInHorizontalResizeZone(event, threshold);
    }

    function attemptPanelResize(event) {
      const isTouch = event.type.startsWith('touch');
      if (!isTouch && event.button !== undefined && event.button !== 0) {
        return;
      }
      const target = event.target;
      if (target.closest('.assistant-resize-handle')) {
        return;
      }
      if (target.closest('textarea, button, input, select, a[data-no-resize], .assistant-send-btn')) {
        return;
      }
      const allowHorizontal = window.innerWidth > 540;
      const allowCorner = allowHorizontal;
      const mode = (allowCorner && isInCornerResizeZone(event)) ? 'corner'
        : (allowHorizontal && isInHorizontalResizeZone(event)) ? 'horizontal'
        : isInVerticalResizeZone(event) ? 'vertical'
        : null;
      if (!mode) {
        return;
      }
      event.preventDefault();
      startResize(event, mode);
    }

    panel.addEventListener('mousedown', attemptPanelResize, { capture: true });
    panel.addEventListener('touchstart', attemptPanelResize, { capture: true, passive: false });

    function clearResizeHover() {
      panel.classList.remove('resize-hover-vertical', 'resize-hover-horizontal', 'resize-hover-corner');
    }

    function updateResizeHover(event) {
      if (resizeState || !isPanelOpen) {
        clearResizeHover();
        return;
      }
      if (!event) {
        clearResizeHover();
        return;
      }
      clearResizeHover();
      const allowHorizontal = window.innerWidth > 540;
      if (allowHorizontal && isInCornerResizeZone(event, 26)) {
        panel.classList.add('resize-hover-corner');
      } else if (allowHorizontal && isInHorizontalResizeZone(event, 20)) {
        panel.classList.add('resize-hover-horizontal');
      } else if (isInVerticalResizeZone(event, 24)) {
        panel.classList.add('resize-hover-vertical');
      }
    }

    const hoverListener = (event) => {
      if (resizeHoverRAF) {
        cancelAnimationFrame(resizeHoverRAF);
      }
      resizeHoverRAF = requestAnimationFrame(() => updateResizeHover(event));
    };

    panel.addEventListener('mousemove', hoverListener);
    panel.addEventListener('touchmove', hoverListener, { passive: true });
    panel.addEventListener('mouseleave', () => updateResizeHover(null));

    if (typeof ResizeObserver === 'function') {
      const observer = new ResizeObserver(() => {
        if (!resizeState) {
          clearResizeHover();
        }
      });
      observer.observe(panel);
    }

    window.addEventListener('resize', () => {
      if (window.innerWidth <= 640) {
        panel.style.width = '';
      }
      const computed = getComputedStyle(root);
      let currentY = parseFloat(computed.getPropertyValue('--assistant-root-y') || '0');
      let currentX = parseFloat(computed.getPropertyValue('--assistant-root-x') || '0');
      const maxY = Math.max(12, window.innerHeight - 120);
      const maxX = Math.max(12, window.innerWidth - 120);
      let changedPosition = false;
      if (currentY > maxY) {
        currentY = maxY;
        root.style.setProperty('--assistant-root-y', `${currentY}px`);
        changedPosition = true;
      }
      if (currentX > maxX) {
        currentX = maxX;
        root.style.setProperty('--assistant-root-x', `${currentX}px`);
        changedPosition = true;
      }
      if (changedPosition) {
        persistState({ fabPosition: { x: currentX, y: currentY } });
      }
      const idealPanelMax = Math.min(PANEL_MAX_HEIGHT, Math.max(PANEL_MIN_HEIGHT, window.innerHeight - 120));
      const currentHeight = panel.offsetHeight;
      if (currentHeight > idealPanelMax) {
        panel.style.height = `${idealPanelMax}px`;
        persistState({ panelHeight: idealPanelMax });
      }
      const idealPanelWidthMax = Math.min(PANEL_MAX_WIDTH, Math.max(PANEL_MIN_WIDTH, window.innerWidth - 60));
      const currentWidth = panel.offsetWidth;
      if (currentWidth > idealPanelWidthMax) {
        panel.style.width = `${idealPanelWidthMax}px`;
        persistState({ panelWidth: idealPanelWidthMax });
      }
    });

    appendAssistantGreeting(history);
  }

  window.initAssistantWidget = init;
  document.addEventListener('DOMContentLoaded', init, { once: true });
})();
