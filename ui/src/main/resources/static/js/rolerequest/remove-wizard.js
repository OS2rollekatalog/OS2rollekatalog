function RequestService() {
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

                // forbid next action if no chosen roles
                if (currentIndex === 0 && chosenRolesDTOs.length === 0)
                    {
                    return false;
                }

                // forbid next if no reason and reasonSetting is OBLIGATORY
                if (currentIndex === 1 && $("#reason").val() === "" && reasonSetting == "OBLIGATORY") {
                    return false
                }

                return true;
            },
            onStepChanged: function (event, currentIndex, priorIndex)
            {
                if (reasonSetting == "NONE") {
                    if (currentIndex === 1) {
                        if (priorIndex === 2) {
                            $("#requestWizard").steps("previous");
                        } else {
                            $("#requestWizard").steps("next");
                        }
                    }
                }

                if (currentIndex === 2) {
                    // remove existing rows if any
                    $("#confirmRolesTable tbody").empty();

                    // for chosenRolesDTOs add row
                    chosenRolesDTOs.forEach(function(role) {
                        const rowHtml = `
                            <tr>
                                <td>${role.type}</td>
                                <td>${role.itSystem}</td>
                                <td>${role.name}</td>
                            </tr>
                        `;
                        $("#confirmRolesTable tbody").append(rowHtml);
                    });

                    const reason = $("#reason").val();
                    $("#confirmReason").text(reason);
                }
            },
            onInit: function (event, currentIndex)
			{
				roleService.init();
			},
            onFinishing: function (event, currentIndex) {
                const reason = $("#reason").val();
                if (chosenRolesDTOs.length === 0 || reason === "" && reasonSetting == "OBLIGATORY") {
                    return false;
                }

                return true;
            },
            onFinished: function (event, currentIndex)
            {
                const request = {
                    userUuid: userUuid,
                    userRoles: chosenUserRoleIds,
                    roleGroups: chosenRoleGroupIds,
                    reason: $("#reason").val()
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

function RoleService() {
    this.init = function() {
		if (!isCombinedEnabled) {
			this.initRoleGroupsTable();
			this.initUserRolesTable();
		}
		else {
			this.initCombinedTable();
		}
	};

    this.initCheckboxes = function(checkboxClass) {
    	$('.' + checkboxClass).iCheck({
			checkboxClass: 'icheckbox_square-green',
			radioClass: 'iradio_square-green',
		});
    }

	this.initRoleGroupsTable = async () => {
		const tableId = 'roleGroupTable'
		const checkboxClass = 'roleGroupsCheckbox'
		this.initCheckboxes(checkboxClass);
		const table = roleGroupService.initRoleGroups(tableId);
		roleGroupService.initRoleGroupSelection(checkboxClass);
		table.on('draw', ()=> {
			roleGroupService.initRoleGroupSelection(checkboxClass);

			$('.'+checkboxClass).each(function () {
				const id = this.value;
				if (!chosenRoleGroupIds.includes(id)) {
					$(this).iCheck('uncheck');
				} else {
					$(this).iCheck('check');
				}
			});
		})
		table.on('click', 'td.dt-control', e => roleGroupService.openCloseDetails(table, e));
	}

	this.initUserRolesTable = async () => {
		const tableId = 'userRoleTable'
		const checkboxClass = 'userRolesCheckbox'
		this.initCheckboxes(checkboxClass);
		const table = userRoleService.initUserRoles(tableId);
		userRoleService.initUserRoleSelection(checkboxClass)
		table.on( 'draw', function () {
			$('.'+checkboxClass).each(function () {
				const id = this.value;
				if (!chosenUserRoleIds.includes(id)) {
					if (this.checked) {}
				}
			})
			userRoleService.initUserRoleSelection(checkboxClass)
		})
	}

	this.initCombinedTable = async () => {
		const tableId = 'combinedRolesTable';
		const checkboxClass = 'combinedCheckbox';

		combinedService.initCheckboxes(checkboxClass);

		combinedService.initCombinedTable(tableId, checkboxClass);
		combinedService.initCombinedSelection(checkboxClass);
	}
}

function RoleGroupService() {
	this.initRoleGroups = function(tableId) {
		let rgTable = $(`#` + tableId).DataTable({
			"pageLength" : 25,
			"responsive" : true,
			"autoWidth" : false,
			"columnDefs" : [{ "orderable" : false, "targets" : [0,1] }],
			"order": [
				[ 2, "desc" ]
			],
			"language" : datatableService.defaultLanguageOptions
		});
		return rgTable;
	}

	this.openCloseDetails = function(table, e) {
		let tr = e.target.closest('tr');
		let row = table.row(tr);
		if (row.child.isShown()) {
			row.child.hide();
		} else {
			row.child(roleGroupService.formatDetails(row.data(), true)).show();
		}
		$(tr).toggleClass('dt-hasChild');
	}

	this.formatDetails = function ( rowData ) {
		const id = rowData[0];
		const url = `${detailsUrl}/${id}/userroles`;

		let div = $('<div/>')
			.addClass( 'loading' )
			.text( 'Henter...' );
		$.ajax( {
			url: url,
			success: function ( data ) {
				div.html( data )
					.removeClass( 'loading' );

				expandableRoleGroupTableService.initUserRoleTable();
			}
		} );

		return div;
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
                type: type
            });
        }
    }

    this.uncheck = (checkbox)=> {
        const id = checkbox.value;
        const type = roleGroupText;

        const idIndex = chosenRoleGroupIds.indexOf(id);
        if (idIndex !== -1) {
            chosenRoleGroupIds.splice(idIndex, 1);
        }

        chosenRolesDTOs = chosenRolesDTOs.filter(function(dto) {
            return !(dto.id === id && dto.type === type);
        });
    }
}

function CombinedRoleService() {
	this.initCombinedSelection = function(checkboxClass) {
		$('.' + checkboxClass).off('ifChecked ifUnchecked');

		$('.' + checkboxClass).on('ifChecked', function(event) {
			const checkbox = event.target;
			const type = $(checkbox).data('type');
			const hasConstraints = $(checkbox).data('has-constraints');

			if (type === 'userRole' && hasConstraints === 'true') {
				const id = checkbox.value;
				constraintService.loadModal(id, checkbox);
			} else if (type === 'userRole') {
				userRoleService.check(checkbox);
			} else if (type === 'roleGroup') {
				roleGroupService.check(checkbox);
			}
		});

		$('.' + checkboxClass).on('ifUnchecked', function(event) {
			const checkbox = event.target;
			const type = $(checkbox).data('type');

			if (type === 'userRole') {
				userRoleService.uncheck(checkbox);
			} else if (type === 'roleGroup') {
				roleGroupService.uncheck(checkbox);
			}
		});
	}

	this.initCombinedTable = function (tableId, checkboxClass) {
		const table = $(`#${tableId}`).DataTable({
			"pageLength": 25,
			"responsive": true,
			"autoWidth": false,
			"columnDefs": [
				{ "orderable": false, "targets": [0, 1] },
				{ "searchable": false, "targets": [0, 1] }
			],
			"order": [
				[2, "asc"],
				[4, "asc"]
			],
			"language": datatableService.defaultLanguageOptions
		});

		table.on('draw', () => {
			combinedService.initCombinedSelection(checkboxClass);

			$('.' + checkboxClass).each(function () {
				const id = this.value;
				const type = $(this).data('type');

				let isChecked = false;
				if (type === 'userRole') {
					isChecked = chosenUserRoleIds.includes(id);
				} else if (type === 'roleGroup') {
					isChecked = chosenRoleGroupIds.includes(id);
				}

				if (isChecked) {
					$(this).iCheck('check');
				} else {
					$(this).iCheck('uncheck');
				}
			});
		});

		table.on('click', 'td.dt-control', function(e) {
			const tr = $(e.target).closest('tr');
			const row = table.row(tr);
			const roleGroupId = $(e.target).data('rolegroup-id');

			if (row.child.isShown()) {
				row.child.hide();
				$(tr).removeClass('dt-hasChild');
			} else {
				const url = `${detailsUrl}/${roleGroupId}/userroles`;

				let div = $('<div/>')
					.addClass('loading')
					.text('Henter...');

				row.child(div).show();
				$(tr).addClass('dt-hasChild');

				$.ajax({
					url: url,
					success: function (data) {
						div.html(data)
							.removeClass('loading');

						expandableRoleGroupTableService.initUserRoleTable();
					}
				});
			}
		});
	}
	this.initCheckboxes = function(checkboxClass) {
		$('.' + checkboxClass).iCheck({
			checkboxClass: 'icheckbox_square-green',
			radioClass: 'iradio_square-green',
		});
	}
}

function UserRoleService() {
    this.initUserRoles = function(tableId) {
		let urTable = $(`#` + tableId).DataTable({
			"pageLength" : 25,
			"responsive" : true,
			"autoWidth" : false,
			"order": [
				[ 0, "desc" ]
			],
			"language" : datatableService.defaultLanguageOptions
		});

	    return urTable;
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
        const type = userRoleText;

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
                type: type
            });
        }
    }

    this.uncheck =(checkBox) =>{
        const id = checkBox.value;
        const type = userRoleText;

        const idIndex = chosenUserRoleIds.indexOf(id);
        if (idIndex !== -1) {
            chosenUserRoleIds.splice(idIndex, 1);
        }

        chosenRolesDTOs = chosenRolesDTOs.filter(function(dto) {
            return !(dto.id === id && dto.type === type);
        });
    }
}
