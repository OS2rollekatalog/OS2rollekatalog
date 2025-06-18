function EmployeeService() {
	this.init = function() {
		this.initTables();
	}

	this.initTables =  async () => {
		const tableId = 'employeeTable'
		const table = this.initEmployeeTable(tableId);
		table.on('click', 'td.dt-control', e => employeeService.openCloseDetails(table, e));
	}

    this.initEmployeeTable = function(tableId) {
		const employeeColumnDefs = [
			{
				targets : [0],
				data : 'uuid',
				orderable: false,
				searchable:false,
				visible: false,
			},
			{
				targets : [1],
				data : 'uuid',
				orderable: false,
				searchable:false,
				className: "dt-control",
				render: (data, type, row, meta) => {
					if(type === 'display') {
						return ''
					} else {return data;}
				}
			},
			{
				targets : [2],
				data : 'name'
			},
			{
				targets : [3],
				data : 'userId'
			},
			{
				targets : [4],
				data : 'positions',
				orderable: false,
				searchable: true,
				render: (data, type, row, meta) => {
					if(type === 'display') {
					let listHtml = '<ul>';
                    data.forEach(position => {
						listHtml += `<li>${position}</li>`;
					});
                    listHtml = listHtml + '</ul>';
						return listHtml;
					} else {return data;}
				}
			},
			{
                targets : [5],
                data : 'uuid',
                orderable: false,
                searchable:false,
                render: (data, type, row, meta) => {
                    if(type === 'display') {
                        const requestBtn = '<a class="btn btn-primary" style="width: 100%;" href="/ui/request/wizard?uuid=' + data + '">Anmod</a>';
                        const removeBtn = row.canRequestRemoval ? '<a class="btn btn-danger" style="margin-top: 5px; width: 100%;" href="/ui/request/remove/wizard?uuid=' + data + '">Anmod om fjernelse</a>' : ''
                        return requestBtn + removeBtn;
                    } else {
                        return data;
                    }
                }
            }
		];
		return datatableService.initDefaultServersideTable(`#${tableId}`, tableUrl, employeeColumnDefs, [[ 2, "asc" ]]);
	}

    this.initRolesTable = function(uuid) {
        $("#urForUserTable" + uuid).DataTable({
            "destroy" : true,
            "pageLength" : 10,
            "language" : datatableService.defaultLanguageOptions
        });

        let rgTable = $("#rgForUserTable" + uuid).DataTable({
            "destroy" : true,
            "pageLength" : 10,
            "order": [ [ 2, "asc" ] ],
            "columnDefs" : [{ "orderable" : false, "targets" : [1] }],
            "language" : datatableService.defaultLanguageOptions
        });

		rgTable.on('click', 'td.dt-control', e => {
            e.stopPropagation();  // stop the event from doing stuff in the outer table
			expandableRoleGroupTableService.openCloseDetails(rgTable, e);
        });
    }

    this.openCloseDetails = function(table, e) {
        let tr = e.target.closest('tr');
        let row = table.row(tr);
        if (row.child.isShown()) {
            row.child.hide();
        } else {
            row.child(employeeService.formatDetails(row.data(), true)).show();
        }
        $(tr).toggleClass('dt-hasChild');
    }

    this.formatDetails = function ( rowData ) {
        let div = $('<div/>')
            .addClass( 'loading' )
            .text( 'Henter...' );
        $.ajax( {
            url: `${userDetailsUrl}/${rowData.uuid}/roles` ,
            data: {
                name: rowData.name
            },
            success: function ( data ) {
                div.html( data )
                    .removeClass( 'loading' );

                employeeService.initRolesTable(rowData.uuid);
            }
        } );

        return div;
    }
}
