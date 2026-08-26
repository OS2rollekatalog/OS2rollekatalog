if (typeof JsTreeService === 'undefined') {
    /**
     * Service class for the jsTree jquery plugin
     */
    class JsTreeService {
        defaultThemeConfig = {
            icons: false
        }

        constructor() {}

		/**
		 * Initializes a jsTree instance. Applies the project's default theme (no icons)
		 * unless a theme is already specified in config.core.themes.
		 * @param selector jquery selector for the tree container
		 * @param config jsTree configuration object (core.data, plugins, search, etc.)
		 */
        initTree(selector, config) {
            config.core = config.core || {};
            config.core.themes = config.core.themes || this.defaultThemeConfig;

            $(selector).jstree(config);
        }

		/**
		 * Binds a callback to jsTree's node selection event
		 * @param selector jquery selector for the tree container
		 * @param callback function(event, data)
		 */
        onSelectNode(selector, callback) {
            $(selector).on("select_node.jstree", callback);
        }

		/**
		 * Returns the jsTree API instance for the given selector, for direct calls
		 * (e.g. .search(term))
		 * @param selector jquery selector for the tree container
		 */
        getInstance(selector) {
            return $(selector).jstree(true);
        }
    }

    window.JsTreeService = JsTreeService;
}

// Shared singleton, since JsTreeService is stateless and used by many fragments and pages.
window.jsTreeService = window.jsTreeService || new window.JsTreeService();
