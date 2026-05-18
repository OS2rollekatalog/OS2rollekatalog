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
		this.initApproveButtons();
		this.initAssignButtons();
    }

	initApproveButtons() {
		$('.approveBtn').on('click', async function () {
			const requestId = $(this).data('requestid');
			const isChooseAnotherEndDate = $(this).data('chooseanotherenddate');
			const assignedTo = $(this).data('assignedname');

			const pendingRequestData = document.getElementById("pending-request-data");
			const currentUserName = pendingRequestData?.dataset.currentUserName;
			const assignWarningTitle = pendingRequestData?.dataset.assignWarningTitle;
			const assignWarningText = pendingRequestData?.dataset.assignWarningText;
			const assignWarningConfirm = pendingRequestData?.dataset.assignWarningConfirm;
			const assignWarningCancel = pendingRequestData?.dataset.assignWarningCancel;

			// Check if someone else has claimed this request
			if (assignedTo && assignedTo !== currentUserName) {
				swal({
					title: assignWarningTitle,
					text: assignWarningText,
					type: "warning",
					showCancelButton: true,
					confirmButtonColor: "#ed5565",
					confirmButtonText: assignWarningConfirm,
					cancelButtonText: assignWarningCancel,
					closeOnConfirm: true,
					closeOnCancel: true
				}, function (confirmed) {
					if (confirmed) {
						onApprovePressed(requestId, isChooseAnotherEndDate);
					}
				});
			} else {
				await onApprovePressed(requestId, isChooseAnotherEndDate);
			}
		})
	}

	initAssignButtons() {
		$('.assignBtn').on('click', function() {
			const requestId = $(this).data('requestid');
			onAssignPressed(requestId, $(this));
		})
	}

    initTable() {
        return $(`#${this.#TABLEID}`).DataTable({
            pageLength : 25,
            responsive: true,
            autoWidth : false,
            stateSave: true,
            order : [[7, "desc"]],
            columnDefs : [
            	{ "orderable" : false, "targets" : [8] },
                {width: "4rem", targets: [0]},
                {
                	targets: [7],
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

function showDateModal(headerText, labelText, cancelText, saveText) {
	document.getElementById('dateModalTitle').textContent = headerText;
	document.getElementById('dateModalLabel').textContent = labelText;
	document.getElementById('dateModalCancelBtn').textContent = cancelText;
	document.getElementById('confirmDateBtn').textContent = saveText;

	document.getElementById('modalDateInput').value = '';

	$('#dateModal').modal('show');
}

async function onApprovePressed(requestId, isChooseAnotherEndDate=false) {
	if (isChooseAnotherEndDate) {

		// Remove any existing modal first
		showDateModal(swalHeaderText, swalNewDateText, cancelText, saveText);

		$('#confirmDateBtn').on('click', function() {
			let newDate = $('#modalDateInput').val();

			if (!newDate) {
				swal("Fejl", "Du skal vælge en dato", "error");
				return;
			}

			$('#dateModal').modal('hide');

			$.ajax({
				method: "POST",
				url: `${restUrl}/${requestId}/approve`,
				headers: {
					"content-type": "application/json",
					'X-CSRF-TOKEN': token
				},
				data: JSON.stringify(newDate),
				success: function() {
					location.reload();
				}
			});
		});

		return;
	}
    const response = await fetch(`${restUrl}/${requestId}/approve`, {
        method: "POST",
        headers: {
            'X-CSRF-TOKEN': token
        },
    })

    if (!response.ok) {
        const errorText = await response.text();
        toastr.error(errorText || response.statusText);
        return;
    }

    location.reload()
}

async function onDenyPressed(requestId) {
    swal({
       html: true,
       title: "Afvis anmodning?",
       text: "Du kan angive en grund til afvisningen.",
       type: "input",
       showCancelButton: true,
       confirmButtonColor: "#ed5565",
       confirmButtonText: "Ja, afvis",
       cancelButtonText: "Fortryd",
       closeOnConfirm: true,
       closeOnCancel: true,
       inputPlaceholder: "Grund til afvisning (valgfri)"
    },
    async function (reason) {
       // User clicked confirm
       if (reason !== false) {
          const url = `${restUrl}/${requestId}/deny`;

          const response = await fetch(url, {
             method: 'POST',
             headers: {
                'X-CSRF-TOKEN': token,
                'Content-Type': 'application/json'
             },
             body: reason || null
          });

          if (!response.ok) {
             console.error('Error when attempting to deny request', response.statusText);
          }

          location.reload();
       }
    });
}

async function onAssignPressed(requestId, buttonElement) {
	const currentAssignee = buttonElement.data('assignedname');

	const pendingRequestData = document.getElementById("pending-request-data");
	const currentUserName = pendingRequestData?.dataset.currentUserName;
	const assignWarningTitle = pendingRequestData?.dataset.assignWarningTitle;
	const assignWarningTextTwo = pendingRequestData?.dataset.assignWarningTextTwo;
	const assignWarningConfirm = pendingRequestData?.dataset.assignWarningConfirm;
	const assignWarningCancel = pendingRequestData?.dataset.assignWarningCancel;

	// Check if someone else has already claimed this request
	if (currentAssignee && currentAssignee !== currentUserName) {
		swal({
			title: assignWarningTitle,
			text: assignWarningTextTwo,
			type: "warning",
			showCancelButton: true,
			confirmButtonColor: "#ed5565",
			confirmButtonText: assignWarningConfirm,
			cancelButtonText: assignWarningCancel,
			closeOnConfirm: true,
			closeOnCancel: true
		}, async function(confirmed) {
			if (confirmed) {
				await performAssignment(requestId, buttonElement);
			}
		});
	} else {
		if (currentAssignee && currentAssignee === currentUserName) {
			return;
		}
		await performAssignment(requestId, buttonElement);
	}
}

async function performAssignment(requestId, buttonElement) {
	const response = await fetch(`${restUrl}/tag/request/${requestId}`, {
		method: "POST",
		headers: {
			'X-CSRF-TOKEN': token
		},
	});

	if (!response.ok) {
		if (response.status === 403) {
			toastr.error("Du har ikke adgang til at tildele anmodningen til dig");
		} else if (response.status === 404) {
			toastr.error("Anmodningen blev ikke fundet");
		} else if (response.status === 500) {
			toastr.error("Din bruger kunne ikke findes");
		}
		return;
	}

	// Update the UI without reloading
	const currentUserName = document.getElementById("pending-request-data")?.dataset.currentUserName;
	const row = buttonElement.closest('tr');
	row.find('.treatedByResponsibleField').text(currentUserName);
	row.find('.approveBtn').data('assignedto', currentUserName);
	toastr.success("Anmodningen er nu tildelt dig");
	window.location.reload();
}
