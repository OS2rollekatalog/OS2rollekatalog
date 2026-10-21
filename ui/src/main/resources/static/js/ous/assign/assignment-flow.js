import * as SELECT_SCOPE from './steps/select-scope.js';
import * as EXCEPTED_OUS from './steps/excepted-ous.js';
import * as EXCEPTED_USERS from './steps/excepted-users.js';
import * as TITLES from './steps/titles.js';
import * as FUNCTIONS from './steps/functions.js';
import * as MANAGER from './steps/manager.js';
import * as RECEIPT from './steps/receipt.js';

const STEP_MODULES = {SELECT_SCOPE, EXCEPTED_OUS, EXCEPTED_USERS, TITLES, FUNCTIONS, MANAGER, RECEIPT};

/** The status the server rejects a step answer with when its business rules fail. */
const UNPROCESSABLE_ENTITY = 422;

/**
 * Owns navigation between wizard steps: submitting the current step, then swapping
 * the whole modal body with the server-rendered next step.
 */
export class AssignmentFlow {
	#bodyEl;
	#navigating = false;

	/** @param {HTMLElement} bodyEl the element wizardBody's fragment is rendered into */
	constructor(bodyEl) {
		this.#bodyEl = bodyEl;
		this.#initCurrentStep();
	}

	currentStepId() {
		return this.#readCurrentStepIdFromDom();
	}

	/**
	 * @param {string} stepId
	 * @returns {{init: Function, applySelection: Function, answerPayload: Function=, destroy: Function=}}
	 *   answerPayload is implemented by SELECT_SCOPE only; destroy only by steps holding
	 *   resources that outlive their markup
	 */
	moduleFor(stepId) {
		const module = STEP_MODULES[stepId];
		if (!module) {
			throw new Error(`No step module registered for: ${stepId}`);
		}

		return module;
	}

	/**
	 * @param {import('./assignment-selection.js').AssignmentSelection} selection
	 * @returns {Promise<boolean>} false when another navigation was already in flight
	 */
	next(selection) {
		return this.#exclusively(async () => {
			const stepId = this.currentStepId();
			const container = this.#currentContentEl();
			const module = this.moduleFor(stepId);

			module.applySelection(container, selection);

			const body = stepId === 'SELECT_SCOPE' ? module.answerPayload(container)
				: this.#stepSubmission(stepId, selection);

			await this.#swapTo(this.#submitUrl(), body);

			return true;
		});
	}

	/**
	 * Returns to an earlier step, discarding the current step's unsubmitted answer.
	 * @returns {Promise<boolean>} false when another navigation was already in flight
	 */
	back(selection, backUrl) {
		return this.#exclusively(async () => {
			await this.#swapTo(backUrl, this.#stepSubmission(this.currentStepId(), selection));

			return true;
		});
	}

	/**
	 * Submits the accumulated selection as the assignment, ending the wizard.
	 * @returns {Promise<boolean|string[]>} true on success, false when another navigation
	 *   was already in flight, or the server's validation errors
	 */
	submit(selection, assignUrl) {
		return this.#exclusively(async () => {
			const container = this.#currentContentEl();

			this.moduleFor(this.currentStepId()).applySelection(container, selection);

			this.#clearErrors();

			try {
				await new NetworkService().Post(assignUrl, selection.toRequestPayload());
			} catch (error) {
				const errors = await this.#readValidationErrors(error);
				if (!errors) {
					throw error;
				}

				this.#showErrors(errors);

				return errors;
			}

			return true;
		});
	}

	/**
	 * Prevents overlapping requests destroying/initing/submitting twice
	 */
	async #exclusively(action) {
		if (this.#navigating) {
			return false;
		}

		this.#navigating = true;
		try {
			return await action();
		} finally {
			this.#navigating = false;
		}
	}

	#stepSubmission(stepId, selection) {
		return {currentStepId: stepId, scopeChoice: selection.scopeChoice, scopeFields: selection.scopeFields};
	}

	async #swapTo(url, body) {
		const container = this.#currentContentEl();
		const module = this.moduleFor(this.currentStepId());

		const response = await new NetworkService().Post(url, body);
		const markup = await response.text();

		module.destroy?.(container);
		this.#bodyEl.innerHTML = markup;
		this.#initCurrentStep();
	}

	/** @returns {Promise<?string[]>} the rejection's messages, or null for any other failure */
	async #readValidationErrors(error) {
		if (error.status !== UNPROCESSABLE_ENTITY) {
			return null;
		}

		return (await error.response.json()).errors;
	}

	#errorsEl() {
		return this.#bodyEl.querySelector('#wizardStepErrors');
	}

	/** @returns {?function(string): void} null when no step is rendered to show errors in */
	errorRenderer() {
		return this.#errorsEl() ? message => this.#showErrors([message]) : null;
	}

	#showErrors(errors) {
		const errorsEl = this.#errorsEl();
		errorsEl.replaceChildren(...errors.map(message => {
			const line = document.createElement('div');
			line.textContent = message;

			return line;
		}));
		errorsEl.hidden = false;
	}

	#clearErrors() {
		const errorsEl = this.#errorsEl();
		errorsEl.replaceChildren();
		errorsEl.hidden = true;
	}

	/** Releases the current step's resources; call when the wizard is dismissed. */
	destroy() {
		this.moduleFor(this.currentStepId()).destroy?.(this.#currentContentEl());
	}

	#initCurrentStep() {
		this.moduleFor(this.currentStepId()).init(this.#currentContentEl());
	}

	#currentContentEl() {
		return this.#bodyEl.querySelector('.assignment-wizard-content');
	}

	#readCurrentStepIdFromDom() {
		return this.#bodyEl.querySelector('.wizard-step--current').dataset.stepId;
	}

	#submitUrl() {
		return this.#bodyEl.querySelector('[data-submit-url]').dataset.submitUrl;
	}
}
