/**
 * Adapter for the jstree jquery plugin.
 * Migrate along with modalService.js's bsModalAdapter when the app drops jQuery.
 */
const jsTreeAdapter = {
	create(el, config) {
		$(el).jstree(config);
	},
	instance(el) {
		return $(el).jstree(true);
	},
	onReady(el, handler) {
		$(el).on('ready.jstree', handler);
	},
	onSelect(el, handler) {
		$(el).on('select_node.jstree', handler);
	},
	destroy(el) {
		$(el).jstree('destroy');
	},
};

const DEFAULT_THEME = {icons: false};

function resolveElement(root, selector) {
	const el = root.querySelector(selector);
	if (!el) {
		throw new Error(`jsTree: no element matching ${selector}`);
	}

	return el;
}

/**
 * Wires a jstree instance, opened fully and with any already-selected nodes checked as soon
 * as it finishes building.
 * @param {HTMLElement} root element containing the tree; bounds the lookup
 * @param {string} selector the tree's own element, a selector within root
 * @param {Object} config jstree's own config object (core.data, plugins, search, checkbox, ...);
 *   a default (icon-less) theme is applied unless config.core.themes already sets one
 * @param {Iterable<string>} [preselectedIds] node ids selected once the tree is ready
 * @returns {{getSelectedTopIds: function(): string[], search: function(string): void,
 *   onSelect: function(Function): void, destroy: function(): void}}
 */
export function initTree(root, selector, config, preselectedIds = []) {
	const el = resolveElement(root, selector);

	config.core = config.core || {};
	config.core.themes = config.core.themes || DEFAULT_THEME;

	jsTreeAdapter.create(el, config);

	jsTreeAdapter.onReady(el, () => {
		const instance = jsTreeAdapter.instance(el);
		instance.open_all();
		for (const id of preselectedIds) {
			instance.select_node(id);
		}
	});

	return {
		// a cascaded child of an already-selected parent is excluded, being implied
		getSelectedTopIds: () => jsTreeAdapter.instance(el)?.get_top_selected() ?? [],
		search: (term) => jsTreeAdapter.instance(el)?.search(term),
		onSelect: (handler) => jsTreeAdapter.onSelect(el, handler),
		destroy: () => jsTreeAdapter.destroy(el),
	};
}
