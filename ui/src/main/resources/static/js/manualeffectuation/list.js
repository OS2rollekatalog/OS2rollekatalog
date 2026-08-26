document.addEventListener("DOMContentLoaded", () => {
	window.token = document.querySelector("meta[name='_csrf']").getAttribute("content");

	const config = JSON.parse(document.getElementById("manualeffectuation-config").textContent);
	const datatablesConfig = JSON.parse(document.getElementById("datatables-config").textContent);
	const networkService = new NetworkService();

	// Digest emails link here with ?ids=12,13,14 - the exact tasks that email covered - so the
	// recipient isn't dumped into a table with potentially 100+ unrelated rows.
	const highlightedIds = new URLSearchParams(window.location.search).get("ids")
		?.split(",")
		.map((id) => id.trim())
		.filter((id) => id.length > 0) ?? [];
	let filterToHighlighted = highlightedIds.length > 0;

	if (filterToHighlighted) {
		$.fn.dataTable.ext.search.push((settings, data, index, rowData) => {
			if (settings.nTable.id !== "listTable" || !filterToHighlighted) {
				return true;
			}
			return highlightedIds.includes(String(rowData.id));
		});
	}

	function operationText(operation) {
		return operation === "ASSIGN" ? config.tildelTxt : config.fjernTxt;
	}

	function commentModalTitle(operation) {
		return operation === "ASSIGN" ? config.commentModalTitleAssignTxt : config.commentModalTitleRemoveTxt;
	}

	function renderOperationsIcon(data, type, row) {
		if (type !== "display") {
			return data;
		}

		return `<a href="#" class="me-comment-btn" data-id="${row.id}"><em class="fa fa-pencil"></em></a>`;
	}

	function openCommentModal(row) {
		const modal = document.getElementById("manualEffectuationCommentModal");
		const title = document.getElementById("manualEffectuationCommentModalTitle");
		const input = document.getElementById("manualEffectuationCommentInput");
		const confirmBtn = document.getElementById("manualEffectuationCommentConfirmBtn");

		if (!modal || !title || !input || !confirmBtn) {
			console.warn("manualEffectuationCommentModal elements not found - aborting modal open");
			return;
		}

		title.textContent = commentModalTitle(row.operation);
		input.value = row.comment ?? "";
		confirmBtn.dataset.id = row.id;

		$(modal).modal("show");
	}

	async function completeEffectuation(id, comment) {
		await networkService.Post(`/rest/manualeffectuation/${id}/complete`, { comment });

		const row = document.querySelector(`.me-comment-btn[data-id="${id}"]`)?.closest("tr");
		if (row) {
			window.manualEffectuationTable.row(row).remove().draw(false);
		}

		$("#manualEffectuationCommentModal").modal("hide");
		$.notify({ message: config.completeSuccessTxt }, { status: "success", autoHideDelay: 2000 });
	}

	async function loadItems() {
		const response = await networkService.Get(config.listUrl);
		const items = await response.json();

		window.manualEffectuationTable = $("#listTable").DataTable({
			destroy: true,
			data: items,
			autoWidth: false,
			paging: true,
			ordering: true,
			info: true,
			order: [[0, "asc"]],
			pageLength: 100,
			language: {
				search: datatablesConfig.searchTxt,
				lengthMenu: datatablesConfig.dropdownTxt,
				info: datatablesConfig.infoDefaultTxt,
				zeroRecords: datatablesConfig.infoEmptyTxt,
				infoEmpty: "",
				infoFiltered: datatablesConfig.infoFilteredTxt,
				paginate: {
					next: datatablesConfig.nextTxt,
					previous: datatablesConfig.prevTxt
				}
			},
			columns: [
				{ data: "userName", width: "12%" },
				{ data: "itSystemName", width: "12%" },
				{ data: "roleName", width: "17%" },
				{ data: "operation", width: "7%", render: (data, type) => (type === "display" ? operationText(data) : data) },
				{ data: null, width: "10%", className: "text-center", render: renderOperationsIcon, orderable: false }
			]
		});
	}

	document.getElementById("listTable").addEventListener("click", (event) => {
		const commentButton = event.target?.closest(".me-comment-btn");
		if (!commentButton) {
			return;
		}

		event.preventDefault();
		const row = window.manualEffectuationTable.row(commentButton.closest("tr")).data();
		openCommentModal(row);
	});

	document.getElementById("manualEffectuationCommentConfirmBtn").addEventListener("click", (event) => {
		const id = event.currentTarget.dataset.id;
		const comment = document.getElementById("manualEffectuationCommentInput").value;

		completeEffectuation(id, comment);
	});

	if (filterToHighlighted) {
		document.getElementById("filteredNotice").style.display = "";

		document.getElementById("showAllLink").addEventListener("click", (event) => {
			event.preventDefault();
			filterToHighlighted = false;
			document.getElementById("filteredNotice").style.display = "none";
			window.manualEffectuationTable.draw();
		});
	}

	loadItems();
});
