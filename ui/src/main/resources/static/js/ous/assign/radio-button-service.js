export function radiosByName(container, name) {
	return Array.from(container.querySelectorAll(`input[name="${name}"]`));
}

export function checkedValue(radios) {
	return radios.find(radio => radio.checked)?.value ?? null;
}

/**
 * Disables radios not in enabledValues, enables the rest. If the currently
 * checked radio ends up disabled, checks the first still-enabled radio
 * instead.
 */
export function applyRadioStates(radios, enabledValues) {
	radios.forEach(radio => {
		radio.disabled = !enabledValues.has(radio.value);
	});

	const checked = radios.find(radio => radio.checked);
	if (checked?.disabled) {
		const fallback = radios.find(radio => !radio.disabled);
		if (fallback) {
			fallback.checked = true;
		}
	}
}

export function updateStateClasses(radios) {
	radios.forEach(radio => {
		const label = radio.closest('label');
		label.classList.toggle('active', radio.checked);
		label.classList.toggle('disabled', radio.disabled);
	});
}

/**
 * Initializes a groupp of radio buttons
 * @param container the element containing the radio buttons
 * @param name the name of the radio buttons
 * @param onChange function taking the changed (selected) value of a radio button, returning an array of enabled values (defaults to all values)
 */
export function initRadioButtonGroup(container, name, onChange = (changedValue)=>{ return null }) {
	const targetRadios = radiosByName(container, name);

	function updateRadioButtons() {
		const value = checkedValue(targetRadios);

		const allEnabledValues = targetRadios.map(radio => radio.value) ?? [];
		const enabledValues = new Set(onChange(value) ?? allEnabledValues);

		applyRadioStates(targetRadios, enabledValues);

		updateStateClasses(targetRadios);
	}

	targetRadios.forEach(radio => radio.addEventListener('change', updateRadioButtons));

	updateRadioButtons();
}
