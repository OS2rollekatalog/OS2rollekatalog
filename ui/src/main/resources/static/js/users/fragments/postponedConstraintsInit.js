$(document).ready(() => {
    const config = JSON.parse(document.getElementById('postponed-constraints-config').textContent);

    window.postponedConstraintsService = new PostponedConstraintsService(config);
    window.kleConstraintService = new KleConstraintService(config.kleList);
    window.orgUnitPostponedConstraintService = new OrgUnitPostponedConstraintService(config.treeOUs);
    window.postponedConstraintGroupingService = new PostponedConstraintGroupingService();

    document.addEventListener('click', (event) => {
        const chooseKleButton = event.target.closest('.js-choose-kle');
        if (chooseKleButton) {
            window.kleConstraintService.chooseKles(chooseKleButton.dataset.systemroleid, chooseKleButton.dataset.constrainttype);
        }

        const chooseOusButton = event.target.closest('.js-choose-ous');
        if (chooseOusButton) {
            window.orgUnitPostponedConstraintService.chooseOUs(chooseOusButton.dataset.systemroleid, chooseOusButton.dataset.constrainttype);
        }

        const kleConstraintSaveButton = event.target.closest('.js-kle-constraint-save');
        if (kleConstraintSaveButton) {
            window.kleConstraintService.kleModalSaveConstraints();
        }
    });
});
