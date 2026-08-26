document.addEventListener("DOMContentLoaded", () => {
    document.addEventListener("click", (event) => {
        const searchPersonInput = event.target.closest("#search_person");
        if (searchPersonInput) {
            event.preventDefault();
            return;
        }

        const removeAuthManagerLink = event.target.closest(".js-remove-auth-manager");
        if (removeAuthManagerLink) {
            event.preventDefault();
            window.autoCompleteService.removeAuthManager(removeAuthManagerLink);
        }
    });
});
