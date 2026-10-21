/**
 * Adapter for the $.notify plugin, loaded as a classic script by the Angle theme.
 * Migrate along with modalService.js's bsModalAdapter when the app drops jQuery.
 */
export function showInfo(message) {
	$.notify({message, status: 'success', timeout: 2000});
}

export function showWarning(message) {
	$.notify({message, status: 'warning', timeout: 3000});
}

export function showError(message) {
	$.notify({message, status: 'danger', timeout: 4000});
}
