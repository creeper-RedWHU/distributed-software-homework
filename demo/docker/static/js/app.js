// API 基础路径
const API_BASE = '';

// 页面加载时获取数据
document.addEventListener('DOMContentLoaded', function() {
    loadProducts();
    loadSeckillList();
    document.getElementById('detailSection').style.display = 'block';
});

// 检查后端服务器
function checkServer() {
    fetch(API_BASE + '/api/product/server-info')
        .then(r => r.json())
        .then(data => {
            const el = document.getElementById('serverInfo');
            el.innerHTML = '<p><strong>' + data.data + '</strong></p>' +
                '<p>时间: ' + new Date().toLocaleTimeString() + '</p>';
        })
        .catch(err => {
            document.getElementById('serverInfo').innerHTML =
                '<p style="color:red">请求失败: ' + err.message + '</p>';
        });
}

// 连续请求10次，观察负载均衡
function batchCheck() {
    const el = document.getElementById('batchResult');
    el.innerHTML = '请求中...\n';
    const results = [];

    const promises = [];
    for (let i = 0; i < 10; i++) {
        promises.push(
            fetch(API_BASE + '/api/product/server-info')
                .then(r => {
                    const upstream = r.headers.get('X-Upstream-Addr') || 'N/A';
                    const serverPort = r.headers.get('X-Server-Port') || 'N/A';
                    return r.json().then(data => ({
                        index: i + 1,
                        upstream: upstream,
                        serverPort: serverPort,
                        msg: data.data
                    }));
                })
        );
    }

    Promise.all(promises).then(items => {
        items.sort((a, b) => a.index - b.index);
        let html = '';
        const portCount = {};
        items.forEach(item => {
            html += '第' + item.index + '次: ' + item.msg +
                    ' | upstream=' + item.upstream + '\n';
            const port = item.serverPort;
            portCount[port] = (portCount[port] || 0) + 1;
        });
        html += '\n--- 统计 ---\n';
        for (const [port, count] of Object.entries(portCount)) {
            html += '端口 ' + port + ': ' + count + ' 次 (' +
                    (count * 10) + '%)\n';
        }
        el.innerHTML = html;
    });
}

// 加载商品列表
function loadProducts() {
    fetch(API_BASE + '/api/product/list?page=1&size=10')
        .then(r => r.json())
        .then(data => {
            const el = document.getElementById('productList');
            if (!data.data || data.data.length === 0) {
                el.innerHTML = '<p class="loading">暂无商品</p>';
                return;
            }
            el.innerHTML = data.data.map(p =>
                '<div class="product-card">' +
                '  <h3>' + escapeHtml(p.productName) + '</h3>' +
                '  <p>' + escapeHtml(p.description || '') + '</p>' +
                '  <p class="price">&yen;' + p.price + '</p>' +
                '  <p class="stock">库存: ' + p.stock + '</p>' +
                '</div>'
            ).join('');
        })
        .catch(err => {
            document.getElementById('productList').innerHTML =
                '<p style="color:red">加载失败: ' + err.message + '</p>';
        });
}

// 加载秒杀列表
function loadSeckillList() {
    fetch(API_BASE + '/api/seckill/list')
        .then(r => r.json())
        .then(data => {
            const el = document.getElementById('seckillList');
            if (!data.data || data.data.length === 0) {
                el.innerHTML = '<p class="loading">暂无秒杀活动</p>';
                return;
            }
            el.innerHTML = data.data.map(s =>
                '<div class="product-card">' +
                '  <span class="seckill-badge">秒杀</span>' +
                '  <h3>' + escapeHtml(s.productName) + '</h3>' +
                '  <p class="price">&yen;' + s.seckillPrice +
                '    <span class="original-price">&yen;' + s.originalPrice + '</span>' +
                '  </p>' +
                '  <p class="stock">秒杀库存: ' + s.seckillStock + '</p>' +
                '  <button onclick="doSeckill(' + s.id + ')">立即秒杀</button>' +
                '</div>'
            ).join('');
        })
        .catch(err => {
            document.getElementById('seckillList').innerHTML =
                '<p style="color:red">加载失败: ' + err.message + '</p>';
        });
}

// 查询商品详情（走Redis缓存）
function queryProduct() {
    const id = document.getElementById('productIdInput').value;
    const start = Date.now();
    fetch(API_BASE + '/api/product/' + id)
        .then(r => {
            const serverPort = r.headers.get('X-Server-Port') || 'N/A';
            return r.json().then(data => ({ data, serverPort }));
        })
        .then(({ data, serverPort }) => {
            const elapsed = Date.now() - start;
            const el = document.getElementById('detailResult');
            el.innerHTML = '响应时间: ' + elapsed + 'ms | 处理端口: ' + serverPort + '\n\n' +
                JSON.stringify(data, null, 2);
        })
        .catch(err => {
            document.getElementById('detailResult').innerHTML =
                '请求失败: ' + err.message;
        });
}

// 清除缓存
function evictCache() {
    const id = document.getElementById('productIdInput').value;
    fetch(API_BASE + '/api/product/' + id + '/evict-cache', { method: 'POST' })
        .then(r => r.json())
        .then(data => {
            document.getElementById('detailResult').innerHTML =
                '操作结果: ' + JSON.stringify(data, null, 2);
        });
}

// 预热缓存
function warmCache() {
    const id = document.getElementById('productIdInput').value;
    fetch(API_BASE + '/api/product/' + id + '/warm-cache', { method: 'POST' })
        .then(r => r.json())
        .then(data => {
            document.getElementById('detailResult').innerHTML =
                '操作结果: ' + JSON.stringify(data, null, 2);
        });
}

// 执行秒杀
function doSeckill(seckillId) {
    const userId = 1; // 演示用固定userId
    fetch(API_BASE + '/api/seckill/do?userId=' + userId + '&seckillId=' + seckillId, {
        method: 'POST'
    })
        .then(r => r.json())
        .then(data => {
            if (data.code === 200) {
                alert('秒杀成功! 订单号: ' + data.data);
                loadSeckillList();
            } else {
                alert('秒杀失败: ' + data.message);
            }
        })
        .catch(err => alert('请求失败: ' + err.message));
}

// HTML转义
function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}
