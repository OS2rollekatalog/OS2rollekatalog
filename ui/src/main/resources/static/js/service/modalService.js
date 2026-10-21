/**
 * Adapter for bootstrap modal. Uses Boostrap 3 but should be migrated when possible
 */
const bsModalAdapter = {
	show(modalEl) {
		$(modalEl).modal('show');
	},
	hide(modalEl) {
		$(modalEl).modal('hide');
	},
	onHidden(modalEl, handler) {
		$(modalEl).on('hidden.bs.modal', handler);
	},
};

const teardownByModal = new WeakMap();
const listenerAttached = new WeakSet();
const openInFlight = new WeakMap();

function resolveToElement(elOrSelector) {
	return typeof elOrSelector === 'string' ? document.querySelector(elOrSelector) : elOrSelector;
}

function runTeardown(modalEl) {
	const pending = teardownByModal.get(modalEl);
	if (!pending) {
		return;
	}

	teardownByModal.delete(modalEl);
	pending.teardown(pending.target);
}

/**
 * Opens a modal, optionally fetching its content first.
 * @param {string|HTMLElement} modalEl the modal element (or selector) Bootstrap shows/hides
 * @param {Object} [options]
 * @param {string} [options.url] fragment URL to fetch into the container before showing
 * @param {string|HTMLElement} [options.container] element to inject fetched content into (defaults to modalEl)
 * @param {(container: HTMLElement) => void} [options.init] called after content is in place, before showing
 * @param {(container: HTMLElement) => void} [options.teardown] called once, when the modal is hidden
 * @returns {Promise<boolean>} whether this call opened the modal
 */
export async function open(modalEl, {url, container, init, teardown} = {}) {
	modalEl = resolveToElement(modalEl);

	if (openInFlight.has(modalEl)) {
		return false;
	}
	openInFlight.set(modalEl, true);

	try {
		// container is used as target for content if provided, .modal-content is fallback
		const target = resolveToElement(container) ?? modalEl.querySelector('.modal-content') ?? modalEl;

		// a previous occupant's resources are released before its markup is replaced
		runTeardown(modalEl);

		// fetch content if url was provided
		if (url) {
			await new NetworkService().GetFragment(url, target);
		}

		// the teardown is paired with the target it was registered for
		if (teardown) {
			teardownByModal.set(modalEl, {teardown, target});
		}

		if (!listenerAttached.has(modalEl)) {
			// attach a teardown listener on modal hide event, if available
			bsModalAdapter.onHidden(modalEl, () => runTeardown(modalEl));
			listenerAttached.add(modalEl);
		}

		// initialize, if init function was provided
		if (init) {
			init(target);
		}

		// show modal
		bsModalAdapter.show(modalEl);

		return true;
	} finally {
		openInFlight.delete(modalEl);
	}
}

export function close(modalEl) {
	bsModalAdapter.hide(resolveToElement(modalEl));
}
