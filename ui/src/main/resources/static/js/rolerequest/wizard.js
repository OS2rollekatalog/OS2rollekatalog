function RequestService() {
	this.tabService = new TabService('rolerequest_wizard_roleselection')


    this.init = function() {
        $("#requestWizard").steps({
            autoFocus: true,
            headerTag: "h2",
            bodyTag: "section",
            transitionEffect: "slideLeft",
            onStepChanging: function (event, currentIndex, newIndex)
            {
                // always allow previous action
                if (currentIndex > newIndex)
                    {
                    return true;
                }

                // forbid next action if no chosenEmploymentId
                if (currentIndex === 0 && chosenEmploymentId == null)
                    {
                    return false;
                }

                // forbid next action if no chosen roles
                if (currentIndex === 1 && chosenRolesDTOs.length === 0)
                    {
                    return false;
                }

                // forbid next if no reason and reasonSetting is OBLIGATORY
                if (currentIndex === 2 && $("#reason").val() === "" && reasonSetting == "OBLIGATORY") {
                    return false
                }

                return true;
            },
            onStepChanged: function (event, currentIndex, priorIndex)
            {
                if (currentIndex === 0) {
                    $('input[name="employmentId"]').off();
                    $('input[name="employmentId"]').on('ifChecked', function (event) {
                        chosenEmploymentId = this.value;
                        chosenEmploymentChanged = true;
                        $("#confirmFor").text(this.dataset.position + " i " + this.dataset.ou)
                    });
                }

                if (priorIndex === 0 && currentIndex === 1) {
                    if (chosenEmploymentChanged) {
                        chosenRolesDTOs = [];
                        chosenUserRoleIds = [];
                        chosenRoleGroupIds = [];

                        $("#roleListPlaceholder").load(baseUrl + "/roles?user=" + userUuid + "&position=" + chosenEmploymentId, function () {
                            roleFragmentService.init();
                            chosenEmploymentChanged = false;

							//Initialize tab restoration
							requestService.tabService.restoreTab()
							const rolelistcontainer = document.querySelector("#roleListPlaceholder")
							const navLinks = rolelistcontainer.querySelectorAll('a.nav-link')
							for (const link of navLinks) {
								link.addEventListener('click', ()=> requestService.tabService.rememberTab(link.href.split('#')[1] ))
							}
                        });
                    }


                }

                if (reasonSetting == "NONE") {
                    if (currentIndex === 2) {
                        if (priorIndex === 3) {
                            $("#requestWizard").steps("previous");
                        } else {
                            $("#requestWizard").steps("next");
                        }
                    }
                }

                if (currentIndex === 3) {
                    // Ryd eksisterende rækker i tabellen, hvis nogen
                    $("#confirmRolesTable tbody").empty();

                    // Iterer over chosenRolesDTOs og tilføj rækker
                    chosenRolesDTOs.forEach(function(role) {
                        const rowHtml = `
                            <tr>
                                <td>${role.type}</td>
                                <td>${role.itSystem}</td>
                                <td>${role.name}</td>
                                <td>${role.approver}</td>
                            </tr>
                        `;
                        $("#confirmRolesTable tbody").append(rowHtml);
                    });

                    const reason = $("#reason").val();
                    $("#confirmReason").text(reason);
                }
            },
            onFinishing: function (event, currentIndex) {
                const reason = $("#reason").val();
                if (chosenEmploymentId == null || chosenRolesDTOs.length === 0 || reason === "" && reasonSetting == "OBLIGATORY") {
                    return false;
                }

                return true;
            },
            onFinished: function (event, currentIndex)
            {
                const request = {
                    userUuid: userUuid,
                    positionId: chosenEmploymentId,
                    userRoles: chosenUserRoleIds,
                    roleGroups: chosenRoleGroupIds,
                    reason: $("#reason").val(),
                    constraints: constraintService.getConstraintsAsObjects()
                };

                $.ajax( {
                    url: `${restUrl}/wizard/save`,
                    contentType: 'application/json',
                    method: 'POST',
                    headers: {
                        'X-CSRF-TOKEN': token
                    },
                    data: JSON.stringify(request),
                    success: function ( data ) {

                        window.location = "/ui/request/myrequests";
                    },
                    error: defaultErrorHandler
                } );
            },
            onInit: function (event, currentIndex)
            {
                $('.employeeRadioButton').iCheck({
                    checkboxClass: 'icheckbox_square-green',
                    radioClass: 'iradio_square-green',
                    increaseArea: '20%'
                });

                $('input[name="employmentId"]').on('ifChecked', function (event) {
                    chosenEmploymentId = this.value;
                    chosenEmploymentChanged = true;
                    $("#confirmFor").text(this.dataset.position + " i " + this.dataset.ou)
                });

                if (employments.length === 1) {
                    chosenEmploymentId = employments[0].id;
                    chosenEmploymentChanged = true;
                    $("#requestWizard").steps("next");
                }
            },
            labels: {
                cancel: "Annuller",
                current: "Nuværende step:",
                pagination: "Paginering",
                finish: "Anmod",
                next: "Næste",
                previous: "Forrige",
                loading: "Loading ..."
            }
        });
    }
}

function RoleFragmentService() {
	/**
	 * Holds subscriptions for refreshing of serverside tables. Contains objects with following properties:<br>
	 * table: the datatable instance<br>
	 * urlBuilder: function returning a URL pointing at the data endpoint for the table<br>
	 * @type {Set<{table, urlBuilder}>}
	 */
	const tablesSubscribedForRefresh = new Set()

    this.init = function() {
		this.initHideAlreadyAssignedSelector()

		this.initRecommendedRoleGroupsTable();
		this.initRecommendedUserRolesTable();

		if (!onlyRecommendRoles) {
			this.initAllUserRolesTable()
			this.initAllRolegroupsTable()
		}
    };

	this.initRecommendedRoleGroupsTable = async () => {
		const tableId = 'recommendedRoleGroupTable'
		const checkboxClass = 'recommendedRoleGroupsCheckbox'
		const table = roleGroupService.initRecommendedRoleGroups(tableId);
		roleGroupService.initRoleGroupSelection(checkboxClass)
		table.on('draw', ()=> {
			$(`.${checkboxClass}`).iCheck({
				checkboxClass: 'icheckbox_square-green',
				radioClass: 'iradio_square-green'
			});
			roleGroupService.initRoleGroupSelection(checkboxClass);

			$('.'+checkboxClass).each(function () {
				const id = this.value;
				if (!chosenRoleGroupIds.includes(id)) {
					$(this).iCheck('uncheck');
				} else {
					$(this).iCheck('check');
				}
			});

			//handle folding out of detail view
			table.on('click', 'td.dt-control', e => {
				const rowData = table.row(e.target.closest('tr')).data()
				new DataTableService().toggleRowDetailsFromServer(e, table, `${detailsUrl}/${rowData.id}/userroles`)
			} );
		})

		tablesSubscribedForRefresh.add({
			table:table,
			urlBuilder: roleGroupService.buildRecommendedTableUrl
		});
	}

	this.initRecommendedUserRolesTable = async () => {
		const tableId = 'recommendedUserRoleTable'
		const checkboxClass = 'recommendedUserRolesCheckbox'
		const table = userRoleService.initRecommendedUserRoles(tableId);
		userRoleService.initUserRoleSelection(checkboxClass)
		table.on( 'draw', function () {
			$(`.${checkboxClass}`).iCheck({
				checkboxClass: 'icheckbox_square-green',
				radioClass: 'iradio_square-green'
			});
			$('.'+checkboxClass).each(function () {
				const id = this.value;
				if (!chosenUserRoleIds.includes(id)) {
					if (this.checked) {}
				}
			})
			userRoleService.initUserRoleSelection(checkboxClass)
		})

		tablesSubscribedForRefresh.add({
			table:table,
			urlBuilder: userRoleService.buildRecommendedTableUrl
		});
	}

    this.initAllUserRolesTable = async () => {
        const tableId = 'allUserRoleTable'
        const checkboxClass = 'allUserRolesCheckbox'
        const table = userRoleService.initAllUserroles(tableId);
        userRoleService.initUserRoleSelection(checkboxClass)
        table.on( 'draw', function () {
            // $('.i-checks').iCheck({
            $(`.${checkboxClass}`).iCheck({
                checkboxClass: 'icheckbox_square-green',
                radioClass: 'iradio_square-green'
            });
            userRoleService.initUserRoleSelection(checkboxClass)

            $('.'+checkboxClass).each(function () {
                const id = this.value;
                if (!chosenUserRoleIds.includes(id)) {
                    $(this).iCheck('uncheck');
                } else {
                    $(this).iCheck('check');
                }
            });
        });

		tablesSubscribedForRefresh.add({
			table:table,
			urlBuilder: userRoleService.buildTableUrl
		});
    }

    this.initAllRolegroupsTable =  async () => {
        const tableId = 'allRoleGroupTable'
        const checkboxClass = 'allRoleGroupsCheckbox'
        const table = roleGroupService.initAllRolegroups(tableId);
        table.on('draw', ()=> {
            $(`.${checkboxClass}`).iCheck({
                checkboxClass: 'icheckbox_square-green',
                radioClass: 'iradio_square-green'
            });
            roleGroupService.initRoleGroupSelection(checkboxClass);

            $('.'+checkboxClass).each(function () {
                const id = this.value;
                if (!chosenRoleGroupIds.includes(id)) {
                    $(this).iCheck('uncheck');
                } else {
                    $(this).iCheck('check');
                }
            });

            //handle folding out of detail view
            table.on('click', 'td.dt-control', e => {
                const rowData = table.row(e.target.closest('tr')).data()
                new DataTableService().toggleRowDetailsFromServer(e, table, `${detailsUrl}/${rowData.id}/userroles`)
            } );
        })

		tablesSubscribedForRefresh.add({
			table:table,
			urlBuilder: roleGroupService.buildTableUrl
		});
    }

	this.initHideAlreadyAssignedSelector = () => {
		const hideAlreadyAssignedSelector = $('#hideAlreadyAssignedSelector')

		//init iCheck
		hideAlreadyAssignedSelector.iCheck({
			checkboxClass: 'icheckbox_square-green',
			radioClass: 'iradio_square-green'
		});

		//load state from sessionstorage
		const savedState = sessionStorage.getItem('requestwizard_hideAlreadyAssignedSelector');
		if (savedState === 'true' || savedState === null) {
			hideAlreadyAssignedSelector.iCheck('check');
		} else {
			hideAlreadyAssignedSelector.iCheck('uncheck');
		}
		hideAlreadyAssignedSelector.checked = savedState;

		//ensure state is saved and tables is reloaded on state toggle
		hideAlreadyAssignedSelector.on('ifToggled', (event) => {
			const state = event.target.checked;
			sessionStorage.setItem('requestwizard_hideAlreadyAssignedSelector', state);
			this.refreshLoadedTables()
		})
	}

	/**
	 * Refreshes serverside datatables that have subscribed to tablesSubscribedForRefresh
	 */
	this.refreshLoadedTables = () => {
		for (const subscription of tablesSubscribedForRefresh) {
			subscription.table
				.ajax.url(subscription.urlBuilder())
				.load(null, true);
		}
	}

}

function RoleGroupService() {
    this.initAllRolegroups = function(tableId) {


        const rolegroupColumnDefs = [
            {
                targets : [0],
                data : 'id',
                orderable: false,
                searchable:false,
                visible: false,
            },
            {
                targets : [1],
                data : 'id',
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
                data : 'id',
                orderable: false,
                searchable:false,
                render: (data, type, row, meta) => {
                    if (type === 'display') {
                        return `<input type="checkbox" class="i-checks allRoleGroupsCheckbox roleGroups${data}" value="${data}" data-name="${row.name}" data-approver="${row.approver}">`
                    } else {return data;}
                },
            },
            {
                targets : [3],
                data : 'name',
                render: (data, type, row, meta) => {
					if (type === 'display') {
						const alreadyAssignedHTML = row.alreadyAssigned ? ` <span class="badge badge-warning">Brugeren har allerede rollen tildelt</span>` : ''
						return data + alreadyAssignedHTML
					} else {return data;}
				}
            },
            {
                targets : [4],
                data : 'description',
                orderable: false
            },
        ]

        return datatableService.initDefaultServersideTable(`#${tableId}`, this.buildTableUrl(), rolegroupColumnDefs, [[ 3, "asc" ]])
    }

	this.initRecommendedRoleGroups = function(tableId) {
		const columns = [
			{
				targets : [0],
				data : 'id',
				orderable: false,
				searchable:false,
				visible: false,
			},
			{
				targets : [1],
				data : 'id',
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
				data : 'id',
				orderable: false,
				searchable:false,
				render: (data, type, row, meta) => {
					if (type === 'display') {
						return `<input type="checkbox" class="i-checks recommendedRoleGroupsCheckbox roleGroups${data}" value="${data}" data-name="${row.name}" data-approver="${row.approver}">`
					} else {return data;}
				},
			},
			{
				targets : [3],
				data : 'name',
				 render: (data, type, row, meta) => {
					if (type === 'display') {
						const alreadyAssignedHTML = row.alreadyAssigned ? ` <span class="badge badge-warning">Brugeren har allerede rollen tildelt</span>` : ''
						return data + alreadyAssignedHTML
					} else {return data;}
				}
			},
			{
				targets : [4],
				data : 'description',
				orderable: false
			},
		];
		return datatableService.initDefaultClientSideTable(`#${tableId}`, this.buildRecommendedTableUrl(),  [ [ 3, "asc" ] ], columns);
	}


	this.buildRecommendedTableUrl = () => {
		const base =  `${restUrl}/wizard/recommendedrolegroups/${userUuid}`
		const hideAlreadyAssignedCheckbox = document.getElementById("hideAlreadyAssignedSelector")
		const hideAlreadyAssigned = hideAlreadyAssignedCheckbox.checked
		return `${base}?hideAlreadyAssigned=${hideAlreadyAssigned}&position=${chosenEmploymentId}`
	}

	this.buildTableUrl = ()=> {
		const base =  `${restUrl}/wizard/allrolegroups/${userUuid}`
		const hideAlreadyAssignedCheckbox = document.getElementById("hideAlreadyAssignedSelector")
		const hideAlreadyAssigned = hideAlreadyAssignedCheckbox.checked
		return `${base}?hideAlreadyAssigned=${hideAlreadyAssigned}`
	}

    this.addCheckboxListeners = function(checkboxClass) {
        $('.' + checkboxClass).off();
        $('.' + checkboxClass).on('ifChanged', (event) => {
            const checkbox = event.target
            roleGroupService.check(checkbox)
        });

        $('.' + checkboxClass).on('ifUnchecked', (event) => {
            const checkbox = event.target
            roleGroupService.uncheck(checkbox)
        });

    }

    this.initRoleGroupSelection = (checkboxClass)=> {
        $('.' + checkboxClass).off();

        $('.' + checkboxClass).on('ifChecked', function (event) {
            const checkbox = event.target
            roleGroupService.check(checkbox)
        })

        $('.' + checkboxClass).on('ifUnchecked', function (event) {
            const checkbox = event.target
            roleGroupService.uncheck(checkbox)
        });
    }

    this.check = (checkbox)=> {
        const id = checkbox.value;
        const name = checkbox.dataset.name;
        const approver = checkbox.dataset.approver;
        const type = roleGroupText;

        $('.roleGroups' + id).each(function () {
            if (!this.checked) {
                $(this).iCheck('check');
            }
        });

        if (!chosenRoleGroupIds.includes(id)) {
            chosenRoleGroupIds.push(id);
        }

        const exists = chosenRolesDTOs.some(function(dto) {
            return dto.id === id && dto.type === type;
        });

        if (!exists) {
            chosenRolesDTOs.push({
                id: id,
                name: name,
                itSystem: "",
                approver: approver,
                type: type
            });
        }
    }

    this.uncheck = (checkbox)=> {
        const id = checkbox.value;
        const type = roleGroupText;

        $('.roleGroups' + id).each(function () {
            if (this.checked) {
                $(this).iCheck('uncheck');
            }
        });

        const idIndex = chosenRoleGroupIds.indexOf(id);
        if (idIndex !== -1) {
            chosenRoleGroupIds.splice(idIndex, 1);
        }

        chosenRolesDTOs = chosenRolesDTOs.filter(function(dto) {
            return !(dto.id === id && dto.type === type);
        });
    }
}

function UserRoleService() {

    this.init = function(tableId) {
        let rgTable = $(`#` + tableId).DataTable({
            "pageLength" : 25,
            "responsive" : true,
            "autoWidth" : false,
            "columnDefs" : [{ "orderable" : false, "targets" : [0,1] }],
            "order": [
                [ 2, "asc" ]
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
    }

    this.initAllUserroles = function(tableId) {
        const userroleColumnDefs = [
			{
				targets : [0],
				data : 'id',
				orderable: false,
				searchable:false,
				render: (data, type, row, meta) => {
					if (type === 'display') {
						return `<input type="checkbox" class="i-checks allUserRolesCheckbox userRoles${data}" value="${data}" data-name="${row.name}" data-approver="${row.approver}" data-itsystem="${row.itSystem.name}" data-has-constraints="${row.hasConstraints}"></input>`
					} else {return data;}
				}
			},
			{
				targets : [1],
				data : 'itSystem.name',
				render: (data, type, row, meta) => {
					if (type === 'display') {
						const alreadyAssignedHTML = row.alreadyAssigned ? ` <span class="badge badge-warning">Brugeren har allerede rollen tildelt</span>` : ''
						const hasConstraintsHTML = row.hasConstraints ? ` <span class="badge badge-warning">Kræver valg af dataafgrænsninger</span>` : ''
						return data + alreadyAssignedHTML + hasConstraintsHTML
					} else {return data;}
				}
			},
			{
				targets : [2],
				data : 'name'
			},
			{
				targets : [3],
				data : 'description',
				orderable: false
			},
		];
        return datatableService.initDefaultServersideTable(`#${tableId}`, this.buildTableUrl(), userroleColumnDefs, [ [ 1, "asc" ] ])
    }

    this.initRecommendedUserRoles = function(tableId) {
		const columns = [
				{
					targets : [0],
					data : 'id',
					orderable: false,
					searchable:false,
					render: (data, type, row, meta) => {
						if (type === 'display') {
							return `<input type="checkbox" class="i-checks recommendedUserRolesCheckbox userRoles${data}" value="${data}" data-name="${row.name}" data-approver="${row.approver}" data-itsystem="${row.itSystem.name}" data-has-constraints="${row.hasConstraints}"></input>`
						} else {return data;}
					}
				},
				{
					targets : [1],
					data : 'itSystem.name',
				},
				{
					targets : [2],
					data : 'name',
					render: (data, type, row, meta) => {
						if (type === 'display') {
							const alreadyAssignedHTML = row.alreadyAssigned ? ` <span class="badge badge-warning">Brugeren har allerede rollen tildelt</span>` : ''
							const hasConstraintsHTML = row.hasConstraints ? ` <span class="badge badge-warning">Kræver valg af dataafgrænsninger</span>` : ''
							return data + alreadyAssignedHTML + hasConstraintsHTML
						} else {return data;}
					}
				},
				{
					targets : [3],
					data : 'description',
					orderable: false
				},
			];
		return datatableService.initDefaultClientSideTable(`#${tableId}`, this.buildRecommendedTableUrl(),  [ [ 1, "asc" ] ], columns);
    }

	this.buildTableUrl = ()=> {
		const base =  `${restUrl}/wizard/alluserroles/${userUuid}`
		const hideAlreadyAssignedCheckbox = document.getElementById("hideAlreadyAssignedSelector")
		const hideAlreadyAssigned = hideAlreadyAssignedCheckbox.checked
		return `${base}?hideAlreadyAssigned=${hideAlreadyAssigned}`
	}

	this.buildRecommendedTableUrl = () => {
		const base =  `${restUrl}/wizard/recommendeduserroles/${userUuid}`
		const hideAlreadyAssignedCheckbox = document.getElementById("hideAlreadyAssignedSelector")
		const hideAlreadyAssigned = hideAlreadyAssignedCheckbox.checked
		return `${base}?hideAlreadyAssigned=${hideAlreadyAssigned}&position=${chosenEmploymentId}`
	}

    this.initUserRoleSelection = (checkboxClass)=> {
        $('.' + checkboxClass).off();

        $('.' + checkboxClass).on('ifChecked', function (event) {
            const checkbox = event.target
            const hasConstraints = checkbox.dataset.hasConstraints
            if (hasConstraints === 'true') {
                const id = checkbox.value;
                constraintService.loadModal(id, checkbox)
            } else {
                userRoleService.check(checkbox)
            }
        })

        $('.' + checkboxClass).on('ifUnchecked', function (event) {
            const checkbox = event.target
            userRoleService.uncheck(checkbox)
        });
    }

    this.check = (checkBox) =>{
        const id = checkBox.value;
        const name = checkBox.dataset.name;
        const itSystem = checkBox.dataset.itsystem;
        const approver = checkBox.dataset.approver;
        const type = userRoleText;


        $('.userRoles' + id).each(function () {
            if (!this.checked) {
                $(this).prop("checked",true);
            }
        });

        if (!chosenUserRoleIds.includes(id)) {
            chosenUserRoleIds.push(id);
        }

        const exists = chosenRolesDTOs.some(function(dto) {
            return dto.id === id && dto.type === type;
        });

        if (!exists) {
            chosenRolesDTOs.push({
                id: id,
                name: name,
                itSystem: itSystem,
                approver: approver,
                type: type
            });
        }
    }

    this.uncheck =(checkBox) =>{
        const id = checkBox.value;
        const type = userRoleText;

        $('.userRoles' + id).each(function () {
            if (this.checked) {
                $(this).iCheck('uncheck');
            }
        });

        const idIndex = chosenUserRoleIds.indexOf(id);
        if (idIndex !== -1) {
            chosenUserRoleIds.splice(idIndex, 1);
        }

        chosenRolesDTOs = chosenRolesDTOs.filter(function(dto) {
            return !(dto.id === id && dto.type === type);
        });
    }
}

class ConstraintService {
    chosenConstraints = new Map()
    modalID = 'userrole-constraint-modal'
    modalContainerID = 'userrole-constraint-modal-container'
    modalUrl = (roleId)=> `${baseUrl}/constraintfragment?roleId=${roleId}`
    modal = null
    currentCheckbox = null
    currentRoleId = null

    constructor() {
    }

    loadModal(roleId, checkboxElement){
        this.currentCheckbox = checkboxElement
        this.currentRoleId = roleId
        const classRef = this
        $(`#${this.modalContainerID}`).load(this.modalUrl(roleId), function (response, status) {
            const modal = $(`#${classRef.modalID}`)
            classRef.modal = modal.modal({
                backdrop: 'static',
                keyboard: false
            });
            if (postponedConstraintsService) {
                postponedConstraintsService.init()
            }

        });
    }

    addConstraints(userRoleId, constraintDTOMap){
        this.chosenConstraints.set(userRoleId, constraintDTOMap)
        return this.chosenConstraints.get(userRoleId)
    }

    removeConstraints(userRoleId) {
        const constraintDTOMap = this.chosenConstraints.get(userRoleId)
        this.chosenConstraints.delete(userRoleId)
        return constraintDTOMap
    }

    onModalConfirm() {
        userRoleService.check(this.currentCheckbox)
        this.modal.modal('hide')

        //Gather constraint values
        const constraintElements = document.querySelectorAll('.constraint')
        const constraintDTOs = []
        for (const constraint of constraintElements) {
            const systemRoleId = constraint.dataset.systemroleid
            const typeUuid = constraint.dataset.constrainttype

            if (constraint.tagName === 'SELECT') {
                const checkedOptions = constraint.querySelectorAll("option:checked")
                for (const option of checkedOptions) {
                    const value = option.value
                    if (value) {
                        constraintDTOs.push(new ConstraintDTO(systemRoleId, typeUuid, value))
                    }
                }

            } else if (constraint.tagName === 'INPUT'){
                const value = constraint.value
                if (value) {
                    constraintDTOs.push(new ConstraintDTO(systemRoleId, typeUuid, value))
                }
            }
        }

        this.addConstraints(this.currentRoleId, constraintDTOs)

        //reset temporary state variables
        this.currentCheckbox = null
        this.currentRoleId = null
    }

    onModalCancel(){
        userRoleService.uncheck(this.currentCheckbox)
        this.currentCheckbox = null
        this.modal.modal('hide')
    }

    getConstraintsAsObjects() {
        const roleConstraintMappingList = []
        for (const [userRoleId, constraintList] of this.chosenConstraints.entries()) {
            roleConstraintMappingList.push({
                userRoleId : userRoleId,
                roleConstraints :constraintList
            })
        }
        return roleConstraintMappingList
    }
}

class ConstraintDTO {
    systemRoleId
    typeUuid
    value

    constructor (systemRoleId, typeUuid, value) {
        this.systemRoleId = systemRoleId
        this.typeUuid = typeUuid
        this.value = value;
    }
}
