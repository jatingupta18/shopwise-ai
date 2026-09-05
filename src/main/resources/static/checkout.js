const CART_API_URL = "/api/cart";
const ORDER_API_URL = "/api/orders";

let cartData = null;
let isProcessing = false;

// DOM elements
const checkoutCartItems = document.querySelector("#checkoutCartItems");
const checkoutTotalItems = document.querySelector("#checkoutTotalItems");
const checkoutTotalAmount = document.querySelector("#checkoutTotalAmount");
const checkoutForm = document.querySelector("#checkoutForm");
const submitOrderButton = document.querySelector("#submitOrderButton");
const paymentStatus = document.querySelector("#paymentStatus");
const orderNumber = document.querySelector("#orderNumber");
const orderStatus = document.querySelector("#orderStatus");

// Initialize checkout page
document.addEventListener("DOMContentLoaded", initializeCheckout);

async function initializeCheckout() {
    await fetchCart();
    setupFormValidation();
}

async function fetchCart() {
    try {
        const response = await fetch(CART_API_URL, {
            method: "GET",
            credentials: "include"
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "Failed to fetch cart");

        cartData = payload;
        renderCartSummary();
        updateCheckoutButton();
    } catch (error) {
        console.error("Cart fetch error:", error);
        showCartError(error.message || "Failed to load cart. Please try again.");
    }
}

function renderCartSummary() {
    if (!cartData || !checkoutCartItems) return;

    const items = cartData.items || [];

    if (items.length === 0) {
        checkoutCartItems.innerHTML = `
            <div class="empty-cart-message">
                <div class="empty-icon">🛒</div>
                <p>Your cart is empty</p>
                <a href="/chat.html" class="back-to-chat">Return to shopping</a>
            </div>
        `;
        checkoutTotalItems.textContent = "0";
        checkoutTotalAmount.textContent = formatPrice(0);
        disableCheckout();
        return;
    }

    checkoutCartItems.innerHTML = items.map(item => `
        <div class="checkout-cart-item">
            <div class="item-info">
                <h4 class="item-name">${escapeHtml(item.name)}</h4>
                <span class="item-category">${escapeHtml(item.category || '')}</span>
            </div>
            <div class="item-details">
                <span class="item-quantity">Qty: ${item.quantity}</span>
                <span class="item-price">${formatPrice(item.lineTotal)}</span>
            </div>
        </div>
    `).join('');

    checkoutTotalItems.textContent = cartData.itemCount;
    checkoutTotalAmount.textContent = formatPrice(cartData.subtotal);
    enableCheckout();
}

function setupFormValidation() {
    const phoneInput = document.querySelector("#phone");
    const pinCodeInput = document.querySelector("#pinCode");

    phoneInput.addEventListener("input", function() {
        this.value = this.value.replace(/[^0-9]/g, '').slice(0, 10);
    });

    pinCodeInput.addEventListener("input", function() {
        this.value = this.value.replace(/[^0-9]/g, '').slice(0, 6);
    });

    checkoutForm.addEventListener("submit", handleCheckout);
}

async function handleCheckout(event) {
    event.preventDefault();

    if (isProcessing) return;
    if (!cartData || cartData.items.length === 0) {
        alert("Your cart is empty. Add items before checkout.");
        return;
    }

    isProcessing = true;
    updateSubmitButton(true);

    try {
        // Step 1: Process mock payment
        await processMockPayment();

        // Step 2: Create order
        const orderData = await createOrder();

        // Step 3: Show success and redirect
        showOrderSuccess(orderData);
    } catch (error) {
        console.error("Checkout error:", error);
        alert(error.message || "Checkout failed. Please try again.");
        resetPaymentStatus();
    } finally {
        isProcessing = false;
        updateSubmitButton(false);
    }
}

async function processMockPayment() {
    updatePaymentStatus("processing", "Processing payment...");

    // Simulate payment processing delay
    await new Promise(resolve => setTimeout(resolve, 2000));

    // Mock payment always succeeds in this demo
    updatePaymentStatus("success", "Payment successful");
}

async function createOrder() {
    const formData = new FormData(checkoutForm);
    const checkoutRequest = {
        fullName: formData.get("fullName"),
        email: formData.get("email"),
        phone: formData.get("phone"),
        address: formData.get("address"),
        city: formData.get("city"),
        state: formData.get("state"),
        pinCode: formData.get("pinCode")
    };

    const response = await fetch(`${ORDER_API_URL}/checkout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(checkoutRequest)
    });

    const payload = await readJson(response);
    if (!response.ok) {
        throw new Error(payload.message || "Failed to create order");
    }

    return payload;
}

function showOrderSuccess(orderData) {
    orderNumber.textContent = orderData.orderNumber;
    orderStatus.textContent = "Confirmed";
    orderStatus.style.color = "#0a8e6f";

    // Show success message
    alert(`Order placed successfully!\n\nOrder Number: ${orderData.orderNumber}\nTotal: ${formatPrice(orderData.totalAmount)}\n\nThank you for your order!`);

    // Redirect to confirmation page after short delay
    setTimeout(() => {
        window.location.href = `/order-confirmation.html?orderNumber=${orderData.orderNumber}`;
    }, 1000);
}

function updatePaymentStatus(status, text) {
    const indicator = paymentStatus.querySelector(".status-indicator");
    const statusText = paymentStatus.querySelector(".status-text");

    statusText.textContent = text;

    indicator.className = "status-indicator";

    switch (status) {
        case "processing":
            indicator.classList.add("processing");
            break;
        case "success":
            indicator.classList.add("success");
            break;
        case "error":
            indicator.classList.add("error");
            break;
        default:
            indicator.classList.add("ready");
    }
}

function resetPaymentStatus() {
    updatePaymentStatus("ready", "Ready for payment");
}

function updateSubmitButton(processing) {
    submitOrderButton.disabled = processing;
    const label = submitOrderButton.querySelector(".submit-label");
    const icon = submitOrderButton.querySelector(".submit-icon");

    if (processing) {
        label.textContent = "Processing...";
        icon.textContent = "⏳";
    } else {
        label.textContent = "Place Order";
        icon.textContent = "→";
    }
}

function enableCheckout() {
    submitOrderButton.disabled = false;
    submitOrderButton.classList.remove("disabled");
}

function disableCheckout() {
    submitOrderButton.disabled = true;
    submitOrderButton.classList.add("disabled");
}

function updateCheckoutButton() {
    if (!cartData || cartData.items.length === 0) {
        disableCheckout();
    } else {
        enableCheckout();
    }
}

function showCartError(message) {
    checkoutCartItems.innerHTML = `
        <div class="cart-error">
            <div class="error-icon">⚠️</div>
            <p>${escapeHtml(message)}</p>
            <button onclick="window.location.reload()" class="retry-button">Try Again</button>
        </div>
    `;
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