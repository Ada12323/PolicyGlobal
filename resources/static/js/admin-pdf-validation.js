/**
 * PDF验证管理前端JS
 * 依赖: jQuery 1.8.0
 */

class PdfValidationManager {
    constructor() {
        // API端点
        this.API_BASE = '/admin/pdf-validation';

        // 当前状态
        this.currentTask = null;
        this.pollingInterval = null;
        this.selectedPolicyIds = new Set();
        this.invalidPolicies = [];

        // DOM元素
        this.elements = {};
        this.initializeElements();

        // 绑定事件
        this.bindEvents();

        // 初始化
        this.init();
    }

    initializeElements() {
        // 控制面板元素
        this.elements = {
            // 统计数据
            totalPolicies: $('#totalPolicies'),
            invalidCount: $('#invalidCount'),
            validCount: $('#validCount'),
            lastCheckTime: $('#lastCheckTime'),

            // 控制按钮
            startValidationBtn: $('#startValidationBtn'),
            refreshStatsBtn: $('#refreshStatsBtn'),
            loadInvalidBtn: $('#loadInvalidBtn'),
            selectAllBtn: $('#selectAllBtn'),
            deleteSelectedBtn: $('#deleteSelectedBtn'),

            // 进度显示
            progressSection: $('#progressSection'),
            progressFill: $('#progressFill'),
            progressPercentage: $('#progressPercentage'),
            processedCount: $('#processedCount'),
            totalCount: $('#totalCount'),
            currentValidCount: $('#currentValidCount'),
            currentInvalidCount: $('#currentInvalidCount'),
            cancelTaskBtn: $('#cancelTaskBtn'),

            // 失效政策列表
            invalidPoliciesBody: $('#invalidPoliciesBody'),
            emptyState: $('#emptyState'),
            selectAllCheckbox: $('#selectAllCheckbox'),
            tableSelectAll: $('#tableSelectAll'),
            selectedCount: $('#selectedCount'),
            refreshListBtn: $('#refreshListBtn'),

            // 模态框和提示
            deleteModal: $('#deleteModal'),
            deleteMessage: $('#deleteMessage'),
            cancelDeleteBtn: $('#cancelDeleteBtn'),
            confirmDeleteBtn: $('#confirmDeleteBtn'),
            toast: $('#toast')
        };
    }

    bindEvents() {
        // 控制按钮事件
        this.elements.startValidationBtn.on('click', () => this.startValidation());
        this.elements.refreshStatsBtn.on('click', () => this.loadStats());
        this.elements.loadInvalidBtn.on('click', () => this.loadInvalidPolicies());
        this.elements.selectAllBtn.on('click', () => this.selectAllPolicies());
        this.elements.deleteSelectedBtn.on('click', () => this.showDeleteConfirm());
        this.elements.cancelTaskBtn.on('click', () => this.cancelTask());

        // 列表控制事件
        this.elements.refreshListBtn.on('click', () => this.loadInvalidPolicies());
        this.elements.selectAllCheckbox.on('change', (e) => this.toggleSelectAll(e.target.checked));
        this.elements.tableSelectAll.on('change', (e) => this.toggleSelectAll(e.target.checked));

        // 删除确认事件
        this.elements.cancelDeleteBtn.on('click', () => this.hideDeleteModal());
        this.elements.confirmDeleteBtn.on('click', () => this.confirmDelete());
    }

    init() {
        // 加载初始数据
        this.loadStats();
        this.loadInvalidPolicies();

        // 检查是否有正在运行的任务
        this.checkCurrentTask();
    }

    // 显示Toast提示
    showToast(message, type = 'info', duration = 3000) {
        const toast = this.elements.toast;
        toast.removeClass('success error info').addClass(type);
        toast.html(`<i class="fas fa-${type === 'success' ? 'check-circle' : type === 'error' ? 'exclamation-circle' : 'info-circle'}"></i> ${message}`);
        toast.fadeIn(300);

        setTimeout(() => {
            toast.fadeOut(300);
        }, duration);
    }

    // 显示加载状态
    showLoading(button, text = '处理中...') {
        button.prop('disabled', true);
        button.data('original-text', button.html());
        button.html(`<span class="loading-spinner"></span> ${text}`);
    }

    // 隐藏加载状态
    hideLoading(button) {
        const originalText = button.data('original-text');
        if (originalText) {
            button.html(originalText);
        }
        button.prop('disabled', false);
    }

    // 检查当前是否有运行中的任务
    async checkCurrentTask() {
        try {
            const response = await $.ajax({
                url: `${this.API_BASE}/current-progress`,
                type: 'GET',
                dataType: 'json'
            });

            if (response.hasTask && response.task) {
                this.currentTask = response.task;
                this.startPollingTask(this.currentTask.id);
                this.showProgress();
                this.updateProgress(this.currentTask);
            }
        } catch (error) {
            console.error('检查当前任务失败:', error);
        }
    }

    // 启动验证任务
    async startValidation() {
        if (this.currentTask) {
            this.showToast('已有验证任务正在运行', 'info');
            return;
        }

        if (!confirm('确定要开始验证所有PDF链接吗？这可能需要一些时间，请耐心等待。')) {
            return;
        }

        this.showLoading(this.elements.startValidationBtn, '启动验证...');

        try {
            const response = await $.ajax({
                url: `${this.API_BASE}/start-batch`,
                type: 'POST',
                contentType: 'application/json',
                dataType: 'json'
            });

            if (response.success) {
                this.showToast('验证任务已启动', 'success');
                this.startPollingTask(response.taskId);
            } else {
                this.showToast(response.error || '启动验证失败', 'error');
            }
        } catch (error) {
            console.error('启动验证失败:', error);
            const errorMsg = error.responseJSON?.error || '服务器错误';
            this.showToast(`启动验证失败: ${errorMsg}`, 'error');
        } finally {
            this.hideLoading(this.elements.startValidationBtn);
        }
    }

    // 开始轮询任务进度
    startPollingTask(taskId) {
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
        }

        this.pollingInterval = setInterval(async () => {
            try {
                const response = await $.ajax({
                    url: `${this.API_BASE}/progress/${taskId}`,
                    type: 'GET',
                    dataType: 'json'
                });

                this.currentTask = response;
                this.updateProgress(response);

                // 如果任务完成或失败，停止轮询
                if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(response.status)) {
                    this.stopPolling();
                    this.currentTask = null;

                    // 任务完成时重新加载数据
                    this.loadStats();
                    this.loadInvalidPolicies();

                    if (response.status === 'COMPLETED') {
                        this.showToast('验证任务已完成', 'success');
                    } else if (response.status === 'CANCELLED') {
                        this.showToast('验证任务已取消', 'info');
                    }
                }
            } catch (error) {
                console.error('获取任务进度失败:', error);
                this.stopPolling();
                this.currentTask = null;
            }
        }, 2000); // 每2秒轮询一次
    }

    // 停止轮询
    stopPolling() {
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
            this.pollingInterval = null;
        }
    }

    // 显示进度条
    showProgress() {
        this.elements.progressSection.addClass('active');
    }

    // 隐藏进度条
    hideProgress() {
        this.elements.progressSection.removeClass('active');
    }

    // 更新进度显示
    updateProgress(task) {
        const percentage = task.progressPercentage || 0;

        this.elements.progressFill.css('width', `${percentage}%`);
        this.elements.progressPercentage.text(`${percentage}%`);
        this.elements.processedCount.text(task.processedCount || 0);
        this.elements.totalCount.text(task.totalCount || 0);
        this.elements.currentValidCount.text(task.validCount || 0);
        this.elements.currentInvalidCount.text(task.invalidCount || 0);

        // 更新总统计数据
        if (task.status === 'COMPLETED') {
            this.elements.validCount.text(task.validCount || 0);
            this.elements.lastCheckTime.text('刚刚');
            setTimeout(() => this.hideProgress(), 3000); // 3秒后隐藏进度条
        }
    }

    // 取消任务
    async cancelTask() {
        if (!this.currentTask) {
            return;
        }

        if (!confirm('确定要取消当前验证任务吗？')) {
            return;
        }

        try {
            await $.ajax({
                url: `${this.API_BASE}/cancel/${this.currentTask.id}`,
                type: 'POST',
                contentType: 'application/json',
                dataType: 'json'
            });

            this.stopPolling();
            this.currentTask = null;
            this.hideProgress();
            this.showToast('任务已取消', 'info');
        } catch (error) {
            console.error('取消任务失败:', error);
            this.showToast('取消任务失败', 'error');
        }
    }

    // 加载统计信息
    async loadStats() {
        try {
            // 获取验证统计
            const statsResponse = await $.ajax({
                url: `${this.API_BASE}/stats`,
                type: 'GET',
                dataType: 'json'
            });

            if (statsResponse.success) {
                this.elements.invalidCount.text(statsResponse.invalidCount || 0);

                if (statsResponse.lastCheckTime) {
                    const checkTime = new Date(statsResponse.lastCheckTime);
                    const now = new Date();
                    const diffMinutes = Math.floor((now - checkTime) / (1000 * 60));

                    if (diffMinutes < 60) {
                        this.elements.lastCheckTime.text(`${diffMinutes}分钟前`);
                    } else if (diffMinutes < 1440) {
                        const hours = Math.floor(diffMinutes / 60);
                        this.elements.lastCheckTime.text(`${hours}小时前`);
                    } else {
                        const days = Math.floor(diffMinutes / 1440);
                        this.elements.lastCheckTime.text(`${days}天前`);
                    }
                } else {
                    this.elements.lastCheckTime.text('从未验证');
                }
            }

            // 尝试获取总政策数（需要现有接口支持，这里使用默认值）
            // 实际项目中可以根据需要修改
            this.elements.totalPolicies.text('--');
            this.elements.validCount.text('--');

        } catch (error) {
            console.error('加载统计信息失败:', error);
        }
    }

    // 加载失效政策列表
    async loadInvalidPolicies() {
        this.showLoading(this.elements.refreshListBtn, '加载中...');

        try {
            const response = await $.ajax({
                url: `${this.API_BASE}/invalid-policies`,
                type: 'GET',
                dataType: 'json'
            });

            if (response.success) {
                this.invalidPolicies = response.data || [];
                this.renderInvalidPolicies();
                this.updateSelectedCount();

                // 更新按钮显示
                if (this.invalidPolicies.length > 0) {
                    this.elements.selectAllBtn.show();
                    this.elements.deleteSelectedBtn.show();
                } else {
                    this.elements.selectAllBtn.hide();
                    this.elements.deleteSelectedBtn.hide();
                }
            } else {
                this.showToast('加载失效政策列表失败', 'error');
            }
        } catch (error) {
            console.error('加载失效政策列表失败:', error);
            this.showToast('加载失效政策列表失败', 'error');
        } finally {
            this.hideLoading(this.elements.refreshListBtn);
        }
    }

    // 渲染失效政策列表
    renderInvalidPolicies() {
        const tbody = this.elements.invalidPoliciesBody;
        tbody.empty();

        if (this.invalidPolicies.length === 0) {
            this.elements.emptyState.show();
            return;
        }

        this.elements.emptyState.hide();

        this.invalidPolicies.forEach(policy => {
            const isSelected = this.selectedPolicyIds.has(policy.id);
            const checkTime = policy.formattedCheckTime || 'N/A';

            // 格式化时间
            let formattedTime = '未知';
            if (policy.lastCheckTime) {
                const date = new Date(policy.lastCheckTime);
                formattedTime = date.toLocaleString('zh-CN');
            }

            const row = $(`
                <tr data-policy-id="${policy.id}">
                    <td>
                        <input type="checkbox" class="policy-checkbox" ${isSelected ? 'checked' : ''}>
                    </td>
                    <td>
                        <div class="policy-title" title="${this.escapeHtml(policy.zhTitle || policy.originalTitle || '无标题')}">
                            ${this.escapeHtml(policy.zhTitle || policy.originalTitle || '无标题')}
                        </div>
                    </td>
                    <td>
                        <div class="pdf-url" title="${this.escapeHtml(policy.pdfUrl || '')}">
                            <a href="${this.escapeHtml(policy.pdfUrl || '#')}" target="_blank" rel="noopener noreferrer">
                                <i class="fas fa-external-link-alt"></i> 查看PDF
                            </a>
                        </div>
                    </td>
                    <td>
                        <div class="check-result" title="${this.escapeHtml(policy.checkResult || '未知原因')}">
                            ${this.escapeHtml(policy.checkResult || '未知原因')}
                        </div>
                        <div class="check-time">${checkTime}</div>
                    </td>
                    <td>${formattedTime}</td>
                    <td class="action-cell">
                        <button class="btn btn-small btn-danger delete-single-btn" title="删除此政策">
                            <i class="fas fa-trash"></i> 删除
                        </button>
                    </td>
                </tr>
            `);

            // 绑定事件
            row.find('.policy-checkbox').on('change', (e) => {
                this.togglePolicySelection(policy.id, e.target.checked);
            });

            row.find('.delete-single-btn').on('click', () => {
                this.showDeleteConfirm([policy.id], policy.zhTitle || policy.originalTitle);
            });

            tbody.append(row);
        });

        // 更新全选框状态
        this.updateSelectAllCheckbox();
    }

    // 切换单个政策的选择状态
    togglePolicySelection(policyId, isSelected) {
        if (isSelected) {
            this.selectedPolicyIds.add(policyId);
        } else {
            this.selectedPolicyIds.delete(policyId);
        }

        this.updateSelectedCount();
        this.updateSelectAllCheckbox();
    }

    // 切换全选
    toggleSelectAll(isSelected) {
        this.elements.selectAllCheckbox.prop('checked', isSelected);
        this.elements.tableSelectAll.prop('checked', isSelected);

        if (isSelected) {
            this.selectedPolicyIds = new Set(this.invalidPolicies.map(p => p.id));
        } else {
            this.selectedPolicyIds.clear();
        }

        // 更新所有复选框状态
        this.elements.invalidPoliciesBody.find('.policy-checkbox').prop('checked', isSelected);

        this.updateSelectedCount();
    }

    // 全选所有政策
    selectAllPolicies() {
        this.toggleSelectAll(true);
    }

    // 更新选中数量显示
    updateSelectedCount() {
        const count = this.selectedPolicyIds.size;
        const selectedCount = this.elements.selectedCount;

        if (count > 0) {
            selectedCount.text(count).addClass('active');
        } else {
            selectedCount.removeClass('active');
        }
    }

    // 更新全选框状态
    updateSelectAllCheckbox() {
        const totalCount = this.invalidPolicies.length;
        const selectedCount = this.selectedPolicyIds.size;

        const isAllSelected = totalCount > 0 && selectedCount === totalCount;
        const isIndeterminate = selectedCount > 0 && selectedCount < totalCount;

        this.elements.selectAllCheckbox.prop('checked', isAllSelected);
        this.elements.tableSelectAll.prop('checked', isAllSelected);
        this.elements.tableSelectAll.prop('indeterminate', isIndeterminate);
    }

    // 显示删除确认对话框
    showDeleteConfirm(policyIds = null, policyTitle = null) {
        const idsToDelete = policyIds || Array.from(this.selectedPolicyIds);

        if (idsToDelete.length === 0) {
            this.showToast('请先选择要删除的政策', 'info');
            return;
        }

        let message = '';
        if (policyTitle) {
            message = `您确定要删除政策 "${this.escapeHtml(policyTitle)}" 吗？此操作不可撤销。`;
        } else if (idsToDelete.length === 1) {
            const policy = this.invalidPolicies.find(p => p.id === idsToDelete[0]);
            message = `您确定要删除政策 "${this.escapeHtml(policy?.zhTitle || policy?.originalTitle || '未知')}" 吗？此操作不可撤销。`;
        } else {
            message = `您确定要删除选中的 ${idsToDelete.length} 个政策吗？此操作不可撤销，请谨慎操作。`;
        }

        this.elements.deleteMessage.text(message);
        this.elements.deleteModal.data('policy-ids', idsToDelete);
        this.elements.deleteModal.addClass('active');
    }

    // 隐藏删除确认对话框
    hideDeleteModal() {
        this.elements.deleteModal.removeClass('active');
        this.elements.deleteModal.data('policy-ids', []);
    }

    // 确认删除
    async confirmDelete() {
        const policyIds = this.elements.deleteModal.data('policy-ids') || [];

        if (policyIds.length === 0) {
            this.hideDeleteModal();
            return;
        }

        this.showLoading(this.elements.confirmDeleteBtn, '删除中...');

        try {
            const response = await $.ajax({
                url: `${this.API_BASE}/delete-invalid`,
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({
                    policyIds: policyIds,
                    confirmed: true
                }),
                dataType: 'json'
            });

            if (response.success) {
                this.showToast(response.message || '删除成功', 'success');

                // 从选中集合中移除已删除的ID
                policyIds.forEach(id => this.selectedPolicyIds.delete(id));

                // 重新加载数据
                this.loadStats();
                this.loadInvalidPolicies();
            } else {
                this.showToast(response.error || '删除失败', 'error');
            }
        } catch (error) {
            console.error('删除政策失败:', error);
            const errorMsg = error.responseJSON?.error || '服务器错误';
            this.showToast(`删除失败: ${errorMsg}`, 'error');
        } finally {
            this.hideLoading(this.elements.confirmDeleteBtn);
            this.hideDeleteModal();
        }
    }

    // HTML转义
    escapeHtml(text) {
        if (!text) return '';
        const map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return text.replace(/[&<>"']/g, m => map[m]);
    }
}

// 页面加载完成后初始化
$(document).ready(function() {
    // 检查是否是管理员
    // 这里可以添加权限检查逻辑，实际项目中根据需求实现

    // 初始化管理器
    window.validationManager = new PdfValidationManager();
});