// 推荐页面 JavaScript
let selectedFlavors = [];
let selectedAllergies = [];
let selectedHealth = [];

// 切换选项
function toggleOption(element, type, value) {
    element.classList.toggle('selected');

    let targetArray;
    switch (type) {
        case 'flavor':
            targetArray = selectedFlavors;
            break;
        case 'allergy':
            targetArray = selectedAllergies;
            break;
        case 'health':
            targetArray = selectedHealth;
            break;
    }

    const index = targetArray.indexOf(value);
    if (index > -1) {
        targetArray.splice(index, 1);
    } else {
        targetArray.push(value);
    }
}

// 获取推荐
function getRecommendations() {
    const sweetLevel = parseInt(document.getElementById('sweetLevel').value);
    const preferLowCalorie = document.getElementById('preferLowCalorie').checked;

    const request = {
        preferredFlavors: selectedFlavors.length > 0 ? selectedFlavors : null,
        sweetLevel: sweetLevel,
        preferLowCalorie: preferLowCalorie || null,
        allergies: selectedAllergies.length > 0 ? selectedAllergies : null,
        healthIssues: selectedHealth.length > 0 ? selectedHealth : null
    };

    showLoading();

    fetch('/api/recommend', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    })
    .then(response => response.json())
    .then(result => {
        hideLoading();
        displayResults(result);
    })
    .catch(error => {
        hideLoading();
        console.error('获取推荐失败:', error);
        alert('获取推荐失败，请重试');
    });
}

// 获取热门推荐
function getHotRecommendations() {
    showLoading();

    fetch('/api/hot')
        .then(response => response.json())
        .then(hotList => {
            hideLoading();
            displayHotResults(hotList);
        })
        .catch(error => {
            hideLoading();
            console.error('获取热门失败:', error);
            alert('获取热门推荐失败，请重试');
        });
}

// 显示Loading
function showLoading() {
    document.getElementById('loadingOverlay').style.display = 'block';
    document.getElementById('resultSection').style.display = 'none';
}

// 隐藏Loading
function hideLoading() {
    document.getElementById('loadingOverlay').style.display = 'none';
}

// 显示推荐结果
function displayResults(result) {
    const resultSection = document.getElementById('resultSection');
    const resultType = document.getElementById('resultType');
    const grid = document.getElementById('recommendGrid');

    resultType.textContent = result.recommendType === 'hot' ? '🔥 本周热门推荐' : '🎉 专属推荐';

    grid.innerHTML = '';

    result.recommendations.forEach((item, index) => {
        const card = createRecommendCard(item, index);
        grid.appendChild(card);
    });

    resultSection.style.display = 'block';
    resultSection.scrollIntoView({ behavior: 'smooth' });
}

// 显示热门结果
function displayHotResults(hotList) {
    const resultSection = document.getElementById('resultSection');
    const resultType = document.getElementById('resultType');
    const grid = document.getElementById('recommendGrid');

    resultType.textContent = '🔥 本周热门TOP10';

    grid.innerHTML = '';

    hotList.forEach((item, index) => {
        // 转换热门列表格式为推荐格式
        const recommendItem = {
            productId: item.productId,
            productName: item.productName,
            brandName: item.brandName,
            image: item.image,
            calorie: item.calorie,
            stars: calculateStars(item.hotScore),
            starsDisplay: generateStarsDisplay(calculateStars(item.hotScore)),
            recommendReason: item.recommendReason,
            matchScore: item.hotScore,
            tags: item.tags
        };

        const card = createRecommendCard(recommendItem, index);
        grid.appendChild(card);
    });

    resultSection.style.display = 'block';
    resultSection.scrollIntoView({ behavior: 'smooth' });
}

// 创建推荐卡片
function createRecommendCard(item, index) {
    const card = document.createElement('div');
    card.className = 'recommend-card';

    const rank = index + 1;
    const rankClass = rank <= 3 ? `rank-${rank}` : 'rank-other';

    card.innerHTML = `
        <div style="position: relative;">
            <img class="recommend-image" src="${item.image || '/img/product-default.png'}" alt="${item.productName}" onerror="this.src='/img/product-default.png'">
            <div class="recommend-rank ${rankClass}">${rank}</div>
        </div>
        <div class="recommend-info">
            <div class="recommend-brand">${item.brandName || ''}</div>
            <div class="recommend-name">${item.productName}</div>
            <div class="recommend-stars">${item.starsDisplay || generateStarsDisplay(item.stars)}</div>
            <div class="recommend-reason">
                <i class="bi bi-lightbulb"></i>
                ${item.recommendReason || '根据您的偏好推荐'}
            </div>
            <div class="recommend-tags">
                ${(item.tags || []).map(tag => `<span class="recommend-tag">${tag}</span>`).join('')}
            </div>
            <div class="recommend-calorie">
                <span class="calorie-value">${item.calorie || '--'} 大卡</span>
                <span class="match-score">匹配度: ${Math.round(item.matchScore || 0)}%</span>
            </div>
        </div>
    `;

    // 点击跳转到热量计算
    card.style.cursor = 'pointer';
    card.onclick = () => {
        window.location.href = `/calorie?brand=${item.brandName}&product=${item.productName}`;
    };

    return card;
}

// 计算星级
function calculateStars(score) {
    if (score >= 90) return 5;
    if (score >= 70) return 4;
    if (score >= 50) return 3;
    if (score >= 30) return 2;
    return 1;
}

// 生成星级显示
function generateStarsDisplay(stars) {
    return '⭐'.repeat(stars) + '☆'.repeat(5 - stars);
}

// 重置偏好
function resetPreferences() {
    selectedFlavors = [];
    selectedAllergies = [];
    selectedHealth = [];

    // 重置UI
    document.querySelectorAll('.option-card').forEach(card => {
        card.classList.remove('selected');
    });

    document.getElementById('sweetLevel').value = 3;
    document.getElementById('preferLowCalorie').checked = false;

    // 隐藏结果
    document.getElementById('resultSection').style.display = 'none';
}
