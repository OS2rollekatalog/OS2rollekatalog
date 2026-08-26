/**
 * Handles the KLE (performing/interest) tabs on the user manage page:
 * viewing assigned KLE codes and editing them via a jsTree picker.
 */
class KleService {
    constructor(config) {
        this.UIUrl = config.UIUrl;
        this.restUrl = config.restUrl;
        this.user = config.user;
        this.allKles = config.allKles;
        this.fieldUpdatedMsg = config.fieldUpdatedMsg;
        this.fieldNotUpdatedMsg = config.fieldNotUpdatedMsg;
    }

    loadViewFragment(type) {
        if (type === 'PERFORMING') {
            $("#kle_performing_menu .content").load(this.UIUrl + this.user + "/kle/PERFORMING", () => {
                $('#editBtnPerforming').show();
            });
        } else if (type === 'INTEREST') {
            $("#kle_interest_menu .content").load(this.UIUrl + this.user + "/kle/INTEREST", () => {
                $('#editBtnInterest').show();
            });
        }
    }

    loadEditFragment(type) {
        if (type === 'PERFORMING') {
            $("#kle_performing_menu .content").load(this.UIUrl + this.user + "/kleEdit/PERFORMING", () => {
                $('#editBtnPerforming').hide();
                this.initJSTree('KlePERFORMING', 'KlePERFORMINGSearch', window.kleEditConfigService.readSelectedKles('PERFORMING'));
            });
        } else if (type === 'INTEREST') {
            $("#kle_interest_menu .content").load(this.UIUrl + this.user + "/kleEdit/INTEREST", () => {
                $('#editBtnInterest').hide();
                this.initJSTree('KleINTEREST', 'KleINTERESTSearch', window.kleEditConfigService.readSelectedKles('INTEREST'));
            });
        }
    }

    initJSTree(id, search, selected) {
        $(`#${id}`).jstree({
            core: {
                data: this.allKles,
                themes: {
                    icons: false
                }
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
            plugins: [
                "wholerow", "search", "checkbox"
            ]
        });

        // Select all already selected klecodes in the tree.
        $(`#${id}`).on("ready.jstree", () => {
            $(`#${id}`).jstree('select_node', selected);
        });

        // Searching in the JSTree
        let searchTimeout = false;
        $(`#${search}`).on('keyup', () => {
            if (searchTimeout) {
                clearTimeout(searchTimeout);
            }

            searchTimeout = setTimeout(() => {
                const searchValue = $(`#${search}`).val();
                $(`#${id}`).jstree(true).search(searchValue);
            }, 400);
        });
    }

    saveChanges(element) {
        const id = `Kle${element.dataset.type}`;
        const codes = $(`#${id}`).jstree('get_top_selected');

        $.ajax({
            contentType: 'application/json',
            url: this.restUrl + "updateAll/kle",
            method: "POST",
            headers: {
                uuid: this.user,
                type: element.dataset.type,
                'X-CSRF-TOKEN': window.token
            },
            error: errorHandler(this.fieldNotUpdatedMsg),
            success: () => {
                this.loadViewFragment(element.dataset.type);
                $.notify({
                    message: this.fieldUpdatedMsg
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

document.addEventListener('DOMContentLoaded', () => {
    const config = JSON.parse(document.getElementById('user-manage-kle-config').textContent);

    const kleService = new KleService(config);
    window.kleService = kleService;

    kleService.loadViewFragment('PERFORMING');
    kleService.loadViewFragment('INTEREST');

    document.addEventListener('click', (event) => {
        const kleEditButton = event.target.closest('.js-kle-edit');
        if (kleEditButton) {
            kleService.loadEditFragment(kleEditButton.dataset.kleType);
        }
    });
});
