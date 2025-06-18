

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
})



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

