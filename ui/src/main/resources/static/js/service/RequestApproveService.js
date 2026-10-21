function RequestApproveService() {
	// These are exclusive, either of these makes it so that they cannot choose any other option
	const EXCLUSIVE_APPROVER_OPTIONS = ['INHERIT', 'AUTOMATIC'];
	const EXCLUSIVE_REQUESTER_OPTIONS = ['INHERIT', 'NONE'];

	// Display-only option shown while the list is empty; never sent to the server.
	const EMPTY_OPTION = 'option[data-empty-option]';

	// Last known selection per select, so a change can tell which values the user just added.
	const previousSelection = new WeakMap();

	function selectedValues(select) {
		return Array.from(select.selectedOptions).map(option => option.value);
	}

	function isEmptyOption(option) {
		return option.matches(EMPTY_OPTION);
	}

	// The exclusive value just added wins; options stay enabled so users can switch directly.
	function enforce(select, exclusiveOptions) {
		if (!select) {
			return;
		}

		const emptyOption = select.querySelector(EMPTY_OPTION);
		const emptyValue = emptyOption?.value;
		const current = selectedValues(select).filter(value => value !== emptyValue);
		const previous = previousSelection.get(select) || [];
		const added = current.filter(value => !previous.includes(value));
		const addedExclusive = added.find(value => exclusiveOptions.includes(value));
		const withoutExclusive = current.filter(value => !exclusiveOptions.includes(value));

		let keep = addedExclusive ? [addedExclusive]
			: (withoutExclusive.length > 0 ? withoutExclusive : current);

		const removedLastValue = keep.length === 0 && previous.some(value => value !== emptyValue);
		if (emptyOption && removedLastValue) {
			keep = [emptyValue];
		}

		let changed = false;
		Array.from(select.options).forEach(option => {
			const shouldBeSelected = keep.includes(option.value);
			if (option.selected !== shouldBeSelected) {
				option.selected = shouldBeSelected;
				changed = true;
			}
		});

		previousSelection.set(select, keep);

		// Namespaced so select2 redraws without re-entering the app's own change handlers.
		if (changed && typeof $ !== 'undefined') {
			$(select).trigger('change.select2');
		}
	}

	this.handleApproverRestrictions = function (select) {
		enforce(select, EXCLUSIVE_APPROVER_OPTIONS);
	}

	this.handleRequesterRestrictions = function (select) {
		enforce(select, EXCLUSIVE_REQUESTER_OPTIONS);
	}

	this.selectedPermissions = function (select) {
		return Array.from(select.selectedOptions)
			.filter(option => !isEmptyOption(option))
			.map(option => option.value);
	}

	// select2 templateResult: keeps the empty option out of the dropdown.
	this.templateResult = function (item) {
		return item.element && isEmptyOption(item.element) ? null : item.text;
	}

	// Seeds the stored selection on page load so the first change can tell what the user added.
	this.rememberSelection = function (selects) {
		selects.forEach(select => previousSelection.set(select, selectedValues(select)));
	}
}
