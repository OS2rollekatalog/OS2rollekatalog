/**
 * Service class for the Select2 jquery plugin
 */
if (typeof Select2Service === 'undefined') {
    /**
     * Service class for the Select2 jquery plugin
     */
    class Select2Service {
        defaultConfig = {}
        defaultServerSideConfig = {
            ajax: {
                delay: 300, //delay request for 300 to let user finish typing search
                url: '',
                data: (params) => ({
                    search: params.term,
                }),
            }
        }

        constructor() {}

		/**
		 * Initializes a default select2 instance
		 * @param selector jquery selector for dropdown(s) to be initialized
		 * @param customConfig (optional)
		 */
        initSelect(selector, customConfig) {
            $(selector).select2(customConfig ? customConfig : this.defaultConfig)
        }

		/**
		 * Initializes a serverside select2 instance, calling the specified url for data
		 * @param selector jquery selector for dropdown(s) to be initialized
		 * @param url request endpoint url. Can be a string or a function returning a string
		 * @param customConfig (optional)
		 */
        initServerSideSelect(selector, url, customConfig) {
            const config = customConfig ? customConfig : {...this.defaultServerSideConfig}
            config.ajax.url = url
            $(selector).select2(config)
        }

		/**
		 * Initializes a select2 instance backed by the project's standard
		 * person-search endpoint format (expects { suggestions: [{ data, value }] })
		 * @param selector jquery selector for dropdown(s) to be initialized
		 * @param url request endpoint url
		 * @param customConfig (optional) overrides merged on top of the defaults
		 */
        initPersonSearchSelect(selector, url, customConfig) {
            const config = {
                placeholder: "",
                allowClear: true,
                multiple: true,
                ajax: {
                    url,
                    dataType: "json",
                    delay: 250,
                    data: (params) => ({ query: params.term || "" }),
                    processResults: (data) => ({
                        results: (data.suggestions || []).map((suggestion) => ({
                            id: suggestion.data,
                            text: suggestion.value
                        }))
                    }),
                    cache: true
                },
                ...customConfig
            };
            $(selector).select2(config);
        }
    }

    window.Select2Service = Select2Service;
}

// Shared singleton, since Select2Service is stateless and used by many fragments and pages.
window.select2Service = window.select2Service || new window.Select2Service();
