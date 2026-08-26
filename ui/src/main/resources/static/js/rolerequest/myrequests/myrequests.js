/**
 * Handles the "my requests" table: pending request overview with expandable
 * detail rows, and cancellation of individual requests.
 */
class RequestService {
	constructor(config, sharedDatatableService) {
		this.config = config;
		// DataTableService comes from common.js (shared across pages) and handles the
		// dt-control / detail-row toggle via its .detailview lookup.
		this.dataTableService = new DataTableService();
		this.sharedDatatableService = sharedDatatableService;
		this.initTable();
	}

	initTable() {
		const table = $("#pendingRequestTable").DataTable({
			"pageLength": 25,
			"responsive": true,
			"autoWidth": false,
			"columnDefs": [
				{ "orderable": false, "targets": [0, 4] },
				{ width: "3rem", targets: [0] },
				{
					targets: [1],
					render: (data, type, row, meta) => {
						if (type === "sort") {
							const dataArray = data.split("-");
							return dataArray[2] + dataArray[1] + dataArray[0];
						} else {
							return data;
						}
					}
				}
			],
			order: [[1, "desc"]],
			"language": this.sharedDatatableService.defaultLanguageOptions
		});

		table.on("click", "td.dt-control", (e) => this.dataTableService.toggleRowDetailsFromClass(e, table));

		const rowTables = document.querySelectorAll(".rowTable");
		for (const rowTable of rowTables) {
			this.initRow(rowTable);
		}

		return table;
	}

	initRow(tableElement) {
		return $(tableElement).DataTable({
			"responsive": true,
			"autoWidth": false,
			"paging": false,
			"columnDefs": [
				{ "orderable": false, "targets": [-1] }
			],
			"language": this.sharedDatatableService.defaultLanguageOptions
		});
	}

	onRequestCancel(requestId) {
		window.sweetAlertService.confirm(
			"Er du sikker?",
			"Dette vil annullere anmodningen, og kan ikke fortrydes.",
			"Ja, annuller",
			"Fortryd",
			async () => {
				const url = `${this.config.restUrl}/${requestId}/cancel`;
				await fetch(url, {
					method: "DELETE",
					headers: {
						"X-CSRF-TOKEN": window.token
					}
				});

				location.reload();
			}
		);
	}
}

class RemovalRequestDTO {
	userRoleId;
	roleGroupId;
	reason;

	constructor(userRoleId, roleGroupId, reason) {
		this.userRoleId = userRoleId;
		this.roleGroupId = roleGroupId;
		this.reason = reason;
	}
}

document.addEventListener("DOMContentLoaded", () => {
	const config = JSON.parse(document.getElementById("myrequests-config").textContent);

	// Same source as before (meta tag), just no longer read via jQuery/inline script.
	window.token = document.querySelector("meta[name='_csrf']").getAttribute("content");
	window.sweetAlertService = window.sweetAlertService || new SweetAlertService();
	window.datatableService = window.datatableService || new DatatableService();

	window.requestService = new RequestService(config, window.datatableService);

	document.addEventListener("click", (event) => {
		const cancelButton = event.target.closest(".js-cancel-request");
		if (cancelButton) {
			const requestId = Number(cancelButton.dataset.requestId);
			window.requestService.onRequestCancel(requestId);
		}
	});
});
