import {initTree} from '../../../fragments/jsTree.js';

const TREE_SELECTOR = '#exceptedOusStepTree';
const SEARCH_DEBOUNCE_MS = 400;

const trees = new WeakMap();
const searchTimeouts = new WeakMap();

function readConfig(container) {
	const configEl = container.querySelector('#excepted-ous-config');

	return configEl ? JSON.parse(configEl.textContent) : {nodes: [], selectedIds: []};
}

function initSearch(container) {
	const searchField = container.querySelector('#excepted-ous-search');

	searchField.addEventListener('keyup', () => {
		clearTimeout(searchTimeouts.get(container));
		searchTimeouts.set(container, setTimeout(() => {
			trees.get(container)?.search(searchField.value);
		}, SEARCH_DEBOUNCE_MS));
	});
}

export function init(container) {
	const {nodes, selectedIds} = readConfig(container);
	if (nodes.length === 0) {
		return;
	}

	trees.set(container, initTree(container, TREE_SELECTOR, {
		core: {data: nodes},
		search: {
			show_only_matches: true,
			search_callback: (str, node) => node.text.toUpperCase().includes(str.toUpperCase()),
		},
		checkbox: {keep_selected_style: false, three_state: false, cascade: 'down+undetermined'},
		plugins: ['checkbox', 'search'],
	}, selectedIds ?? []));

	initSearch(container);
}

// Excepting an org unit covers its descendants server-side, so sending the whole
// cascaded selection would be thousands of redundant uuids.
export function applySelection(container, selection) {
	selection.updateScopeFields({exceptedOuUuids: trees.get(container)?.getSelectedTopIds() ?? []});
}

export function destroy(container) {
	clearTimeout(searchTimeouts.get(container));
	searchTimeouts.delete(container);

	trees.get(container)?.destroy();
	trees.delete(container);
}
