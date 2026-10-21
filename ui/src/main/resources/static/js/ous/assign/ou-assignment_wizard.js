import {close, open} from '../../service/modalService.js';
import {showError, showInfo} from '../../service/notifications.js';
import {AssignmentFlow} from './assignment-flow.js';
import {AssignmentSelection} from './assignment-selection.js';

const MODAL_SELECTOR = '#assign-modal';

const UNEXPECTED_FAILURE_MESSAGE = 'En uventet fejl opstod. Forsøg igen. Hvis problemet vedholder kan du kontakte din It-support.';

/** The two role kinds the assignment endpoints accept; mirrors the server-side RoleType enum. */
const ROLE_TYPES = Object.freeze(['userrole', 'rolegroup']);

function assertRoleType(roleType) {
	if (!ROLE_TYPES.includes(roleType)) {
		throw new Error(`Unsupported role type: ${roleType}. Expected one of ${ROLE_TYPES.join(', ')}`);
	}

	return roleType;
}

async function reportFailure(error, context, render) {
	console.error(`[Enhedstildeling] ${context} fejlede (status ${error?.status ?? 'ingen'})`, error);

	let message = UNEXPECTED_FAILURE_MESSAGE;
	try {
		message = (await error.response?.clone().json())?.friendlyMessage ?? UNEXPECTED_FAILURE_MESSAGE;
	} catch {
		// keep the generic message
	}

	(render ?? showError)(message);
}

/**
 * Initializes the next or submit button
 */
function bindProgressButton(bodyEl, flow, selection, assignUrl, onAssigned) {
	bodyEl.addEventListener('click', async (event) => {
		const button = event.target.closest('#progressButton');
		if (!button) {
			return;
		}

		// Guards against a double-click creating two assignments
		button.disabled = true;

		try {
			if (button.dataset.finalStep !== 'true') {
				await flow.next(selection);

				return;
			}

			if (await flow.submit(selection, assignUrl) !== true) {
				return;
			}

			close(MODAL_SELECTOR);
			showInfo(button.dataset.successMessage);
			await onAssigned?.();
		} catch (error) {
			await reportFailure(error, 'Trinnet', flow.errorRenderer());
		} finally {
			button.disabled = false;
		}
	});
}

/**
 * Initializes the back button and the completed steps in the step list
 */
function bindBackNavigation(bodyEl, flow, selection) {
	bodyEl.addEventListener('click', async (event) => {
		const trigger = event.target.closest('[data-back-url]');
		if (!trigger) {
			return;
		}

		try {
			await flow.back(selection, trigger.dataset.backUrl);
		} catch (error) {
			await reportFailure(error, 'Tilbage', flow.errorRenderer());
		}
	});
}

/**
 * Opens the assignment wizard for one role on one org unit
 * @param {Object} options
 * @param {string} options.ouUuid
 * @param {'userrole'|'rolegroup'} options.roleType
 * @param {string|number} options.roleId
 * @param {string|number} [options.assignmentId] id of existing assignment, signifying edit flow instead of creating flow
 * @param {Function} [options.onAssigned] called after the assignment succeeded
 */
export async function openAssignmentModal({ouUuid, roleType, roleId, assignmentId, onAssigned}) {
	const type = assertRoleType(roleType);
	let flow = null;

	const openUrl = assignmentId
		? `/ou/${ouUuid}/${type}/${roleId}/assignment/${assignmentId}`
		: `/ou/${ouUuid}/${type}/${roleId}`;

	let opened;
	try {
		opened = await open(MODAL_SELECTOR, {
			url: openUrl,
			teardown: () => flow?.destroy(),
		});
	} catch (error) {
		await reportFailure(error, 'Åbning af tildelingsdialogen');

		return;
	}

	if (!opened) {
		return;
	}

	const bodyEl = document.getElementById('assignmentWizardBody');
	flow = new AssignmentFlow(bodyEl);

	const submitUrl = assignmentId
		? `/rest/ou/${ouUuid}/${type}/${roleId}/assignment/${assignmentId}`
		: `/rest/ou/${ouUuid}/${type}/${roleId}/assign`;

	const selection = new AssignmentSelection();
	bindProgressButton(bodyEl, flow, selection, submitUrl, onAssigned);
	bindBackNavigation(bodyEl, flow, selection);
}

/**
 * Delegates clicks that opens an OU assignment modal to a stable container
 * @param {HTMLElement|Document} root
 * @param {Object} options
 * @param {string} options.selector matches the trigger element
 * @param {(el: HTMLElement) => {ouUuid: string, roleType: 'userrole'|'rolegroup', roleId: string, assignmentId: (string|undefined)}} options.resolve
 * @param {Function} [options.onAssigned]
 */
export function registerOUAssignmentDelegation(root, {selector, resolve, onAssigned}) {
	root.addEventListener('click', async (event) => {
		const trigger = event.target.closest(selector);
		if (!trigger) {
			return;
		}

		event.preventDefault();
		await openAssignmentModal({...resolve(trigger), onAssigned});
	});
}
