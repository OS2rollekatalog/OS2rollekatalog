class TextareaAutosizeService {
    init(selector) {
        document.querySelectorAll(selector).forEach((textarea) => {
            this.#resize(textarea)
            textarea.addEventListener('input', () => this.#resize(textarea))
        })
    }

    #resize(textarea) {
        textarea.style.height = 'auto'
        textarea.style.height = `${textarea.scrollHeight}px`
    }
}
