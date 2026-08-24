document.addEventListener('DOMContentLoaded', ()=> {
    const pendingRequestService = new PendingRequestService()
})

class PendingRequestService {
    #TABLEID = `pendingRequestTable`
    #table

    constructor () {
        this.#table = this.initTable()
		this.initApproveButtons();
		this.initAssignButtons();
    }

	// The handlers below are delegated from the table, not bound to the buttons. DataTables keeps
	// only the current page in the document, so binding directly would leave every row on every
	// other page without a handler - the buttons would silently do nothing.
	initApproveButtons() {
		$(`#${this.#TABLEID}`).on('click', '.approveBtn', async function () {
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
		$(`#${this.#TABLEID}`).on('click', '.assignBtn', function() {
			const requestId = $(this).data('requestid');
			onAssignPressed(requestId, $(this));
		})
	}

    initTable() {
        // 8 = Gyldighedsperiode and 11 = Godkendes af were already non-sortable before this change.
        // 12 = Handlinger is new here: it holds nothing but button labels that are the same on every
        // row, so sorting on it looked like a random shuffle.
        const NON_SORTABLE_COLUMNS = [8, 11, 12];
        // 7 = Anmodningsdato, sorted on the data-order attribute rendered by the server
        const DEFAULT_ORDER = [[7, "desc"]];

        return $(`#${this.#TABLEID}`).DataTable({
            pageLength : 25,
            autoWidth : false,
            stateSave: true,
            order : DEFAULT_ORDER,
            columnDefs : [
            	{ "orderable" : false, "targets" : NON_SORTABLE_COLUMNS }
            ],
            stateLoadParams: (settings, data) => {
                // a sort saved before these columns became non-sortable would otherwise survive in
                // localStorage for hours and keep the table looking randomly ordered
                // this runs during DataTable() construction, so anything thrown here aborts the whole
                // init and leaves a raw table with no controls - stay defensive about the saved shape
                if (Array.isArray(data.order)) {
                    data.order = data.order.filter(entry => Array.isArray(entry) && !NON_SORTABLE_COLUMNS.includes(entry[0]));
                }
                if (!Array.isArray(data.order) || data.order.length === 0) {
                    data.order = DEFAULT_ORDER.map(entry => [...entry]);
                }
            },
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

	// Update the UI without reloading. A reload re-runs the whole query and re-renders every row
	// just to change one cell, and it throws away the scroll position on the way.
	const currentUserName = document.getElementById("pending-request-data")?.dataset.currentUserName;
	const row = buttonElement.closest('tr');
	row.find('.treatedByResponsibleField').text(currentUserName);
	row.find('.approveBtn').data('assignedto', currentUserName);
	// the "already claimed" guards read assignedname off the buttons, so keep it in sync
	row.find('.assignBtn, .approveBtn').data('assignedname', currentUserName);
	// Let DataTables re-read the row, otherwise sorting and searching keep using the old value.
	// draw('page') repaints the current page only - draw(false) would re-sort and re-filter, which
	// moves the row out from under the cursor when the table is sorted on or filtered by the
	// responsible column. invalidate() has already dropped the cached sort and filter data, so the
	// next real sort or search still picks up the new value.
	$('#pendingRequestTable').DataTable().row(row).invalidate('dom').draw('page');
	toastr.success("Anmodningen er nu tildelt dig");
}
