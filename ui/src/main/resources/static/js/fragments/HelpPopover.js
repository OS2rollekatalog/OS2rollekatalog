// Prevents the help popover trigger from navigating (href="#"),
// since the anchor only exists to host a Bootstrap popover.
document.addEventListener("DOMContentLoaded", () => {
    document.addEventListener("click", (event) => {
        const trigger = event.target.closest(".js-help-popover");
        if (trigger) {
            event.preventDefault();
        }
    });
});
