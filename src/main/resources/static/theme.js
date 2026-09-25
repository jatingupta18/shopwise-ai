(() => {
    const storageKey = "shopwise-theme";
    const root = document.documentElement;

    const applyTheme = (theme) => {
        root.dataset.theme = theme;
        root.style.colorScheme = theme === "dark" ? "dark" : "light";
        try {
            localStorage.setItem(storageKey, theme);
        } catch {
            // Theme selection still works if browser storage is unavailable.
        }
    };

    try {
        applyTheme(localStorage.getItem(storageKey) === "dark" ? "dark" : "light");
    } catch {
        applyTheme("light");
    }

    const addToggle = () => {
        const actions = document.querySelector(".header-actions");
        const landingNavigation = document.querySelector(".landing-nav");
        if ((!actions && !landingNavigation) || document.querySelector("#themeToggle")) return;

        const toggle = document.createElement("button");
        toggle.id = "themeToggle";
        toggle.className = "theme-toggle";
        toggle.type = "button";
        toggle.setAttribute("aria-label", "Switch to dark mode");
        toggle.innerHTML = '<span class="theme-toggle-icon" aria-hidden="true">☀</span><span class="theme-toggle-knob" aria-hidden="true"></span><span class="theme-toggle-icon" aria-hidden="true">☾</span>';

        const sync = () => {
            const isDark = root.dataset.theme === "dark";
            toggle.setAttribute("aria-pressed", String(isDark));
            toggle.setAttribute("aria-label", isDark ? "Switch to light mode" : "Switch to dark mode");
            toggle.title = isDark ? "Switch to light mode" : "Switch to dark mode";
        };
        toggle.addEventListener("click", () => {
            applyTheme(root.dataset.theme === "dark" ? "light" : "dark");
            sync();
        });
        (actions || landingNavigation).prepend(toggle);
        sync();
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", addToggle, { once: true });
    } else {
        addToggle();
    }
})();
