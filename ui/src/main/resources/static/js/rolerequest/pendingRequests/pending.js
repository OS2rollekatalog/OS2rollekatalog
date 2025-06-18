
document.addEventListener('DOMContentLoaded', ()=> {
    const pendingRequestService = new PendingRequestService()
})

class PendingRequestService {
    #TABLEID = `pendingRequestTable`
    #CHILDROWCLASS = `childrow_contents`
    #table

    constructor () {
        this.#table = this.initTable()
        this.initRowSelection()
    }

    initTable() {
        return $(`#${this.#TABLEID}`).DataTable({
            pageLength : 25,
            responsive: true,
            autoWidth : false,
            order : [[6, "asc"]],
            columnDefs : [
            	{ "orderable" : false, "targets" : [8] },
                {width: "4rem", targets: [0]},
                {
                	targets: [-1],
					render: (data, type, row, meta) => {
						if (type === 'sort') {
							const dataArray = data.split('-')
							return dataArray[2]+dataArray[1]+dataArray[0]
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

    initRowSelection() {
        this.#table.on('click', 'td.dt-control', (e) => {
            let row = e.target.closest('tr');

            const id = row.id.split('_')[0];
            this.onRowClicked(row, id)
        });
    }

    onRowClicked(rowElement, dataId) {
        if (!rowElement || !dataId) {
            console.error("missing element or Id for clicked row", "rowelement: "+rowElement, "data id: "+dataId);
        }

        $(rowElement).toggleClass('dt-hasChild');

        let row = this.#table.row(rowElement);

        if (row.child.isShown()) {
            // This row is already open - close it
            row.child.hide();
        } else {
            // Open this row
            row.child(this.fetchRowDetails(dataId)).show();
        }

    }

    fetchRowDetails(requestId ) {
        let div = $('<div/>')
        .addClass( 'loading' )
        .text( 'Henter...' );

        $.ajax( {
            url: `${detailsUrl}/${requestId}`,
            success: function ( data ) {
                div
                .html( data )
                .removeClass( 'loading' );
            },
            error: (error)=>{
                console.error(error)
                toastr.warning("Der er sket en fejl. Se consollen for detaljer");
            }
        } );

        return div;
    }
}

async function onApprovePressed(requestId){
    const response = await fetch(`${restUrl}/${requestId}/approve`, {
        method: "POST",
        headers: {
            'X-CSRF-TOKEN': token
        },
    })

    if (!response.ok) {
        console.error('Error when attempting to approve request', response.statusText)
    }

    location.reload()
}

async function onDenyPressed(requestId) {
    const response = await fetch(`${restUrl}/${requestId}/deny`, {
        method: "POST",
        headers: {
            'X-CSRF-TOKEN': token
        },
    })

    if (!response.ok) {
        console.error('Error when attempting to approve request', response.statusText)
    }

    location.reload()
}
