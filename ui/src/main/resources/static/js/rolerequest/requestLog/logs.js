class RequestLogService{
    #TABLEID = `requestLogTable`
    #table

    constructor () {
        this.#table = this.initTable()
    }

    initTable() {
        return $(`#${this.#TABLEID}`).DataTable({
            pageLength : 25,
            responsive: true,
            autoWidth : false,
            order : [0, "desc"],
            columnDefs : [
				{
					targets: [0],
					render: (data, type, row, meta) => {
						if (type === 'sort') {
							const dateAndTime = data.split(' ')
							const date = dateAndTime[0].split('-')
							return date[2]+date[1]+date[0]+dateAndTime
						} else {
						return data;}
					}
				}
            ],
            language : {
                search : "Søg",
                lengthMenu : "_MENU_ rækker per side",
                info : "Viser _START_ til _END_ af _TOTAL_ rækker",
                zeroRecords : "Ingen data...",
                infoEmpty : "",
                infoFiltered : "(ud af _MAX_ rækker)",
                paginate : {
                    "previous" : "Forrige",
                    "next" : "Næste"
                }
            }
        })
    }
}

document.addEventListener("DOMContentLoaded", () => {
    // Expose token globally, since other shared scripts/services expect a global `token`
    window.token = document.querySelector("meta[name='_csrf']").getAttribute("content");

    window.logTableService = new RequestLogService();

    $(document).on("click", ".show-request-log-details-btn", function () {
        const detailsJson = $(this).attr("data-details-json");
        const list = $("#requestLogDetailModalList");
        const fallback = $("#requestLogDetailModalFallback");

        list.empty();
        fallback.text("");

        const entries = parseDetailsJson(detailsJson);
        if (entries) {
            entries.forEach((entry) => {
                list.append(buildRequestLogDetailRow(entry.label, entry.value));
            });
        } else {
            fallback.text($(this).attr("data-details"));
        }

        $("#requestLogDetailModal").modal("show");
    });
});

function parseDetailsJson(detailsJson) {
    if (!detailsJson) {
        return null;
    }
    try {
        return JSON.parse(detailsJson);
    } catch (error) {
        return null;
    }
}

function buildRequestLogDetailRow(label, value) {
    const template = document.getElementById("requestLogDetailRowTemplate");
    const clone = document.importNode(template.content, true);
    const $row = $(clone);

    $row.find(".request-log-detail-label").text(label);
    $row.find(".request-log-detail-value").text(value);

    return $row;
}
