// 全局变量
const orgSelect = document.getElementById('orgSelect');
const orgSelectHint = document.getElementById('orgSelectHint');
const orgSelectAllBtn = document.getElementById('orgSelectAll');
const orgSelectClearBtn = document.getElementById('orgSelectClear');
const orgToggleBtn = document.getElementById('orgToggleBtn');
const orgSelectWrapper = document.getElementById('orgSelectWrapper');
const searchInput = document.getElementById('searchInput');
const sortField = document.getElementById('sortField');
const sortOrder = document.getElementById('sortOrder');
const resultsCount = document.getElementById('resultsCount');

const selectedOrgIds = new Set();
let orgOptions = [];
let orgCollapsed = true;
let lastCreatedOrgId = null;
const ORG_PREVIEW_LIMIT = 8;

// 新增：政策数据管理
let allPolicies = []; // 存储所有政策数据
let filteredPolicies = []; // 存储筛选后的政策数据

// 初始化
document.addEventListener('DOMContentLoaded', function() {
    // 添加事件监听
    searchInput.addEventListener('input', handleSearch);
    sortField.addEventListener('change', handleSort);
    sortOrder.addEventListener('change', handleSort);

    // 新增：监听排序字段变化，动态更新排序方向选项
    sortField.addEventListener('change', updateSortOrderOptions);

    // 获取机构数据
    getOrgs();
});

// 更新排序方向选项
function updateSortOrderOptions() {
    const sortFieldValue = sortField.value;
    const currentOrderValue = sortOrder.value;

    if (sortFieldValue === 'rating') {
        // 按评分排序时，选项为"从高到低"和"从低到高"
        sortOrder.innerHTML = `
            <option value="desc">从高到低</option>
            <option value="asc">从低到高</option>
        `;

        // 如果当前选项不是desc或asc，或者当前是时间排序的选项，默认设置为desc
        if (!['desc', 'asc'].includes(currentOrderValue)) {
            sortOrder.value = 'desc';
        }
    } else {
        // 按发布时间排序时，选项为"从新到旧"和"从旧到新"
        sortOrder.innerHTML = `
            <option value="desc">从新到旧</option>
            <option value="asc">从旧到新</option>
        `;

        // 保持原有选择，如果没有合适的值，默认设置为desc
        if (!['desc', 'asc'].includes(currentOrderValue)) {
            sortOrder.value = 'desc';
        }
    }
}

function updateOrgHint() {
    if (orgSelectHint) {
        orgSelectHint.style.display = selectedOrgIds.size === 0 ? 'block' : 'none';
    }
}

function handleSearch() {
    filterAndRenderPolicies();
}

function handleSort() {
    filterAndRenderPolicies();
}

function fetchPoliciesByOrgIds(selectedIds) {
    if (!selectedIds || selectedIds.length === 0) {
        allPolicies = [];
        filterAndRenderPolicies();
        return;
    }

    $.ajax({
        // 使用新的带评分接口
        url: '/org/getpolicy-mysearch',
        type: 'GET',
        dataType: 'json',
        data: {
            orgIds: selectedIds.join(',')
        },
        success: function(data) {
            // 保存所有政策数据
            allPolicies = Array.isArray(data) ? data : [];

            // 过滤和渲染政策
            filterAndRenderPolicies();
        }
    });
}

// 过滤和渲染政策（纯前端搜索和排序）
function filterAndRenderPolicies() {
    if (!allPolicies || allPolicies.length === 0) {
        renderEmptyState();
        return;
    }

    // 1. 搜索过滤
    const searchTerm = searchInput.value.trim().toLowerCase();
    filteredPolicies = allPolicies.filter(policy => {
        if (!searchTerm) return true;

        const title = (policy.zhTitle || policy.originalTitle || '').toLowerCase();
        const summary = (policy.summary || '').toLowerCase();

        return title.includes(searchTerm) || summary.includes(searchTerm);
    });

    // 2. 排序
    const sortFieldValue = sortField.value;
    const sortOrderValue = sortOrder.value;

    filteredPolicies.sort((a, b) => {
        let valueA, valueB;

        if (sortFieldValue === 'publishDate') {
            // 按发布时间排序
            valueA = a.publishDate ? new Date(a.publishDate).getTime() : 0;
            valueB = b.publishDate ? new Date(b.publishDate).getTime() : 0;
        } else if (sortFieldValue === 'rating') {
            // 按评分排序
            valueA = a.averageRating || 0; // 使用后端返回的averageRating字段
            valueB = b.averageRating || 0;
        } else if (sortFieldValue === 'collectTime') {
            // 按收藏时间排序
            // 注意：如果政策数据中没有收藏时间字段，需要根据实际情况调整
            // 这里使用创建时间作为替代
            valueA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
            valueB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        }

        // 处理空值
        if (!valueA) valueA = 0;
        if (!valueB) valueB = 0;

        // 排序
        return sortOrderValue === 'desc' ? valueB - valueA : valueA - valueB;
    });

    // 3. 渲染
    renderPolicies();

    // 4. 更新结果计数
    updateResultsCount();
}

// 渲染政策列表
function renderPolicies() {
    const ul = document.getElementById('addPolicyList');
    ul.innerHTML = '';

    if (!Array.isArray(filteredPolicies) || filteredPolicies.length === 0) {
        renderEmptyState();
        return;
    }

    filteredPolicies.forEach(policy => {
        const li = document.createElement('li');
        li.className = 'policy-card';
        li.onclick = () => goPolicy(policy.id);

        const title = policy.zhTitle || policy.originalTitle || '(无标题)';
        const summary = policy.summary || '';
        const publishDate = policy.publishDate ? formatDate(policy.publishDate) : '';
        const orgName = policy.org && policy.org.orgName ? policy.org.orgName : '';
        const rating = policy.averageRating || 0;

        li.innerHTML = `
            <div class="policy-title">${escapeHtml(title)}</div>
            <div class="policy-meta">
                ${publishDate ? `<div class="policy-meta-item"><i class="far fa-calendar-alt"></i>${publishDate}</div>` : ''}
                ${orgName ? `<div class="policy-meta-item"><i class="far fa-building"></i>${escapeHtml(orgName)}</div>` : ''}
                <div class="policy-meta-item">
                    <i class="fas fa-star" style="color: #ffc107;"></i>评分: ${rating.toFixed(1)}
                </div>
            </div>
            <div class="policy-summary">${escapeHtml(summary)}</div>
        `;

        ul.appendChild(li);
    });
}

// 渲染空状态
function renderEmptyState() {
    const ul = document.getElementById('addPolicyList');
    ul.innerHTML = `
        <div class="empty-state">
            <i class="far fa-file-alt"></i>
            <h3>暂无政策数据</h3>
            <p>请选择其他机构或调整搜索条件</p>
        </div>
    `;
}

// 更新结果计数
function updateResultsCount() {
    if (resultsCount) {
        const totalCount = allPolicies.length;
        const filteredCount = filteredPolicies.length;

        if (totalCount === filteredCount) {
            resultsCount.textContent = `共 ${totalCount} 条政策`;
        } else {
            resultsCount.textContent = `共 ${filteredCount} 条政策（从 ${totalCount} 条中筛选）`;
        }
    }
}

// 日期格式化函数
function formatDate(dateString) {
    if (!dateString) return '';

    try {
        const date = new Date(dateString);
        return date.toLocaleDateString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit'
        });
    } catch (e) {
        return dateString;
    }
}

function getOrgs() {
    if (!orgSelect) return;

    $.ajax({
        url: '/org/someorgs-mysearch',
        type: 'GET',
        dataType: 'json',
        success: function(data) {
            orgOptions = Array.isArray(data) ? data : [];
            selectedOrgIds.clear();
            orgOptions.forEach(org => {
                if (!org || org.id == null) return;
                const id = String(org.id);
                selectedOrgIds.add(id);
            });
            lastCreatedOrgId = null;
            renderOrgOptions();

            const selectedIds = Array.from(selectedOrgIds);
            fetchPoliciesByOrgIds(selectedIds);
        }
    });
}

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
            const selectedIds = Array.from(selectedOrgIds);
            fetchPoliciesByOrgIds(selectedIds);
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

function updateOrgToggle() {
    if (!orgToggleBtn || !orgSelectWrapper) return;
    const needsCollapse = Array.isArray(orgOptions) && orgOptions.length > ORG_PREVIEW_LIMIT;
    if (!needsCollapse) {
        orgCollapsed = false;
        orgSelectWrapper.setAttribute('data-collapsed', 'true');
        orgToggleBtn.style.display = 'none';
        return;
    }
    orgToggleBtn.style.display = '';
    orgSelectWrapper.setAttribute('data-collapsed', orgCollapsed ? 'true' : 'false');
    orgToggleBtn.textContent = orgCollapsed ? '展开更多' : '收起';
}

if (orgSelectAllBtn) {
    orgSelectAllBtn.addEventListener('click', () => {
        orgOptions.forEach(org => {
            if (org && org.id != null) {
                selectedOrgIds.add(String(org.id));
            }
        });
        renderOrgOptions();
        const selectedIds = Array.from(selectedOrgIds);
        fetchPoliciesByOrgIds(selectedIds);
    });
}

if (orgSelectClearBtn) {
    orgSelectClearBtn.addEventListener('click', () => {
        selectedOrgIds.clear();
        renderOrgOptions();
        const selectedIds = Array.from(selectedOrgIds);
        fetchPoliciesByOrgIds(selectedIds);
    });
}

if (orgToggleBtn) {
    orgToggleBtn.addEventListener('click', () => {
        orgCollapsed = !orgCollapsed;
        renderOrgOptions();
    });
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"]/g, (c) => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;'
    }[c]));
}

// 跳转到政策详情页
function goPolicy(id) {
    if (id) {
        window.location.href = `/my-policy-detail/${id}`;
    }
}