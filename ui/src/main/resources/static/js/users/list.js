/**
 * Handles the users list page: DataTable setup, column filtering,
 * and the "load from CICS" admin action.
 */
class UsersListService {
    constructor(config) {
        this.baseUrl = config.baseUrl;
        this.restUrl = config.restUrl;
        this.searchTxt = config.searchTxt;
        this.dropdownTxt = config.dropdownTxt;
        this.infoDefaultTxt = config.infoDefaultTxt;
        this.infoEmptyTxt = config.infoEmptyTxt;
        this.infoFilteredTxt = config.infoFilteredTxt;
        this.cicsErrorMsg = config.cicsErrorMsg;
        this.cicsSuccessMsg = config.cicsSuccessMsg;
        this.multipleDomains = config.multipleDomains;
        this.table = null;
    }

    init() {
        this.initTableFilterInputs();
        this.table = this.initDataTable();
        this.restoreTableState();
        this.initColumnSearchListeners();
        this.applyDomainColumnVisibility();
        this.initEventListeners();
    }

    // Adds a text input to each footer cell used for per-column filtering
    initTableFilterInputs() {
        $('#listTable tfoot th[class="input-filter"]').each(function () {
            $(this).html('<input type="text" class="form-control input-sm" placeholder="Søg" />');
        });
    }

    initDataTable() {
        const table = $('#listTable').DataTable({
            destroy: true,
            stateSave: true,
            ajax: {
                contentType: 'application/json',
                url: this.restUrl + 'list',
                type: 'POST',
                headers: {
                    'X-CSRF-TOKEN': window.token
                },
                data: (d) => JSON.stringify(d)
            },
            serverSide: true,
            columns: [
                {
                    data: "name",
                    orderable: true,
                    searchable: true,
                    render: (data, type, row) => {
                        if (row.disabled === true) {
                            return `${data}<span style="margin-left:5px;" class="badge badge-warning">Deaktiveret</span>`;
                        }
                        return data;
                    }
                },
                {
                    data: "userId",
                    orderable: true,
                    searchable: true
                },
                {
                    data: "domain",
                    orderable: true,
                    searchable: true
                },
                {
                    data: "title",
                    orderable: true,
                    searchable: true,
                    render: (data) => {
                        if (data == null) {
                            return null;
                        }

                        const titles = data.split(";");
                        let result = "";
                        titles.forEach((title) => {
                            result += `${title}</br>`;
                        });
                        // Strip the trailing </br>
                        return result.substring(0, result.length - 5);
                    }
                },
                {
                    data: "allowedActions",
                    orderable: false,
                    searchable: false,
                    render: (data, type, row) => {
                        if (type !== 'display') {
                            return data;
                        }

                        const id = row.uuid;
                        let html = '';
                        html += data.readable ? `<a href="${this.baseUrl}ui/users/view/${id}"><em class="fa fa-fw fa-search"></em></a>` : '';
                        html += data.editable ? `<a href="${this.baseUrl}ui/users/manage/${id}"><em class="fa fa-fw fa-pencil"></em></a>` : '';
                        return html;
                    }
                }
            ],
            paging: true,
            ordering: true,
            info: true,
            pageLength: 25,
            language: {
                search: this.searchTxt,
                lengthMenu: this.dropdownTxt,
                info: this.infoDefaultTxt,
                zeroRecords: this.infoEmptyTxt,
                infoEmpty: "",
                infoFiltered: this.infoFilteredTxt
            },
            initComplete: () => {
                const footerRow = $('#listTable tfoot tr');
                footerRow.find('th').each(function () {
                    $(this).css('padding', 8);
                });
                $('#listTable thead').append(footerRow);
                $('#search_0').css('text-align', 'center');
            }
        });

        return table;
    }

    restoreTableState() {
        const state = this.table.state.loaded();
        if (!state) {
            return;
        }

        this.table.columns().eq(0).each((colIdx) => {
            const colSearch = state.columns[colIdx].search;
            if (colSearch.search) {
                $('input', this.table.column(colIdx).footer()).val(colSearch.search);
            }
        });

        this.table.draw();
    }

    initColumnSearchListeners() {
        $.each($('.input-filter', this.table.table().footer()), (index, footerCell) => {
            const column = this.table.column($(footerCell).index());
            $('input', footerCell).on('keyup change', function () {
                if (column.search() !== this.value) {
                    column.search(this.value).draw();
                }
            });
        });
    }

    // Hides the domain column entirely when the installation only has a single domain
    applyDomainColumnVisibility() {
        if (!this.multipleDomains) {
            this.table.column(2).visible(false);
        }
    }

    initEventListeners() {
        document.addEventListener('click', (event) => {
            const cicsButton = event.target.closest('.js-run-cics');
            if (cicsButton) {
                this.runCics();
            }
        });
    }

    runCics() {
        $.ajax({
            url: this.restUrl + "loadcics",
            method: "POST",
            headers: {
                'X-CSRF-TOKEN': window.token
            },
            error: errorHandler(this.cicsErrorMsg),
            success: () => {
                $.notify({
                    message: this.cicsSuccessMsg,
                    status: 'success',
                    timeout: 4000
                });
            }
        });
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const csrfMeta = document.querySelector("meta[name='_csrf']");
    window.token = csrfMeta ? csrfMeta.getAttribute('content') : null;

    const config = JSON.parse(document.getElementById('users-list-config').textContent);
    const usersListService = new UsersListService(config);
    usersListService.init();
});
