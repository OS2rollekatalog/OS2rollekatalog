class NetworkService {
	XCSRFToken;
	loadingMessage;
	loadingClass;
	#loadingElement;

	/**
	 * Creates a new NetworkService instance
	 * @param {string} [XCSRFToken] the X-CSRF token to use for requests. If not defined, will attempt to use any accessable variable called 'token'
	 * @param {string} [loadingMessage='Henter...'] The message shown while fragment loads
	 * @param {string} [loadingClass='loading'] the class applied to the loading div while a fragment loads
	 */
	constructor(XCSRFToken, loadingMessage = 'Henter...', loadingClass = 'loading') {
		if (!XCSRFToken) {
			this.XCSRFToken = this.getCSRFToken();
		} else {
			this.XCSRFToken = XCSRFToken;
		}

		this.loadingMessage = loadingMessage;
		this.loadingClass = loadingClass;

		this.#loadingElement = document.createElement('div');
		this.#loadingElement.classList.add(this.loadingClass);
		this.#loadingElement.textContent = this.loadingMessage;


	}

	/**
	 * Fetches a fragment from the provided url and inserts it into the provided container, meanwhile showing a loading message
	 * @param {string} url
	 * @param {HTMLElement} containerElement
	 * @returns {Promise<boolean>} the response ok status
	 */
	async GetFragment(url, containerElement) {
		if (!this.XCSRFToken && !token) {
			throw new Error(`X-CSRF token not defined. NetworkService methods requires the token variable to be defined in the document`);
		}
		if (!containerElement) {
			throw new Error('No container element defined. A container must be provided to contain the fragment');
		}

		containerElement.appendChild(this.#loadingElement);

		let response;
		try {
			response = await fetch(url, {
				headers: this.#headers()
			});
		} finally {
			this.#loadingElement.remove();
		}

		if (!response.ok) {
			throw this.#responseError(`Error when getting url: ${url}`, response);
		}

		containerElement.innerHTML = await response.text();

		return response.ok;
	}

	/**
	 * Fetch GET's from a url, returning the response
	 * @param {string} url
	 * @returns {Promise<Response>} the response
	 */
	async Get(url) {
		if (!this.XCSRFToken && !token) {
			throw new Error(`X-CSRF token not defined. NetworkService methods requires the token variable to be defined in the document`);
		}

		const response = await fetch(url, {
			headers: this.#headers()
		});

		if (!response.ok) {
			throw this.#responseError(`Error when getting url: ${url}`, response);
		}

		return response;
	}

	/**
	 * Fetch POST's data to the provided url as json, returning the response
	 * @param {string} url
	 * @param {Object} data
	 * @returns {Promise<Response>} response
	 * @throws {Error} on a non-ok response, carrying status and response
	 */
	async Post(url, data) {
		if (!this.XCSRFToken && !token) {
			throw new Error(`X-CSRF token not defined. NetworkService methods requires the token variable to be defined in the document`);
		}

		const response = await fetch(url, {
			method: 'POST',
			headers: this.#headers({"Content-Type": "application/json"}),
			body: JSON.stringify(data)
		});

		if (!response.ok) {
			throw this.#responseError(`Error when posting to url: ${url}`, response);
		}

		return response;
	}

	#responseError(message, response) {
		const error = new Error(message);
		error.status = response.status;
		error.response = response;

		return error;
	}

	// X-Requested-With makes the server answer errors with JSON instead of HTML.
	// Used for Rest controllers and pages that prefer problem/detail to the standard /error page
	#headers(extra) {
		return {
			'X-CSRF-TOKEN': this.XCSRFToken || token,
			'X-Requested-With': 'XMLHttpRequest',
			...extra,
		};
	}

	/**
	 * Fetch PUT's the data to the provided url as json, returning the  response
	 * @param {string} url
	 * @param {Object} data
	 * @returns {Promise<Response>} response
	 */
	async Put(url, data) {

		if (!this.XCSRFToken && !token) {
			throw new Error(`X-CSRF token not defined. NetworkService methods requires the token variable to be defined in the document`);
		}

		const response = await fetch(url, {
			method: 'PUT',
			headers: this.#headers({"Content-Type": "application/json"}),
			body: JSON.stringify(data)
		});

		if (!response.ok) {
			throw this.#responseError(`Error when posting to url: ${url}`, response);
		}

		return response;
	}

	/**
	 * Fetch DELETE's to the provided url, returning the response
	 * @returns {Promise<Response>} response
	 */
	async Delete(url) {
		if (!this.XCSRFToken && !token) {
			throw new Error(`X-CSRF token not defined. NetworkService methods requires the token variable to be defined in the document`);
		}

		const response = await fetch(url, {
			method: 'DELETE',
			headers: this.#headers()
		});

		if (!response.ok) {
			throw this.#responseError(`Error when getting url: ${url}`, response);
		}

		return response;
	}

	getCSRFToken() {
		const tokenElement = document.querySelector("meta[name='_csrf']");
		const token = tokenElement.getAttribute("content");
		if (!token) {
			throw new Error(`X-CSRF token not defined. NetworkService methods requires the token variable to be defined in the document`);
		}
		return token;
	}

}
