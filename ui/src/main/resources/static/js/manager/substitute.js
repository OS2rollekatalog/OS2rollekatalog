/**
 * Page logic for the manager substitute administration page.
 */
class ManagerSubstituteService {
    config;
    networkService;
    sweetAlertService;

    constructor(config, networkService, sweetAlertService) {
        this.config = config;
        this.networkService = networkService;
        this.sweetAlertService = sweetAlertService;
    }

    /**
     * Initializes the person search autocomplete field, filtering out the
     * current manager from suggestions.
     */
    initSearchPersonAutocomplete() {
        const searchField = $("#search_person");

        searchField.autocomplete({
            serviceUrl: `${this.config.restUrl}/search/person`,
            onSelect: (suggestion) => {
                searchField.val(suggestion.value);
                $("#selectedSubstituteUuid").val(suggestion.data);
            },
            preventBadQueries: true,
            triggerSelectOnValidInput: false,
            // Filter results to not show the manager themself
            transformResult: (response) => {
                const responseObject = $.parseJSON(response);
                if (!responseObject) {
                    return { suggestions: [] };
                }

                const filteredSuggestions = responseObject.suggestions.filter(
                    (item) => item.data !== this.config.managerUuid
                );

                return { suggestions: filteredSuggestions };
            }
        });

        searchField.select();
        searchField.focus();
    }

    openSubstituteModal() {
        $("#addSubstituteModal").modal("show");
        $("#selectedSubstituteError").hide();
        $("#selectedSubstituteUuid").val(null);
        $("#search_person").val(null);
    }

    removeSubstitute(element) {
        const assignmentId = element.dataset.assignmentid;

        this.sweetAlertService.confirm(
            this.config.removeConfirmTitle,
            this.config.removeConfirmText,
            this.config.confirmButtonText,
            this.config.cancelButtonText,
            async () => {
                try {
                    await this.networkService.Post(`${this.config.restUrl}/remove`, { id: assignmentId });
                    location.reload(true);
                } catch (error) {
                    errorHandler(this.config.genericErrorMessage)(error);
                }
            }
        );
    }

    async addSubstitute() {
        const substituteUuid = $("#selectedSubstituteUuid").val();
        const orgUnitUuids = $("#selectedOrgUnit").val();

        if (!substituteUuid) {
            $("#selectedSubstituteError").show();
            return;
        }

        if (!orgUnitUuids || orgUnitUuids.length === 0) {
            $("#selectedOrgUnitError").show();
            return;
        }

        try {
            await this.networkService.Post(`${this.config.restUrl}/add`, {
                substitute: { uuid: substituteUuid },
                orgUnitUUIDs: orgUnitUuids,
                manager: { uuid: this.config.managerUuid }
            });
            location.reload(true);
        } catch (error) {
            $("#addSubstituteModal").modal("hide");
            errorHandler(this.config.assignFailureMessage)(error);
        }
    }
}

document.addEventListener("DOMContentLoaded", () => {
    window.token = $("meta[name='_csrf']").attr("content");

    const configElement = document.getElementById("manager-substitute-config");
    const config = JSON.parse(configElement.textContent);

    window.managerSubstituteService = new ManagerSubstituteService(
        config,
        new NetworkService(window.token),
        new SweetAlertService()
    );

    window.managerSubstituteService.initSearchPersonAutocomplete();

    $(document).on("click", ".js-open-substitute-modal", () => {
        window.managerSubstituteService.openSubstituteModal();
    });

    $(document).on("click", ".js-remove-substitute", function () {
        window.managerSubstituteService.removeSubstitute(this);
    });

    $(document).on("click", ".js-add-substitute", () => {
        window.managerSubstituteService.addSubstitute();
    });

    // Prevents default click behavior on the search field (matches old onclick="return false;")
    $(document).on("click", "#search_person", (event) => {
        event.preventDefault();
    });
});
