/**
 * Nebula Store — admin dashboard.
 *
 * Talks to /api/admin with the X-Admin-Token header. The token is kept in sessionStorage
 * so it survives a page reload but never leaves the tab.
 */
(() => {
    'use strict';

    const API = '/api/admin';
    const TOKEN_KEY = 'nebula.admin-token';
    const PAGE_SIZE = 10;

    const STATUS_LABELS = {
        NEW: 'Новый',
        PAID: 'Оплачен',
        SHIPPED: 'Отправлен',
        DELIVERED: 'Доставлен',
        CANCELLED: 'Отменён',
    };

    const TRANSITIONS = {
        NEW: [
            { status: 'PAID', label: 'Оплачен' },
            { status: 'CANCELLED', label: 'Отменить', danger: true },
        ],
        PAID: [
            { status: 'SHIPPED', label: 'Отправить' },
            { status: 'CANCELLED', label: 'Отменить', danger: true },
        ],
        SHIPPED: [
            { status: 'DELIVERED', label: 'Доставлен' },
            { status: 'CANCELLED', label: 'Отменить', danger: true },
        ],
        DELIVERED: [],
        CANCELLED: [],
    };

    const state = {
        token: null,
        orders: [],
        stats: null,
        status: null,
        page: 0,
        totalPages: 0,
        totalElements: 0,
        busy: false,
    };

    const dom = {
        login: document.getElementById('login'),
        loginForm: document.getElementById('login-form'),
        loginError: document.getElementById('login-error'),
        dashboard: document.getElementById('dashboard'),
        dashboardSubtitle: document.getElementById('dashboard-subtitle'),
        logout: document.getElementById('logout'),
        refresh: document.getElementById('refresh'),
        stats: document.getElementById('stat-orders'),
        revenue: document.getElementById('stat-revenue'),
        units: document.getElementById('stat-units'),
        average: document.getElementById('stat-average'),
        chips: document.getElementById('status-chips'),
        body: document.getElementById('orders-body'),
        empty: document.getElementById('empty-orders'),
        pager: document.getElementById('pager'),
        dialog: document.getElementById('order-dialog'),
        dialogBody: document.getElementById('order-body'),
        toasts: document.getElementById('toasts'),
    };

    const money = new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        maximumFractionDigits: 0,
    });

    const dateFormat = new Intl.DateTimeFormat('ru-RU', {
        day: '2-digit',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
    });

    class UnauthorizedError extends Error {
    }

    /* ------------------------------------------------------------------ */
    /* helpers                                                            */
    /* ------------------------------------------------------------------ */

    const escapeHtml = (value) => String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');

    function toast(message, variant = 'info') {
        const node = document.createElement('div');
        node.className = `toast${variant === 'error' ? ' toast--error' : ''}`;
        node.innerHTML = `<span aria-hidden="true">${variant === 'error' ? '⚠️' : '✨'}</span><span>${escapeHtml(message)}</span>`;
        dom.toasts.append(node);
        window.setTimeout(() => {
            node.classList.add('is-leaving');
            node.addEventListener('animationend', () => node.remove(), { once: true });
        }, 3200);
    }

    async function api(path, options = {}) {
        const { headers, ...rest } = options;
        const response = await fetch(API + path, {
            ...rest,
            headers: {
                Accept: 'application/json',
                'X-Admin-Token': state.token || '',
                ...headers,
            },
        });

        if (response.status === 401) {
            throw new UnauthorizedError('Требуется токен администратора');
        }
        if (!response.ok) {
            let detail = `Ошибка ${response.status}`;
            try {
                const body = await response.json();
                detail = body.detail || body.title || detail;
            } catch {
                /* not JSON */
            }
            throw new Error(detail);
        }
        return response.json();
    }

    function readToken() {
        try {
            return sessionStorage.getItem(TOKEN_KEY);
        } catch {
            return null;
        }
    }

    function writeToken(token) {
        try {
            if (token) {
                sessionStorage.setItem(TOKEN_KEY, token);
            } else {
                sessionStorage.removeItem(TOKEN_KEY);
            }
        } catch {
            /* storage disabled */
        }
    }

    /* ------------------------------------------------------------------ */
    /* rendering                                                          */
    /* ------------------------------------------------------------------ */

    function statusChip(status, title) {
        return `<span class="status-chip status-chip--${status}">${escapeHtml(title || STATUS_LABELS[status] || status)}</span>`;
    }

    function actionButtons(order) {
        const actions = TRANSITIONS[order.status] || [];
        if (!actions.length) {
            return '<span class="muted small">—</span>';
        }
        return actions.map((action) => `
            <button class="btn btn--ghost btn--small${action.danger ? ' btn--danger' : ''}"
                    type="button" data-action="status" data-code="${escapeHtml(order.code)}"
                    data-status="${action.status}">${escapeHtml(action.label)}</button>`).join('');
    }

    function renderStats() {
        const stats = state.stats;
        if (!stats) {
            return;
        }
        dom.stats.textContent = String(stats.totalOrders);
        dom.revenue.textContent = money.format(stats.revenue);
        dom.units.textContent = String(stats.unitsSold);
        dom.average.textContent = money.format(stats.averageOrderValue);
    }

    function renderChips() {
        const byStatus = state.stats ? state.stats.byStatus : {};
        const total = state.stats ? state.stats.totalOrders : 0;
        const chips = [
            `<button class="chip" type="button" data-status-filter="" aria-pressed="${state.status === null}">
                Все <span class="chip__count">${total}</span>
             </button>`,
            ...Object.keys(STATUS_LABELS).map((status) => `
                <button class="chip" type="button" data-status-filter="${status}"
                        aria-pressed="${state.status === status}">
                    ${STATUS_LABELS[status]} <span class="chip__count">${byStatus[status] || 0}</span>
                </button>`),
        ];
        dom.chips.innerHTML = chips.join('');
    }

    function renderTable() {
        if (!state.orders.length) {
            dom.body.innerHTML = '';
            dom.empty.hidden = false;
        } else {
            dom.empty.hidden = true;
            dom.body.innerHTML = state.orders.map((order) => `
                <tr>
                    <td>
                        <button class="order-code-cell" type="button" data-action="open"
                                data-code="${escapeHtml(order.code)}">${escapeHtml(order.code)}</button>
                    </td>
                    <td class="cell-nowrap">${dateFormat.format(new Date(order.createdAt))}</td>
                    <td>
                        <div class="customer-cell">
                            <span>${escapeHtml(order.customerName)}</span>
                            <span class="customer-cell__mail">${escapeHtml(order.email)}</span>
                        </div>
                    </td>
                    <td class="cell-mono">${order.itemCount}</td>
                    <td class="cell-mono">${money.format(order.total)}</td>
                    <td>${statusChip(order.status, order.statusTitle)}</td>
                    <td>
                        <div class="row-actions">${actionButtons(order)}</div>
                    </td>
                </tr>`).join('');
        }

        const from = state.totalElements === 0 ? 0 : state.page * PAGE_SIZE + 1;
        const to = Math.min((state.page + 1) * PAGE_SIZE, state.totalElements);
        dom.dashboardSubtitle.textContent = state.totalElements === 0
            ? 'Заказов пока нет'
            : `${state.totalElements} заказов · показаны ${from}–${to}`;

        renderPager();
    }

    function renderPager() {
        if (state.totalPages <= 1) {
            dom.pager.innerHTML = '';
            return;
        }
        const pages = [];
        const first = Math.max(0, Math.min(state.page - 2, state.totalPages - 5));
        const last = Math.min(state.totalPages - 1, first + 4);

        pages.push(`<button class="pager-button" type="button" data-page="${state.page - 1}"
                        ${state.page === 0 ? 'disabled' : ''} aria-label="Предыдущая страница">←</button>`);
        for (let page = first; page <= last; page += 1) {
            pages.push(`<button class="pager-button" type="button" data-page="${page}"
                            ${page === state.page ? 'aria-current="page"' : ''}>${page + 1}</button>`);
        }
        pages.push(`<button class="pager-button" type="button" data-page="${state.page + 1}"
                        ${state.page >= state.totalPages - 1 ? 'disabled' : ''} aria-label="Следующая страница">→</button>`);

        dom.pager.innerHTML = pages.join('');
    }

    function render() {
        renderStats();
        renderChips();
        renderTable();
    }

    /* ------------------------------------------------------------------ */
    /* data                                                               */
    /* ------------------------------------------------------------------ */

    async function load() {
        const params = new URLSearchParams({
            page: String(state.page),
            size: String(PAGE_SIZE),
        });
        if (state.status) {
            params.set('status', state.status);
        }

        const [stats, page] = await Promise.all([
            api('/stats'),
            api(`/orders?${params}`),
        ]);

        state.stats = stats;
        state.orders = page.items;
        state.page = page.page;
        state.totalPages = page.totalPages;
        state.totalElements = page.totalElements;
        render();
    }

    async function changeStatus(code, status, button) {
        if (state.busy) {
            return;
        }
        state.busy = true;
        if (button) {
            button.disabled = true;
        }
        try {
            await api(`/orders/${encodeURIComponent(code)}/status`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ status }),
            });
            toast(`Статус заказа ${code} → «${STATUS_LABELS[status]}»`);
            closeDialog();
            await load();
        } catch (error) {
            toast(error.message, 'error');
        } finally {
            state.busy = false;
            if (button) {
                button.disabled = false;
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* order dialog                                                       */
    /* ------------------------------------------------------------------ */

    function openOrder(code) {
        const order = state.orders.find((item) => item.code === code);
        if (!order) {
            return;
        }

        const items = order.items.map((item) => `
            <li class="summary-line">
                <span class="summary-line__icon" aria-hidden="true">${item.icon}</span>
                <span>
                    <span class="summary-line__name">${escapeHtml(item.name)}</span>
                    <span class="summary-line__qty">${item.quantity} × ${money.format(item.unitPrice)}</span>
                </span>
                <strong>${money.format(item.lineTotal)}</strong>
            </li>`).join('');

        dom.dialogBody.innerHTML = `
            <div class="order-detail">
                <div class="order-detail__head">
                    <h2 id="order-dialog-title" class="order-detail__code">${escapeHtml(order.code)}</h2>
                    ${statusChip(order.status, order.statusTitle)}
                </div>

                <div class="order-detail__grid">
                    <div class="detail-block">
                        <span class="detail-block__label">Покупатель</span>
                        <span class="detail-block__value">${escapeHtml(order.customerName)}</span>
                    </div>
                    <div class="detail-block">
                        <span class="detail-block__label">Email</span>
                        <span class="detail-block__value">${escapeHtml(order.email)}</span>
                    </div>
                    <div class="detail-block">
                        <span class="detail-block__label">Адрес</span>
                        <span class="detail-block__value">${escapeHtml(order.address)}</span>
                    </div>
                    <div class="detail-block">
                        <span class="detail-block__label">Создан</span>
                        <span class="detail-block__value">${dateFormat.format(new Date(order.createdAt))}</span>
                    </div>
                    ${order.comment ? `
                    <div class="detail-block">
                        <span class="detail-block__label">Комментарий</span>
                        <span class="detail-block__value">${escapeHtml(order.comment)}</span>
                    </div>` : ''}
                </div>

                <ul class="summary-list">${items}</ul>

                <div class="order-detail__totals">
                    <span>Итого</span>
                    <strong>${money.format(order.total)}</strong>
                </div>

                <div class="order-detail__actions">${actionButtons(order)}</div>

                <button class="dialog__close" type="button" data-action="close-dialog" aria-label="Закрыть">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"></path></svg>
                </button>
            </div>`;

        dom.dialog.showModal();
    }

    function closeDialog() {
        if (dom.dialog.open) {
            dom.dialog.close();
        }
    }

    /* ------------------------------------------------------------------ */
    /* views                                                              */
    /* ------------------------------------------------------------------ */

    function showLogin(message = '') {
        dom.login.hidden = false;
        dom.dashboard.hidden = true;
        dom.logout.hidden = true;
        dom.loginError.textContent = message;
    }

    function showDashboard() {
        dom.login.hidden = true;
        dom.dashboard.hidden = false;
        dom.logout.hidden = false;
    }

    async function signIn(token) {
        state.token = token;
        try {
            await load();
            writeToken(token);
            showDashboard();
        } catch (error) {
            state.token = null;
            throw error;
        }
    }

    /* ------------------------------------------------------------------ */
    /* events                                                             */
    /* ------------------------------------------------------------------ */

    dom.loginForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        const token = new FormData(event.target).get('token');
        try {
            await signIn(String(token || '').trim());
            toast('Добро пожаловать в админку');
        } catch (error) {
            showLogin(error instanceof UnauthorizedError
                ? 'Токен не подошёл — проверьте значение app.admin-token'
                : error.message);
        }
    });

    dom.logout.addEventListener('click', () => {
        state.token = null;
        writeToken(null);
        state.orders = [];
        state.stats = null;
        state.status = null;
        state.page = 0;
        showLogin();
    });

    dom.refresh.addEventListener('click', async () => {
        try {
            await load();
            toast('Данные обновлены');
        } catch (error) {
            toast(error.message, 'error');
        }
    });

    dom.chips.addEventListener('click', async (event) => {
        const chip = event.target.closest('[data-status-filter]');
        if (!chip) {
            return;
        }
        state.status = chip.dataset.statusFilter || null;
        state.page = 0;
        try {
            await load();
        } catch (error) {
            toast(error.message, 'error');
        }
    });

    dom.body.addEventListener('click', (event) => {
        const button = event.target.closest('[data-action]');
        if (!button) {
            return;
        }
        if (button.dataset.action === 'open') {
            openOrder(button.dataset.code);
        }
        if (button.dataset.action === 'status') {
            changeStatus(button.dataset.code, button.dataset.status, button);
        }
    });

    dom.dialog.addEventListener('click', (event) => {
        if (event.target === dom.dialog) {
            closeDialog();
            return;
        }
        const button = event.target.closest('[data-action]');
        if (!button) {
            return;
        }
        if (button.dataset.action === 'close-dialog') {
            closeDialog();
        }
        if (button.dataset.action === 'status') {
            changeStatus(button.dataset.code, button.dataset.status, button);
        }
    });

    dom.pager.addEventListener('click', async (event) => {
        const button = event.target.closest('[data-page]');
        if (!button || button.disabled) {
            return;
        }
        state.page = Number(button.dataset.page);
        try {
            await load();
        } catch (error) {
            toast(error.message, 'error');
        }
    });

    /* ------------------------------------------------------------------ */
    /* boot                                                               */
    /* ------------------------------------------------------------------ */

    (async () => {
        const token = readToken();
        if (!token) {
            showLogin();
            return;
        }
        state.token = token;
        try {
            await load();
            showDashboard();
        } catch (error) {
            writeToken(null);
            state.token = null;
            showLogin(error instanceof UnauthorizedError
                ? 'Токен больше не действует — войдите заново'
                : error.message);
        }
    })();
})();
