// 热量计算页面 JavaScript

let selectedBrand = null;
let selectedProduct = null;
let selectedSize = '中杯';
let selectedToppings = [];

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    loadBrands();
    loadToppings();
    setupImageUpload();
});

// 加载品牌列表
function loadBrands() {
    fetch('/api/brands')
        .then(response => response.json())
        .then(brands => {
            const brandGrid = document.getElementById('brandGrid');
            brandGrid.innerHTML = '';

            brands.forEach(brand => {
                const brandItem = document.createElement('div');
                brandItem.className = 'brand-item';
                brandItem.dataset.id = brand.id;
                brandItem.innerHTML = `
                    <img class="brand-logo" src="${brand.logo || '/img/brand-default.png'}" alt="${brand.name}" onerror="this.src='/img/brand-default.png'">
                    <div class="brand-name">${brand.name}</div>
                `;
                brandItem.onclick = () => selectBrand(brand);
                brandGrid.appendChild(brandItem);
            });
        })
        .catch(error => {
            console.error('加载品牌失败:', error);
            showDefaultBrands();
        });
}

// 显示默认品牌（API失败时）
function showDefaultBrands() {
    const defaultBrands = [
        {id: 1, name: '喜茶'},
        {id: 2, name: '奈雪的茶'},
        {id: 3, name: '蜜雪冰城'},
        {id: 4, name: '茶百道'},
        {id: 5, name: '古茗'},
        {id: 6, name: '一点点'},
        {id: 7, name: 'CoCo都可'},
        {id: 8, name: '书亦烧仙草'}
    ];

    const brandGrid = document.getElementById('brandGrid');
    brandGrid.innerHTML = '';

    defaultBrands.forEach(brand => {
        const brandItem = document.createElement('div');
        brandItem.className = 'brand-item';
        brandItem.dataset.id = brand.id;
        brandItem.innerHTML = `
            <img class="brand-logo" src="/img/brand-default.png" alt="${brand.name}">
            <div class="brand-name">${brand.name}</div>
        `;
        brandItem.onclick = () => selectBrand(brand);
        brandGrid.appendChild(brandItem);
    });
}

// 选择品牌
function selectBrand(brand) {
    selectedBrand = brand;
    selectedProduct = null; // 重置产品选择
    selectedToppings = []; // 重置小料选择

    // 更新UI
    document.querySelectorAll('.brand-item').forEach(item => {
        item.classList.remove('selected');
    });
    document.querySelector(`.brand-item[data-id="${brand.id}"]`).classList.add('selected');

    // 显示产品选择区域
    document.getElementById('productSection').style.display = 'block';

    // 加载该品牌的产品
    loadProducts(brand.id);

    // 更新已选信息
    updateSelectedInfo();
}

// 加载产品列表
function loadProducts(brandId) {
    fetch(`/api/brands/${brandId}/products`)
        .then(response => response.json())
        .then(products => {
            const productGrid = document.getElementById('productGrid');
            productGrid.innerHTML = '';

            products.forEach(product => {
                const productItem = document.createElement('div');
                productItem.className = 'product-item';
                productItem.dataset.id = product.id;
                productItem.innerHTML = `
                    <img class="product-image" src="${product.image || '/img/product-default.png'}" alt="${product.name}" onerror="this.src='/img/product-default.png'">
                    <div class="product-name">${product.name}</div>
                    <div class="product-calorie">${product.calorie} 大卡</div>
                `;
                productItem.onclick = () => selectProduct(product);
                productGrid.appendChild(productItem);
            });
        })
        .catch(error => {
            console.error('加载产品失败:', error);
        });
}

// 选择产品
function selectProduct(product) {
    selectedProduct = product;

    // 更新UI
    document.querySelectorAll('.product-item').forEach(item => {
        item.classList.remove('selected');
    });
    document.querySelector(`.product-item[data-id="${product.id}"]`).classList.add('selected');

    // 显示容量和小料选择区域
    document.getElementById('sizeSection').style.display = 'block';
    document.getElementById('toppingSection').style.display = 'block';

    // 更新已选信息
    updateSelectedInfo();
}

// 选择容量
function selectSize(element) {
    selectedSize = element.dataset.size;

    // 更新UI
    document.querySelectorAll('.size-item').forEach(item => {
        item.classList.remove('selected');
    });
    element.classList.add('selected');

    updateSelectedInfo();
}

// 加载小料列表
function loadToppings() {
    fetch('/api/toppings')
        .then(response => response.json())
        .then(toppings => {
            const toppingGrid = document.getElementById('toppingGrid');
            toppingGrid.innerHTML = '';

            toppings.forEach(topping => {
                const toppingItem = document.createElement('div');
                toppingItem.className = 'topping-item';
                toppingItem.dataset.id = topping.id;
                toppingItem.innerHTML = `
                    <img class="topping-image" src="${topping.image || '/img/topping-default.png'}" alt="${topping.name}" onerror="this.src='/img/topping-default.png'">
                    <div class="topping-name">${topping.name}</div>
                    <div class="topping-calorie">+${topping.calorie}大卡</div>
                `;
                toppingItem.onclick = () => toggleTopping(topping);
                toppingGrid.appendChild(toppingItem);
            });
        })
        .catch(error => {
            console.error('加载小料失败:', error);
            showDefaultToppings();
        });
}

// 显示默认小料
function showDefaultToppings() {
    const defaultToppings = [
        {id: 1, name: '珍珠', calorie: 56},
        {id: 2, name: '椰果', calorie: 25},
        {id: 3, name: '芝士奶盖', calorie: 120},
        {id: 4, name: '布丁', calorie: 65},
        {id: 5, name: '仙草', calorie: 18},
        {id: 6, name: '芋泥', calorie: 80},
        {id: 7, name: '红豆', calorie: 45},
        {id: 8, name: '芋圆', calorie: 52}
    ];

    const toppingGrid = document.getElementById('toppingGrid');
    toppingGrid.innerHTML = '';

    defaultToppings.forEach(topping => {
        const toppingItem = document.createElement('div');
        toppingItem.className = 'topping-item';
        toppingItem.dataset.id = topping.id;
        toppingItem.innerHTML = `
            <img class="topping-image" src="/img/topping-default.png" alt="${topping.name}">
            <div class="topping-name">${topping.name}</div>
            <div class="topping-calorie">+${topping.calorie}大卡</div>
        `;
        toppingItem.onclick = () => toggleTopping(topping);
        toppingGrid.appendChild(toppingItem);
    });
}

// 切换小料选择
function toggleTopping(topping) {
    const index = selectedToppings.findIndex(t => t.id === topping.id);
    const toppingItem = document.querySelector(`.topping-item[data-id="${topping.id}"]`);

    if (index > -1) {
        selectedToppings.splice(index, 1);
        toppingItem.classList.remove('selected');
    } else {
        selectedToppings.push(topping);
        toppingItem.classList.add('selected');
    }

    updateSelectedInfo();
}

// 切换小料选择模式
function switchToppingMode(mode) {
    const tabs = document.querySelectorAll('#toppingTabs .nav-link');
    tabs.forEach(tab => tab.classList.remove('active'));
    event.target.classList.add('active');

    if (mode === 'select') {
        document.getElementById('toppingSelectMode').style.display = 'block';
        document.getElementById('toppingUploadMode').style.display = 'none';
    } else {
        document.getElementById('toppingSelectMode').style.display = 'none';
        document.getElementById('toppingUploadMode').style.display = 'block';
    }
}

// 设置图片上传
function setupImageUpload() {
    const uploadArea = document.getElementById('uploadArea');
    const fileInput = document.getElementById('toppingImageInput');

    // 点击上传区域
    uploadArea.onclick = () => fileInput.click();

    // 拖拽上传
    uploadArea.ondragover = (e) => {
        e.preventDefault();
        uploadArea.classList.add('drag-over');
    };

    uploadArea.ondragleave = () => {
        uploadArea.classList.remove('drag-over');
    };

    uploadArea.ondrop = (e) => {
        e.preventDefault();
        uploadArea.classList.remove('drag-over');
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            handleImageUpload(files[0]);
        }
    };

    // 文件选择
    fileInput.onchange = (e) => {
        if (e.target.files.length > 0) {
            handleImageUpload(e.target.files[0]);
        }
    };
}

// 处理图片上传
function handleImageUpload(file) {
    if (!file.type.startsWith('image/')) {
        alert('请上传图片文件');
        return;
    }

    const reader = new FileReader();
    reader.onload = (e) => {
        const base64 = e.target.result;

        // 显示预览
        const previewImage = document.getElementById('previewImage');
        previewImage.src = base64;
        previewImage.style.display = 'block';

        // 调用识别API
        recognizeImage(base64);
    };
    reader.readAsDataURL(file);
}

// 识别图片
function recognizeImage(base64) {
    document.getElementById('loadingOverlay').style.display = 'block';

    fetch('/api/image/recognize-base64', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ image: base64 })
    })
    .then(response => response.json())
    .then(data => {
        document.getElementById('loadingOverlay').style.display = 'none';

        if (data.success && data.results && data.results.length > 0) {
            displayRecognitionResults(data.results);
        } else {
            alert('未能识别到小料，请尝试手动选择');
        }
    })
    .catch(error => {
        document.getElementById('loadingOverlay').style.display = 'none';
        console.error('识别失败:', error);
        alert('识别失败，请尝试手动选择');
    });
}

// 显示识别结果
function displayRecognitionResults(results) {
    const resultDiv = document.getElementById('recognitionResult');
    const toppingsDiv = document.getElementById('recognizedToppings');

    resultDiv.style.display = 'block';
    toppingsDiv.innerHTML = '';

    results.forEach(result => {
        const confidence = Math.round(result.confidence * 100);
        const item = document.createElement('div');
        item.className = 'recognized-topping-item';
        item.innerHTML = `
            <div class="d-flex justify-content-between align-items-center p-2 mb-2 bg-light rounded">
                <div>
                    <strong>${result.toppingName}</strong>
                    <small class="text-muted ms-2">+${result.calorie}大卡</small>
                </div>
                <div>
                    <span class="badge bg-success">${confidence}%置信度</span>
                    <button class="btn btn-sm btn-primary ms-2" onclick="addRecognizedTopping(${result.toppingId}, '${result.toppingName}', ${result.calorie})">
                        添加
                    </button>
                </div>
            </div>
        `;
        toppingsDiv.appendChild(item);
    });
}

// 添加识别到的小料
function addRecognizedTopping(id, name, calorie) {
    const topping = { id, name, calorie };
    const index = selectedToppings.findIndex(t => t.id === id);
    if (index === -1) {
        selectedToppings.push(topping);
        updateSelectedInfo();
        alert(`已添加: ${name}`);
    } else {
        alert(`${name} 已在已选列表中`);
    }
}

// 更新已选信息
function updateSelectedInfo() {
    const infoDiv = document.getElementById('selectedInfo');
    let html = '';

    if (selectedBrand) {
        html += `<p><strong>品牌:</strong> ${selectedBrand.name}</p>`;
    }

    if (selectedProduct) {
        html += `<p><strong>奶茶:</strong> ${selectedProduct.name} (${selectedProduct.calorie}大卡)</p>`;
    }

    html += `<p><strong>容量:</strong> ${selectedSize}</p>`;

    if (selectedToppings.length > 0) {
        const toppingNames = selectedToppings.map(t => t.name).join(', ');
        const toppingCalorie = selectedToppings.reduce((sum, t) => sum + t.calorie, 0);
        html += `<p><strong>小料:</strong> ${toppingNames} (+${toppingCalorie}大卡)</p>`;
    }

    if (!selectedBrand && !selectedProduct) {
        html = '<p class="text-muted">请选择品牌和奶茶</p>';
    }

    infoDiv.innerHTML = html;

    // 更新计算按钮状态 - 启用条件：已选择品牌和产品
    const calculateBtn = document.getElementById('calculateBtn');
    if (calculateBtn) {
        calculateBtn.disabled = !(selectedBrand && selectedProduct);
    }
}

// 计算热量
function calculateCalorie() {
    if (!selectedBrand || !selectedProduct) {
        alert('请先选择品牌和奶茶');
        return;
    }

    document.getElementById('loadingOverlay').style.display = 'block';

    const request = {
        brandId: selectedBrand.id,
        productId: selectedProduct.id,
        size: selectedSize,
        toppingIds: selectedToppings.map(t => t.id)
    };

    fetch('/api/calorie/calculate', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    })
    .then(response => response.json())
    .then(result => {
        document.getElementById('loadingOverlay').style.display = 'none';
        displayResult(result);
    })
    .catch(error => {
        document.getElementById('loadingOverlay').style.display = 'none';
        console.error('计算失败:', error);
        alert('计算失败，请重试');
    });
}

// 显示结果
function displayResult(result) {
    document.getElementById('resultSection').style.display = 'block';

    // 滚动到结果区域
    document.getElementById('resultSection').scrollIntoView({ behavior: 'smooth' });

    // 更新基本信息
    document.getElementById('resultBrand').textContent = result.brandName;
    document.getElementById('resultProductName').textContent = result.productName;
    document.getElementById('totalCalorie').textContent = result.totalCalorie;
    document.getElementById('totalSugar').textContent = result.totalSugar + 'g';
    document.getElementById('totalCarbs').textContent = result.totalCarbs + 'g';

    // 更新热量等级
    const levelDiv = document.getElementById('calorieLevel');
    levelDiv.textContent = result.calorieLevel + '热量';
    levelDiv.className = 'calorie-level';
    if (result.calorieLevel === '低') {
        levelDiv.classList.add('level-low');
    } else if (result.calorieLevel === '中') {
        levelDiv.classList.add('level-medium');
    } else if (result.calorieLevel === '高') {
        levelDiv.classList.add('level-high');
    } else {
        levelDiv.classList.add('level-veryhigh');
    }

    // 更新健康建议
    document.getElementById('healthAdvice').textContent = result.healthAdvice;

    // 显示图表
    renderChart(result);

    // 显示运动建议
    displayExercises(result.exerciseAdvices);
}

// 渲染图表
function renderChart(result) {
    const chart = echarts.init(document.getElementById('calorieChart'));

    const option = {
        title: {
            text: '热量构成分析',
            left: 'center',
            textStyle: { fontSize: 14 }
        },
        tooltip: {
            trigger: 'item',
            formatter: '{b}: {c}大卡 ({d}%)'
        },
        legend: {
            bottom: 0,
            data: ['奶茶基底', '小料']
        },
        series: [{
            type: 'pie',
            radius: ['40%', '70%'],
            center: ['50%', '50%'],
            avoidLabelOverlap: false,
            label: {
                show: true,
                formatter: '{b}: {c}大卡'
            },
            data: [
                { value: result.baseCalorie || result.totalCalorie - (result.toppingCalorie || 0), name: '奶茶基底', itemStyle: { color: '#FF6B9D' } },
                { value: result.toppingCalorie || 0, name: '小料', itemStyle: { color: '#FF8C42' } }
            ]
        }]
    };

    chart.setOption(option);
}

// 显示运动建议
function displayExercises(exercises) {
    const grid = document.getElementById('exerciseGrid');
    grid.innerHTML = '';

    exercises.forEach(exercise => {
        const card = document.createElement('div');
        card.className = 'exercise-card';
        card.innerHTML = `
            <img class="exercise-image" src="${exercise.image || '/img/exercise-default.png'}" alt="${exercise.exerciseName}" onerror="this.src='/img/exercise-default.png'">
            <div class="exercise-name">${exercise.exerciseName}</div>
            <div class="exercise-duration">${exercise.durationText || exercise.duration + '小时'}</div>
        `;
        grid.appendChild(card);
    });
}

// 重置计算器
function resetCalculator() {
    selectedBrand = null;
    selectedProduct = null;
    selectedToppings = [];

    // 重置UI
    document.querySelectorAll('.brand-item, .product-item, .topping-item').forEach(item => {
        item.classList.remove('selected');
    });

    document.getElementById('productGrid').innerHTML = '<p class="text-muted text-center py-4">请先选择品牌</p>';
    document.getElementById('resultSection').style.display = 'none';
    document.getElementById('recognitionResult').style.display = 'none';
    document.getElementById('previewImage').style.display = 'none';

    updateSelectedInfo();

    // 滚动到顶部
    window.scrollTo({ top: 0, behavior: 'smooth' });
}
