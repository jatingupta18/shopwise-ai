const ORDERS_API_URL = "/api/orders";

let ordersData = null;

// DOM elements
const ordersContainer = document.querySelector("#ordersContainer");
const emptyState = document.querySelector("#emptyState");
const loadingState = document.querySelector("#loadingState");
const errorState = document.querySelector("#errorState");

// Initialize order history page
document.addEventListener("DOMContentLoaded", loadOrders);

async function loadOrders() {
    showLoading();

    try {
        const response = await fetch(ORDERS_API_URL, {
            method: "GET",
            credentials: "include"
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "Failed to fetch orders");

        ordersData = payload;
        renderOrders();
    } catch (error) {
        console.error("Orders fetch error:", error);
        showError(error.message || "Failed to load orders. Please try again.");
    }
}

function renderOrders() {
    if (!ordersData || ordersData.length === 0) {
        showEmpty();
        return;
    }

    showOrders();
    ordersContainer.innerHTML = ordersData.map(order => `
        <div class="order-card">
            <div class="order-header">
                <div class="order-number">
                    <span class="order-label">Order Number</span>
                    <span class="order-value">${escapeHtml(order.orderNumber)}</span>
                </div>
                <div class="order-status status-${order.status.toLowerCase()}">
                    ${formatStatus(order.status)}
                </div>
            </div>
            
            <div class="order-date">
                <span class="date-label">Order Date</span>
                <span class="date-value">${formatDate(order.createdAt)}</span>
            </div>

            <div class="order-items-preview">
                <span class="items-label">Items</span>
                <div class="items-list">
                    ${order.items.slice(0, 3).map(item => `
                        <div class="item-preview">
                            <span class="item-name">${escapeHtml(item.productName)}</span>
                            <span class="item-qty">x${item.quantity}</span>
                        </div>
                    `).join('')}
                    ${order.items.length > 3 ? `<span class="more-items">+${order.items.length - 3} more items</span>` : ''}
                </div>
            </div>

            <div class="order-footer">
                <div class="order-total">
                    <span class="total-label">Total</span>
                    <span class="total-value">${formatPrice(order.totalAmount)}</span>
                </div>
                <div class="order-delivery">
                    <span class="delivery-label">Estimated Delivery</span>
                    <span class="delivery-value">3-5 business days</span>
                </div>
            </div>
        </div>
    `).join('');
}

function showLoading() {
    loadingState.style.display = "block";
    emptyState.style.display = "none";
    errorState.style.display = "none";
    ordersContainer.style.display = "none";
}

function showEmpty() {
    emptyState.style.display = "block";
    loadingState.style.display = "none";
    errorState.style.display = "none";
    ordersContainer.style.display = "none";
}

function showOrders() {
    ordersContainer.style.display = "block";
    loadingState.style.display = "none";
    emptyState.style.display = "none";
    errorState.style.display = "none";
}

function showError(message) {
    errorState.style.display = "block";
    errorState.querySelector("p").textContent = message;
    loadingState.style.display = "none";
    emptyState.style.display = "none";
    ordersContainer.style.display = "none";
}

function formatStatus(status) {
    const statusMap = {
        "PENDING": "Pending",
        "CONFIRMED": "Confirmed",
        "PROCESSING": "Processing",
        "SHIPPED": "Shipped",
        "DELIVERED": "Delivered",
        "CANCELLED": "Cancelled"
    };
    return statusMap[status] || status;
}

function formatDate(dateString) {
    if (!dateString) return "Unknown";
    const date = new Date(dateString);
    return date.toLocaleDateString("en-IN", {
        year: "numeric",
        month: "short",
        day: "numeric"
    });
}

function formatPrice(value) {
    const amount = Number(value);
    return Number.isFinite(amount) 
        ? new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(amount) 
        : "Price unavailable";
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

async function readJson(response) {
    try {
        return await response.json();
    } catch {
        return {};
    }
}
