/**
 * Handles the employee table and per-employee role/role-group detail rows on the request wizard's employee list page
 */
class EmployeeService {
    constructor(config, datatableService, expandableRoleGroupTableService) {
        this.config = config;
        this.datatableService = datatableService;
        this.expandableRoleGroupTableService = expandableRoleGroupTableService;
    }

    init() {
        this.initTables();
    }

    async initTables() {
        const tableId = 'employeeTable';
        const table = this.initEmployeeTable(tableId);
        table.on('click', 'td.dt-control', (e) => this.openCloseDetails(table, e));
    }

    initEmployeeTable(tableId) {
        const employeeColumnDefs = [
            {
                targets: [0],
                data: 'uuid',
                orderable: false,
                searchable: false,
                visible: false
            },
            {
                targets: [1],
                data: 'uuid',
                orderable: false,
                searchable: false,
                className: "dt-control",
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        return '';
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [2],
                data: 'name',
                render: (data, type, row) => {
                    if (type === 'display' && row.disabled === true) {
                        const wrapper = $('<span/>').text(data);
                        $('<span/>').addClass('badge badge-warning').css('margin-left', '5px').text('Deaktiveret').appendTo(wrapper);
                        return wrapper.prop('outerHTML');
                    }
                    return data;
                }
            },
            {
                targets: [3],
                data: 'userId'
            },
            {
                targets: [4],
                data: 'positions',
                orderable: false,
                searchable: true,
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        let listHtml = '<ul>';
                        data.forEach((position) => {
                            listHtml += `<li>${position}</li>`;
                        });
                        listHtml = listHtml + '</ul>';
                        return listHtml;
                    } else {
                        return data;
                    }
                }
            },
            {
                targets: [5],
                data: 'uuid',
                orderable: false,
                searchable: false,
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        const requestBtn = '<a class="btn btn-primary" style="width: 100%;" href="/ui/request/wizard?uuid=' + data + '">Anmod</a>';
                        const removeBtn = row.canRequestRemoval ? '<a class="btn btn-danger" style="margin-top: 5px; width: 100%;" href="/ui/request/remove/wizard?uuid=' + data + '">Anmod om fjernelse</a>' : '';
                        return requestBtn + removeBtn;
                    } else {
                        return data;
                    }
                }
            }
        ];
        return this.datatableService.initDefaultServersideTable(`#${tableId}`, this.config.tableUrl, employeeColumnDefs, [[2, "asc"]]);
    }

    initRolesTable(uuid) {
        $("#urForUserTable" + uuid).DataTable({
            "destroy": true,
            "pageLength": 10,
            "language": this.datatableService.defaultLanguageOptions
        });

        const rgTable = $("#rgForUserTable" + uuid).DataTable({
            "destroy": true,
            "pageLength": 10,
            "order": [[2, "asc"]],
            "columnDefs": [{ "orderable": false, "targets": [1] }],
            "language": this.datatableService.defaultLanguageOptions
        });

        rgTable.on('click', 'td.dt-control', (e) => {
            e.stopPropagation(); // stop the event from doing stuff in the outer table
            this.expandableRoleGroupTableService.openCloseDetails(rgTable, e);
        });
    }

    openCloseDetails(table, e) {
        const tr = e.target.closest('tr');
        const row = table.row(tr);
        if (row.child.isShown()) {
            row.child.hide();
        } else {
            row.child(this.formatDetails(row.data())).show();
        }
        $(tr).toggleClass('dt-hasChild');
    }

    formatDetails(rowData) {
        const div = $('<div/>')
            .addClass('loading')
            .text('Henter...');

        $.ajax({
            url: `${this.config.userDetailsUrl}/${rowData.uuid}/roles`,
            data: {
                name: rowData.name
            },
            success: (data) => {
                div.html(data).removeClass('loading');
                this.initRolesTable(rowData.uuid);
            }
        });

        return div;
    }
}
