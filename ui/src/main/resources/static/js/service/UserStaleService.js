class UserStaleService {
    #userUuid
    #pollTimeout = null

    /**
     * @param {string} userUuid the UUID of the user to watch
     */
    constructor(userUuid) {
        this.#userUuid = userUuid
    }

    /**
     * Polls the stale endpoint until the user is no longer stale, then invokes onReady.
     * If the user is stale (409), retries after the Retry-After interval.
     * @param {Function} onReady callback invoked when the user is no longer stale
     */
    async watch(onReady) {
        this.stop()
        await this.#poll(onReady)
    }

    /**
     * Cancels any ongoing polling
     */
    stop() {
        if (this.#pollTimeout !== null) {
            clearTimeout(this.#pollTimeout)
            this.#pollTimeout = null
        }
    }

    async #poll(onReady) {
        const response = await fetch(`/rest/users/${this.#userUuid}/stale`, {
            headers: { 'X-CSRF-TOKEN': token }
        })

        if (response.status === 409) {
            await response.text()
            const retryAfterSeconds = parseInt(response.headers.get('Retry-After') || '1', 10)
            this.#pollTimeout = setTimeout(() => this.#poll(onReady), retryAfterSeconds * 1000)
        } else if (response.status === 204) {
            onReady()
        }
    }
}