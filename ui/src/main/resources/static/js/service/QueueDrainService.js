/**
 * QueueDrainService — show a spinner overlay on a target element while a
 * simple-queue drains, then invoke a callback.
 *
 * Usage:
 *   new QueueDrainService('#users-list').watch(() => reloadList())
 *
 * Or with options:
 *   new QueueDrainService(element, {
 *     queue: 'assignment_update_queue',
 *     message: 'Opdaterer tildelinger…',
 *     messageId: userUuid
 *   }).watch(onDone)
 *
 * The constructor accepts either a CSS selector or a DOM element as the
 * overlay target. The queue name defaults to 'assignment_update_queue'.
 *
 * Pass messageId to wait only for items with that specific id (e.g. a user
 * uuid). Without it, the service waits for the whole queue to drain.
 */
class QueueDrainService {
    static OVERLAY_DELAY_MS = 300
    static SINCE_HEADER = 'X-Queue-Drain-Since'

    #target
    #queue
    #message
    #messageId
    #since = null
    #overlay = null
    #overlayTimeout = null
    #pollTimeout = null

    constructor(target, options = {}) {
        this.#target = (typeof target === 'string') ? document.querySelector(target) : target
        this.#queue = options.queue || 'assignment_update_queue'
        this.#message = options.message || ''
        this.#messageId = options.messageId || null
    }

    /**
     * Polls the drain endpoint until the queue is drained, then invokes onReady.
     * If the overlay is not already shown (e.g. via show()), it is scheduled to
     * appear after OVERLAY_DELAY_MS to avoid a visual flash for very fast drains.
     * @param {Function} [onReady] callback invoked once the queue has drained
     */
    async watch(onReady) {
        if (this.#pollTimeout !== null) {
            clearTimeout(this.#pollTimeout)
            this.#pollTimeout = null
        }
        this.#since = null
        if (!this.#overlay && this.#overlayTimeout === null) {
            this.#scheduleOverlay()
        }
        await this.#poll(() => {
            this.#hideOverlay()
            if (onReady) onReady()
        })
    }

    /**
     * Shows the overlay immediately, bypassing the overlay delay. Useful when the
     * action itself (e.g. a slow POST) should already display feedback before
     * polling starts.
     */
    show() {
        if (this.#overlayTimeout !== null) {
            clearTimeout(this.#overlayTimeout)
            this.#overlayTimeout = null
        }
        this.#showOverlay()
    }

    /**
     * Cancels polling and removes the overlay if shown.
     */
    stop() {
        if (this.#pollTimeout !== null) {
            clearTimeout(this.#pollTimeout)
            this.#pollTimeout = null
        }
        this.#hideOverlay()
    }

    #scheduleOverlay() {
        if (!this.#target) return
        this.#overlayTimeout = setTimeout(() => {
            this.#overlayTimeout = null
            this.#showOverlay()
        }, QueueDrainService.OVERLAY_DELAY_MS)
    }

    async #poll(onReady) {
        const base = `/rest/queue/${encodeURIComponent(this.#queue)}/drained`
        let url = base
        if (this.#messageId) {
            url = `${base}?messageId=${encodeURIComponent(this.#messageId)}`
        } else if (this.#since) {
            url = `${base}?since=${encodeURIComponent(this.#since)}`
        }

        let response
        try {
            response = await fetch(url, {
                headers: { 'X-CSRF-TOKEN': token }
            })
        } catch (error) {
            // Network error — retry with default backoff so a transient failure doesn't
            // leave the spinner stuck forever.
            console.error('QueueDrainService: polling failed, retrying', error)
            this.#pollTimeout = setTimeout(() => this.#poll(onReady), 1000)
            return
        }

        // Whole-queue mode: lock in the server-side cut-off from the first poll so
        // subsequent polls never include items enqueued after we started watching.
        // Per-message mode: no cut-off needed.
        if (!this.#messageId && !this.#since) {
            this.#since = response.headers.get(QueueDrainService.SINCE_HEADER)
        }

        if (response.status === 204) {
            onReady()
            return
        }

        // 202 Accepted means still processing; any other status is unexpected but
        // not recoverable here — retry after the server-advised interval.
        await response.text()
        if (response.status !== 202) {
            console.warn(`QueueDrainService: unexpected status ${response.status}, retrying`)
        }
        const retryAfterSeconds = parseInt(response.headers.get('Retry-After') || '1', 10)
        this.#pollTimeout = setTimeout(() => this.#poll(onReady), retryAfterSeconds * 1000)
    }

    #showOverlay() {
        if (!this.#target || this.#overlay) return
        if (getComputedStyle(this.#target).position === 'static') {
            this.#target.style.position = 'relative'
        }
        // Ensures the overlay has space to render even when the target has no
        // content yet (e.g. an empty tab pane waiting for its initial load).
        this.#target.classList.add('queue-drain-overlay__host')

        const spinner = document.createElement('i')
        spinner.className = 'fa fa-spinner fa-spin fa-3x queue-drain-overlay__spinner'

        const content = document.createElement('div')
        content.className = 'queue-drain-overlay__content'
        content.appendChild(spinner)

        if (this.#message) {
            const message = document.createElement('p')
            message.className = 'queue-drain-overlay__message'
            message.textContent = this.#message
            content.appendChild(message)
        }

        const overlay = document.createElement('div')
        overlay.className = 'queue-drain-overlay'
        overlay.appendChild(content)

        this.#target.appendChild(overlay)
        this.#overlay = overlay
    }

    #hideOverlay() {
        if (this.#overlayTimeout !== null) {
            clearTimeout(this.#overlayTimeout)
            this.#overlayTimeout = null
        }
        if (this.#overlay) {
            this.#overlay.remove()
            this.#overlay = null
        }
        if (this.#target) {
            this.#target.classList.remove('queue-drain-overlay__host')
        }
    }
}
