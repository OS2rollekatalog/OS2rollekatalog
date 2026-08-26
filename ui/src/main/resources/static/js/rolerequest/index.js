/**
 * Handles the request dashboard: role group / user role tables, removal
 * requests (with confirmation modal), and cancellation of pending requests.
 */
class RoleGroupService {
	constructor(config, requestService, expandableRoleGroupTableService, datatableService) {
		this.config = config;
		this.requestService = requestService;
		this.expandableRoleGroupTableService = expandableRoleGroupTableService;
		this.datatableService = datatableService;
		this.init();
	}

	init() {
		const rgTable = $("#roleGroupTable").DataTable({
			pageLength: 25,
			responsive: true,
			autoWidth: false,
			columnDefs: [{ orderable: false, targets: [1, 5] }],
			language: this.datatableService.defaultLanguageOptions
		});

		rgTable.on("click", "td.dt-control", (e) => this.expandableRoleGroupTableService.openCloseDetails(rgTable, e));
	}

	onRoleGroupRemoval(roleGroupId) {
		const removalRequestDTO = new RemovalRequestDTO(null, roleGroupId, "");
		if (this.config.reasonRequirement === "NONE") {
			this.requestService.removeRequest(removalRequestDTO);
		} else {
			this.requestService.openRequestRemovalModal(removalRequestDTO);
		}
	}
}

class UserRoleService {
	constructor(config, requestService, datatableService) {
		this.config = config;
		this.requestService = requestService;
		this.datatableService = datatableService;
		this.init();
	}

	init() {
		$("#userRoleTable").DataTable({
			pageLength: 25,
			responsive: true,
			autoWidth: false,
			columnDefs: [{ orderable: false, targets: [4] }],
			language: this.datatableService.defaultLanguageOptions
		});
	}

	onUserRoleRemoval(userRoleId) {
		const removalRequestDTO = new RemovalRequestDTO(userRoleId, null, "");
		if (this.config.reasonRequirement === "NONE") {
			this.requestService.removeRequest(removalRequestDTO);
		} else {
			this.requestService.openRequestRemovalModal(removalRequestDTO);
		}
	}
}

class RequestService {
	constructor(config) {
		this.config = config;
		this.currentRemovalRequestDTO = null;
		this.removalModalId = "request-removal-modal";
	}

	async removeRequest(removalRequestDTO) {
		const url = `${this.config.restUrl}/remove`;
		await fetch(url, {
			method: "POST",
			headers: {
				"X-CSRF-TOKEN": window.token,
				"Content-Type": "application/json"
			},
			body: JSON.stringify(removalRequestDTO)
		});

		location.reload();
	}

	openRequestRemovalModal(removalRequestDTO) {
		this.currentRemovalRequestDTO = removalRequestDTO;
		$(`#${this.removalModalId}`).modal({
			backdrop: "static",
			keyboard: false
		});
	}

	// NOTE: called from the request_removal_modal fragment (not part of this file).
	// Kept on the instance and exposed via window.requestService below so that
	// fragment keeps working until it gets its own ROL-433 pass.
	onRemovalModalConfirm() {
		const reasonTextField = document.getElementById("removal_reason");
		const reasonGiven = reasonTextField.value || "";
		if (this.config.reasonRequirement === "OBLIGATORY" && !reasonGiven) {
			const validationError = document.getElementById("reasonEmptyError");
			validationError.hidden = false;
			reasonTextField.classList.add("is-invalid");
		} else {
			this.currentRemovalRequestDTO.reason = reasonGiven;
			this.removeRequest(this.currentRemovalRequestDTO);
			this.currentRemovalRequestDTO = null;
		}
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
	const config = JSON.parse(document.getElementById("index-config").textContent);

	// Same source as before (meta tag), just no longer read via jQuery/inline script.
	window.token = document.querySelector("meta[name='_csrf']").getAttribute("content");

	// ExpandableRoleGroupTableService (common.js) reads "detailsUrl" as a bare global
	// internally - a plain const/let here would not be visible to it, so it must be
	// exposed on window explicitly, same as token.
	window.detailsUrl = config.detailsUrl;

	window.sweetAlertService = window.sweetAlertService || new SweetAlertService();
	window.datatableService = window.datatableService || new DatatableService();

	// ExpandableRoleGroupTableService (common.js) also refers to itself via the bare
	// global name "expandableRoleGroupTableService" internally (formatDetails /
	// initUserRoleTable), so the singleton must live on window under that exact name.
	window.expandableRoleGroupTableService = window.expandableRoleGroupTableService || new ExpandableRoleGroupTableService();

	const requestService = new RequestService(config);
	const roleGroupService = new RoleGroupService(config, requestService, window.expandableRoleGroupTableService, window.datatableService);
	const userRoleService = new UserRoleService(config, requestService, window.datatableService);

	// Exposed in case the (not yet refactored) removal modal fragment still
	// references these by their old global names.
	window.requestService = requestService;
	window.roleGroupService = roleGroupService;
	window.userRoleService = userRoleService;

	const tabService = new TabService("rolerequest_my_rights");
	tabService.restoreTab();

	const navLinks = document.querySelectorAll("a.nav-link");
	for (const link of navLinks) {
		link.addEventListener("click", () => tabService.rememberTab(link.href.split("#")[1]));
	}

	document.addEventListener("click", (event) => {
		const removeRoleGroupButton = event.target.closest(".js-remove-rolegroup");
		if (removeRoleGroupButton) {
			roleGroupService.onRoleGroupRemoval(Number(removeRoleGroupButton.dataset.roleId));
			return;
		}

		const removeUserRoleButton = event.target.closest(".js-remove-userrole");
		if (removeUserRoleButton) {
			userRoleService.onUserRoleRemoval(Number(removeUserRoleButton.dataset.roleId));
			return;
		}

		const cancelRequestButton = event.target.closest(".js-cancel-request");
		if (cancelRequestButton) {
			requestService.onRequestCancel(Number(cancelRequestButton.dataset.requestId));
		}
	});
});
