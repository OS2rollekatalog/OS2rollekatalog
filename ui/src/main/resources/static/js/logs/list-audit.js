$(document).ready(() => {
    const config = JSON.parse(document.getElementById("audit-log-config").textContent);
    const token = $("meta[name='_csrf']").attr("content");

    let auditLogTable;
    let entityFilter = null; // { entityType, entityId }

    const downloadAuditLog = () => {
        const $btn = $("#downloadBtn");
        $btn.prop("disabled", true).text("Downloader...");

        $.ajax({
            url: config.downloadUrl,
            method: "GET",
            xhrFields: {
                responseType: "blob"
            },
            success: (data, status, xhr) => {
                const contentType = xhr.getResponseHeader("Content-Type") || "application/octet-stream";
                const blob = new Blob([data], { type: contentType });
                const url = URL.createObjectURL(blob);

                const a = document.createElement("a");
                a.href = url;
                a.download = "auditlog.xlsx";
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);
            },
            error: errorHandler("Fejl ved download"),
            complete: () => {
                $btn.prop("disabled", false).text("Download Excel");
            }
        });
    };

    const buildDataTableAjaxUrl = () => {
        const selected = $("#eventTypeFilter").val();
        const params = [];

        if (selected && selected.length > 0) {
            selected.forEach(value => params.push("eventTypes=" + encodeURIComponent(value)));
        }
        if (entityFilter) {
            params.push("entityType=" + encodeURIComponent(entityFilter.entityType));
            params.push("entityId=" + encodeURIComponent(entityFilter.entityId));
        }

        return params.length > 0 ? config.ajaxUrl + "?" + params.join("&") : config.ajaxUrl;
    };

    const initAuditLogTable = () => {
        auditLogTable = $(".listTable").DataTable({
            destroy: true,
            searchDelay: 1000,
            ajax: (data, callback) => {
                $.ajax({
                    url: buildDataTableAjaxUrl(),
                    type: "POST",
                    contentType: "application/json",
                    headers: { "X-CSRF-TOKEN": token },
                    data: JSON.stringify(data),
                    success: callback
                });
            },
            serverSide: true,
            columns: [{
                data: "timestamp",
                // Timestamp is formatted client-side for readability
                render: (data) => moment(new Date(data)).format("YYYY-MM-DD HH:mm")
            }, {
                data: "username"
            }, {
                data: "eventType"
            }, {
                data: "entityName",
                // Builds the entity name cell with a clickable filter button
                render: (data, type, row) => {
                    const cell = $("<span>");
                    $("<button>", {
                        "class": "btn btn-xs btn-default entity-filter-btn",
                        "title": "Vis historik for dette objekt",
                        "data-entity-type": row.entityTypeRaw || "",
                        "data-entity-id": row.entityId || "",
                        "html": '<em class="fa fa-search"></em>'
                    }).appendTo(cell);
                    $("<span>").text(" " + (data || "") + " (" + (row.entityType || "") + ")").appendTo(cell);
                    return cell.prop("outerHTML");
                }
            }, {
                data: "secondaryEntityName"
            }, {
                data: "description",
                className: "preformat"
            }],
            order: [], // Empty array = no initial sorting
            paging: true,
            ordering: true,
            stateSave: true,
            info: true,
            pageLength: 25,
            // dom: controls the order of rendered elements ('f' = global search field)
            dom: "lfrtip",
            language: {
                search: config.searchTxt,
                lengthMenu: config.dropdownTxt,
                info: config.infoDefaultTxt,
                zeroRecords: config.infoEmptyTxt,
                infoEmpty: "",
                infoFiltered: config.infoFilteredTxt,
                paginate: {
                    next: config.nextTxt,
                    previous: config.prevTxt
                }
            }
        });

        $.each($(".input-filter", auditLogTable.table().footer()), function () {
            const column = auditLogTable.column($(this).index());

            $("input", this).on("keyup change", function () {
                if (column.search() !== this.value) {
                    column.search(this.value).draw();
                }
            });
        });

        $("#eventTypeFilter").select2({
            placeholder: "Alle",
            allowClear: true,
            width: "100%"
        }).on("change", () => {
            auditLogTable.draw();
        });
    };

    const bindEventListeners = () => {
        $("#downloadBtn").on("click", downloadAuditLog);

        // Delegated listener, since rows (and thereby the filter buttons) are rendered dynamically by DataTables
        $(document).on("click", ".entity-filter-btn", function () {
            entityFilter = {
                entityType: $(this).data("entity-type"),
                entityId: String($(this).data("entity-id"))
            };
            $("#clearEntityFilter").removeClass("hidden");
            auditLogTable.draw();
        });

        $("#clearEntityFilter").on("click", function () {
            entityFilter = null;
            $(this).addClass("hidden");
            auditLogTable.draw();
        });
    };

    initAuditLogTable();
    bindEventListeners();
});
