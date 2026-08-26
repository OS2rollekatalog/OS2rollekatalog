/**
 * Handles the KLE picker modal used for postponed constraints.
 */
class KleConstraintService {
    constructor(kleList) {
        this.kleList = kleList;
        this.searchTimeout = false;
    }

    init() {
        this.initJSTree("kle-constraint-tree", "kle-constraint-tree-search");

        $('#modal-kle-constraint').on('shown.bs.modal', () => {
            $('#kle-constraint-tree-search').focus();
        });
    }

    // Show modal
    chooseKles(systemRoleId, constraintUuid) {
        const kles = $(`#postponed${systemRoleId}${constraintUuid}input`).val().replace(/\.\*/g, "").split(",");

        $("#modal-kle-constraint-systemRoleId").val(systemRoleId);
        $("#modal-kle-constraint-constraintUuid").val(constraintUuid);

        const selected = [];
        const parseErrors = [];

        for (let i = 0; i < kles.length; i++) {
            // NOTE: this condition is preserved verbatim from the original code.
            // It always evaluates to true (a pre-existing quirk), so no filtering
            // of empty entries actually happens here.
            if (!kles[i] == "") {
                if (kles[i].match(/^((\d{2})|(\d{2}).(\d{2})|(\d{2}).(\d{2}).(\d{2}))$/)) {
                    selected.push(kles[i]);
                } else {
                    parseErrors.push(kles[i]);
                }
            }
        }

        const errorList = $("#modal-kle-constraint-errors");
        errorList.empty();
        if (parseErrors.length !== 0) {
            for (let i = 0; i < parseErrors.length; i++) {
                errorList.append(`<li>${parseErrors[i]}</li>`);
            }
            $("#modal-kle-constraint-error").show();
        } else {
            $("#modal-kle-constraint-error").hide();
        }

        const tree = $("#kle-constraint-tree");
        tree.jstree("deselect_all");
        tree.jstree("select_node", selected);

        $('#modal-kle-constraint').modal('show');
    }

    // Save and close modal
    kleModalSaveConstraints() {
        const systemRoleId = $("#modal-kle-constraint-systemRoleId").val();
        const constraintUuid = $("#modal-kle-constraint-constraintUuid").val();
        const kleInput = $(`#postponed${systemRoleId}${constraintUuid}input`);

        const selected = $("#kle-constraint-tree").jstree("get_top_checked");

        const constraintValue = [];
        for (let i = 0; i < selected.length; i++) {
            if (selected[i].length !== 8) {
                constraintValue.push(`${selected[i]}.*`);
            } else {
                constraintValue.push(selected[i]);
            }
        }

        kleInput.val(constraintValue.join());
        $('#modal-kle-constraint').modal('hide');
        kleInput.trigger('change');
    }

    initJSTree(id, search) {
        window.jsTreeService.initTree(`#${id}`, {
            core: {
                data: this.kleList
            },
            checkbox: {
                keep_selected_style: false,
                three_state: false,
                cascade: "undetermined"
            },
            search: {
                show_only_matches: true,
                search_callback: (str, node) => {
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
            plugins: ["wholerow", "search", "checkbox"]
        });

        $(`#${search}`).on('keyup', () => {
            if (this.searchTimeout) {
                clearTimeout(this.searchTimeout);
            }
            this.searchTimeout = setTimeout(() => {
                const searchValue = $(`#${search}`).val();
                window.jsTreeService.getInstance(`#${id}`).search(searchValue);
            }, 400);
        });
    }
}
