/**
 * Nebula Store storefront.
 *
 * Talks to the Spring Boot API under /api, keeps the catalog state in memory and
 * re-renders the grid whenever the category, search query or sort order changes.
 *
 * The cart is server-side and stateless on the client: the browser stores only an opaque
 * cart id in localStorage and sends it back in the X-Cart-Id header. Every mutation
 * returns the full cart snapshot, which is rendered as-is.
 */
(() => {
    'use strict';

    const API = '/api';
    const CART_KEY = 'nebula.cart-id';

    const state = {
        products: [],
        categories: [],
        category: null,
        query: '',
        sort: 'popular',
        loading: false,
        cart: { id: null, items: [], itemCount: 0, subtotal: 0 },
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
        cartDrawer: document.getElementById('cart-drawer'),
        cartBackdrop: document.getElementById('cart-backdrop'),
        cartLines: document.getElementById('cart-lines'),
        cartSubtotal: document.getElementById('cart-subtotal'),
        drawerCount: document.getElementById('drawer-count'),
        checkoutButton: document.getElementById('checkout-button'),
        checkoutDialog: document.getElementById('checkout-dialog'),
        checkoutBody: document.getElementById('checkout-body'),
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

    async function request(path, options = {}) {
        const { headers, ...rest } = options;
        const response = await fetch(API + path, {
            ...rest,
            headers: { Accept: 'application/json', ...headers },
        });
        if (!response.ok) {
            let detail = `Ошибка ${response.status}`;
            let fieldErrors = null;
            try {
                const body = await response.json();
                detail = body.detail || body.title || detail;
                fieldErrors = body.errors || null;
            } catch {
                /* body is not JSON — keep the generic message */
            }
            const error = new Error(detail);
            error.fieldErrors = fieldErrors;
            throw error;
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

    function plural(n, forms) {
        const mod10 = n % 10;
        const mod100 = n % 100;
        if (mod10 === 1 && mod100 !== 11) return forms[0];
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return forms[1];
        return forms[2];
    }

    /* ------------------------------------------------------------------ */
    /* catalog rendering                                                  */
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
                    <button class="card__quickadd" type="button" data-action="quick-add"
                            data-id="${product.id}" aria-label="Добавить «${escapeHtml(product.name)}» в корзину"
                            ${product.inStock ? '' : 'disabled'}>+</button>
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
        if (!state.products.length) {
            dom.subtitle.textContent = 'Ничего не найдено — попробуйте другой запрос';
            return;
        }
        const count = state.products.length;
        const parts = [`${count} ${plural(count, ['товар', 'товара', 'товаров'])}`];
        if (state.category) {
            parts.push(`в категории «${state.category}»`);
        }
        if (state.query) {
            parts.push(`по запросу «${state.query}»`);
        }
        dom.subtitle.textContent = parts.join(' ');
    }

    /* ------------------------------------------------------------------ */
    /* catalog loading                                                    */
    /* ------------------------------------------------------------------ */

    function catalogQuery() {
        const params = new URLSearchParams();
        if (state.category) params.set('category', state.category);
        if (state.query) params.set('q', state.query);
        params.set('sort', state.sort);
        return params.toString();
    }

    async function loadProducts() {
        dom.empty.hidden = true;
        if (!dom.grid.children.length) {
            renderSkeletons();
        }
        try {
            state.products = await request(`/products?${catalogQuery()}`);
            renderProducts();
        } catch (error) {
            renderError(error);
        }
        updateSubtitle();
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
    /* cart                                                               */
    /* ------------------------------------------------------------------ */

    let cartQueue = Promise.resolve();
    let lastFocused = null;

    function rememberCartId(id) {
        try {
            localStorage.setItem(CART_KEY, id);
        } catch {
            /* storage can be unavailable in private mode */
        }
    }

    function savedCartId() {
        try {
            return localStorage.getItem(CART_KEY);
        } catch {
            return null;
        }
    }

    const cartHeaders = () => (state.cart.id ? { 'X-Cart-Id': state.cart.id } : {});

    /** Serialises cart mutations so rapid clicks cannot interleave responses. */
    function enqueue(task) {
        const run = cartQueue.then(task);
        cartQueue = run.catch(() => undefined);
        return run;
    }

    function applyCart(cart) {
        state.cart = {
            id: cart.id,
            items: cart.items || [],
            itemCount: cart.itemCount || 0,
            subtotal: cart.subtotal || 0,
        };
        rememberCartId(cart.id);
        renderCart();
    }

    async function loadCart() {
        try {
            applyCart(await request('/cart', { headers: cartHeaders() }));
        } catch (error) {
            console.warn('Не удалось загрузить корзину', error);
        }
    }

    function addToCart(productId, { quantity = 1, openDrawer = false } = {}) {
        return enqueue(async () => {
            const cart = await request('/cart/items', {
                method: 'POST',
                headers: { ...cartHeaders(), 'Content-Type': 'application/json' },
                body: JSON.stringify({ productId, quantity }),
            });
            applyCart(cart);
            bumpBadge();
            if (openDrawer) {
                openCart();
            } else {
                toast('Товар добавлен в корзину');
            }
        }).catch((error) => toast(error.message, 'error'));
    }

    function setQuantity(productId, quantity) {
        return enqueue(async () => {
            const cart = await request(`/cart/items/${productId}`, {
                method: 'PUT',
                headers: { ...cartHeaders(), 'Content-Type': 'application/json' },
                body: JSON.stringify({ quantity }),
            });
            applyCart(cart);
        }).catch((error) => toast(error.message, 'error'));
    }

    function removeLine(productId) {
        return enqueue(async () => {
            const cart = await request(`/cart/items/${productId}`, {
                method: 'DELETE',
                headers: cartHeaders(),
            });
            applyCart(cart);
        }).catch((error) => toast(error.message, 'error'));
    }

    function renderCart() {
        const { items, itemCount, subtotal } = state.cart;

        dom.cartCount.textContent = String(itemCount);
        dom.cartCount.dataset.count = String(itemCount);
        dom.drawerCount.textContent = String(itemCount);
        dom.cartSubtotal.textContent = money.format(subtotal);
        dom.checkoutButton.disabled = itemCount === 0;

        if (!items.length) {
            dom.cartLines.innerHTML = `
                <div class="cart-empty">
                    <p class="cart-empty__icon" aria-hidden="true">🛒</p>
                    <p>Пока пусто. Самое время что-нибудь выбрать.</p>
                    <button class="btn btn--ghost" type="button" data-action="close-cart">За покупками</button>
                </div>`;
            return;
        }

        dom.cartLines.innerHTML = items.map((line) => `
            <article class="cart-line">
                <div class="cart-line__art" style="--tint:${tintFor(line.category)}">
                    <span aria-hidden="true">${line.icon}</span>
                </div>
                <div class="cart-line__body">
                    <p class="cart-line__name">${escapeHtml(line.name)}</p>
                    <p class="cart-line__price">${money.format(line.unitPrice)}</p>
                    <div class="stepper">
                        <button type="button" data-action="dec" data-id="${line.productId}"
                                aria-label="Уменьшить количество">−</button>
                        <span>${line.quantity}</span>
                        <button type="button" data-action="inc" data-id="${line.productId}"
                                aria-label="Увеличить количество"
                                ${line.quantity >= line.stock ? 'disabled' : ''}>+</button>
                    </div>
                </div>
                <div class="cart-line__side">
                    <span class="cart-line__sum">${money.format(line.lineTotal)}</span>
                    <button class="cart-line__remove" type="button" data-action="remove"
                            data-id="${line.productId}" aria-label="Удалить «${escapeHtml(line.name)}»">✕</button>
                </div>
            </article>`).join('');
    }

    function bumpBadge() {
        dom.cartCount.classList.remove('is-bumping');
        void dom.cartCount.offsetWidth;
        dom.cartCount.classList.add('is-bumping');
    }

    /** Keeps the page from scrolling behind the cart drawer or the checkout dialog. */
    function syncScrollLock() {
        const locked = dom.cartDrawer.classList.contains('is-open') || dom.checkoutDialog.open;
        document.body.classList.toggle('no-scroll', locked);
    }

    function openCart() {
        if (dom.cartDrawer.classList.contains('is-open')) {
            return;
        }
        lastFocused = document.activeElement;
        dom.cartDrawer.classList.add('is-open');
        dom.cartDrawer.setAttribute('aria-hidden', 'false');
        dom.cartButton.setAttribute('aria-expanded', 'true');
        dom.cartBackdrop.hidden = false;
        requestAnimationFrame(() => dom.cartBackdrop.classList.add('is-visible'));
        document.body.classList.add('cart-open');
        syncScrollLock();
        dom.cartDrawer.focus({ preventScroll: true });
    }

    function closeCart() {
        if (!dom.cartDrawer.classList.contains('is-open')) {
            return;
        }
        dom.cartDrawer.classList.remove('is-open');
        dom.cartDrawer.setAttribute('aria-hidden', 'true');
        dom.cartButton.setAttribute('aria-expanded', 'false');
        dom.cartBackdrop.classList.remove('is-visible');
        document.body.classList.remove('cart-open');
        syncScrollLock();
        window.setTimeout(() => {
            if (!dom.cartDrawer.classList.contains('is-open')) {
                dom.cartBackdrop.hidden = true;
            }
        }, 320);
        if (lastFocused instanceof HTMLElement) {
            lastFocused.focus({ preventScroll: true });
        }
    }

    /* ------------------------------------------------------------------ */
    /* checkout                                                           */
    /* ------------------------------------------------------------------ */

    function openCheckout() {
        if (!state.cart.itemCount) {
            toast('Сначала добавьте товары в корзину');
            return;
        }
        dom.checkoutBody.innerHTML = checkoutFormHtml();
        dom.checkoutDialog.showModal();
        syncScrollLock();
        dom.checkoutBody.querySelector('input[name="name"]')?.focus({ preventScroll: true });
    }

    function closeCheckout() {
        if (dom.checkoutDialog.open) {
            dom.checkoutDialog.close();
        }
    }

    function summaryLines(items) {
        return items.map((item) => `
            <li class="summary-line">
                <span class="summary-line__icon" aria-hidden="true">${item.icon}</span>
                <span>
                    <span class="summary-line__name">${escapeHtml(item.name)}</span>
                    <span class="summary-line__qty">${item.quantity} × ${money.format(item.unitPrice)}</span>
                </span>
                <strong>${money.format(item.lineTotal)}</strong>
            </li>`).join('');
    }

    function checkoutFormHtml() {
        const { items, subtotal } = state.cart;
        return `
        <form id="checkout-form" class="checkout">
            <div class="checkout__form">
                <h2 id="checkout-title" class="dialog__title">Оформление заказа</h2>
                <p class="muted small">Доставка за два дня, оплата при получении. Данные нужны только для демо-заказа.</p>

                <label class="field">
                    <span>Имя и фамилия</span>
                    <input class="input" name="name" maxlength="120" autocomplete="name" required>
                    <small class="field-error" data-error-for="name"></small>
                </label>

                <label class="field">
                    <span>Email</span>
                    <input class="input" name="email" type="email" maxlength="160" autocomplete="email" required>
                    <small class="field-error" data-error-for="email"></small>
                </label>

                <label class="field">
                    <span>Адрес доставки</span>
                    <textarea class="input input--area" name="address" rows="2" maxlength="400" required
                              placeholder="Город, улица, дом, квартира"></textarea>
                    <small class="field-error" data-error-for="address"></small>
                </label>

                <label class="field">
                    <span>Комментарий <em>— необязательно</em></span>
                    <input class="input" name="comment" maxlength="600" placeholder="Домофон, удобное время…">
                </label>

                <div id="checkout-alert" class="form-alert" hidden></div>

                <button class="btn btn--primary btn--block" type="submit" id="checkout-submit">
                    Оформить заказ · ${money.format(subtotal)}
                </button>
                <p class="muted small">Это демо-проект: заказ сохранится в базе, но никто никуда его не повезёт.</p>
            </div>

            <aside class="checkout__summary" aria-label="Состав заказа">
                <h3>Ваш заказ</h3>
                <ul class="summary-list">${summaryLines(items)}</ul>
                <div class="summary-total"><span>Итого</span><strong>${money.format(subtotal)}</strong></div>
            </aside>

            <button class="dialog__close" type="button" data-action="close-checkout" aria-label="Закрыть">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"></path></svg>
            </button>
        </form>`;
    }

    function showOrderSuccess(order) {
        dom.checkoutBody.innerHTML = `
            <div class="checkout-success">
                <div class="checkout-success__icon" aria-hidden="true">✓</div>
                <h2 id="checkout-title" class="dialog__title">Заказ оформлен</h2>
                <p class="muted">Номер заказа — нажмите, чтобы скопировать</p>
                <button class="order-code" type="button" data-action="copy-code" data-code="${escapeHtml(order.code)}">
                    ${escapeHtml(order.code)}
                </button>
                <div class="checkout-success__meta">
                    <span class="pill pill--ok">${escapeHtml(order.statusTitle)}</span>
                    <span class="pill">
                        ${order.itemCount} ${plural(order.itemCount, ['позиция', 'позиции', 'позиций'])}
                        · ${money.format(order.total)}
                    </span>
                </div>
                <ul class="summary-list">${summaryLines(order.items)}</ul>
                <p class="muted small">Подтверждение отправим на ${escapeHtml(order.email)}.</p>
                <button class="btn btn--primary" type="button" data-action="finish-checkout">Продолжить покупки</button>
            </div>
            <button class="dialog__close" type="button" data-action="close-checkout" aria-label="Закрыть">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"></path></svg>
            </button>`;

        dom.checkoutDialog.focus({ preventScroll: true });
    }

    async function submitCheckout(event) {
        event.preventDefault();
        const form = event.target;
        const submit = form.querySelector('#checkout-submit');
        const label = submit ? submit.textContent : '';
        clearFormErrors(form);

        const data = Object.fromEntries(new FormData(form).entries());
        if (submit) {
            submit.disabled = true;
            submit.textContent = 'Оформляем…';
        }

        try {
            const order = await request('/orders', {
                method: 'POST',
                headers: { ...cartHeaders(), 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name: String(data.name || '').trim(),
                    email: String(data.email || '').trim(),
                    address: String(data.address || '').trim(),
                    comment: data.comment ? String(data.comment).trim() : null,
                }),
            });
            applyCart({ id: state.cart.id, items: [], itemCount: 0, subtotal: 0 });
            closeCart();
            showOrderSuccess(order);
        } catch (error) {
            if (error.fieldErrors) {
                showFieldErrors(form, error.fieldErrors);
            } else {
                showFormAlert(form, error.message);
            }
        } finally {
            if (submit) {
                submit.disabled = false;
                submit.textContent = label;
            }
        }
    }

    function showFieldErrors(form, errors) {
        Object.entries(errors).forEach(([field, message]) => {
            const hint = form.querySelector(`[data-error-for="${field}"]`);
            const input = form.querySelector(`[name="${field}"]`);
            if (hint) hint.textContent = message;
            if (input) {
                input.classList.add('is-invalid');
                input.setAttribute('aria-invalid', 'true');
            }
        });
        form.querySelector('.input.is-invalid')?.focus({ preventScroll: true });
    }

    function clearFormErrors(form) {
        form.querySelectorAll('.field-error').forEach((hint) => {
            hint.textContent = '';
        });
        form.querySelectorAll('.input').forEach((input) => {
            input.classList.remove('is-invalid');
            input.removeAttribute('aria-invalid');
        });
        const alert = form.querySelector('#checkout-alert');
        if (alert) {
            alert.hidden = true;
            alert.textContent = '';
        }
    }

    function showFormAlert(form, message) {
        const alert = form.querySelector('#checkout-alert');
        if (!alert) {
            toast(message, 'error');
            return;
        }
        alert.textContent = message;
        alert.hidden = false;
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
        if (button.dataset.action === 'quick-add') {
            addToCart(Number(button.dataset.id));
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
            const productId = Number(button.dataset.id);
            closeDialog();
            addToCart(productId, { openDrawer: true });
        }
    });

    dom.cartDrawer.addEventListener('click', (event) => {
        const button = event.target.closest('[data-action]');
        if (!button) return;
        const { action } = button.dataset;
        const productId = Number(button.dataset.id);

        if (action === 'close-cart') {
            closeCart();
            return;
        }
        const line = state.cart.items.find((item) => item.productId === productId);
        if (!line) return;

        if (action === 'inc') setQuantity(productId, Math.min(line.quantity + 1, line.stock));
        if (action === 'dec') setQuantity(productId, Math.max(line.quantity - 1, 0));
        if (action === 'remove') removeLine(productId);
    });

    dom.cartBackdrop.addEventListener('click', closeCart);

    dom.cartButton.addEventListener('click', openCart);

    dom.checkoutButton.addEventListener('click', openCheckout);

    dom.checkoutDialog.addEventListener('submit', (event) => {
        if (event.target.id === 'checkout-form') {
            submitCheckout(event);
        }
    });

    dom.checkoutDialog.addEventListener('click', (event) => {
        if (event.target === dom.checkoutDialog) {
            closeCheckout();
            return;
        }
        const button = event.target.closest('[data-action]');
        if (!button) return;
        const { action } = button.dataset;

        if (action === 'close-checkout' || action === 'finish-checkout') {
            closeCheckout();
        }
        if (action === 'copy-code') {
            const code = button.dataset.code || '';
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(code)
                    .then(() => toast(`Номер ${code} скопирован`))
                    .catch(() => toast(`Номер заказа: ${code}`));
            } else {
                toast(`Номер заказа: ${code}`);
            }
        }
    });

    dom.checkoutDialog.addEventListener('close', syncScrollLock);

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && dom.cartDrawer.classList.contains('is-open')) {
            closeCart();
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
        await Promise.all([loadInitialData(), loadCart()]);
        const deepLink = location.hash.match(/^#product-(\d+)$/);
        if (deepLink) {
            openProduct(Number(deepLink[1]));
        }
    })();
})();
