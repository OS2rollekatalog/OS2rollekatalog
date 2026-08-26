// Handles the report assign page (report/assign.html)
class ReportAssignService {
    constructor() {
        const config = JSON.parse(document.getElementById("report-assign-config").textContent);
        this.searchTxt = config.searchTxt;
        this.dropdownTxt = config.dropdownTxt;
        this.infoDefaultTxt = config.infoDefaultTxt;
        this.infoEmptyTxt = config.infoEmptyTxt;
        this.infoFilteredTxt = config.infoFilteredTxt;
        this.fieldUpdatedMsg = config.fieldUpdatedMsg;
        this.fieldNotUpdatedMsg = config.fieldNotUpdatedMsg;
        this.backendUrl = config.backendUrl;
        this.templateId = config.templateId;

        this.allUsers = [];
        this.table = null;
    }

    init() {
        window.token = $("meta[name='_csrf']").attr("content");

        // Setup - add a text input to each footer cell
        $('#listTable tfoot th[class="input-filter"]').each((index, element) => {
            $(element).html('<input type="text" class="form-control input-sm" placeholder="Søg" />');
        });

        this.table = $('#listTable').DataTable({
            paging: true,
            ordering: true,
            info: true,
            stateSave: true,
            deferRender: true,
            pageLength: 100,
            language: {
                search: this.searchTxt,
                lengthMenu: this.dropdownTxt,
                info: this.infoDefaultTxt,
                zeroRecords: this.infoEmptyTxt,
                infoEmpty: "",
                infoFiltered: this.infoFilteredTxt
            },
            columnDefs: [{
                targets: 0,
                data: "uuid",
                render: (data, type, row) => {
                    return '<div class="checkbox c-checkbox">'
                        + '<label>'
                        + '<input id="user-checkbox" class="js-user-checkbox" type="checkbox" data-uuid="' + row.uuid + '" ' + (row.assigned ? 'checked' : '') + '/>'
                        + '<span class="fa fa-check"></span>'
                        + '</label>'
                        + '<label id="sortLabel-' + row.uuid + '" style="display:none">' + ((row.assigned) ? "1" : "0") + '</label>'
                        + '</div>';
                }
            }],
            data: this.allUsers,
            columns: [
                { data: "uuid" },
                { data: "name" },
                { data: "userId" },
                { data: "positions" }
            ],
            initComplete: () => {
                const footerRow = $('#listTable tfoot tr');
                footerRow.find('th').each(function () {
                    $(this).css('padding', 8);
                });
                $('#listTable thead').append(footerRow);
                $('#search_0').css('text-align', 'center');
            }
        });

        this.restoreState();
        this.setupColumnSearch();
        this.setupCheckboxListener();
        this.loadUsers();
    }

    restoreState() {
        const state = this.table.state.loaded();
        if (state) {
            this.table.columns().eq(0).each((colIdx) => {
                const colSearch = state.columns[colIdx].search;

                if (colSearch.search) {
                    $('input', this.table.column(colIdx).footer()).val(colSearch.search);
                }
            });

            this.table.draw();
        }
    }

    setupColumnSearch() {
        $.each($('.input-filter', this.table.table().header()), (index, element) => {
            const column = this.table.column($(element).index());

            $('input', element).on('keyup change', function () {
                if (column.search() !== this.value) {
                    column.search(this.value).draw();
                }
            });
        });
    }

    setupCheckboxListener() {
        document.addEventListener("click", (event) => {
            const checkbox = event.target.closest(".js-user-checkbox");
            if (checkbox) {
                this.toggleUserChecked(checkbox);
            }
        });
    }

    loadUsers() {
        // TODO: refactor to use datatables dao backend
        const jsonUsers = sessionStorage.getItem(this.templateId + "reportUserList");
        const jsonUsersExpire = sessionStorage.getItem(this.templateId + "reportUserListExpire");
        const cacheExpire = (jsonUsersExpire !== null && jsonUsersExpire < new Date().getTime());

        if (jsonUsers !== null && !cacheExpire) {
            setTimeout(() => {
                this.allUsers = JSON.parse(jsonUsers);
                this.updateTable();
            }, 10);
        } else {
            $.ajax({
                url: this.backendUrl + "/users/" + this.templateId,
                method: "GET",
                error: errorHandler('Could not connect to user database!'),
                success: (response) => {
                    this.allUsers = response;
                    this.updateTable();

                    setTimeout(() => {
                        const cachedUsers = JSON.stringify(response);
                        const expiryTimestamp = new Date().getTime() + (30 * 60 * 1000);
                        sessionStorage.setItem(this.templateId + "reportUserList", cachedUsers);
                        sessionStorage.setItem(this.templateId + "reportUserListExpire", expiryTimestamp);
                    }, 10);
                }
            });
        }
    }

    updateTable() {
        this.table.rows.add(this.allUsers);
        this.table.columns.adjust().draw();
    }

    toggleUserChecked(checkbox) {
        const uuid = $(checkbox).data('uuid');
        const checked = $(checkbox).prop("checked");

        $.ajax({
            url: this.backendUrl + "/toggleUser",
            method: 'POST',
            data: {
                uuid: uuid,
                templateId: this.templateId
            },
            headers: {
                'X-CSRF-TOKEN': window.token
            },
            error: errorHandler(this.fieldNotUpdatedMsg + ""),
            success: () => {
                $('#sortLabel-' + uuid).text(checked ? "1" : "0");

                this.updateSessionUserData(uuid, checked);
                $.notify({
                    message: this.fieldUpdatedMsg
                }, {
                    status: 'success',
                    autoHideDelay: 2000
                });
            }
        });
    }

    updateSessionUserData(uuid, checked) {
        this.allUsers = JSON.parse(sessionStorage.getItem(this.templateId + "reportUserList"));

        const userIndex = this.allUsers.findIndex((obj) => obj.uuid == uuid);
        this.allUsers[userIndex].assigned = checked;

        this.table.clear();
        this.table.rows.add(this.allUsers);
        this.table.draw();

        setTimeout(() => {
            const cachedUsers = JSON.stringify(this.allUsers);
            const expiryTimestamp = new Date().getTime() + (30 * 60 * 1000);
            sessionStorage.setItem(this.templateId + "reportUserList", cachedUsers);
            sessionStorage.setItem(this.templateId + "reportUserListExpire", expiryTimestamp);
        }, 10);
    }
}

document.addEventListener("DOMContentLoaded", () => {
    window.reportAssignService = new ReportAssignService();
    window.reportAssignService.init();
});
