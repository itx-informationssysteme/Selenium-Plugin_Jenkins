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

    window.toggleSlots = toggleSlots;

    function initToggleSwitches() {
        var toggleInputs = document.querySelectorAll('.selenium-toggle-input');
        toggleInputs.forEach(function(input) {
            input.addEventListener('change', function() {
                var form = this.closest('form');
                if (form) {
                    form.submit();
                } else if (this.form) {
                    this.form.submit();
                }
            });
        });
    }

    function initSlotsToggle() {
        var slotsToggleLinks = document.querySelectorAll('.selenium-slots-toggle');
        slotsToggleLinks.forEach(function(link) {
            link.addEventListener('click', function(e) {
                e.preventDefault();
                var slotsId = this.getAttribute('data-slots-id');
                if (slotsId) {
                    toggleSlots(slotsId);
                }
            });
        });
    }

    function init() {
        initToggleSwitches();
        initSlotsToggle();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

