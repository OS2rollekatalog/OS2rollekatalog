// List page for role groups: sets up DataTable footer search boxes, state
// restoration, and the delete confirmation dialog.

document.addEventListener("DOMContentLoaded", () => {
    const config = JSON.parse(document.getElementById("rolegroups-list-config").textContent);

    initFooterSearch();
    initDeleteDialog(config);
});

/**
 * Replaces {0}, {1}, etc. placeholders in a template string with the given arguments.
 * @param {string} template
 * @param {...string} args
 * @returns {string}
 */
function formatString(template, ...args) {
    return template.replace(/(\{\d+\})/g, (match) => {
        const index = Number(match.substr(1, match.length - 2)) || 0;
        return args[index];
    });
}

function initFooterSearch() {
    // 1. Setup - add a text input to each footer cell
    $("#listTable tfoot th").each(function () {
        if ($(this).hasClass("input-filter")) {
            $(this).html('<input type="text" class="form-control input-sm" placeholder="Søg" />');
        }
    });

    const table = $("#listTable").DataTable();

    // 2. Move search boxes to top
    const footerRow = $("#listTable tfoot tr");
    footerRow.find("th").each(function () {
        $(this).css("padding", 8);
    });
    $("#listTable thead").append(footerRow);
    $("#search_0").css("text-align", "center");

    // 2.5 Restore state
    const state = table.state.loaded();
    if (state) {
        table.columns().eq(0).each((colIdx) => {
            const colSearch = state.columns[colIdx].search;

            if (colSearch.search) {
                $("input", table.column(colIdx).footer()).val(colSearch.search);
            }
        });

        table.draw();
    }

    // 3. Apply the search
    $.each($(".input-filter", table.table().header()), function () {
        const column = table.column($(this).index());

        $("input", this).on("keyup change", function () {
            if (column.search() !== this.value) {
                column.search(this.value).draw();
            }
        });
    });
}

function initDeleteDialog(config) {
    const token = $("meta[name='_csrf']").attr("content");

    document.body.addEventListener("click", (event) => {
        const trigger = event.target.closest(".openConfirmDeleteDialog");
        if (!trigger) {
            return;
        }

        event.preventDefault();

        const id = trigger.dataset.id;
        const tryDeleteUrl = config.tryDeleteUrl + id;
        const deleteUrl = config.deleteUrl + id;
        let bodyTxt = config.bodyTxt;

        $.ajax({
            url: tryDeleteUrl,
            cache: false,
            headers: {
                "X-CSRF-TOKEN": token
            },
            error: errorHandler(config.errorMsg),
            success: (result) => {
                if (result.success === false) {
                    const ous = "Enheder: " + result.ous;
                    const users = "Brugere: " + result.users;

                    bodyTxt = formatString(config.bodyTxtAdditional, ous, users);
                }

                window.sweetAlertService.confirm(
                    config.titleTxt,
                    bodyTxt,
                    config.confirmTxt,
                    config.cancelTxt,
                    () => {
                        $.ajax({
                            type: "POST",
                            url: deleteUrl,
                            headers: {
                                "X-CSRF-TOKEN": token
                            },
                            success: () => {
                                window.location.href = config.listUrl;
                            },
                            error: errorHandler(config.errorMsg)
                        });
                    }
                );
            }
        });
    });
}
