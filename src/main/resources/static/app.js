const API_URL = "/api/chat";
const CART_API_URL = "/api/cart";
const ORDER_API_URL = "/api/orders";
const form = document.querySelector("#chatForm");
const input = document.querySelector("#messageInput");
const sendButton = document.querySelector("#sendButton");
const sendLabel = document.querySelector(".send-label");
const characterHint = document.querySelector("#characterHint");
const messages = document.querySelector("#chatMessages");
const welcomeState = document.querySelector("#welcomeState");
const quickPrompts = document.querySelectorAll(".prompt-chip");
const loadingTemplate = document.querySelector("#loadingTemplate");
const emptyProductsTemplate = document.querySelector("#emptyProductsTemplate");

// Cart elements
const cartButton = document.querySelector("#cartButton");
const cartBadge = document.querySelector("#cartBadge");
const cartDrawer = document.querySelector("#cartDrawer");
const cartOverlay = document.querySelector("#cartOverlay");
const cartItemsContainer = document.querySelector("#cartItems");
const cartSubtotal = document.querySelector("#cartSubtotal");
const cartItemCount = document.querySelector("#cartItemCount");
const closeCartButton = document.querySelector("#closeCartButton");
const clearCartButton = document.querySelector("#clearCartButton");
const checkoutButton = document.querySelector("#checkoutButton");

let cartData = null;
let isCartLoading = false;

form.addEventListener("submit", sendMessage);
input.addEventListener("keydown", (event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); form.requestSubmit(); } });
input.addEventListener("input", resizeInput);
quickPrompts.forEach((button) => button.addEventListener("click", () => { input.value = button.dataset.prompt; resizeInput(); form.requestSubmit(); }));

// Cart event listeners

//if (cartButton) {
//    cartButton.addEventListener("click", toggleCart);
//}
if (cartButton) {
    cartButton.addEventListener("click", toggleCart);
    cartButton.setAttribute("aria-controls", "cartDrawer");
    cartButton.setAttribute("aria-expanded", "false");
}
if (cartOverlay) {
    cartOverlay.addEventListener("click", closeCart);
}
if (closeCartButton) {
    closeCartButton.addEventListener("click", closeCart);
}
if (clearCartButton) {
    clearCartButton.addEventListener("click", clearCart);
}
if (checkoutButton) {
    checkoutButton.addEventListener("click", proceedToCheckout);
}
// Close cart drawer with Escape key
document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && cartDrawer.classList.contains("open")) {
        closeCart();
    }
});

// Initialize cart on page load
document.addEventListener("DOMContentLoaded", fetchCart);

async function sendMessage(event) {
    event.preventDefault();
    const message = input.value.trim();
    if (!message) return input.focus();

    welcomeState.hidden = true;
    appendMessage(message, "user");
    input.value = "";
    resizeInput();
    setLoading(true);
    const loadingMessage = loadingTemplate.content.firstElementChild.cloneNode(true);
    messages.appendChild(loadingMessage);
    scrollToBottom();

    try {
        const response = await fetch(API_URL, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify({ message })
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "The shopping assistant could not process that request.");

        const products = Array.isArray(payload.products) ? payload.products : [];
        loadingMessage.remove();
        appendMessage(getDisplayAnswer(payload.answer, products), "assistant");
        products.length ? appendProducts(products) : appendEmptyProducts();

        // Refresh cart after AI response in case cart operations were performed
        await fetchCart();

        // Check if the AI response mentions cart operations and provide feedback
        if (payload.answer && (
            payload.answer.toLowerCase().includes('added to cart') ||
            payload.answer.toLowerCase().includes('removed from cart') ||
            payload.answer.toLowerCase().includes('cart updated') ||
            payload.answer.toLowerCase().includes('your cart')
        )) {
            // Brief delay to show cart badge update
            setTimeout(() => {
                if (cartData && cartData.itemCount > 0) {
                    // Optionally auto-open cart if items were added
                    // openCart();
                }
            }, 500);
        }
    } catch (error) {
        loadingMessage.remove();
        appendError(error.message || "Network error. Please confirm the backend is running and try again.", message);
    } finally {
        setLoading(false);
        input.focus();
        scrollToBottom();
    }
}

function resizeInput() {
    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, 150)}px`;
    characterHint.textContent = input.value.length > 1800 ? `${input.value.length}/2000` : "";
}

async function readJson(response) { try { return await response.json(); } catch { return {}; } }

function getDisplayAnswer(answer, products) {
    const text = typeof answer === "string" ? answer.trim() : "";
    if (text && !looksLikeToolCall(text)) return text;
    if (!products.length) return "I could not find matching products in the current catalog. Try another product name, category, or budget.";
    return `I found ${products.length} matching ${products.length === 1 ? "product" : "products"} in the live catalog. ${products.length === 1 ? "Here is the best match" : "Here are the best matches"} for your request.`;
}

function looksLikeToolCall(text) { return /^\s*[{[]/.test(text) && /"(?:name|parameters|tool_calls|function|arguments)"\s*:/i.test(text); }

function appendMessage(text, role) {
    const article = document.createElement("article");
    article.className = `message ${role === "user" ? "user-message" : "assistant-message"}`;
    const avatar = document.createElement("div");
    avatar.className = `message-avatar ${role === "assistant" ? "ai-avatar" : ""}`;
    avatar.setAttribute("aria-hidden", "true");
    avatar.textContent = role === "user" ? "YOU" : "✦";
    const stack = document.createElement("div"); stack.className = "message-stack";
    const label = document.createElement("span"); label.className = "message-label"; label.textContent = role === "user" ? "YOU" : "SHOPWISE AI";
    if (role === "assistant") { const status = document.createElement("time"); status.textContent = "CATALOG CHECKED"; label.appendChild(status); }
    const content = document.createElement("div"); content.className = "message-content";
    const paragraph = document.createElement("p"); paragraph.textContent = text;
    content.appendChild(paragraph); stack.append(label, content); article.append(avatar, stack); messages.appendChild(article);
}

function appendProducts(products) {
    const label = document.createElement("p"); label.className = "products-label"; label.textContent = `Catalog recommendations · ${products.length}`; messages.appendChild(label);
    const grid = document.createElement("section"); grid.className = "product-grid"; grid.setAttribute("aria-label", "Product recommendations");
    products.forEach((product) => {
        const card = document.createElement("article"); card.className = "product-card";
        const visual = document.createElement("div"); visual.className = "product-visual";
        const icon = document.createElement("span"); icon.textContent = productIcon(product);
        const badge = document.createElement("small"); badge.textContent = product.category || "Catalog item";
        visual.append(icon, badge);
        const body = document.createElement("div"); body.className = "product-body";
        const titleRow = document.createElement("div"); titleRow.className = "product-title-row";
        const title = document.createElement("h3"); title.textContent = product.name || "Unnamed product";
        const category = document.createElement("span"); category.className = "category"; category.textContent = product.category || "Uncategorized";
        titleRow.append(title, category);
        const price = document.createElement("strong"); price.className = "product-price"; price.textContent = formatPrice(product.price);
        const description = document.createElement("p"); description.className = "product-description"; description.textContent = product.description || "No description available.";
        const actions = document.createElement("div"); actions.className = "product-actions";
        const details = document.createElement("button"); details.className = "card-button"; details.type = "button"; details.textContent = "Ask about it";
        details.addEventListener("click", () => askAboutProduct(product));
        const compare = document.createElement("button"); compare.className = "card-button primary"; compare.type = "button"; compare.textContent = "Compare";
        compare.addEventListener("click", () => { input.value = `Compare ${product.name} with other available products`; resizeInput(); input.focus(); });
        const addToCart = document.createElement("button"); addToCart.className = "card-button cart-button"; addToCart.type = "button"; addToCart.textContent = "Add to Cart";
        addToCart.addEventListener("click", () => addToCartFromCard(product, addToCart));
        actions.append(details, compare, addToCart); body.append(titleRow, price, description, actions); card.append(visual, body); grid.appendChild(card);
    });
    messages.appendChild(grid);
}

function productIcon(product) {
    const text = `${product.name || ""} ${product.category || ""}`.toLowerCase();
    if (text.includes("laptop") || text.includes("computer")) return "💻";
    if (text.includes("phone") || text.includes("mobile")) return "📱";
    if (text.includes("headphone") || text.includes("audio")) return "🎧";
    if (text.includes("watch")) return "⌚";
    return "✦";
}

function askAboutProduct(product) { input.value = `Tell me more about ${product.name}`; resizeInput(); form.requestSubmit(); }

function appendEmptyProducts() {
    const state = emptyProductsTemplate.content.firstElementChild.cloneNode(true);
    state.querySelector("[data-empty-retry]").addEventListener("click", () => input.focus());
    messages.appendChild(state);
}

function appendError(message, retryMessage) {
    const article = document.createElement("article"); article.className = "message assistant-message error-message";
    const avatar = document.createElement("div"); avatar.className = "message-avatar"; avatar.setAttribute("aria-hidden", "true"); avatar.textContent = "!";
    const stack = document.createElement("div"); stack.className = "message-stack";
    const label = document.createElement("span"); label.className = "message-label"; label.textContent = "CONNECTION ISSUE";
    const content = document.createElement("div"); content.className = "message-content";
    const paragraph = document.createElement("p"); paragraph.textContent = message;
    const retry = document.createElement("button"); retry.className = "retry-button"; retry.type = "button"; retry.textContent = "Try again";
    retry.addEventListener("click", () => { input.value = retryMessage; resizeInput(); form.requestSubmit(); });
    content.append(paragraph, retry); stack.append(label, content); article.append(avatar, stack); messages.appendChild(article);
}

function formatPrice(value) { const amount = Number(value); return Number.isFinite(amount) ? new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(amount) : "Price unavailable"; }
function setLoading(isLoading) { input.disabled = isLoading; sendButton.disabled = isLoading; quickPrompts.forEach((button) => button.disabled = isLoading); sendLabel.textContent = isLoading ? "Searching" : "Send"; }
function scrollToBottom() { messages.scrollTop = messages.scrollHeight; }

// =========================================================
// CART FUNCTIONS
// =========================================================

async function fetchCart() {
    if (isCartLoading) return;
    isCartLoading = true;
    updateCartLoadingState(true);

    try {
        const response = await fetch(CART_API_URL, {
            method: "GET",
            credentials: "include"
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "Failed to fetch cart");

        cartData = payload;
        updateCartUI();
    } catch (error) {
        console.error("Cart fetch error:", error);
        // Silently fail for cart fetch to not interrupt main experience
    } finally {
        isCartLoading = false;
        updateCartLoadingState(false);
    }
}

async function addToCart(productId, quantity = 1) {
    try {
        const response = await fetch(`${CART_API_URL}/items`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify({ productId, quantity })
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "Failed to add to cart");

        cartData = payload;
        updateCartUI();
        return true;
    } catch (error) {
        console.error("Add to cart error:", error);
        alert(error.message || "Failed to add item to cart");
        return false;
    }
}

async function updateCartItemQuantity(itemId, quantity) {
    try {
        const response = await fetch(`${CART_API_URL}/items/${itemId}`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify({ quantity })
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "Failed to update cart");

        cartData = payload;
        updateCartUI();
        return true;
    } catch (error) {
        console.error("Update cart error:", error);
        alert(error.message || "Failed to update cart");
        return false;
    }
}

async function removeCartItem(itemId) {
    try {
        const response = await fetch(`${CART_API_URL}/items/${itemId}`, {
            method: "DELETE",
            credentials: "include"
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "Failed to remove item");

        cartData = payload;
        updateCartUI();
        return true;
    } catch (error) {
        console.error("Remove cart item error:", error);
        alert(error.message || "Failed to remove item");
        return false;
    }
}

async function clearCart() {
    if (!confirm("Are you sure you want to clear your cart?")) return;

    try {
        const response = await fetch(CART_API_URL, {
            method: "DELETE",
            credentials: "include"
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "Failed to clear cart");

        cartData = payload;
        updateCartUI();
    } catch (error) {
        console.error("Clear cart error:", error);
        alert(error.message || "Failed to clear cart");
    }
}

function addToCartFromCard(product, button) {
    const originalText = button.textContent;
    button.textContent = "Adding...";
    button.disabled = true;

    addToCart(product.id, 1).then(success => {
        button.textContent = success ? "Added!" : originalText;
        button.disabled = false;

        if (success) {
            setTimeout(() => {
                button.textContent = originalText;
            }, 2000);
        }
    });
}

function toggleCart() {
    const isOpen = cartDrawer.classList.contains("open");
    if (isOpen) {
        closeCart();
    } else {
        openCart();
    }
}

//function openCart() {
//    cartDrawer.classList.add("open");
//    cartOverlay.classList.add("open");
//    document.body.style.overflow = "hidden";
//    renderCartItems();
//}

function openCart() {
    cartDrawer.classList.add("open");
    cartOverlay.classList.add("open");
    cartButton.setAttribute("aria-expanded", "true");
    document.body.style.overflow = "hidden";
    renderCartItems();
}

//function closeCart() {
//    cartDrawer.classList.remove("open");
//    cartOverlay.classList.remove("open");
//    document.body.style.overflow = "";
//}

function closeCart() {
    cartDrawer.classList.remove("open");
    cartOverlay.classList.remove("open");
    cartButton.setAttribute("aria-expanded", "false");
    document.body.style.overflow = "";
}

function updateCartUI() {
    if (!cartData) return;

    // Update badge
    const itemCount = cartData.itemCount || 0;
    cartBadge.textContent = itemCount > 99 ? "99+" : itemCount;
    cartBadge.style.display = itemCount > 0 ? "flex" : "none";

    // Update cart drawer if open
    if (cartDrawer.classList.contains("open")) {
        renderCartItems();
    }
}

function updateCartLoadingState(isLoading) {
    if (cartButton) {
        cartButton.disabled = isLoading;
        cartButton.style.opacity = isLoading ? "0.5" : "1";
    }
}

function renderCartItems() {
    if (!cartData || !cartItemsContainer) return;

    const items = cartData.items || [];

    if (items.length === 0) {
        cartItemsContainer.innerHTML = `
            <div class="cart-empty">
                <div class="cart-empty-icon">🛒</div>
                <p>Your cart is empty</p>
                <small>Add products from recommendations to get started</small>
            </div>
        `;
        cartSubtotal.textContent = formatPrice(0);
        cartItemCount.textContent = "0 items";
        clearCartButton.style.display = "none";
        checkoutButton.disabled = true;
        return;
    }

    clearCartButton.style.display = "block";
    checkoutButton.disabled = false;
    cartItemCount.textContent = `${cartData.itemCount} item${cartData.itemCount !== 1 ? 's' : ''}`;
    cartSubtotal.textContent = formatPrice(cartData.subtotal);

    cartItemsContainer.innerHTML = items.map(item => `
        <div class="cart-item" data-item-id="${item.id}">
            <div class="cart-item-info">
                <h4 class="cart-item-name">${escapeHtml(item.name)}</h4>
                <span class="cart-item-category">${escapeHtml(item.category || '')}</span>
                <div class="cart-item-price">
                    <span class="unit-price">${formatPrice(item.unitPrice)}</span>
                    <span class="line-total">${formatPrice(item.lineTotal)}</span>
                </div>
            </div>
            <div class="cart-item-controls">
                <div class="quantity-controls">
                    <button class="quantity-btn" data-action="decrease" data-item-id="${item.id}" ${item.quantity <= 1 ? 'disabled' : ''}>−</button>
                    <span class="quantity-value">${item.quantity}</span>
                    <button class="quantity-btn" data-action="increase" data-item-id="${item.id}" ${item.quantity >= 99 ? 'disabled' : ''}>+</button>
                </div>
                <button class="remove-item-btn" data-item-id="${item.id}" aria-label="Remove ${escapeHtml(item.name)}">
                    <span>×</span>
                </button>
            </div>
        </div>
    `).join('');

    // Add event listeners to quantity controls
    document.querySelectorAll('.quantity-btn').forEach(btn => {
        btn.addEventListener('click', handleQuantityChange);
    });

    // Add event listeners to remove buttons
    document.querySelectorAll('.remove-item-btn').forEach(btn => {
        btn.addEventListener('click', handleRemoveItem);
    });
}

function handleQuantityChange(event) {
    const button = event.target;
    const itemId = parseInt(button.dataset.itemId);
    const action = button.dataset.action;
    const item = cartData.items.find(i => i.id === itemId);

    if (!item) return;

    let newQuantity = item.quantity;
    if (action === 'increase') {
        newQuantity = Math.min(99, item.quantity + 1);
    } else if (action === 'decrease') {
        newQuantity = Math.max(1, item.quantity - 1);
    }

    if (newQuantity !== item.quantity) {
        updateCartItemQuantity(itemId, newQuantity);
    }
}

function handleRemoveItem(event) {
    const button = event.target.closest('.remove-item-btn');
    const itemId = parseInt(button.dataset.itemId);

    if (confirm('Remove this item from cart?')) {
        removeCartItem(itemId);
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// =========================================================
// CHECKOUT FUNCTIONS
// =========================================================

function proceedToCheckout() {
    if (!cartData || cartData.items.length === 0) {
        alert("Your cart is empty. Add items before checkout.");
        return;
    }
    // Navigate to checkout page
    window.location.href = "/checkout.html";
}
