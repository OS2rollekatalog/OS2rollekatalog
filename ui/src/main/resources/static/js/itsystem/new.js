document.addEventListener("DOMContentLoaded", () => {
    $("[rel=popover]").popover();

    const configElement = document.getElementById("itsystem-new-config");
    if (!configElement) {
        return;
    }

    const config = JSON.parse(configElement.textContent);
    const select2Service = new Select2Service();

    select2Service.initPersonSearchSelect("#selectedResponsibleUuid", config.personSearchUrl);
});
