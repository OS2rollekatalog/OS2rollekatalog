/**
 * Handles the org-unit picker modal used for postponed constraints.
 */
class OrgUnitPostponedConstraintService {
    constructor(treeOUs) {
        this.treeOUs = treeOUs;
        this.searchTimeout = false;
    }

    init() {
        $('#modal-kle-constraint').on('shown.bs.modal', () => {
            $('#kle-constraint-tree-search').focus();
        });
        $('#modal-ou').on('shown.bs.modal', () => {
            $('#ou-tree-search').focus();
        });

        this.initOUJSTree("ou-tree", "ou-tree-search", this.treeOUs);
    }

    chooseOUs(systemRoleId, constraintUuid) {
        const ous = $(`#postponed${systemRoleId}${constraintUuid}input`).val().split(",");

        $("#modal-ou-systemRoleId").val(systemRoleId);
        $("#modal-ou-constraintUuid").val(constraintUuid);

        const selected = [];
        const parseErrors = [];

        for (let i = 0; i < ous.length; i++) {
            if (!ous[i] == "") {
                if (ous[i].match(/^[0-9a-fA-F]{8}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{12}$/)) {
                    selected.push(ous[i]);
                } else {
                    parseErrors.push(ous[i]);
                }
            }
        }

        const errorList = $("#modal-ou-errors");
        errorList.empty();
        if (parseErrors.length !== 0) {
            for (let i = 0; i < parseErrors.length; i++) {
                errorList.append(`<li>${parseErrors[i]}</li>`);
            }
            $("#modal-ou-error").show();
        } else {
            $("#modal-ou-error").hide();
        }

        // #ou-tree is shared with the non-postponed OrgUnitConstraintService on
        // pages like userroles/edit.html, so its state/config may be left over
        // from that service's last use. Destroy and reinitialize before
        // selecting, matching the robust pattern used elsewhere, to guarantee
        // the tree reflects this constraint's saved value.
        const tree = $("#ou-tree");
        tree.jstree("destroy").empty();
        this.initOUJSTree("ou-tree", "ou-tree-search", this.treeOUs);

        tree.on("ready.jstree", () => {
            tree.jstree("select_node", selected);
        });

        $(".postponed-constraint-save-btn").show();
        $(".constraint-save-btn").hide();
        $('#modal-ou').modal('show');
    }

    oUModalSaveConstraints() {
        const systemRoleId = $("#modal-ou-systemRoleId").val();
        const constraintUuid = $("#modal-ou-constraintUuid").val();
        const ouInput = $(`#postponed${systemRoleId}${constraintUuid}input`);

        const selected = $("#ou-tree").jstree("get_checked");
        ouInput.val(selected.join());
        $('#modal-ou').modal('hide');
        ouInput.trigger('change');
    }

    initOUJSTree(id, search, list) {
        window.jsTreeService.initTree(`#${id}`, {
            core: {
                data: list
            },
            checkbox: {
                keep_selected_style: false,
                three_state: false,
                cascade: "undetermined"
            },
            search: {
                show_only_matches: true,
                search_callback: (str, node) => node.text.toUpperCase().includes(str.toUpperCase())
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
