

document.addEventListener('DOMContentLoaded', ()=> {
    const constraintHandler = new ConstraintHandler()

    const deleteBtn = document.getElementById('deleteConstraintBtn')
    deleteBtn.addEventListener('click', constraintHandler.onConstraintDeletion)

    const constraintSelect = document.getElementById('constraintList')
    constraintSelect.addEventListener('change', ()=> constraintHandler.onConstraintChange(constraintSelect, deleteBtn))
    constraintHandler.onConstraintChange(constraintSelect, deleteBtn)

    const createBtn = document.getElementById('createConstraintBtn')
    createBtn.addEventListener('click', constraintHandler.onConstraintCreation)

    const createInputField = document.getElementById('constraintCreationInput')
    createInputField.addEventListener('change', ()=> constraintHandler.onCreateInputChange(createInputField, createBtn))
    constraintHandler.onCreateInputChange(createInputField, createBtn)

    $("#onlyRecommendRolesCheckbox").change(function() {
		$("#onlyRecommendRoles").val(this.checked);
	});

	$("#single-table-checkbox").change(function() {
		$("#showSingleTableInRequestApproveEnabled").val(this.checked);
	});

	$("#allow-self-approval-checkbox").change(function() {
		$("#allowSelfApproval").val(this.checked);
	});

	$('.checkbox-email').on("change", function() {
		let name = $(this).data("name")
		$('#approverEmail_' + name).prop("disabled", !$(this).is(":checked")).change();
	});

	$('.checkbox-email').trigger("change");

	// INIT code to disable if NONE/AUTOMATIC is pre-selected
	$('.requesterSetting:checked').each(function() {
		onRequesterCheckBoxClick(this);
	});
	$('.approverSetting:checked').each(function() {
		onApproverCheckboxClick(this);
		let id = $(this).data("id");
		if (id !== "AUTOMATIC") {
			$('#approver_' + id + '_email').show();
		}
	});
})

function onApproverCheckboxClick(obj) {
	let id = $(obj).data("id");
	if (id === "AUTOMATIC") {
		if ($(obj).is(":checked")) {
			// disable all except AUTOMATIC
			$(".approverSetting").not(obj).prop("checked", false).prop("disabled", true);
			$('.inputEmailField').val("");
			$('.role-request-email').hide();
			$('.checkbox-email').prop("checked", false);
		} else {
			// re-enable all if AUTOMATIC is unchecked
			$(".approverSetting").prop("disabled", false);
		}
	} else {
		// If any other is clicked, make sure AUTOMATIC is unchecked & enabled
		$("#approver_AUTOMATIC").prop("checked", false).prop("disabled", false);
		const emailDiv = $('#approver_' + id + '_email');

		if ($(obj).is(":checked")) {
			emailDiv.show();
		} else {
			emailDiv.hide();
			emailDiv.find('.checkbox-email').prop("checked", false);
			emailDiv.find('.inputEmailField').val("");
		}
	}
}

function onEmailCheckboxClick(obj) {
	const emailField = $(obj).closest('.role-request-email').find('.inputEmailField');

	if ($(obj).is(":checked")) {
		emailField.prop("disabled", false).focus();
	} else {
		emailField.val("").prop("disabled", true);
	}
}

function onRequesterCheckBoxClick(obj) {
	let id = $(obj).data("id");
	if (id === "NONE") {
		if ($(obj).is(":checked")) {
			// disable all except NONE
			$(".requesterSetting").not(obj).prop("checked", false).prop("disabled", true);
		} else {
			// re-enable all if NONE is unchecked
			$(".requesterSetting").prop("disabled", false);
		}
	} else {
		// If any other is clicked, make sure NONE is unchecked & enabled
		$("#request_NONE").prop("checked", false).prop("disabled", false);
	}
}


class ConstraintHandler {
    constructor () {}

    onCreateInputChange(createInputField, createBtn) {
        if (createInputField.value.length > 0) {
            createBtn.disabled = false
        } else {
            createBtn.disabled = true
        }
    }

    onConstraintChange (constraintSelect, deleteBtn) {

        const selected = Array.from( constraintSelect.options).filter( option => option.selected)
        if (selected.length < 1) {
            deleteBtn.disabled = true
        } else {
            deleteBtn.disabled = false
        }
    }

    onConstraintDeletion() {
        const options = document.getElementById('constraintList').options

        const selected = Array.from( options).filter( option => option.selected)
        if (selected.length < 1) {
            return null
        }

        const selectedValues = selected.map(selectedOption => selectedOption.value)
        const selectedText = selected.map(selectedOption => selectedOption.textContent)

        swal({
            html: true,
            title : 'Du er ved at slette følgende værdier:',
            text : `${selectedText.join(', ')}`,
            type : "warning",
            showCancelButton : true,
            confirmButtonColor : "#DD6B55",
            confirmButtonText : 'Slet',
            cancelButtonText : 'Fortryd',
            closeOnConfirm : true,
            closeOnCancel : true
        },
        (isConfirmed) => {
            if (isConfirmed) {

                const response = fetch(deleteURL + '?constraintIds='+ selectedValues.join(','), {
                    method: "DELETE",
                    headers: {
                        'X-CSRF-TOKEN': token
                    }
                })

                if (!response.ok) {
                    defaultErrorHandler(response)
                }

               window.location.reload()
            }
        });
    }

    onConstraintCreation() {
        const inputField = document.getElementById('constraintCreationInput')

        const value = inputField?.value

        if (value) {
            const response = fetch(createURL, {
                method: "POST",
                headers: {
                    'X-CSRF-TOKEN': token,
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'

                },
                body: JSON.stringify({
                    value: value
                })
            })

            if (!response.ok) {
                defaultErrorHandler(response)
            }

               window.location.reload()
        }
    }
}

