class RequestService {

    constructor() {
        this.initTable()
    }

    initTable() {
        const table = $(`#pendingRequestTable`).DataTable({
            "pageLength" : 25,
            "responsive" : true,
            "autoWidth" : false,
            "columnDefs" : [
            	{ "orderable" : false, "targets" : [ 0, 4] },
            	{width : "3rem", targets: [0]},
            	{
            		targets: [1],
            		render: (data, type, row, meta) => {
            			if (type === 'sort') {
            				const dataArray = data.split('-')
            				return dataArray[2]+dataArray[1]+dataArray[0]
            			} else {
            			return data;}
            		}
            	}
            ],
            "language" : {
                "search" : "Søg",
                "lengthMenu" : "_MENU_ rækker per side",
                "info" : "Viser _START_ til _END_ af _TOTAL_ rækker",
                "zeroRecords" : "Ingen data...",
                "infoEmpty" : "",
                "infoFiltered" : "(ud af _MAX_ rækker)",
                "paginate" : {
                    "previous" : "Forrige",
                    "next" : "Næste"
                }
            }
        });

        table.on('click', 'td.dt-control', e => dataTableService.toggleRowDetailsFromClass(e, table));

		const rowTables = document.querySelectorAll('.rowTable')
		for(const table of rowTables) {
			this.initRow(table)
		}



        return table;
    }

    initRow (tableElement) {
            const table = $(tableElement).DataTable({
                "responsive" : true,
                "autoWidth" : false,
                "paging" : false,
                "columnDefs" : [
                	{ "orderable" : false, "targets" : [-1] }
                ],
                "language" : {
                    "search" : "Søg",
                    "lengthMenu" : "_MENU_ rækker per side",
                    "info" : "Viser _START_ til _END_ af _TOTAL_ rækker",
                    "zeroRecords" : "Ingen data...",
                    "infoEmpty" : "",
                    "infoFiltered" : "(ud af _MAX_ rækker)",
                    "paginate" : {
                        "previous" : "Forrige",
                        "next" : "Næste"
                    }
                }
            });
            return table;
    }

    async onRequestCancel (requestId) {
            swal({
                html: true,
                title : "Er du sikker?",
                text : "Dette vil annullere anmodningen, og kan ikke fortrydes.",
                type : "warning",
                showCancelButton : true,
                confirmButtonColor : "#DD6B55",
                confirmButtonText : "Ja, annuller",
                cancelButtonText : "Fortryd",
                closeOnConfirm : true,
                closeOnCancel : true
            },
            async function (confirmed) {
                if (confirmed) {
                    const url = `${restUrl}/${requestId}/cancel`
                    await fetch (url, {
                        method: 'DELETE',
                        headers: {
                            'X-CSRF-TOKEN': token,
                        }
                    })

                    location.reload()
                }
            });
        }

}

class RemovalRequestDTO {
    userRoleId
    roleGroupId
    reason

    constructor(
        userRoleId,
        roleGroupId,
        reason
    ){
        this.userRoleId = userRoleId
        this.roleGroupId = roleGroupId
        this.reason = reason
    }
}
