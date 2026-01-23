function RequestApproveService() {
	// These are exclusive, either of these makes it so that they cannot choose any other option
	const EXCLUSIVE_APPROVER_OPTIONS = ['INHERIT', 'AUTOMATIC'];
	const EXCLUSIVE_REQUESTER_OPTIONS = ['INHERIT', 'NONE'];


	this.handleApproverRestrictions = function () {
		const select = document.getElementById("approverSettingsSelect");

		if (!select) {
			return;
		}

		const selectedValues = Array.from(select.selectedOptions).map(option => option.value);
		const includesExclusiveValues = selectedValues.some(value => EXCLUSIVE_APPROVER_OPTIONS.includes(value));

		Array.from(select.options).forEach(option => {
			if (includesExclusiveValues) {
				// If an exclusive option is selected, disable ALL other options (including other exclusive ones)
				if (!option.selected) {
					option.disabled = true;
				}
			} else {
				// If no exclusive option is selected, disable exclusive options if others are selected
				if (EXCLUSIVE_APPROVER_OPTIONS.includes(option.value) && selectedValues.length > 0 && !option.selected) {
					option.disabled = true;
				} else {
					option.disabled = false;
				}
			}
		});
	}

	this.handleRequesterRestrictions = function () {
		const select = document.getElementById('requesterSettingsSelect');
		if (!select) {
			return;
		}

		const selectedValues = Array.from(select.selectedOptions).map(option => option.value);
		const selectedExclusiveOptions = selectedValues.filter(value => EXCLUSIVE_REQUESTER_OPTIONS.includes(value));

		// If more than one exclusive option is selected, clear all but the first one
		if (selectedExclusiveOptions.length > 1) {
			Array.from(select.options).forEach(option => {
				if (EXCLUSIVE_REQUESTER_OPTIONS.includes(option.value) && option.value !== selectedExclusiveOptions[0]) {
					option.selected = false;
				}
			});
			// Update selectedValues after clearing duplicates
			const updatedSelectedValues = Array.from(select.selectedOptions).map(option => option.value);
			const updatedSelectedExclusiveOptions = updatedSelectedValues.filter(value => EXCLUSIVE_REQUESTER_OPTIONS.includes(value));

			Array.from(select.options).forEach(option => {
				if (updatedSelectedExclusiveOptions.length > 0) {
					// If any exclusive option is selected, disable ALL other options
					if (option.value !== updatedSelectedExclusiveOptions[0]) {
						option.disabled = true;
					}
				}
			});
			return;
		}

		// Add this missing part - handle the normal case
		const includesExclusiveValues = selectedValues.some(value => EXCLUSIVE_REQUESTER_OPTIONS.includes(value));

		Array.from(select.options).forEach(option => {
			if (includesExclusiveValues) {
				// If an exclusive option is selected, disable ALL other options
				if (!option.selected) {
					option.disabled = true;
				}
			} else {
				// If no exclusive option is selected, disable exclusive options if others are selected
				if (EXCLUSIVE_REQUESTER_OPTIONS.includes(option.value) && selectedValues.length > 0 && !option.selected) {
					option.disabled = true;
				} else {
					option.disabled = false;
				}
			}
		});
	}
}
