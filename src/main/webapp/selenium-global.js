(function() {
    function toggleSlots(id) {
        var element = document.getElementById(id);
        var toggle = document.getElementById(id.replace('slots-', 'slots-toggle-'));
        if (!element || !toggle) {
            return;
        }
        if (element.style.display === "none" || element.style.display === "") {
            element.style.display = "block";
            toggle.textContent = "\u25b2"; // ▲
        } else {
            element.style.display = "none";
            toggle.textContent = "\u25bc"; // ▼
        }
    }

    // expose globally for jelly onclick handlers
    window.toggleSlots = toggleSlots;
})();

