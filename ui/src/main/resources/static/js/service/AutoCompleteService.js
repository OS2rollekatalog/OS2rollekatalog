/**
 * Handles the person-search autocomplete used for adding an authorization manager to an organisational unit
 */
class AutoCompleteService {
    constructor(config, onAssignmentChanged) {
        this.config = config;
        this.onAssignmentChanged = onAssignmentChanged;
    }

    init() {
        const searchField = $("#search_person");

        searchField.autocomplete({
            serviceUrl: this.config.substituteUrl + "/search/person",
            onSelect: (suggestion) => {
                searchField.val("");
                this.addAuthManager(suggestion.data);
            },
            preventBadQueries: true,
            triggerSelectOnValidInput: false
        });
    }

    removeAuthManager(elem) {
        const personUuid = $(elem).data('uuid');

        $.ajax({
            method: "POST",
            url: this.config.url + this.config.ou + "/authorizationmanager/remove",
            headers: {
                "content-type": "plain/text",
                'X-CSRF-TOKEN': window.token
            },
            data: personUuid
        }).done(() => {
            this.onAssignmentChanged();
        }).fail(errorHandler(this.config.msgAssignFailure));
    }

    addAuthManager(personUuid) {
        $.ajax({
            method: "POST",
            url: this.config.url + this.config.ou + "/authorizationmanager/save",
            headers: {
                "content-type": "plain/text",
                'X-CSRF-TOKEN': window.token
            },
            data: personUuid
        }).done(() => {
            this.onAssignmentChanged();
        }).fail(errorHandler(this.config.msgAssignFailure));
    }
}
