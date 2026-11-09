/**
 * Nebula Store storefront.
 *
 * Talks to the Spring Boot API under /api, keeps the catalog state in memory and
 * re-renders the grid whenever the category, search query or sort order changes.
 * The cart is a placeholder for now: the API part lands in the next iteration.
 */
(() => {
    'use strict';

    const API = '/api';

    const state = {
        products: [],
        categories: [],
        category: null,
        query: '',
        sort: 'popular',
        stats: null,
        loading: false,
    };

    const dom = {
        search: document.getElementById('search-input'),
        sort: document.getElementById('sort-select'),
        chips: document.getElementById('category-chips'),
        grid: document.getElementById('product-grid'),
        empty: document.getElementById('empty-state'),
        subtitle: document.getElementById('catalog-subtitle'),
        reset: document.getElementById('reset-filters'),
        dialog: document.getElementById('product-dialog'),
        dialogBody: document.getElementById('dialog-body'),
        toasts: document.getElementById('toasts'),
        cartButton: document.getElementById('cart-button'),
        cartCount: document.getElementById('cart-count'),
        shuffle: document.getElementById('hero-shuffle'),
        statProducts: document.getElementById('stat-products'),
        statCategories: document.getElementById('stat-categories'),
        statRating: document.getElementById('stat-rating'),
    };

    const money = new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        maximumFractionDigits: 0,
    });

    const TINTS = {
        'Аудио': '#7c5cff',
        'Клавиатуры': '#22d3ee',
        'Мониторы': '#f472b6',
        'Носимые': '#34d399',
        'Аксессуары': '#fbbf24',
    };

    /* ------------------------------------------------------------------ */
    /* helpers                                                            */
    /* ------------------------------------------------------------------ */

    const tintFor = (category) => TINTS[category] || '#7c5cff';

    const escapeHtml = (value) => String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');

    const debounce = (fn, delay) => {
        let timer;
        return (...args) => {
            clearTimeout(timer);
            timer = setTimeout(() => fn(...args), delay);
        };
    };

    async function request(path) {
        const response = await fetch(API + path, { headers: { Accept: 'application/json' } });
        if (!response.ok) {
            let detail = `Ошибка ${response.status}`;
            try {
                const body = await response.json();
                detail = body.detail || body.title || detail;
            } catch {
                /* body is not JSON — keep the generic message */
            }
            throw new Error(detail);
        }
        return response.json();
    }

    function toast(message, variant = 'info') {
        const node = document.createElement('div');
        node.className = `toast${variant === 'error' ? ' toast--error' : ''}`;
        node.innerHTML = `<span aria-hidden="true">${variant === 'error' ? '⚠️' : '✨'}</span><span>${escapeHtml(message)}</span>`;
        dom.toasts.append(node);
        setTimeout(() => {
            node.classList.add('is-leaving');
            node.addEventListener('animationend', () => node.remove(), { once: true });
        }, 3200);
    }

    function renderStars(rating) {
        const filled = Math.round(rating);
        let stars = '';
        for (let i = 1; i <= 5; i += 1) {
            stars += `<span class="${i <= filled ? 'is-on' : ''}">★</span>`;
        }
        return `<span class="stars" aria-hidden="true">${stars}</span>`;
    }

    /* ------------------------------------------------------------------ */
    /* rendering                                                          */
    /* ------------------------------------------------------------------ */

    function renderSkeletons(count = 8) {
        dom.empty.hidden = true;
        dom.grid.innerHTML = Array.from({ length: count }, () => `
            <div class="skeleton">
                <div class="skeleton__art"></div>
                <div class="skeleton__line"></div>
                <div class="skeleton__line skeleton__line--short"></div>
            </div>`).join('');
    }

    function renderError(error) {
        dom.empty.hidden = true;
        dom.grid.innerHTML = `
            <div class="status-message">
                <p>Не удалось загрузить каталог: ${escapeHtml(error.message)}</p>
                <button class="btn btn--ghost" type="button" data-action="retry" style="margin-top:16px">Повторить</button>
            </div>`;
    }

    function renderChips() {
        const total = state.categories.reduce((sum, category) => sum + category.count, 0);
        const chips = [
            `<button class="chip" type="button" data-category="" aria-pressed="${state.category === null}">
                Все <span class="chip__count">${total}</span>
             </button>`,
            ...state.categories.map((category) => `
                <button class="chip" type="button" data-category="${escapeHtml(category.name)}"
                        aria-pressed="${state.category === category.name}">
                    ${escapeHtml(category.name)} <span class="chip__count">${category.count}</span>
                </button>`),
        ];
        dom.chips.innerHTML = chips.join('');
    }

    function renderProducts() {
        if (!state.products.length) {
            dom.grid.innerHTML = '';
            dom.empty.hidden = false;
            return;
        }
        dom.empty.hidden = true;
        dom.grid.innerHTML = state.products.map((product, index) => `
            <article class="card" style="animation-delay:${Math.min(index * 45, 400)}ms">
                <div class="card__art" style="--tint:${tintFor(product.category)}">
                    ${product.inStock ? '' : '<span class="card__badge">нет в наличии</span>'}
                    <span aria-hidden="true">${product.icon}</span>
                </div>
                <div class="card__body">
                    <span class="card__category">${escapeHtml(product.category)}</span>
                    <h3 class="card__name">${escapeHtml(product.name)}</h3>
                    <p class="card__desc">${escapeHtml(product.description)}</p>
                    <div class="card__rating">
                        ${renderStars(product.rating)}
                        <span>${product.rating.toFixed(1)}</span>
                    </div>
                    <div class="card__footer">
                        <span class="price">${money.format(product.price)}</span>
                        <button class="btn btn--small" type="button" data-action="details" data-id="${product.id}">
                            Подробнее
                        </button>
                    </div>
                </div>
            </article>`).join('');
    }

    function renderStats(stats) {
        dom.statProducts.textContent = stats.products;
        dom.statCategories.textContent = stats.categories;
        dom.statRating.textContent = stats.rating.toFixed(1);
    }

    function updateSubtitle() {
        const count = state.products.length;
        if (state.error) {
            dom.subtitle.textContent = 'Каталог временно недоступен';
            return;
        }
        const parts = [`${count} ${plural(count, ['товар', 'товара', 'товаров'])}`];
        if (state.category) {
            parts.push(`в категории «${state.category}»`);
        }
        if (state.query) {
            parts.push(`по запросу «${state.query}»`);
        }
        dom.subtitle.textContent = parts.join(' ');
    }

    function plural(n, forms) {
        const mod10 = n % 10;
        const mod100 = n % 100;
        if (mod10 === 1 && mod100 !== 11) return forms[0];
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return forms[1];
        return forms[2];
    }

    /* ------------------------------------------------------------------ */
    /* data loading                                                       */
    /* ------------------------------------------------------------------ */

    function catalogQuery() {
        const params = new URLSearchParams();
        if (state.category) params.set('category', state.category);
        if (state.query) params.set('q', state.query);
        params.set('sort', state.sort);
        return params.toString();
    }

    async function loadProducts() {
        state.loading = true;
        dom.empty.hidden = true;
        if (!dom.grid.children.length) {
            renderSkeletons();
        }
        try {
            state.products = await request(`/products?${catalogQuery()}`);
            renderProducts();
            updateSubtitle();
        } catch (error) {
            renderError(error);
            updateSubtitle();
        } finally {
            state.loading = false;
        }
    }

    async function loadInitialData() {
        renderSkeletons();
        try {
            const [products, categories] = await Promise.all([
                request('/products'),
                request('/categories'),
            ]);
            state.categories = categories;
            const rating = products.length
                ? products.reduce((sum, product) => sum + product.rating, 0) / products.length
                : 0;
            renderStats({
                products: products.length,
                categories: categories.length,
                rating,
            });
            renderChips();
        } catch (error) {
            toast(`Каталог недоступен: ${error.message}`, 'error');
        }
        await loadProducts();
    }

    /* ------------------------------------------------------------------ */
    /* product dialog                                                     */
    /* ------------------------------------------------------------------ */

    function openProduct(id) {
        const product = state.products.find((item) => item.id === Number(id));
        if (!product) return;

        const stockLabel = product.inStock
            ? `<span class="pill pill--ok">В наличии: ${product.stock} шт.</span>`
            : '<span class="pill pill--low">Нет в наличии</span>';

        dom.dialogBody.innerHTML = `
            <div class="dialog__art" style="--tint:${tintFor(product.category)}">
                <span aria-hidden="true">${product.icon}</span>
            </div>
            <div class="dialog__content">
                <span class="dialog__category">${escapeHtml(product.category)}</span>
                <h2 id="dialog-title" class="dialog__title">${escapeHtml(product.name)}</h2>
                <p class="dialog__desc">${escapeHtml(product.description)}</p>
                <div class="dialog__meta">
                    ${stockLabel}
                    <span class="pill">${renderStars(product.rating)} ${product.rating.toFixed(1)}</span>
                    <span class="pill">Артикул: ${escapeHtml(product.slug)}</span>
                </div>
                <div class="dialog__price">
                    ${money.format(product.price)}
                    <small>включая НДС</small>
                </div>
                <div class="dialog__actions">
                    <button class="btn btn--primary btn--block" type="button" data-action="add-to-cart"
                            data-id="${product.id}" ${product.inStock ? '' : 'disabled'}>
                        Добавить в корзину
                    </button>
                </div>
            </div>
            <button class="dialog__close" type="button" data-action="close-dialog" aria-label="Закрыть">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"></path></svg>
            </button>`;

        dom.dialog.showModal();
        history.replaceState(null, '', `#product-${product.id}`);
    }

    function closeDialog() {
        if (!dom.dialog.open) return;
        dom.dialog.close();
        history.replaceState(null, '', location.pathname + location.search);
    }

    /* ------------------------------------------------------------------ */
    /* events                                                             */
    /* ------------------------------------------------------------------ */

    dom.chips.addEventListener('click', (event) => {
        const chip = event.target.closest('[data-category]');
        if (!chip) return;
        state.category = chip.dataset.category || null;
        renderChips();
        loadProducts();
    });

    dom.grid.addEventListener('click', (event) => {
        const button = event.target.closest('[data-action]');
        if (!button) return;
        if (button.dataset.action === 'details') {
            openProduct(button.dataset.id);
        }
        if (button.dataset.action === 'retry') {
            loadProducts();
        }
    });

    dom.dialog.addEventListener('click', (event) => {
        if (event.target === dom.dialog) {
            closeDialog();
            return;
        }
        const button = event.target.closest('[data-action]');
        if (!button) return;
        if (button.dataset.action === 'close-dialog') {
            closeDialog();
        }
        if (button.dataset.action === 'add-to-cart') {
            toast('Корзина появится в следующем обновлении — каталог уже готов.');
        }
    });

    dom.search.addEventListener('input', debounce((event) => {
        state.query = event.target.value.trim();
        loadProducts();
    }, 250));

    dom.sort.addEventListener('change', (event) => {
        state.sort = event.target.value;
        loadProducts();
    });

    dom.reset.addEventListener('click', () => {
        state.category = null;
        state.query = '';
        dom.search.value = '';
        renderChips();
        loadProducts();
    });

    dom.shuffle.addEventListener('click', () => {
        if (!state.products.length) {
            toast('Каталог ещё загружается');
            return;
        }
        const random = state.products[Math.floor(Math.random() * state.products.length)];
        openProduct(random.id);
    });

    dom.cartButton.addEventListener('click', () => {
        toast('Корзина в разработке — следующий шаг: API корзины и оформление заказа.');
    });

    /* keep the URL in sync when the native <dialog> is closed with Esc */
    dom.dialog.addEventListener('close', () => {
        if (location.hash.startsWith('#product-')) {
            history.replaceState(null, '', location.pathname + location.search);
        }
    });

    /* ------------------------------------------------------------------ */
    /* boot                                                               */
    /* ------------------------------------------------------------------ */

    (async () => {
        await loadInitialData();
        const deepLink = location.hash.match(/^#product-(\d+)$/);
        if (deepLink) {
            openProduct(Number(deepLink[1]));
        }
    })();
})();
