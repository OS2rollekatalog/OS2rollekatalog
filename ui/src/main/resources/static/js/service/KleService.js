/**
 * Handles KLE (performing/interest) code viewing and editing for an organisational unit
 */
class KleService {
    constructor(config, jsTreeService) {
        this.config = config;
        this.jsTreeService = jsTreeService;
    }

    loadViewFragment(type) {
        if (type === 'PERFORMING') {
            $("#kle_performing_menu .content").load(this.config.urlUi + this.config.ou + "/kle/PERFORMING", () => {
                $('#editBtnPerforming').show();
            });
        } else if (type === 'INTEREST') {
            $("#kle_interest_menu .content").load(this.config.urlUi + this.config.ou + "/kle/INTEREST", () => {
                $('#editBtnInterest').show();
            });
        }
    }

    loadEditFragment(type) {
        if (type === 'PERFORMING') {
            $("#kle_performing_menu .content").load(this.config.urlUi + this.config.ou + "/kleEdit/PERFORMING", () => {
                $('#editBtnPerforming').hide();
                this.initJSTree('KlePERFORMING', 'KlePERFORMINGSearch', window.kleEditConfigService.readSelectedKles('PERFORMING'));
            });
        } else if (type === 'INTEREST') {
            $("#kle_interest_menu .content").load(this.config.urlUi + this.config.ou + "/kleEdit/INTEREST", () => {
                $('#editBtnInterest').hide();
                this.initJSTree('KleINTEREST', 'KleINTERESTSearch', window.kleEditConfigService.readSelectedKles('INTEREST'));
            });
        }
    }

    initJSTree(id, search, selected) {
        this.jsTreeService.initTree(`#${id}`, {
            core: {
                data: this.config.allKles
            },
            checkbox: {
                keep_selected_style: false,
                three_state: false,
                cascade: "undetermined"
            },
            search: {
                show_only_matches: true,
                search_callback: (str, node) => {
                    // special KLE search support
                    let kleValue = str.split('.').join("");
                    if (!isNaN(kleValue)) {
                        if (kleValue.length > 4) {
                            kleValue = kleValue.substr(0, 2) + "." + kleValue.substr(2, 2) + "." + kleValue.substr(4);
                        } else if (kleValue.length > 2) {
                            kleValue = kleValue.substr(0, 2) + "." + kleValue.substr(2);
                        }

                        return node.text.startsWith(kleValue);
                    }

                    return node.text.toUpperCase().includes(str.toUpperCase());
                }
            },
            plugins: ["checkbox", "search"]
        });

        // Select all already selected klecodes in the tree.
        $(`#${id}`).on("ready.jstree", () => {
            $(`#${id}`).jstree('select_node', selected);
        });

        // Searching in the JSTree
        let searchTimeout = false;
        $(`#${search}`).keyup(() => {
            if (searchTimeout) {
                clearTimeout(searchTimeout);
            }

            searchTimeout = setTimeout(() => {
                const searchValue = $(`#${search}`).val();
                this.jsTreeService.getInstance(`#${id}`).search(searchValue);
            }, 400);
        });
    }

    saveChanges(element) {
        const id = 'Kle' + element.dataset.type;
        const codes = $(`#${id}`).jstree('get_top_selected');

        $.ajax({
            contentType: 'application/json',
            url: this.config.url + "updateAll/kle",
            method: "POST",
            headers: {
                "uuid": this.config.ou,
                "type": element.dataset.type,
                'X-CSRF-TOKEN': window.token
            },
            error: errorHandler(this.config.fieldNotUpdatedMsg),
            success: () => {
                this.loadViewFragment(element.dataset.type);
                $.notify({
                    message: this.config.fieldUpdatedMsg
                }, {
                    status: 'success',
                    autoHideDelay: 2000
                });
            },
            data: JSON.stringify(codes)
        });
    }

    abortChanges(element) {
        this.loadViewFragment(element.dataset.type);
    }
}
