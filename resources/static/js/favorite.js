document.addEventListener('DOMContentLoaded', function() {
    // 全局变量
    let currentPage = 0;
    let totalPages = 0;
    let totalElements = 0;
    let currentFavorites = [];
    let allFavorites = []; // 新增：存储所有收藏数据
    const pageSize = 6;

    // DOM元素
    const favoritesList = document.getElementById('favoritesList');
    const pagination = document.getElementById('pagination');
    const loadingIndicator = document.getElementById('loadingIndicator');
    const noFavorites = document.getElementById('noFavorites');
    const searchInput = document.getElementById('searchInput');
    const searchBtn = document.getElementById('searchBtn');
    const sortSelect = document.getElementById('sortSelect');

    // 初始化加载 - 获取所有数据
    loadAllFavorites();

    // 事件监听
    searchBtn.addEventListener('click', handleSearch);
    searchInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') handleSearch();
    });
    sortSelect.addEventListener('change', () => {
        currentPage = 0;
        filterAndRenderFavorites();
    });

    // 加载所有收藏数据（一次性加载）
    function loadAllFavorites() {
        showLoading();

        // 获取所有数据，不进行分页
        fetch(`/api/favorite?size=1000`, { // 假设最多1000条收藏
            credentials: 'include'
        })
            .then(response => {
                if (!response.ok) throw new Error(`请求失败：${response.status}`);
                return response.json();
            })
            .then(data => {
                if (!data.success) {
                    throw new Error(data.message || '获取数据失败');
                }

                const pageData = data.data || {};
                // 存储所有收藏数据
                allFavorites = Array.isArray(pageData.content) ? pageData.content : [];

                // 初始渲染
                filterAndRenderFavorites();
                hideLoading();
            })
            .catch(error => {
                console.error('获取收藏数据失败:', error);
                hideLoading();
                alert('获取收藏数据失败: ' + error.message);
            });
    }

    // 过滤和渲染收藏（纯前端搜索和排序）
    function filterAndRenderFavorites() {
        let filteredFavorites = [...allFavorites];
        const searchTerm = searchInput.value.trim().toLowerCase();

        // 1. 搜索过滤
        if (searchTerm) {
            filteredFavorites = filteredFavorites.filter(favorite => {
                const title = (favorite.zhTitle || favorite.originalTitle || '').toLowerCase();
                const summary = (favorite.summary || '').toLowerCase();

                return title.includes(searchTerm) || summary.includes(searchTerm);
            });
        }

        // 2. 排序
        const [sortField, sortOrder] = sortSelect.value.split(',');
        filteredFavorites.sort((a, b) => {
            let aValue = a[sortField] || '';
            let bValue = b[sortField] || '';

            // 处理日期排序
            if (sortField === 'createdAt') {
                aValue = new Date(aValue).getTime();
                bValue = new Date(bValue).getTime();
            }

            if (sortOrder === 'desc') {
                return bValue - aValue;
            } else {
                return aValue - bValue;
            }
        });

        // 3. 分页
        totalElements = filteredFavorites.length;
        totalPages = Math.ceil(totalElements / pageSize);

        const startIndex = currentPage * pageSize;
        const endIndex = startIndex + pageSize;
        currentFavorites = filteredFavorites.slice(startIndex, endIndex);

        // 4. 渲染
        renderFavorites();
        renderPagination();

        // 5. 显示空状态
        if (totalElements === 0) {
            showNoFavorites();
        } else {
            hideNoFavorites();
        }
    }

    // 渲染收藏列表
    function renderFavorites() {
        favoritesList.innerHTML = '';

        if (!Array.isArray(currentFavorites)) {
            console.warn("收藏数据格式错误，不是数组");
            return;
        }

        currentFavorites.forEach(favorite => {
            const card = createFavoriteCard(favorite);
            favoritesList.appendChild(card);
        });
    }

    // 创建收藏卡片（修正后的版本）
    function createFavoriteCard(favorite) {
        const card = document.createElement('div');
        card.className = 'favorite-card';

        const createDate = favorite.createdAt
            ? new Date(favorite.createdAt).toLocaleDateString('zh-CN')
            : '未知时间';

        card.innerHTML = `
            <div class="card-header">
                <h3 class="card-title">${escapeHtml(favorite.zhTitle || favorite.originalTitle || '未知政策标题')}</h3>
                <div class="card-meta">
                    <span class="meta-item">
                        <i class="fas fa-clock"></i>收藏时间: ${createDate}
                    </span>
                </div>
            </div>
            <div class="card-body">
                <div class="card-content">
                    ${escapeHtml(favorite.summary || '暂无政策内容描述')}
                </div>
            </div>
            <div class="card-footer">
                <button class="btn btn-primary" onclick="viewPolicy('${escapeHtml(favorite.policyDataId || '')}')">
                    <i class="fas fa-eye"></i>查看政策
                </button>
                <button class="btn btn-danger" onclick="removeFavorite('${escapeHtml(favorite.favoriteId || favorite.id || '')}')">
                    <i class="fas fa-trash"></i>取消收藏
                </button>
            </div>
        `;

        return card;
    }

    // 渲染分页
    function renderPagination() {
        if (totalPages <= 1) {
            pagination.style.display = 'none';
            return;
        }

        pagination.style.display = 'flex';
        let paginationHTML = '<div class="pagination">';

        // 上一页
        paginationHTML += `
            <button ${currentPage === 0 ? 'disabled' : ''} onclick="goToPage(${currentPage - 1})">
                <i class="fas fa-chevron-left"></i>
            </button>
        `;

        // 页码
        const startPage = Math.max(0, currentPage - 2);
        const endPage = Math.min(totalPages - 1, currentPage + 2);

        if (startPage > 0) {
            paginationHTML += `<button disabled>...</button>`;
        }

        for (let i = startPage; i <= endPage; i++) {
            paginationHTML += `
                <button class="${i === currentPage ? 'active' : ''}" onclick="goToPage(${i})">
                    ${i + 1}
                </button>
            `;
        }

        if (endPage < totalPages - 1) {
            paginationHTML += `<button disabled>...</button>`;
        }

        // 下一页
        paginationHTML += `
            <button ${currentPage === totalPages - 1 ? 'disabled' : ''} onclick="goToPage(${currentPage + 1})">
                <i class="fas fa-chevron-right"></i>
            </button>
        `;

        paginationHTML += '</div>';
        pagination.innerHTML = paginationHTML;
    }

    // 搜索处理 - 现在纯前端处理
    function handleSearch() {
        currentPage = 0;
        filterAndRenderFavorites();
    }

    // 页面跳转
    function goToPage(page) {
        if (page < 0 || page >= totalPages) return;
        currentPage = page;
        filterAndRenderFavorites();
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    // 查看政策
    function viewPolicy(policyId) {
        if (policyId && policyId !== 'undefined') {
            window.location.href = `/policy/${policyId}`;
        } else {
            alert('政策ID无效，无法查看');
        }
    }

    // 取消收藏
    function removeFavorite(favoriteId) {
        if (!favoriteId || favoriteId === 'undefined') {
            alert('收藏ID无效，无法取消');
            return;
        }

        if (confirm('确定要取消收藏吗？')) {
            fetch(`/api/favorite/${favoriteId}`, {
                method: 'DELETE',
                credentials: 'include'
            })
                .then(response => {
                    if (!response.ok) throw new Error('取消收藏失败');
                    return response.json();
                })
                .then(data => {
                    if (data.success) {
                        alert('取消收藏成功');
                        // 重新加载所有数据
                        loadAllFavorites();
                    } else {
                        throw new Error(data.message || '取消收藏失败');
                    }
                })
                .catch(error => {
                    console.error('取消收藏失败:', error);
                    alert('取消收藏失败: ' + error.message);
                });
        }
    }

    // 加载状态控制
    function showLoading() {
        loadingIndicator.style.display = 'block';
        favoritesList.style.display = 'none';
        pagination.style.display = 'none';
        noFavorites.style.display = 'none';
    }

    function hideLoading() {
        loadingIndicator.style.display = 'none';
        favoritesList.style.display = 'grid';
    }

    // 无数据状态控制
    function showNoFavorites() {
        noFavorites.style.display = 'block';
        favoritesList.style.display = 'none';
        pagination.style.display = 'none';
    }

    function hideNoFavorites() {
        noFavorites.style.display = 'none';
        favoritesList.style.display = 'grid';
    }

    // HTML转义
    function escapeHtml(text) {
        if (text === undefined || text === null) return '';
        const map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return String(text).replace(/[&<>"']/g, m => map[m]);
    }

    // 暴露全局函数
    window.goToPage = goToPage;
    window.viewPolicy = viewPolicy;
    window.removeFavorite = removeFavorite;
});